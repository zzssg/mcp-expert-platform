package com.bank.platform.mcp.engine.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * A concrete {@link GeminiClient} that talks to the Vertex AI {@code generateContent}
 * REST API (Part 5.10). It targets a configurable {@code baseUrl} — typically an
 * in-house LLM proxy that fronts Gemini — using a Bearer token, so the whole platform
 * reaches the model through one governed egress point. No gRPC SDK and no Google
 * client library: just the JDK {@link HttpClient} and Jackson, which keeps the
 * dependency surface (and the data-governance attack surface) minimal.
 *
 * <p>Endpoint shape:
 * <pre>POST {baseUrl}/v1/projects/{project}/locations/{location}/publishers/google/models/{model}:generateContent</pre>
 * The {@code model} comes from the per-request {@link ModelProfile}, so migrating
 * Gemini 2.5 → 3.x is a profile change. Output is requested as JSON
 * ({@code responseMimeType: application/json}); the engine's parser validates it
 * against the expert's schema and repairs minor deviations.
 *
 * <p>Transport/throttling failures (timeouts, 429, 5xx) surface as <em>retryable</em>
 * {@link GeminiException}s; client errors (4xx) and safety blocks as <em>terminal</em>
 * ones — so {@code ResilientGeminiClient} retries and trips its breaker correctly.
 * Stateless and thread-safe; share one instance.
 */
public final class VertexRestGeminiClient implements GeminiClient {

    private static final int MAX_ERROR_BODY = 500;

    private final String baseUrl; // normalized, no trailing slash
    private final String project;
    private final String location;
    private final Supplier<String> bearerTokenSupplier;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public VertexRestGeminiClient(String baseUrl, String project, String location,
                                  Supplier<String> bearerTokenSupplier, Duration requestTimeout,
                                  HttpClient httpClient, ObjectMapper mapper) {
        this.baseUrl = stripTrailingSlash(require(baseUrl, "baseUrl"));
        this.project = require(project, "project");
        this.location = require(location, "location");
        this.bearerTokenSupplier = bearerTokenSupplier == null ? () -> null : bearerTokenSupplier;
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(60) : requestTimeout;
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    /** Convenience factory with a sensible default HTTP client + mapper. */
    public static VertexRestGeminiClient create(String baseUrl, String project, String location,
                                                Supplier<String> bearerTokenSupplier,
                                                Duration requestTimeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new VertexRestGeminiClient(baseUrl, project, location, bearerTokenSupplier,
                requestTimeout, client, new ObjectMapper());
    }

    @Override
    public GeminiResponse generate(GeminiRequest request) throws GeminiException {
        String modelId = request.profile().modelId();
        String url = baseUrl + "/v1/projects/" + project + "/locations/" + location
                + "/publishers/google/models/" + modelId + ":generateContent";

        HttpRequest httpRequest = buildHttpRequest(url, body(request));

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new GeminiException("Vertex proxy call failed: " + e.getMessage(), e); // retryable
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GeminiException.nonRetryable("interrupted awaiting Vertex proxy response", e);
        }

        int status = response.statusCode();
        if (status / 100 != 2) {
            throw httpError(status, response.body());
        }
        return parse(response.body(), modelId);
    }

    private HttpRequest buildHttpRequest(String url, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        String token = bearerTokenSupplier.get();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    /** Builds the Vertex {@code generateContent} request body from the engine request. */
    private String body(GeminiRequest request) {
        ObjectNode root = mapper.createObjectNode();

        if (!request.systemPrompt().isBlank()) {
            ObjectNode sys = root.putObject("systemInstruction");
            sys.putArray("parts").addObject().put("text", request.systemPrompt());
        }

        ArrayNode contents = root.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", request.userPrompt());

        ObjectNode gen = root.putObject("generationConfig");
        gen.put("temperature", request.profile().temperature());
        gen.put("maxOutputTokens", request.profile().maxOutputTokens());
        // JSON mode. Native responseSchema binding is a follow-up (Vertex accepts only an
        // OpenAPI-subset schema); the engine validates against the full schema regardless.
        gen.put("responseMimeType", "application/json");

        try {
            return mapper.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw GeminiException.nonRetryable("failed to serialize Vertex request: " + e.getMessage(), e);
        }
    }

    private GeminiResponse parse(String responseBody, String modelId) {
        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (Exception e) {
            throw new GeminiException("unparseable Vertex response: " + e.getMessage(), e);
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            String blockReason = root.path("promptFeedback").path("blockReason").asText("");
            throw GeminiException.nonRetryable("Vertex returned no candidates"
                    + (blockReason.isBlank() ? "" : " (blocked: " + blockReason + ")"));
        }

        JsonNode candidate = candidates.get(0);
        String finishReason = candidate.path("finishReason").asText("STOP");

        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            text.append(part.path("text").asText(""));
        }
        if (text.length() == 0) {
            throw GeminiException.nonRetryable(
                    "Vertex returned an empty candidate (finishReason=" + finishReason + ")");
        }

        JsonNode usage = root.path("usageMetadata");
        int inputTokens = usage.path("promptTokenCount").asInt(0);
        int outputTokens = usage.path("candidatesTokenCount").asInt(0);
        String modelVersion = root.path("modelVersion").asText(modelId);

        return new GeminiResponse(text.toString(), inputTokens, outputTokens, false, modelVersion, finishReason);
    }

    private GeminiException httpError(int status, String body) {
        String message = "Vertex proxy HTTP " + status + ": " + truncate(body);
        // 429 + 5xx are transient; other 4xx (auth, bad request, not found) are terminal.
        boolean retryable = status == 429 || status >= 500;
        return retryable ? new GeminiException(message) : GeminiException.nonRetryable(message);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ERROR_BODY ? s : s.substring(0, MAX_ERROR_BODY) + "…";
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value;
    }
}

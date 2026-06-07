package com.bank.platform.mcp.engine.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Exercises the REST adapter over a real loopback HTTP server (JDK built-in, no extra
 * deps), asserting both the request it sends to the Vertex proxy and how it maps
 * responses and HTTP failures.
 */
class VertexRestGeminiClientTest {

    private static final String SUCCESS_BODY = """
            {
              "candidates": [
                {
                  "content": {"role": "model", "parts": [{"text": "{\\"findings\\":[]}"}]},
                  "finishReason": "STOP"
                }
              ],
              "usageMetadata": {"promptTokenCount": 12, "candidatesTokenCount": 3, "totalTokenCount": 15},
              "modelVersion": "gemini-2.5-pro-2026-05"
            }
            """;

    private record Captured(String method, String path, String authorization, String body) {}

    /** Starts a loopback server returning a fixed status/body and capturing the request. */
    private HttpServer start(int status, String responseBody, AtomicReference<Captured> sink) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            sink.set(new Captured(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        return server;
    }

    private GeminiRequest request() {
        return new GeminiRequest("You are a reviewer.", "Review this diff.",
                "{\"type\":\"object\"}", ModelProfile.analysis("gemini-2.5-pro"));
    }

    private VertexRestGeminiClient client(HttpServer server) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return VertexRestGeminiClient.create(baseUrl, "proj-1", "us-central1",
                () -> "test-token", Duration.ofSeconds(5));
    }

    @Test
    void sendsVertexRequestAndMapsResponse() throws IOException {
        var captured = new AtomicReference<Captured>();
        HttpServer server = start(200, SUCCESS_BODY, captured);
        try {
            GeminiResponse response = client(server).generate(request());

            // Response mapping.
            assertThat(response.rawJson()).isEqualTo("{\"findings\":[]}");
            assertThat(response.inputTokens()).isEqualTo(12);
            assertThat(response.outputTokens()).isEqualTo(3);
            assertThat(response.modelVersion()).isEqualTo("gemini-2.5-pro-2026-05");
            assertThat(response.finishReason()).isEqualTo("STOP");

            // Request shape: path, auth, and a Vertex-flavoured JSON body.
            Captured c = captured.get();
            assertThat(c.method()).isEqualTo("POST");
            assertThat(c.path()).isEqualTo(
                    "/v1/projects/proj-1/locations/us-central1/publishers/google/models/gemini-2.5-pro:generateContent");
            assertThat(c.authorization()).isEqualTo("Bearer test-token");
            assertThat(c.body())
                    .contains("\"systemInstruction\"")
                    .contains("You are a reviewer.")
                    .contains("Review this diff.")
                    .contains("\"responseMimeType\":\"application/json\"")
                    .contains("\"maxOutputTokens\":8000");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void serverErrorIsRetryable() throws IOException {
        HttpServer server = start(503, "upstream unavailable", new AtomicReference<>());
        try {
            GeminiException ex = catchThrowableOfType(
                    GeminiException.class, () -> client(server).generate(request()));
            assertThat(ex).isNotNull();
            assertThat(ex.retryable()).isTrue();
            assertThat(ex.getMessage()).contains("503");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clientErrorIsTerminal() throws IOException {
        HttpServer server = start(400, "bad request", new AtomicReference<>());
        try {
            GeminiException ex = catchThrowableOfType(
                    GeminiException.class, () -> client(server).generate(request()));
            assertThat(ex).isNotNull();
            assertThat(ex.retryable()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emptyCandidatesAreTerminal() throws IOException {
        HttpServer server = start(200, "{\"candidates\":[]}", new AtomicReference<>());
        try {
            GeminiException ex = catchThrowableOfType(
                    GeminiException.class, () -> client(server).generate(request()));
            assertThat(ex).isNotNull();
            assertThat(ex.retryable()).isFalse();
            assertThat(ex.getMessage()).contains("no candidates");
        } finally {
            server.stop(0);
        }
    }
}

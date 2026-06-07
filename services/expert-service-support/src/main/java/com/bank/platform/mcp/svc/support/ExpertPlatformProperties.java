package com.bank.platform.mcp.svc.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalized configuration shared by every expert service. {@code modelId} selects
 * the Gemini version surfaced in result envelopes and used by the model profile
 * (Part 5.10) — swap it to migrate 2.5 → 3.x without code changes. {@link Vertex}
 * configures the single REST egress to the in-house Gemini proxy.
 */
@ConfigurationProperties(prefix = "platform.expert")
public class ExpertPlatformProperties {

    /** Vertex/Gemini model id reported by the experts, e.g. {@code gemini-2.5-pro}. */
    private String modelId = "gemini-2.5-pro";

    private final Vertex vertex = new Vertex();

    private final Budget budget = new Budget();

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Vertex getVertex() {
        return vertex;
    }

    public Budget getBudget() {
        return budget;
    }

    /**
     * Token + latency budget for expert calls (Part 3.1 / 5.5). Each field is honored
     * at a different stage:
     * <ul>
     *   <li>{@code maxInputTokens} → deterministic context reduction (the diff is kept,
     *       off-hunk context is trimmed/dropped to fit) via the engine's budget planner;</li>
     *   <li>{@code maxOutputTokens} → the model's output cap on the expert's model profile;</li>
     *   <li>{@code deadline} → the resilient client's per-attempt timeout.</li>
     * </ul>
     */
    public static class Budget {
        /** Input-token cap that drives prompt context reduction. */
        private int maxInputTokens = 60_000;
        /** Output-token cap passed to the model. */
        private int maxOutputTokens = 8_000;
        /** Per-attempt model-call timeout. */
        private Duration deadline = Duration.ofSeconds(30);

        /** Builds the contract envelope carried on each {@code ExpertRequest}. Validates eagerly. */
        public com.bank.platform.mcp.contract.Budget toContractBudget() {
            return new com.bank.platform.mcp.contract.Budget(maxInputTokens, maxOutputTokens, deadline.toMillis());
        }

        public int getMaxInputTokens() { return maxInputTokens; }
        public void setMaxInputTokens(int maxInputTokens) { this.maxInputTokens = maxInputTokens; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
        public Duration getDeadline() { return deadline; }
        public void setDeadline(Duration deadline) { this.deadline = deadline; }
    }

    /**
     * The Vertex AI {@code generateContent} REST endpoint — typically an in-house LLM
     * proxy fronting Gemini. The platform reaches the model only through this single,
     * governed egress (Part 9 R1). When {@link #isConfigured()} is false the service
     * still boots and advertises its catalog, returning ERROR on calls.
     */
    public static class Vertex {
        /** Proxy base URL, e.g. {@code https://llm-proxy.bank.internal}. */
        private String baseUrl;
        /** GCP project segment of the endpoint path. */
        private String project;
        /** Location segment of the endpoint path. */
        private String location = "us-central1";
        /** Static Bearer token. Inject via env; rotate out-of-band, or replace with an OAuth supplier. */
        private String token;
        /** Per-request HTTP timeout (the resilience layer adds its own per-attempt cap). */
        private Duration requestTimeout = Duration.ofSeconds(60);

        public boolean isConfigured() {
            return notBlank(baseUrl) && notBlank(project) && notBlank(location);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getProject() { return project; }
        public void setProject(String project) { this.project = project; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    }
}

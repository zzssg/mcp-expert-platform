package com.bank.platform.mcp.engine.client;

/**
 * The model + decoding parameters for one expert (Part 5.10 / 9.5). Holding the
 * model id here — not hard-coded in the engine — is what makes the Gemini 2.5 →
 * 3.x move a configuration change rather than a code change: an expert swaps its
 * {@code ModelProfile} and nothing in the pipeline is touched.
 *
 * @param modelId         Vertex model identifier, e.g. {@code gemini-2.5-pro}
 * @param label           short profile label surfaced in diagnostics, e.g. {@code analysis}
 * @param temperature     decoding temperature; experts run at 0–0.2 for determinism (Part 3.4)
 * @param maxOutputTokens hard cap on output tokens for this profile (cost ceiling, Part 5.5)
 */
public record ModelProfile(String modelId, String label, double temperature, int maxOutputTokens) {

    public ModelProfile {
        if (modelId == null || modelId.isBlank())
            throw new IllegalArgumentException("modelId required");
        if (temperature < 0.0 || temperature > 2.0)
            throw new IllegalArgumentException("temperature out of range: " + temperature);
        if (maxOutputTokens <= 0)
            throw new IllegalArgumentException("maxOutputTokens must be > 0");
        label = label == null ? modelId : label;
    }

    /** The deterministic analysis profile used by most experts (temp 0). */
    public static ModelProfile analysis(String modelId) {
        return new ModelProfile(modelId, "analysis", 0.0, 8_000);
    }
}

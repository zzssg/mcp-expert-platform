package com.bank.platform.mcp.engine.client;

/**
 * The raw result of a Gemini call, before any parsing/validation (those are the
 * engine's job, Part 5.6). {@code rawJson} is exactly what the model emitted — the
 * parse layer owns repair and schema validation, so the client stays a thin,
 * swappable transport. Token counts feed the cost meter (Part 2.8 / 5.10).
 *
 * @param rawJson      the model's raw textual output (expected JSON)
 * @param inputTokens  prompt tokens billed
 * @param outputTokens completion tokens billed
 * @param cached       whether the cacheable system block was served from cache
 * @param modelVersion the concrete served model version, e.g. {@code gemini-2.5-pro-2026-05}
 * @param finishReason model stop reason, e.g. {@code STOP}, {@code MAX_TOKENS}, {@code SAFETY}
 */
public record GeminiResponse(
        String rawJson,
        int inputTokens,
        int outputTokens,
        boolean cached,
        String modelVersion,
        String finishReason
) {
    public GeminiResponse {
        rawJson = rawJson == null ? "" : rawJson;
        modelVersion = modelVersion == null ? "unknown" : modelVersion;
        finishReason = finishReason == null ? "STOP" : finishReason;
    }

    public boolean truncated() {
        return "MAX_TOKENS".equals(finishReason);
    }
}

package com.bank.platform.mcp.engine.client;

/**
 * One structured-output call to Gemini (Part 5.4 / T2). The {@code systemPrompt}
 * is the stable, cacheable role/rules block (served from context caching to cut
 * input-token cost); {@code userPrompt} is the dynamic per-call material. When
 * {@code responseSchemaJson} is present the client must enforce structured output
 * ({@code responseMimeType: application/json} + {@code responseSchema}).
 *
 * @param systemPrompt        stable, cache-friendly system block (Part 2.7)
 * @param userPrompt          dynamic task material (diff, digest, signatures)
 * @param responseSchemaJson  JSON Schema the model output must satisfy; nullable to skip
 * @param profile             model + decoding parameters
 */
public record GeminiRequest(
        String systemPrompt,
        String userPrompt,
        String responseSchemaJson,
        ModelProfile profile
) {
    public GeminiRequest {
        if (userPrompt == null || userPrompt.isBlank())
            throw new IllegalArgumentException("userPrompt required");
        if (profile == null)
            throw new IllegalArgumentException("profile required");
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    public boolean structured() {
        return responseSchemaJson != null && !responseSchemaJson.isBlank();
    }
}

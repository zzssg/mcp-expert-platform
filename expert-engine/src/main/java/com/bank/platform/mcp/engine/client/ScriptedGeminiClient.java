package com.bank.platform.mcp.engine.client;

/**
 * A deterministic, in-memory {@link GeminiClient} that returns canned output. Two
 * roles:
 * <ul>
 *   <li>it lets the whole engine + every expert be exercised JDK-only — no Vertex,
 *       no network, no credentials — exactly like the deterministic-core smoke
 *       tests, and it is shareable across module test suites (hence {@code main});</li>
 *   <li>it is the backing client for the local stdio "single-tenant" dev mode
 *       (Part 2.1), where a checked-in scripted response replaces a live model.</li>
 * </ul>
 */
public final class ScriptedGeminiClient implements GeminiClient {

    private final String rawJson;
    private final GeminiException failure;
    private final String modelVersion;

    private ScriptedGeminiClient(String rawJson, GeminiException failure, String modelVersion) {
        this.rawJson = rawJson;
        this.failure = failure;
        this.modelVersion = modelVersion;
    }

    /** Returns {@code rawJson} as the model output (modelVersion fixed for assertions). */
    public static ScriptedGeminiClient returning(String rawJson) {
        return new ScriptedGeminiClient(rawJson, null, "gemini-test-1");
    }

    /** Always fails the call, to exercise the engine's degrade-to-ERROR path. */
    public static ScriptedGeminiClient failing(String message) {
        return new ScriptedGeminiClient(null, new GeminiException(message), "gemini-test-1");
    }

    @Override
    public GeminiResponse generate(GeminiRequest request) throws GeminiException {
        if (failure != null) throw failure;
        // Token counts are illustrative; the real client reports Vertex usage.
        int inTok = (request.systemPrompt().length() + request.userPrompt().length()) / 4;
        int outTok = rawJson.length() / 4;
        return new GeminiResponse(rawJson, inTok, outTok, true, modelVersion, "STOP");
    }
}

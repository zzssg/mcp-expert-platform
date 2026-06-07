package com.bank.platform.mcp.engine.client;

/**
 * The single seam between the engine and Vertex AI Gemini (Part 5.10). Everything
 * above this interface is deterministic, testable Java; everything below is the
 * probabilistic model call. Keeping it this thin means:
 * <ul>
 *   <li>the Gemini 2.5 → 3.x migration is a {@link ModelProfile} change;</li>
 *   <li>the whole engine is unit-testable with a scripted in-memory client, no
 *       network and no Vertex credentials (mirrors the JDK-only smoke tests);</li>
 *   <li>resilience (retry, breaker, timeout — Part 5.9) decorates this interface
 *       rather than being entangled with parsing/verification.</li>
 * </ul>
 */
public interface GeminiClient {

    /**
     * Performs one structured-output generation.
     *
     * @throws GeminiException on transport, timeout, quota, or safety failures
     */
    GeminiResponse generate(GeminiRequest request) throws GeminiException;
}

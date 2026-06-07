package com.bank.platform.mcp.svc.support;

import com.bank.platform.mcp.engine.client.GeminiClient;
import com.bank.platform.mcp.engine.client.GeminiException;
import com.bank.platform.mcp.engine.client.GeminiRequest;
import com.bank.platform.mcp.engine.client.GeminiResponse;

/**
 * The fallback {@link GeminiClient} when neither the Vertex REST proxy nor a Spring AI
 * {@link org.springframework.ai.chat.model.ChatModel} is configured. It fails every
 * call with a clear, <em>terminal</em> message so the engine returns a clean
 * {@code ERROR} envelope instead of the service failing to start — keeping the MCP
 * server bootable and its tool catalog discoverable without model access (CI, smoke tests).
 */
public final class UnconfiguredGeminiClient implements GeminiClient {

    @Override
    public GeminiResponse generate(GeminiRequest request) throws GeminiException {
        throw GeminiException.nonRetryable(
                "No model is configured. Set platform.expert.vertex.base-url/project/location (the Vertex "
                        + "REST proxy) or add a Spring AI model starter to enable analysis.");
    }
}

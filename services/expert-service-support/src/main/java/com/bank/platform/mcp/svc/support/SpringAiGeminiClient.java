package com.bank.platform.mcp.svc.support;

import com.bank.platform.mcp.engine.client.GeminiClient;
import com.bank.platform.mcp.engine.client.GeminiException;
import com.bank.platform.mcp.engine.client.GeminiRequest;
import com.bank.platform.mcp.engine.client.GeminiResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

/**
 * A {@link GeminiClient} over Spring AI's provider-agnostic {@link ChatModel}, used
 * when a model starter is on the classpath instead of the Vertex REST proxy. Whichever
 * starter supplies the {@code ChatModel} bean determines the model/provider, so the
 * model stays a dependency/config choice, not a code change (Part 5.10, tenet T7).
 */
public final class SpringAiGeminiClient implements GeminiClient {

    private final ChatModel chatModel;
    private final String modelId;

    public SpringAiGeminiClient(ChatModel chatModel, String modelId) {
        if (chatModel == null) throw new IllegalArgumentException("chatModel required");
        this.chatModel = chatModel;
        this.modelId = modelId == null ? "unknown" : modelId;
    }

    @Override
    public GeminiResponse generate(GeminiRequest request) throws GeminiException {
        try {
            String raw = request.systemPrompt().isBlank()
                    ? chatModel.call(new UserMessage(request.userPrompt()))
                    : chatModel.call(new SystemMessage(request.systemPrompt()),
                                     new UserMessage(request.userPrompt()));
            return new GeminiResponse(raw, 0, 0, false, modelId, "STOP");
        } catch (RuntimeException e) {
            throw new GeminiException("chat model call failed: " + e.getMessage(), e);
        }
    }
}

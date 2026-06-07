package com.bank.platform.mcp.svc.support;

import com.bank.platform.mcp.engine.GeminiExpertEngine;
import com.bank.platform.mcp.engine.client.GeminiClient;
import com.bank.platform.mcp.engine.client.VertexRestGeminiClient;
import com.bank.platform.mcp.engine.resilience.ResilienceConfig;
import com.bank.platform.mcp.engine.resilience.ResilientGeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the model-egress seam for any expert service that depends on this
 * module. It is the <strong>single place</strong> the platform decides how to reach
 * Gemini (Part 5.10 / 9 R1) and always wraps the chosen transport in the resilience
 * policies (retry / circuit breaker / timeout, Part 5.9):
 * <ol>
 *   <li><b>Vertex REST proxy</b> when {@code platform.expert.vertex} is configured —
 *       the production path: one governed egress to the in-house Gemini proxy;</li>
 *   <li>a Spring AI {@link ChatModel} bean, if a model starter is on the classpath;</li>
 *   <li>a clearly-failing fallback, so the server still boots and serves its catalog.</li>
 * </ol>
 * Both beans are {@link ConditionalOnMissingBean}, so a service may override either
 * (e.g. inject a stub client in tests).
 */
@AutoConfiguration
@EnableConfigurationProperties(ExpertPlatformProperties.class)
public class ExpertModelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExpertModelAutoConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public GeminiClient geminiClient(ObjectProvider<ChatModel> chatModelProvider,
                                     ExpertPlatformProperties properties) {
        // The budget deadline becomes the resilient client's per-attempt timeout (Part 5.9).
        ResilienceConfig resilience = ResilienceConfig.defaults()
                .withCallTimeout(properties.getBudget().getDeadline());
        return new ResilientGeminiClient(selectTransport(chatModelProvider, properties),
                resilience, "gemini");
    }

    @Bean
    @ConditionalOnMissingBean
    public GeminiExpertEngine geminiExpertEngine(GeminiClient geminiClient) {
        return new GeminiExpertEngine(geminiClient);
    }

    private GeminiClient selectTransport(ObjectProvider<ChatModel> chatModelProvider,
                                         ExpertPlatformProperties properties) {
        ExpertPlatformProperties.Vertex vertex = properties.getVertex();
        if (vertex.isConfigured()) {
            log.info("Gemini client: Vertex REST proxy at {} (project={}, location={})",
                    vertex.getBaseUrl(), vertex.getProject(), vertex.getLocation());
            return VertexRestGeminiClient.create(
                    vertex.getBaseUrl(), vertex.getProject(), vertex.getLocation(),
                    vertex::getToken, vertex.getRequestTimeout());
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null) {
            log.info("Gemini client: Spring AI ChatModel ({})", chatModel.getClass().getSimpleName());
            return new SpringAiGeminiClient(chatModel, properties.getModelId());
        }
        log.warn("Gemini client: NONE configured — set platform.expert.vertex.* or add a model starter. "
                + "Tool calls will return ERROR.");
        return new UnconfiguredGeminiClient();
    }
}

package com.bank.platform.mcp.svc.code.config;

import com.bank.platform.mcp.engine.GeminiExpertEngine;
import com.bank.platform.mcp.expert.codereview.CodeReviewExpert;
import com.bank.platform.mcp.svc.code.tools.CodeExpertTools;
import com.bank.platform.mcp.svc.support.ExpertPlatformProperties;
import com.bank.platform.mcp.svc.support.metrics.MeteredExpert;
import com.bank.platform.mcp.svc.support.metrics.UsageMetrics;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the code-group experts into Spring beans and exposes them to the MCP server.
 * The model client + {@link GeminiExpertEngine} are provided by
 * {@code expert-service-support}'s auto-configuration, so this class only declares
 * what is specific to this deployable (tenet T7).
 */
@Configuration
public class ExpertPlatformConfig {

    @Bean
    public CodeReviewExpert codeReviewExpert(GeminiExpertEngine engine,
                                             ExpertPlatformProperties properties) {
        return new CodeReviewExpert(engine, properties.getModelId(),
                properties.getBudget().getMaxOutputTokens());
    }

    @Bean
    public CodeExpertTools codeExpertTools(CodeReviewExpert codeReviewExpert,
                                           ExpertPlatformProperties properties,
                                           UsageMetrics usageMetrics) {
        // Wrap the expert so every call's token usage is metered (Part 2.9).
        return new CodeExpertTools(new MeteredExpert(codeReviewExpert, usageMetrics),
                properties.getBudget().toContractBudget());
    }

    /** Registers the code-group experts as MCP tools; the autoconfig publishes them in tools/list. */
    @Bean
    public ToolCallbackProvider expertToolCallbackProvider(CodeExpertTools codeExpertTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(codeExpertTools)
                .build();
    }
}

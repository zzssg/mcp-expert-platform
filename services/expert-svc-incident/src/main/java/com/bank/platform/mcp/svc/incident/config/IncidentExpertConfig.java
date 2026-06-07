package com.bank.platform.mcp.svc.incident.config;

import com.bank.platform.mcp.engine.GeminiExpertEngine;
import com.bank.platform.mcp.expert.stacktrace.StacktraceAnalyzerExpert;
import com.bank.platform.mcp.svc.incident.tools.IncidentExpertTools;
import com.bank.platform.mcp.svc.support.ExpertPlatformProperties;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the incident-group experts into Spring beans and exposes them to the MCP
 * server. The model client + {@link GeminiExpertEngine} come from
 * {@code expert-service-support}'s auto-configuration, so this class only declares
 * what is specific to this deployable (tenet T7).
 */
@Configuration
public class IncidentExpertConfig {

    @Bean
    public StacktraceAnalyzerExpert stacktraceAnalyzerExpert(GeminiExpertEngine engine,
                                                             ExpertPlatformProperties properties) {
        return new StacktraceAnalyzerExpert(engine, properties.getModelId(),
                properties.getBudget().getMaxOutputTokens());
    }

    @Bean
    public IncidentExpertTools incidentExpertTools(StacktraceAnalyzerExpert stacktraceAnalyzerExpert,
                                                   ExpertPlatformProperties properties) {
        return new IncidentExpertTools(stacktraceAnalyzerExpert, properties.getBudget().toContractBudget());
    }

    /** Registers the incident-group experts as MCP tools; the autoconfig publishes them in tools/list. */
    @Bean
    public ToolCallbackProvider incidentToolCallbackProvider(IncidentExpertTools incidentExpertTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(incidentExpertTools)
                .build();
    }
}

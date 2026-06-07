package com.bank.platform.mcp.svc.incident;

import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Status;
import com.bank.platform.mcp.svc.incident.tools.IncidentExpertTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the incident service context offline (no model configured ⇒ fallback client)
 * and asserts the MCP wiring: stacktrace_analyzer is registered as a discoverable
 * tool with a generated schema, and a call degrades to a clean ERROR envelope —
 * proving the shared support auto-configuration supplied the engine + client beans.
 */
@SpringBootTest(properties = {
        "spring.ai.mcp.server.name=expert-svc-incident",
        "spring.ai.mcp.server.version=1.0.0",
        "spring.main.web-application-type=servlet",
        // Force the unconfigured-model path regardless of any local .env.
        "platform.expert.vertex.base-url="
})
class ExpertSvcIncidentApplicationTests {

    @Autowired
    ToolCallbackProvider toolCallbackProvider;

    @Autowired
    IncidentExpertTools incidentExpertTools;

    @Test
    void registersStacktraceAnalyzerAsAnMcpTool() {
        Set<String> names = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(tc -> tc.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertThat(names).contains("stacktrace_analyzer");
    }

    @Test
    void toolInputSchemasAreGenerated() {
        for (ToolCallback tc : toolCallbackProvider.getToolCallbacks()) {
            assertThat(tc.getToolDefinition().inputSchema())
                    .as("input schema for %s", tc.getToolDefinition().name())
                    .isNotBlank();
        }
    }

    @Test
    void callDegradesToErrorWithoutAModelConfigured() {
        ExpertResult result = incidentExpertTools.stacktraceAnalyzer(
                "java.lang.NullPointerException\n\tat com.bank.A.b(A.java:1)", List.of("com.bank"));

        assertThat(result.tool()).isEqualTo("stacktrace_analyzer");
        assertThat(result.status()).isEqualTo(Status.ERROR);
        assertThat(result.limitations()).anyMatch(s -> s.toLowerCase().contains("no model is configured"));
    }
}

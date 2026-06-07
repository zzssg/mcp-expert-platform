package com.bank.platform.mcp.svc.code;

import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Status;
import com.bank.platform.mcp.svc.code.tools.CodeExpertTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full Spring context offline (no model configured ⇒ the fallback client)
 * and asserts the MCP wiring is sound: the code-group expert is registered as a
 * discoverable tool, schemas generate, and a call degrades to a clean ERROR envelope
 * — proving the server boots and its catalog is usable without model credentials, and
 * that the shared support auto-configuration supplied the engine + client beans.
 */
@SpringBootTest(properties = {
        "spring.ai.mcp.server.name=expert-svc-code",
        "spring.ai.mcp.server.version=1.0.0",
        "spring.main.web-application-type=servlet",
        // Force the unconfigured-model path regardless of any local .env.
        "platform.expert.vertex.base-url="
})
class ExpertSvcCodeApplicationTests {

    @Autowired
    ToolCallbackProvider toolCallbackProvider;

    @Autowired
    CodeExpertTools codeExpertTools;

    @Test
    void registersCodeReviewExpertAsAnMcpTool() {
        Set<String> names = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(tc -> tc.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertThat(names).contains("code_review_expert");
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
        ExpertResult result = codeExpertTools.codeReviewExpert(
                "diff --git a/A.java b/A.java\n+ // change", "SECURITY", null);

        assertThat(result.tool()).isEqualTo("code_review_expert");
        assertThat(result.status()).isEqualTo(Status.ERROR);
        assertThat(result.limitations()).anyMatch(s -> s.toLowerCase().contains("no model is configured"));
    }
}

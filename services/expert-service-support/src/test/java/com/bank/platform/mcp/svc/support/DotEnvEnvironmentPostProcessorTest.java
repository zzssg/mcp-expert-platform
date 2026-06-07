package com.bank.platform.mcp.svc.support;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class DotEnvEnvironmentPostProcessorTest {

    @Test
    void parsesKeyValuesCommentsExportsAndQuotes() {
        Map<String, Object> values = DotEnvEnvironmentPostProcessor.parse(List.of(
                "# a comment",
                "",
                "VERTEX_BASE_URL=https://proxy.internal",
                "export GEMINI_MODEL_ID=gemini-2.5-pro",
                "EXPERT_DEADLINE='30s'",
                "QUOTED=\"with spaces\"",
                "EMPTY=",
                "no_equals_sign",
                "  SPACED_KEY  =  trimmed  "));

        assertThat(values)
                .containsEntry("VERTEX_BASE_URL", "https://proxy.internal")
                .containsEntry("GEMINI_MODEL_ID", "gemini-2.5-pro")
                .containsEntry("EXPERT_DEADLINE", "30s")
                .containsEntry("QUOTED", "with spaces")
                .containsEntry("EMPTY", "")
                .containsEntry("SPACED_KEY", "trimmed")
                .doesNotContainKey("no_equals_sign");
    }

    @Test
    void appliesDotEnvAsLowestPrecedencePropertySource() throws IOException {
        Path envFile = Files.createTempFile("dotenv-test", ".env");
        Files.writeString(envFile,
                "VERTEX_PROJECT=proj-from-dotenv\nEXPERT_MAX_OUTPUT_TOKENS=1234\n",
                StandardCharsets.UTF_8);
        System.setProperty("dotenv.path", envFile.toString());
        try {
            var environment = new StandardEnvironment();
            // DeferredLogFactory's only abstract method is getLog(Supplier<Log>).
            var processor = new DotEnvEnvironmentPostProcessor((Supplier<org.apache.commons.logging.Log> s) -> s.get());

            processor.postProcessEnvironment(environment, null);

            assertThat(environment.getPropertySources().contains(
                    DotEnvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isTrue();
            assertThat(environment.getProperty("VERTEX_PROJECT")).isEqualTo("proj-from-dotenv");
            assertThat(environment.getProperty("EXPERT_MAX_OUTPUT_TOKENS")).isEqualTo("1234");
        } finally {
            System.clearProperty("dotenv.path");
            Files.deleteIfExists(envFile);
        }
    }

    @Test
    void osEnvironmentVariablesOverrideDotEnv() throws IOException {
        // Pick a real OS env var and assert .env does NOT clobber it (dotenv semantics).
        Map.Entry<String, String> realEnv = System.getenv().entrySet().stream()
                .filter(e -> e.getKey().matches("[A-Za-z_][A-Za-z0-9_]*"))
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(realEnv != null, "no OS env var to test against");

        Path envFile = Files.createTempFile("dotenv-test", ".env");
        Files.writeString(envFile, realEnv.getKey() + "=dotenv-should-not-win\n", StandardCharsets.UTF_8);
        System.setProperty("dotenv.path", envFile.toString());
        try {
            var environment = new StandardEnvironment();
            new DotEnvEnvironmentPostProcessor((Supplier<org.apache.commons.logging.Log> s) -> s.get())
                    .postProcessEnvironment(environment, null);

            assertThat(environment.getProperty(realEnv.getKey())).isEqualTo(realEnv.getValue());
        } finally {
            System.clearProperty("dotenv.path");
            Files.deleteIfExists(envFile);
        }
    }
}

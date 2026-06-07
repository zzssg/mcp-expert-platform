package com.bank.platform.mcp.expert.codereview;

import com.bank.platform.mcp.contract.Budget;
import com.bank.platform.mcp.contract.Epistemic;
import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Finding;
import com.bank.platform.mcp.contract.Options;
import com.bank.platform.mcp.contract.RepoRef;
import com.bank.platform.mcp.contract.Status;
import com.bank.platform.mcp.engine.GeminiExpertEngine;
import com.bank.platform.mcp.engine.client.GeminiClient;
import com.bank.platform.mcp.engine.client.GeminiRequest;
import com.bank.platform.mcp.engine.client.GeminiResponse;
import com.bank.platform.mcp.engine.client.ScriptedGeminiClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the whole expert path: {@code execute} → typed-task parse → diff pre-pass
 * → engine (model call, parse, verify, score) → envelope. The scripted model
 * returns one finding grounded in the diff and one citing a line that isn't there;
 * the grounded one must survive (re-labelled), the fabricated one must be dropped.
 */
class CodeReviewExpertTest {

    private static final String DIFF = String.join("\n",
            "diff --git a/src/Payment.java b/src/Payment.java",
            "--- a/src/Payment.java",
            "+++ b/src/Payment.java",
            "@@ -1,2 +1,4 @@",
            " class Payment {",
            "+  String q = \"SELECT * FROM acct WHERE id=\" + id;",
            "+  Statement st = conn.createStatement();",
            " }");

    // Grounded finding cites src/Payment.java:L2-2 (the injected SQL line).
    // Fabricated finding cites L99-99, which does not exist -> must be dropped.
    private static final String MODEL_OUTPUT = """
            {
              "overallConfidence": 0.6,
              "limitations": ["no build/test execution; static analysis only"],
              "payload": { "summary": { "riskLevel": "HIGH", "blocking": true, "fileCount": 1 } },
              "findings": [
                {
                  "id": "f1",
                  "category": "security",
                  "severity": "CRITICAL",
                  "epistemic": "FACT",
                  "confidence": 0.9,
                  "title": "SQL injection via string concatenation",
                  "detail": "User-controlled id is concatenated into a SQL string.",
                  "recommendation": "Use a PreparedStatement with a bind parameter.",
                  "evidence": [
                    {"type": "diff", "ref": "src/Payment.java:L2-2", "quote": "SELECT * FROM acct"}
                  ]
                },
                {
                  "id": "f2",
                  "category": "resource",
                  "severity": "HIGH",
                  "epistemic": "FACT",
                  "confidence": 0.8,
                  "title": "Unclosed Statement (fabricated citation)",
                  "detail": "Claims a leak at a line that was never provided.",
                  "evidence": [
                    {"type": "diff", "ref": "src/Payment.java:L99-99", "quote": "leak"}
                  ]
                }
              ]
            }
            """;

    private ExpertRequest request() {
        Map<String, Object> task = Map.of(
                "diff", DIFF,
                "reviewProfile", "SECURITY",
                "standards", List.of("no-string-concatenated-sql"));
        return new ExpertRequest(
                "req-1",
                new RepoRef("core-payments", "main", "abc123", "java", "gradle"),
                Options.defaults(),
                Budget.defaults(),
                task);
    }

    private CodeReviewExpert expert(String modelOutput) {
        var engine = new GeminiExpertEngine(ScriptedGeminiClient.returning(modelOutput));
        return new CodeReviewExpert(engine, "gemini-2.5-pro");
    }

    @Test
    void descriptorAdvertisesTheTool() {
        var descriptor = expert(MODEL_OUTPUT).descriptor();
        assertThat(descriptor.id()).isEqualTo("code_review_expert");
        assertThat(descriptor.mcpToolName()).isEqualTo("code_review_expert");
        assertThat(descriptor.costClass().name()).isEqualTo("MEDIUM");
        assertThat(descriptor.tenantScopes()).contains("expert.code_review.invoke");
    }

    @Test
    void groundsRealFindingAndDropsFabrication() {
        ExpertResult result = expert(MODEL_OUTPUT).execute(request());

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.tool()).isEqualTo("code_review_expert");
        assertThat(result.toolVersion()).isEqualTo("1.4.0");
        assertThat(result.promptVersion()).isEqualTo("v3");

        assertThat(result.findings())
                .extracting(Finding::id)
                .containsExactly("f1");

        Finding kept = result.findings().get(0);
        // The model claimed FACT; with a single evidence anchor the verifier caps it.
        assertThat(kept.epistemic()).isEqualTo(Epistemic.INFERENCE);
        assertThat(kept.confidence()).isGreaterThan(0.0);

        assertThat(result.payload()).containsKey("summary");
        assertThat(result.overallConfidence()).isGreaterThan(0.0);
    }

    /** Captures the prompt the engine sends, to inspect template rendering. */
    private static final class CapturingClient implements GeminiClient {
        GeminiRequest captured;
        @Override public GeminiResponse generate(GeminiRequest request) {
            this.captured = request;
            return new GeminiResponse("{\"findings\":[]}", 0, 0, false, "gemini-test-1", "STOP");
        }
    }

    @Test
    void honorsConfiguredOutputTokenBudget() {
        var client = new CapturingClient();
        var expert = new CodeReviewExpert(new GeminiExpertEngine(client), "gemini-2.5-pro", 512);

        expert.execute(request());

        assertThat(client.captured.profile().maxOutputTokens()).isEqualTo(512);
    }

    @Test
    void smallInputBudgetReducesContextAndRecordsIt() {
        var client = new CapturingClient();
        var expert = new CodeReviewExpert(new GeminiExpertEngine(client), "gemini-2.5-pro");

        // A tiny input budget cannot fit the diff after the fixed-overhead reserve.
        Map<String, Object> task = Map.of("diff", DIFF);
        var req = new ExpertRequest("req-budget", null, Options.defaults(),
                new Budget(50, 8000, 30000), task);

        ExpertResult result = expert.execute(req);

        assertThat(result.limitations())
                .anyMatch(s -> s.toLowerCase().contains("context reduced to fit the token budget"));
    }

    @Test
    void rendersDeveloperTemplateCleanlyWithBudgetedBody() {
        var client = new CapturingClient();
        var expert = new CodeReviewExpert(new GeminiExpertEngine(client), "gemini-2.5-pro");

        expert.execute(request());
        String prompt = client.captured.userPrompt();

        // No unrendered template syntax leaked through.
        assertThat(prompt).doesNotContain("{{").doesNotContain("}}");
        // Templated framing is present...
        assertThat(prompt)
                .contains("## Emphasis: security defects first")   // SECURITY profile
                .contains("- no-string-concatenated-sql")           // standards list item
                .contains("- src/Payment.java");                    // changed-files list
        // ...and the budget-fitted body carries the actual diff.
        assertThat(prompt).contains("```diff").contains("SELECT * FROM acct");
    }

    @Test
    void schemaInvalidOutputIsReportedAsPartial() {
        // Missing the required "evidence" array on the only finding -> schema-invalid.
        String invalid = """
                { "findings": [ { "id": "x", "category": "bug", "severity": "LOW", "title": "t" } ] }
                """;
        ExpertResult result = expert(invalid).execute(request());

        assertThat(result.status()).isEqualTo(Status.PARTIAL);
        assertThat(result.limitations()).anyMatch(s -> s.toLowerCase().contains("schema"));
    }
}

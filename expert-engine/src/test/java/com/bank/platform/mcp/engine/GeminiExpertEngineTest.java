package com.bank.platform.mcp.engine;

import com.bank.platform.mcp.analysis.diff.CitationIndex;
import com.bank.platform.mcp.contract.Epistemic;
import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Finding;
import com.bank.platform.mcp.contract.Status;
import com.bank.platform.mcp.engine.client.ModelProfile;
import com.bank.platform.mcp.engine.client.ScriptedGeminiClient;
import com.bank.platform.mcp.engine.verify.EvidenceSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end engine test: the model proposes a grounded finding and a fabricated
 * one; the engine must keep + re-label the grounded one and drop the fabrication —
 * the "Gemini proposes, verifiers dispose" contract running through the full
 * pipeline (call → parse → verify → score → envelope).
 */
class GeminiExpertEngineTest {

    private static final String SOURCE = String.join("\n",
            "class OrderCache {",
            "  Connection c = ds.getConnection();",
            "  // never closed",
            "}");

    private static final String MODEL_OUTPUT = """
            {
              "overallConfidence": 0.7,
              "limitations": ["static analysis only; no runtime data"],
              "payload": { "summary": { "riskLevel": "HIGH" } },
              "findings": [
                {
                  "id": "f-grounded",
                  "category": "resource",
                  "severity": "HIGH",
                  "epistemic": "FACT",
                  "confidence": 0.9,
                  "title": "Unclosed JDBC Connection",
                  "detail": "Connection obtained but never closed.",
                  "recommendation": "Use try-with-resources.",
                  "evidence": [
                    {"type": "file", "ref": "OrderCache.java:L2-2", "quote": "getConnection"}
                  ]
                },
                {
                  "id": "f-fabricated",
                  "category": "security",
                  "severity": "CRITICAL",
                  "epistemic": "FACT",
                  "confidence": 0.95,
                  "title": "Hardcoded secret",
                  "detail": "A secret is hardcoded in the cache.",
                  "evidence": [
                    {"type": "file", "ref": "OrderCache.java:L99-99", "quote": "PASSWORD=hunter2"}
                  ]
                }
              ]
            }
            """;

    private PreparedInput preparedInput() {
        CitationIndex citations = new CitationIndex().addFile("OrderCache.java", SOURCE);
        return new PreparedInput(
                "You are a senior code reviewer. Cite file:line for every finding.",
                "Review:\n" + SOURCE,
                EvidenceSet.of(citations));
    }

    private ExpertProfile profile() {
        // No response schema here — the universal envelope shape is exercised; schema
        // enforcement is covered separately in the parser tests.
        return ExpertProfile.of("code_review_expert", "1.4.0", "v3",
                ModelProfile.analysis("gemini-2.5-pro"), null);
    }

    @Test
    void dropsFabricationKeepsAndRelabelsGroundedFinding() {
        var engine = new GeminiExpertEngine(ScriptedGeminiClient.returning(MODEL_OUTPUT));

        ExpertResult result = engine.run(preparedInput(), profile());

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.tool()).isEqualTo("code_review_expert");
        assertThat(result.modelVersion()).isEqualTo("gemini-test-1");

        assertThat(result.findings())
                .extracting(Finding::id)
                .containsExactly("f-grounded");

        Finding kept = result.findings().get(0);
        // A single evidence anchor caps the label at INFERENCE — the model's FACT claim
        // is NOT trusted at face value.
        assertThat(kept.epistemic()).isEqualTo(Epistemic.INFERENCE);
        assertThat(kept.confidence()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);

        assertThat(result.overallConfidence()).isGreaterThan(0.0);
        assertThat(result.payload()).containsKey("summary");
        assertThat(result.usage().cached()).isTrue();
        assertThat(result.diagnostics().repairAttempts()).isZero();
    }

    @Test
    void recoversFromMarkdownFencedOutputAsPartial() {
        String fenced = "```json\n" + MODEL_OUTPUT + "\n```";
        var engine = new GeminiExpertEngine(ScriptedGeminiClient.returning(fenced));

        ExpertResult result = engine.run(preparedInput(), profile());

        // Output parsed only after stripping the fence -> honest PARTIAL, repair recorded.
        assertThat(result.status()).isEqualTo(Status.PARTIAL);
        assertThat(result.diagnostics().repairAttempts()).isGreaterThan(0);
        assertThat(result.findings()).extracting(Finding::id).containsExactly("f-grounded");
        assertThat(result.limitations()).anyMatch(s -> s.toLowerCase().contains("repair"));
    }

    @Test
    void degradesToErrorWhenModelCallFails() {
        var engine = new GeminiExpertEngine(ScriptedGeminiClient.failing("deadline exceeded"));

        ExpertResult result = engine.run(preparedInput(), profile());

        assertThat(result.status()).isEqualTo(Status.ERROR);
        assertThat(result.findings()).isEmpty();
        assertThat(result.overallConfidence()).isZero();
        assertThat(result.limitations()).anyMatch(s -> s.contains("deadline exceeded"));
    }
}

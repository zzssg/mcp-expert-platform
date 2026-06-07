package com.bank.platform.mcp.expert.stacktrace;

import com.bank.platform.mcp.contract.Budget;
import com.bank.platform.mcp.contract.Epistemic;
import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Finding;
import com.bank.platform.mcp.contract.Options;
import com.bank.platform.mcp.contract.Status;
import com.bank.platform.mcp.engine.GeminiExpertEngine;
import com.bank.platform.mcp.engine.client.ScriptedGeminiClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the stacktrace expert end-to-end on a Spring → Oracle constraint-violation
 * trace. The deterministic pre-pass must resolve the application root frame and
 * decode ORA-00001; the scripted model then cites a real frame symbol + the FACT
 * signal (kept) plus a fabricated symbol (dropped).
 */
class StacktraceAnalyzerExpertTest {

    private static final String TRACE = String.join("\n",
            "org.springframework.dao.DataIntegrityViolationException: could not execute statement",
            "\tat org.springframework.orm.jpa.JpaTransactionManager.commit(JpaTransactionManager.java:1)",
            "\tat com.bank.payments.PaymentService.settle(PaymentService.java:52)",
            "\tat com.bank.payments.PaymentController.pay(PaymentController.java:31)",
            "Caused by: java.sql.SQLIntegrityConstraintViolationException: ORA-00001: unique constraint (BANK.PK_TXN) violated",
            "\tat oracle.jdbc.driver.T4CTTIoer11.processError(T4CTTIoer11.java:494)",
            "\tat com.bank.payments.PaymentService.settle(PaymentService.java:52)");

    private static final String MODEL_OUTPUT = """
            {
              "overallConfidence": 0.72,
              "limitations": ["static trace reasoning; no runtime data"],
              "payload": {
                "failureClass": "ORA_CONSTRAINT_VIOLATION",
                "rootFrame": "com.bank.payments.PaymentService#settle",
                "summary": "Duplicate transaction insert violates PK_TXN"
              },
              "findings": [
                {
                  "id": "rc1",
                  "category": "root-cause",
                  "severity": "HIGH",
                  "epistemic": "INFERENCE",
                  "confidence": 0.8,
                  "title": "Duplicate primary key on transaction insert",
                  "detail": "settle() inserts a transaction whose primary key already exists.",
                  "recommendation": "Make settle idempotent on requestId; pre-check or use MERGE.",
                  "evidence": [
                    {"type": "feed", "ref": "ORA-00001", "quote": "unique constraint"},
                    {"type": "symbol", "ref": "com.bank.payments.PaymentService#settle"}
                  ]
                },
                {
                  "id": "rc2",
                  "category": "root-cause",
                  "severity": "MEDIUM",
                  "epistemic": "FACT",
                  "confidence": 0.9,
                  "title": "Fabricated frame (must be dropped)",
                  "detail": "Cites a symbol that never appears in the trace.",
                  "evidence": [
                    {"type": "symbol", "ref": "com.evil.Ghost#vanish"}
                  ]
                }
              ]
            }
            """;

    private ExpertRequest request() {
        Map<String, Object> task = Map.of(
                "stackTrace", TRACE,
                "applicationPackages", List.of("com.bank"));
        return new ExpertRequest("req-st-1", null, Options.defaults(), Budget.defaults(), task);
    }

    private StacktraceAnalyzerExpert expert(String modelOutput) {
        var engine = new GeminiExpertEngine(ScriptedGeminiClient.returning(modelOutput));
        return new StacktraceAnalyzerExpert(engine, "gemini-2.5-pro");
    }

    @Test
    void descriptorAdvertisesTheTool() {
        var d = expert(MODEL_OUTPUT).descriptor();
        assertThat(d.id()).isEqualTo("stacktrace_analyzer");
        assertThat(d.costClass().name()).isEqualTo("LOW");
        assertThat(d.tenantScopes()).contains("expert.stacktrace.invoke");
    }

    @Test
    void groundsRootCauseInRealFrameAndSignalAndDropsFabrication() {
        ExpertResult result = expert(MODEL_OUTPUT).execute(request());

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.tool()).isEqualTo("stacktrace_analyzer");

        assertThat(result.findings())
                .extracting(Finding::id)
                .containsExactly("rc1"); // rc2 cited a non-existent symbol -> dropped

        Finding kept = result.findings().get(0);
        assertThat(kept.epistemic()).isEqualTo(Epistemic.INFERENCE);
        assertThat(kept.confidence()).isGreaterThan(0.0);

        assertThat(result.payload())
                .containsEntry("failureClass", "ORA_CONSTRAINT_VIOLATION")
                .containsEntry("rootFrame", "com.bank.payments.PaymentService#settle");
        // No source provided -> deterministic pre-pass records that honest limitation.
        assertThat(result.limitations()).anyMatch(s -> s.toLowerCase().contains("no source"));
    }

    @Test
    void notesMissingApplicationPackages() {
        Map<String, Object> task = Map.of("stackTrace", TRACE); // no applicationPackages
        var req = new ExpertRequest("req-st-2", null, Options.defaults(), Budget.defaults(), task);

        ExpertResult result = expert(MODEL_OUTPUT).execute(req);

        assertThat(result.limitations())
                .anyMatch(s -> s.toLowerCase().contains("no application package"));
    }
}

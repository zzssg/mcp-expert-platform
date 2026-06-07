package com.bank.platform.mcp.svc.support.metrics;

import com.bank.platform.mcp.contract.ExpertDescriptor;
import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Status;
import com.bank.platform.mcp.contract.Usage;
import com.bank.platform.mcp.engine.Expert;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UsageMetricsTest {

    private static ExpertResult result(String tool, Status status, int in, int out, boolean cached) {
        return ExpertResult.builder(tool, "1.0")
                .status(status)
                .usage(new Usage(in, out, 0, cached))
                .build();
    }

    @Test
    void aggregatesPerToolAndTotals() {
        var metrics = new UsageMetrics();
        metrics.record(result("code_review_expert", Status.OK, 100, 20, true));
        metrics.record(result("code_review_expert", Status.ERROR, 0, 0, false));
        metrics.record(result("stacktrace_analyzer", Status.PARTIAL, 50, 10, false));

        var byTool = metrics.snapshot().stream()
                .collect(java.util.stream.Collectors.toMap(UsageMetrics.ToolUsage::tool, t -> t));

        var code = byTool.get("code_review_expert");
        assertThat(code.calls()).isEqualTo(2);
        assertThat(code.ok()).isEqualTo(1);
        assertThat(code.error()).isEqualTo(1);
        assertThat(code.inputTokens()).isEqualTo(100);
        assertThat(code.outputTokens()).isEqualTo(20);
        assertThat(code.totalTokens()).isEqualTo(120);
        assertThat(code.cachedCalls()).isEqualTo(1);
        assertThat(code.lastUpdated()).isNotNull();

        var totals = metrics.totals();
        assertThat(totals.calls()).isEqualTo(3);
        assertThat(totals.totalTokens()).isEqualTo(180);
        assertThat(totals.error()).isEqualTo(1);
        assertThat(totals.partial()).isEqualTo(1);
    }

    @Test
    void meteredExpertRecordsEachCall() {
        var metrics = new UsageMetrics();
        Expert stub = new Expert() {
            @Override public ExpertDescriptor descriptor() {
                return new ExpertDescriptor("t", "1.0", null, null, null, null, null, null, null, null, null, null);
            }
            @Override public ExpertResult execute(ExpertRequest request) {
                return result("t", Status.OK, 7, 3, false);
            }
        };
        var metered = new MeteredExpert(stub, metrics);

        metered.execute(new ExpertRequest("r1", null, null, null, Map.of()));
        metered.execute(new ExpertRequest("r2", null, null, null, Map.of()));

        assertThat(metrics.totals().calls()).isEqualTo(2);
        assertThat(metrics.totals().totalTokens()).isEqualTo(20);
    }
}

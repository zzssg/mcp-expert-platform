package com.bank.platform.mcp.svc.incident.tools;

import com.bank.platform.mcp.contract.Budget;
import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Options;
import com.bank.platform.mcp.expert.stacktrace.StacktraceAnalyzerExpert;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The MCP-facing surface of the incident service. Thin {@code @Tool} methods assemble
 * the shared {@link ExpertRequest} envelope and delegate to the expert; verification
 * and scoring happen behind the engine (tenet T1/T7). More incident-group tools
 * ({@code log_analyzer}, {@code sql_expert}) will be added here as their experts land.
 */
public class IncidentExpertTools {

    private final StacktraceAnalyzerExpert stacktraceAnalyzerExpert;
    private final Budget budget;

    public IncidentExpertTools(StacktraceAnalyzerExpert stacktraceAnalyzerExpert, Budget budget) {
        this.stacktraceAnalyzerExpert = stacktraceAnalyzerExpert;
        this.budget = budget;
    }

    @Tool(name = "stacktrace_analyzer",
            description = "Find the true root cause and likely fix for a single Java exception. Anchored "
                    + "on the deterministically-resolved application root frame and decoded FACT signals "
                    + "(ORA-/Spring/Kafka/JVM). Use for one exception + its Caused-by chain.")
    public ExpertResult stacktraceAnalyzer(
            @ToolParam(description = "Raw Java stack trace: top exception plus its Caused by chain.")
            String stackTrace,
            @ToolParam(required = false,
                    description = "Application package prefixes (e.g. com.bank) for precise root-frame resolution.")
            List<String> applicationPackages) {
        Map<String, Object> task = new HashMap<>();
        task.put("stackTrace", stackTrace == null ? "" : stackTrace);
        if (applicationPackages != null && !applicationPackages.isEmpty()) {
            task.put("applicationPackages", applicationPackages);
        }
        return stacktraceAnalyzerExpert.execute(newRequest(task));
    }

    private ExpertRequest newRequest(Map<String, Object> task) {
        return new ExpertRequest(UUID.randomUUID().toString(), null, Options.defaults(), budget, task);
    }
}

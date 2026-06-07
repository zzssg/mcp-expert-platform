package com.bank.platform.mcp.svc.code.tools;

import com.bank.platform.mcp.contract.Budget;
import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Options;
import com.bank.platform.mcp.engine.Expert;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The MCP-facing surface of the code service: each {@code @Tool} method is exposed to
 * clients (Claude / Copilot) with its input schema generated from the typed
 * parameters. The methods are intentionally thin — they assemble the shared
 * {@link ExpertRequest} envelope and delegate to the expert; all judgement,
 * verification, and scoring happen behind the engine (tenet T1/T7).
 */
public class CodeExpertTools {

    private final Expert codeReviewExpert;
    private final Budget budget;

    public CodeExpertTools(Expert codeReviewExpert, Budget budget) {
        this.codeReviewExpert = codeReviewExpert;
        this.budget = budget;
    }

    @Tool(name = "code_review_expert",
            description = "Review a unified git diff for correctness, concurrency, resource-safety and "
                    + "security defects in a Java/Spring/Oracle codebase. Returns findings with file:line "
                    + "evidence, epistemic labels (FACT/INFERENCE/HYPOTHESIS), and confidence.")
    public ExpertResult codeReviewExpert(
            @ToolParam(description = "The unified git diff under review (primary evidence).")
            String diff,
            @ToolParam(required = false,
                    description = "Emphasis: DEFAULT, SECURITY, CONCURRENCY, or PERFORMANCE.")
            String reviewProfile,
            @ToolParam(required = false, description = "Bank coding-standard ids to enforce.")
            List<String> standards) {
        Map<String, Object> task = new HashMap<>();
        task.put("diff", diff == null ? "" : diff);
        if (reviewProfile != null && !reviewProfile.isBlank()) task.put("reviewProfile", reviewProfile);
        if (standards != null && !standards.isEmpty()) task.put("standards", standards);
        return codeReviewExpert.execute(newRequest(task));
    }

    private ExpertRequest newRequest(Map<String, Object> task) {
        // Gateway-supplied options/tenant land here in production; budget is from config.
        return new ExpertRequest(UUID.randomUUID().toString(), null, Options.defaults(), budget, task);
    }
}

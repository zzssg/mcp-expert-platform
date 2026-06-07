package com.bank.platform.mcp.engine.budget;

import java.util.List;

/**
 * The outcome of {@link BudgetPlanner#plan}: the assembled prompt text (sections in
 * their original order) plus a deterministic account of what had to give to fit the
 * budget. The {@code dropped}/{@code truncated} labels are surfaced as honest result
 * limitations (tenet T3) — a reviewer must know the model didn't see everything.
 *
 * @param text            assembled prompt text, sections joined in original order
 * @param estimatedTokens estimated token cost of {@code text}
 * @param tokenBudget     the budget the plan was fit to
 * @param includedLabels  sections that made it in (some possibly truncated)
 * @param truncatedLabels sections included only as a trimmed prefix
 * @param droppedLabels   sections omitted entirely
 */
public record BudgetPlan(
        String text,
        int estimatedTokens,
        int tokenBudget,
        List<String> includedLabels,
        List<String> truncatedLabels,
        List<String> droppedLabels
) {
    public BudgetPlan {
        text = text == null ? "" : text;
        includedLabels = includedLabels == null ? List.of() : List.copyOf(includedLabels);
        truncatedLabels = truncatedLabels == null ? List.of() : List.copyOf(truncatedLabels);
        droppedLabels = droppedLabels == null ? List.of() : List.copyOf(droppedLabels);
    }

    public boolean withinBudget() {
        return estimatedTokens <= tokenBudget;
    }

    /** True if anything was trimmed or dropped — the caller should record a limitation. */
    public boolean reduced() {
        return !truncatedLabels.isEmpty() || !droppedLabels.isEmpty();
    }

    /** A human-readable limitation line, or {@code null} if nothing was reduced. */
    public String limitationNote() {
        if (!reduced()) return null;
        StringBuilder sb = new StringBuilder("Context reduced to fit the token budget:");
        if (!droppedLabels.isEmpty()) sb.append(" dropped ").append(droppedLabels).append('.');
        if (!truncatedLabels.isEmpty()) sb.append(" truncated ").append(truncatedLabels).append('.');
        return sb.toString();
    }
}

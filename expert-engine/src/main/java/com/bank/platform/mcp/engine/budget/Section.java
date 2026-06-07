package com.bank.platform.mcp.engine.budget;

/**
 * One labelled, prioritized block of prompt material handed to the
 * {@link BudgetPlanner} (Part 2.6 stage 4 / 7). The planner keeps higher-priority
 * sections when the token budget is tight and drops or (for {@code truncatable}
 * sections) trims the rest — so the primary evidence survives and only the
 * nice-to-have context is sacrificed.
 *
 * @param label       short identifier for diagnostics (e.g. {@code "diff"})
 * @param text        the section content
 * @param priority    higher is kept first when budgeting
 * @param truncatable whether the planner may include a trimmed prefix rather than
 *                    dropping the section whole
 */
public record Section(String label, String text, int priority, boolean truncatable) {

    public Section {
        if (label == null || label.isBlank())
            throw new IllegalArgumentException("section label required");
        text = text == null ? "" : text;
    }

    /** A section that is kept whole or dropped whole (never trimmed). */
    public static Section fixed(String label, String text, int priority) {
        return new Section(label, text, priority, false);
    }

    /** A section the planner may trim to a prefix to make it fit. */
    public static Section elastic(String label, String text, int priority) {
        return new Section(label, text, priority, true);
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }
}

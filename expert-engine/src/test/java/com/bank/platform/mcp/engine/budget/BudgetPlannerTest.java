package com.bank.platform.mcp.engine.budget;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetPlannerTest {

    private final BudgetPlanner planner = BudgetPlanner.heuristic();

    @Test
    void heuristicEstimateTracksLength() {
        // ~4 chars per token.
        assertThat(TokenEstimator.heuristic().estimate("")).isZero();
        assertThat(TokenEstimator.heuristic().estimate("abcd")).isEqualTo(1);
        assertThat(TokenEstimator.heuristic().estimate("a".repeat(400))).isEqualTo(100);
    }

    @Test
    void keepsEverythingWhenUnderBudget() {
        var plan = planner.plan(10_000, List.of(
                Section.fixed("standards", "be safe", 80),
                Section.elastic("diff", "diff body", 100)));

        assertThat(plan.reduced()).isFalse();
        assertThat(plan.droppedLabels()).isEmpty();
        assertThat(plan.includedLabels()).containsExactly("standards", "diff");
        assertThat(plan.text()).contains("be safe").contains("diff body");
    }

    @Test
    void dropsLowestPriorityNonTruncatableSectionWhenTight() {
        String bigContext = "x".repeat(400); // ~100 tokens
        var plan = planner.plan(10, List.of(
                Section.fixed("diff", "AAAA", 100),               // ~1 token, must survive
                Section.fixed("context", bigContext, 20)));        // ~100 tokens, must go

        assertThat(plan.includedLabels()).containsExactly("diff");
        assertThat(plan.droppedLabels()).containsExactly("context");
        assertThat(plan.text()).contains("AAAA").doesNotContain(bigContext);
        assertThat(plan.reduced()).isTrue();
    }

    @Test
    void truncatesAnElasticSectionToFit() {
        String big = ("line of source code\n").repeat(100); // ~2000 chars, ~500 tokens
        var plan = planner.plan(80, List.of(Section.elastic("diff", big, 100)));

        assertThat(plan.truncatedLabels()).containsExactly("diff");
        assertThat(plan.estimatedTokens()).isLessThanOrEqualTo(80);
        assertThat(plan.text()).contains("truncated to fit").hasSizeLessThan(big.length());
    }

    @Test
    void priorityOrdersInclusionButOriginalOrderIsPreserved() {
        // Budget admits only the two cheap high-priority sections, but they must appear
        // in their original positions in the assembled text.
        var plan = planner.plan(4, List.of(
                Section.fixed("a", "AAAA", 50),  // ~1 token
                Section.fixed("b", "x".repeat(400), 10), // ~100 tokens -> dropped
                Section.fixed("c", "CCCC", 90))); // ~1 token

        assertThat(plan.includedLabels()).containsExactly("a", "c");
        assertThat(plan.text().indexOf("AAAA")).isLessThan(plan.text().indexOf("CCCC"));
        assertThat(plan.droppedLabels()).containsExactly("b");
    }
}

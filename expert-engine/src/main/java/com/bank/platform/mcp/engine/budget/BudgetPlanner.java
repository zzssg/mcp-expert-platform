package com.bank.platform.mcp.engine.budget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic context-reduction (Part 2.6 stages 4 + 7): fit a set of prioritized
 * {@link Section}s into a token budget. The strategy is the cheap, predictable one
 * the architecture calls for before reaching for map-reduce — keep the highest-value
 * material whole, trim or drop the rest:
 * <ol>
 *   <li>consider sections in priority order (ties broken by original position);</li>
 *   <li>include a section whole if it fits the remaining budget;</li>
 *   <li>else, if it is {@code truncatable} and a useful amount of room remains,
 *       include a trimmed prefix;</li>
 *   <li>else drop it.</li>
 * </ol>
 * Included sections are emitted in their <em>original</em> order so the prompt stays
 * coherent. Pure and side-effect free; the same inputs always yield the same plan.
 */
public final class BudgetPlanner {

    /** Below this many tokens of headroom, truncating a section isn't worth it — drop instead. */
    private static final int MIN_TRUNCATE_TOKENS = 50;
    private static final int SEPARATOR_TOKENS = 1; // the "\n\n" between sections
    private static final String TRUNCATE_MARKER = "\n… [truncated to fit token budget]";

    private final TokenEstimator estimator;

    public BudgetPlanner(TokenEstimator estimator) {
        if (estimator == null) throw new IllegalArgumentException("estimator required");
        this.estimator = estimator;
    }

    public static BudgetPlanner heuristic() {
        return new BudgetPlanner(TokenEstimator.heuristic());
    }

    public int estimate(CharSequence text) {
        return estimator.estimate(text);
    }

    public BudgetPlan plan(int tokenBudget, List<Section> sections) {
        int budget = Math.max(0, tokenBudget);
        int n = sections.size();

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        // Highest priority first; stable on the original index for ties.
        java.util.Arrays.sort(order, Comparator
                .comparingInt((Integer i) -> sections.get(i).priority()).reversed()
                .thenComparingInt(i -> i));

        String[] chosen = new String[n]; // null => dropped
        boolean[] truncated = new boolean[n];
        int used = 0;

        for (int idx : order) {
            Section s = sections.get(idx);
            if (s.isEmpty()) continue; // nothing to include or drop
            int sep = used == 0 ? 0 : SEPARATOR_TOKENS;
            int cost = estimator.estimate(s.text());
            if (used + sep + cost <= budget) {
                chosen[idx] = s.text();
                used += sep + cost;
            } else if (s.truncatable()) {
                int room = budget - used - sep;
                if (room >= MIN_TRUNCATE_TOKENS) {
                    String trimmed = truncateToTokens(s.text(), room);
                    chosen[idx] = trimmed;
                    truncated[idx] = true;
                    used += sep + estimator.estimate(trimmed);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        List<String> included = new ArrayList<>();
        List<String> trunc = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Section s = sections.get(i);
            if (s.isEmpty()) continue;
            if (chosen[i] == null) {
                dropped.add(s.label());
                continue;
            }
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(chosen[i]);
            included.add(s.label());
            if (truncated[i]) trunc.add(s.label());
        }

        String text = sb.toString();
        return new BudgetPlan(text, estimator.estimate(text), budget, included, trunc, dropped);
    }

    /** Trim {@code text} to roughly {@code maxTokens}, snapping back to a line boundary. */
    private String truncateToTokens(String text, int maxTokens) {
        int markerTokens = estimator.estimate(TRUNCATE_MARKER);
        int target = maxTokens - markerTokens;
        if (target <= 0) return TRUNCATE_MARKER.strip();

        int full = estimator.estimate(text);
        if (full <= target) return text;

        double ratio = (double) target / full;
        int cut = Math.max(0, Math.min(text.length(), (int) Math.floor(text.length() * ratio)));
        int nl = text.lastIndexOf('\n', cut);
        if (nl > cut / 2) cut = nl; // prefer a clean line break, but don't lose half the kept text
        String head = text.substring(0, cut).stripTrailing();
        return head + TRUNCATE_MARKER;
    }
}

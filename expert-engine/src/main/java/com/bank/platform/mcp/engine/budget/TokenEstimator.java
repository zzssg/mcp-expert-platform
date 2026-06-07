package com.bank.platform.mcp.engine.budget;

/**
 * Estimates the token cost of a piece of prompt text (Part 5.5, tenet T5). The
 * abstraction lets the platform start with a cheap deterministic heuristic and
 * later swap in an exact model tokenizer without touching the experts that budget
 * against it.
 */
public interface TokenEstimator {

    int estimate(CharSequence text);

    /** The default char-ratio heuristic — no model dependency, good enough for budgeting. */
    static TokenEstimator heuristic() {
        return HeuristicTokenEstimator.INSTANCE;
    }
}

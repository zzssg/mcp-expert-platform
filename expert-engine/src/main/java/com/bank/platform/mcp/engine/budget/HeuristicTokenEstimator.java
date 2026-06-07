package com.bank.platform.mcp.engine.budget;

/**
 * A dependency-free token estimate based on the well-known ~4-characters-per-token
 * ratio for English/code under BPE tokenizers. It is intentionally conservative and
 * approximate: budgeting only needs to keep prompts comfortably under the model
 * window and the per-call cost ceiling, not to count tokens exactly. Swap in an
 * exact tokenizer via {@link TokenEstimator} when precision matters.
 */
final class HeuristicTokenEstimator implements TokenEstimator {

    static final HeuristicTokenEstimator INSTANCE = new HeuristicTokenEstimator();

    private static final double CHARS_PER_TOKEN = 4.0;

    @Override
    public int estimate(CharSequence text) {
        if (text == null || text.length() == 0) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
}

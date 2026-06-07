package com.bank.platform.mcp.contract;

/**
 * Token / latency budget (Part 3.1). Advisory from the client, hard-capped by
 * tenant policy at the gateway. We operate far below the model window for cost.
 */
public record Budget(int maxInputTokens, int maxOutputTokens, long deadlineMs) {
    public Budget {
        if (maxInputTokens <= 0) throw new IllegalArgumentException("maxInputTokens must be > 0");
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens must be > 0");
        if (deadlineMs <= 0) throw new IllegalArgumentException("deadlineMs must be > 0");
    }
    public static Budget defaults() {
        return new Budget(60_000, 8_000, 30_000L);
    }
}

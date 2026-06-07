package com.bank.platform.mcp.contract;

/** Operational detail attached to every result for debugging + audit (Part 2.8). */
public record Diagnostics(int repairAttempts, int samples, long latencyMs) {
    public static Diagnostics of(long latencyMs) {
        return new Diagnostics(0, 1, latencyMs);
    }
}

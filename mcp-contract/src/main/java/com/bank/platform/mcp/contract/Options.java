package com.bank.platform.mcp.contract;

/**
 * Client-supplied output shaping. {@code ragEnabled} is retained for
 * forward-compatibility but ignored in this RAG-excluded build.
 */
public record Options(int maxFindings, Severity minSeverity, boolean includeFixes, boolean ragEnabled) {
    public static Options defaults() {
        return new Options(50, Severity.LOW, true, false);
    }
}

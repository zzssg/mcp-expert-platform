package com.bank.platform.mcp.contract;

/**
 * Impact if the finding is true (Part 3.3). Orthogonal to {@link Epistemic}:
 * a CRITICAL finding may carry low confidence (flag for human), a LOW finding
 * may carry high confidence.
 */
public enum Severity {
    CRITICAL, HIGH, MEDIUM, LOW, INFO;

    public int weight() {
        return switch (this) {
            case CRITICAL -> 5;
            case HIGH -> 4;
            case MEDIUM -> 3;
            case LOW -> 2;
            case INFO -> 1;
        };
    }

    public boolean atLeast(Severity floor) {
        return this.weight() >= floor.weight();
    }
}

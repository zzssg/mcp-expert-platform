package com.bank.platform.mcp.contract;

/**
 * How strongly a finding is supported by verifiable evidence (Part 3.3) — the
 * anti-hallucination spine. Assigned by the deterministic verifier layer
 * (Part 5.7), never trusted from the model's self-report.
 */
public enum Epistemic {
    FACT, INFERENCE, HYPOTHESIS, UNKNOWN;

    public int rank() {
        return switch (this) {
            case FACT -> 3;
            case INFERENCE -> 2;
            case HYPOTHESIS -> 1;
            case UNKNOWN -> 0;
        };
    }

    /** Returns the weaker (lower-rank) of two labels — verifiers only ever downgrade. */
    public Epistemic downgradeTo(Epistemic other) {
        return this.rank() <= other.rank() ? this : other;
    }
}

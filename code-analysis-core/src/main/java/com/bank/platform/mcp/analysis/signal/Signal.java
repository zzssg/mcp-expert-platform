package com.bank.platform.mcp.analysis.signal;

/**
 * A deterministically decoded signal (Part 8.3 step 3). Because these come from a
 * maintained lookup / structural match (not the model), they are emitted as FACT.
 *
 * @param code     the matched token, e.g. "ORA-00001" or "NullPointerException"
 * @param meaning  human-readable decoded meaning
 * @param category coarse class (see {@link FailureClass})
 * @param fact     always true here; signals are verifiable by construction
 */
public record Signal(String code, String meaning, FailureClass category, boolean fact) {
    public static Signal fact(String code, String meaning, FailureClass category) {
        return new Signal(code, meaning, category, true);
    }
}

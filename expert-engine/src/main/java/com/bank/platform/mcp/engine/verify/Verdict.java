package com.bank.platform.mcp.engine.verify;

/** What a verifier decided about a single finding. */
public enum Verdict {
    /** Fully supported by evidence. */
    PASS,
    /** Partially supported; epistemic label should be capped (e.g. to INFERENCE/HYPOTHESIS). */
    DOWNGRADE,
    /** Unsupported / fabricated; the finding must be dropped. */
    DROP
}

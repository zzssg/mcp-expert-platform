package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.contract.Epistemic;

/**
 * One verifier's decision about one finding, with an optional epistemic ceiling
 * and a grounding contribution in [0,1] that feeds the confidence formula (S_grounding).
 */
public record VerifierResult(
        String verifier,
        Verdict verdict,
        Epistemic epistemicCeiling, // nullable: null means "no opinion on the ceiling"
        double grounding,           // [0,1] fraction of claims this verifier could ground
        String note
) {
    public static VerifierResult pass(String v, double grounding) {
        return new VerifierResult(v, Verdict.PASS, null, grounding, null);
    }
    public static VerifierResult downgrade(String v, Epistemic ceiling, double grounding, String note) {
        return new VerifierResult(v, Verdict.DOWNGRADE, ceiling, grounding, note);
    }
    public static VerifierResult drop(String v, String note) {
        return new VerifierResult(v, Verdict.DROP, Epistemic.UNKNOWN, 0.0, note);
    }
}

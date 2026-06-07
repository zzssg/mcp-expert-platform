package com.bank.platform.mcp.engine.confidence;

import com.bank.platform.mcp.contract.Epistemic;

/**
 * Per-tool confidence weights + caps (Part 3.6 / 5.8). Weights are tuned against a
 * labelled golden set per tool. The formula:
 *
 * <pre>C = clamp(w_s·S_schema + w_g·S_grounding + w_c·S_consistency
 *               + w_m·S_model + w_h·S_heuristic, 0, 1)</pre>
 *
 * @param hypothesisCap maximum confidence permitted for a HYPOTHESIS finding (e.g. 0.5)
 */
public record ConfidencePolicy(
        double wSchema,
        double wGrounding,
        double wConsistency,
        double wModel,
        double wHeuristic,
        double hypothesisCap
) {
    /** Default: grounding- and heuristic-heavy, model self-report discounted (Part 4.1). */
    public static ConfidencePolicy defaults() {
        return new ConfidencePolicy(0.15, 0.40, 0.10, 0.10, 0.25, 0.5);
    }

    public double cap(Epistemic label, double confidence) {
        double c = confidence;
        if (label == Epistemic.HYPOTHESIS) c = Math.min(c, hypothesisCap);
        if (label == Epistemic.UNKNOWN) c = Math.min(c, 0.25);
        return c;
    }
}

package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.contract.Epistemic;

import java.util.List;

/** Aggregated verifier decision for one finding (drives drop/keep + the epistemic ceiling). */
public record FindingVerdict(
        String findingId,
        boolean drop,
        Epistemic epistemicCeiling,  // strongest label this finding may keep
        double grounding,            // mean grounding across verifiers → S_grounding
        List<VerifierResult> results
) {}

package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.contract.Epistemic;
import com.bank.platform.mcp.contract.Finding;

/**
 * GroundingScore (Part 5.7): a coarse check that a finding carries enough evidence
 * for the epistemic label it is asking for. A finding that wants to be FACT but
 * cites nothing locatable cannot be FACT. This verifier never drops; it only caps.
 */
public final class GroundingVerifier implements Verifier {

    @Override public String name() { return "Grounding"; }

    @Override
    public VerifierResult verify(Finding finding, EvidenceSet evidence) {
        long locatable = finding.evidence().stream()
                .filter(r -> "diff".equals(r.type()) || "file".equals(r.type())
                        || "symbol".equals(r.type()) || "feed".equals(r.type()))
                .count();
        if (locatable == 0) {
            return VerifierResult.downgrade(name(), Epistemic.HYPOTHESIS, 0.0,
                    "no locatable evidence; cannot exceed HYPOTHESIS");
        }
        if (locatable == 1) {
            return VerifierResult.downgrade(name(), Epistemic.INFERENCE, 0.7,
                    "single evidence anchor; cannot exceed INFERENCE");
        }
        return VerifierResult.pass(name(), 1.0);
    }
}

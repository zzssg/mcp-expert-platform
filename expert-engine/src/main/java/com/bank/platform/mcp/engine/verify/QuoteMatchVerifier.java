package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.contract.EvidenceRef;
import com.bank.platform.mcp.contract.Epistemic;
import com.bank.platform.mcp.contract.Finding;

/**
 * QuoteMatch (Part 5.7): the quoted evidence text must actually appear at the cited
 * span. A small mismatch downgrades; a quote that resolves to a span but does not
 * appear there at all is a fabrication signal and drops the finding.
 */
public final class QuoteMatchVerifier implements Verifier {

    @Override public String name() { return "QuoteMatch"; }

    @Override
    public VerifierResult verify(Finding finding, EvidenceSet evidence) {
        if (evidence.citations() == null) return VerifierResult.pass(name(), 0.5);
        int quoted = 0;
        int matched = 0;
        for (EvidenceRef ref : finding.evidence()) {
            if (ref.quote() == null || ref.quote().isBlank()) continue;
            if (!"diff".equals(ref.type()) && !"file".equals(ref.type())) continue;
            quoted++;
            if (evidence.citations().quoteMatches(ref.ref(), ref.quote())) matched++;
        }
        if (quoted == 0) return VerifierResult.pass(name(), 0.6); // nothing to falsify
        double ratio = (double) matched / quoted;
        if (matched == 0) {
            return VerifierResult.drop(name(), "quoted text not present at cited span (fabrication)");
        }
        if (ratio < 1.0) {
            return VerifierResult.downgrade(name(), Epistemic.INFERENCE, ratio,
                    "some quotes do not match the cited span");
        }
        return VerifierResult.pass(name(), 1.0);
    }
}

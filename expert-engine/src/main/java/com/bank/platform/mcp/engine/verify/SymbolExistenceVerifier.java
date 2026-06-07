package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.contract.EvidenceRef;
import com.bank.platform.mcp.contract.Finding;

/**
 * SymbolExistence (Part 5.7): any evidence of type {@code symbol} or {@code feed}
 * (e.g. a class/method, an ORA code, a CVE id) must exist in the provided context
 * or trusted feeds. A nonexistent symbol is pure fabrication → drop.
 */
public final class SymbolExistenceVerifier implements Verifier {

    @Override public String name() { return "SymbolExistence"; }

    @Override
    public VerifierResult verify(Finding finding, EvidenceSet evidence) {
        int checked = 0;
        int present = 0;
        for (EvidenceRef ref : finding.evidence()) {
            if ("symbol".equals(ref.type())) {
                checked++;
                if (evidence.symbolKnown(ref.ref())) present++;
            } else if ("feed".equals(ref.type())) {
                checked++;
                if (evidence.signalIsFact(ref.ref())) present++;
            }
        }
        if (checked == 0) return VerifierResult.pass(name(), 0.5);
        if (present == 0) {
            return VerifierResult.drop(name(), "referenced symbol/feed id does not exist in evidence");
        }
        double grounding = (double) present / checked;
        return grounding < 1.0
                ? VerifierResult.downgrade(name(),
                    com.bank.platform.mcp.contract.Epistemic.INFERENCE, grounding, "some symbols unverified")
                : VerifierResult.pass(name(), 1.0);
    }
}

package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.contract.EvidenceRef;
import com.bank.platform.mcp.contract.Epistemic;
import com.bank.platform.mcp.contract.Finding;

/**
 * CitationResolver (Part 5.7): every {@code evidence.ref} of type diff/file must
 * resolve to a real span in the citation index. Unresolvable citations mean the
 * finding points at code that wasn't there → downgrade to HYPOTHESIS; if the
 * finding has no resolvable evidence at all, it is dropped.
 */
public final class CitationResolverVerifier implements Verifier {

    @Override public String name() { return "CitationResolver"; }

    @Override
    public VerifierResult verify(Finding finding, EvidenceSet evidence) {
        var refs = finding.evidence();
        if (refs.isEmpty()) {
            // No evidence at all: only allowed for honest UNKNOWNs.
            return finding.epistemic() == Epistemic.UNKNOWN
                    ? VerifierResult.pass(name(), 0.0)
                    : VerifierResult.downgrade(name(), Epistemic.HYPOTHESIS, 0.0, "no evidence cited");
        }
        int resolvable = 0;
        int locatable = 0;
        for (EvidenceRef ref : refs) {
            if (!isLocatable(ref)) continue;
            locatable++;
            if (evidence.citations() != null && evidence.citations().exists(ref.ref())) {
                resolvable++;
            }
        }
        if (locatable == 0) {
            // All evidence is non-locatable (e.g. symbol/feed handled by other verifiers).
            return VerifierResult.pass(name(), 0.5);
        }
        double grounding = (double) resolvable / locatable;
        if (resolvable == 0) {
            return VerifierResult.drop(name(), "no cited span resolves to provided source");
        }
        if (grounding < 1.0) {
            return VerifierResult.downgrade(name(), Epistemic.INFERENCE, grounding,
                    "some cited spans do not resolve");
        }
        return VerifierResult.pass(name(), 1.0);
    }

    private static boolean isLocatable(EvidenceRef ref) {
        String t = ref.type();
        return "diff".equals(t) || "file".equals(t);
    }
}

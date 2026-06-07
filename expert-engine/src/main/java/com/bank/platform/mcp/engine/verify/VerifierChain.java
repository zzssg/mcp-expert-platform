package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.contract.Epistemic;
import com.bank.platform.mcp.contract.Finding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the per-finding verifier list plus the batch contradiction pass and folds
 * the results into a {@link VerifierReport} (Part 5.7). The chain is the single
 * place that decides drop/keep and the epistemic ceiling; the confidence scorer
 * consumes its output. Pure, deterministic, dependency-free.
 */
public final class VerifierChain {

    private final List<Verifier> verifiers;

    public VerifierChain(List<Verifier> verifiers) {
        this.verifiers = List.copyOf(verifiers);
    }

    /** The default chain ordering used by most experts. */
    public static VerifierChain defaultChain() {
        return new VerifierChain(List.of(
                new CitationResolverVerifier(),
                new QuoteMatchVerifier(),
                new SymbolExistenceVerifier(),
                new GroundingVerifier()));
    }

    public VerifierReport verify(List<Finding> findings, EvidenceSet evidence) {
        Map<String, FindingVerdict> byFinding = new LinkedHashMap<>();
        Set<String> contradicted = ContradictionVerifier.detect(findings);

        for (Finding f : findings) {
            List<VerifierResult> results = new ArrayList<>();
            boolean drop = false;
            Epistemic ceiling = Epistemic.FACT; // start optimistic, verifiers only lower it
            double groundingSum = 0;
            int groundingCount = 0;

            for (Verifier v : verifiers) {
                VerifierResult r = v.verify(f, evidence);
                results.add(r);
                if (r.verdict() == Verdict.DROP) drop = true;
                if (r.epistemicCeiling() != null) ceiling = ceiling.downgradeTo(r.epistemicCeiling());
                groundingSum += r.grounding();
                groundingCount++;
            }

            if (contradicted.contains(f.id())) {
                results.add(VerifierResult.downgrade(ContradictionVerifier.NAME,
                        Epistemic.HYPOTHESIS, 0.4, "contradicts another finding on the same span"));
                ceiling = ceiling.downgradeTo(Epistemic.HYPOTHESIS);
            }

            double grounding = groundingCount == 0 ? 0 : groundingSum / groundingCount;
            byFinding.put(f.id(), new FindingVerdict(f.id(), drop, ceiling, grounding, results));
        }
        return new VerifierReport(byFinding, List.copyOf(contradicted));
    }
}

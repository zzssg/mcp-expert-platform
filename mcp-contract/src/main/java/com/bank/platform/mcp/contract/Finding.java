package com.bank.platform.mcp.contract;

import java.util.List;

/**
 * The shared finding object every expert emits (Part 2.8 / 3.2). Uniform shape
 * lets the orchestrator fuse findings from any expert identically.
 * {@code epistemic} and {@code confidence} are set by the platform's verifier and
 * scorer (Parts 5.7–5.8), not by the model.
 */
public record Finding(
        String id,
        String category,
        Severity severity,
        Epistemic epistemic,
        double confidence,
        String title,
        String detail,
        List<EvidenceRef> evidence,
        String recommendation
) {
    public Finding {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("finding.id required");
        if (severity == null) throw new IllegalArgumentException("finding.severity required");
        if (epistemic == null) throw new IllegalArgumentException("finding.epistemic required");
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0,1], was " + confidence);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public Finding withScore(Epistemic newEpistemic, double newConfidence) {
        return new Finding(id, category, severity, newEpistemic, newConfidence,
                title, detail, evidence, recommendation);
    }
}

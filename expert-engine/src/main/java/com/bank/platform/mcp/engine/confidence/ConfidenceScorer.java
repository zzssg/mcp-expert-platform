package com.bank.platform.mcp.engine.confidence;

import com.bank.platform.mcp.contract.Epistemic;
import com.bank.platform.mcp.contract.Finding;
import com.bank.platform.mcp.contract.Severity;
import com.bank.platform.mcp.engine.verify.FindingVerdict;
import com.bank.platform.mcp.engine.verify.VerifierReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the shared confidence formula and assigns epistemic labels from the
 * verifier report — NOT from the model's self-report (Part 5.8). Drops findings the
 * verifier chain rejected, caps labels at the verifier ceiling, and emits a
 * severity-weighted {@code overallConfidence}.
 */
public final class ConfidenceScorer {

    private final ConfidencePolicy policy;
    private final ModelCalibration calibration;

    public ConfidenceScorer(ConfidencePolicy policy, ModelCalibration calibration) {
        this.policy = policy;
        this.calibration = calibration;
    }

    public static ConfidenceScorer defaults() {
        return new ConfidenceScorer(ConfidencePolicy.defaults(), ModelCalibration.conservativeShrink());
    }

    public record Scored(List<Finding> findings, double overallConfidence) {}

    /**
     * @param candidate     findings as proposed by the model (with provisional epistemic)
     * @param report        verifier verdicts
     * @param schemaScore   S_schema (1.0 on first-parse success, decays per repair)
     * @param consistency   S_consistency (1.0 when no sampling, else agreement ratio)
     * @param rawModelConf  model's self-reported overall confidence in [0,1]
     */
    public Scored score(List<Finding> candidate, VerifierReport report,
                        double schemaScore, double consistency, double rawModelConf) {
        double sModel = calibration.calibrate(rawModelConf);
        List<Finding> kept = new ArrayList<>();

        for (Finding f : candidate) {
            FindingVerdict v = report.verdictFor(f.id());
            if (v != null && v.drop()) continue; // verifier dropped it — never emitted

            Epistemic label = f.epistemic();
            if (v != null) label = label.downgradeTo(v.epistemicCeiling());
            // Concurrency/perf style findings can never be FACT (no runtime evidence) — Part 4.1.
            if (isRuntimeDependent(f) && label == Epistemic.FACT) label = Epistemic.INFERENCE;

            double grounding = v != null ? v.grounding() : 0.0;
            double sHeuristic = grounding; // deterministic heuristics dominated by grounding here
            Signals s = new Signals(schemaScore, grounding, consistency, sModel, sHeuristic);
            double c = formula(s);
            c = policy.cap(label, c);

            kept.add(f.withScore(label, round(c)));
        }
        return new Scored(kept, round(severityWeighted(kept)));
    }

    private double formula(Signals s) {
        double c = policy.wSchema() * s.sSchema()
                + policy.wGrounding() * s.sGrounding()
                + policy.wConsistency() * s.sConsistency()
                + policy.wModel() * s.sModel()
                + policy.wHeuristic() * s.sHeuristic();
        return Math.max(0, Math.min(1, c));
    }

    private static boolean isRuntimeDependent(Finding f) {
        String cat = f.category() == null ? "" : f.category().toLowerCase();
        return cat.contains("concurrency") || cat.contains("performance") || cat.contains("perf");
    }

    private static double severityWeighted(List<Finding> findings) {
        if (findings.isEmpty()) return 0.0;
        double num = 0, den = 0;
        for (Finding f : findings) {
            int w = f.severity() == null ? Severity.LOW.weight() : f.severity().weight();
            num += w * f.confidence();
            den += w;
        }
        return den == 0 ? 0 : num / den;
    }

    private static double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}

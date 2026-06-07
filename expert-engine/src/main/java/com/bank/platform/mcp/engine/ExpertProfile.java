package com.bank.platform.mcp.engine;

import com.bank.platform.mcp.engine.client.ModelProfile;
import com.bank.platform.mcp.engine.confidence.ConfidenceScorer;
import com.bank.platform.mcp.engine.verify.VerifierChain;

/**
 * The thin, declarative specialization that turns the shared engine into one
 * specific expert (Part 2.10, tenet T7). A new expert is, in steady state, an
 * {@code ExpertProfile} + prompt assets + schema + an optional deterministic
 * pre-processor — most experts add &lt; 200 lines of Java.
 *
 * @param id                  tool id, e.g. {@code code_review_expert}
 * @param toolVersion         semver surfaced in the result envelope
 * @param promptVersion       prompt asset bundle version, e.g. {@code v3}
 * @param model               model + decoding profile (swap to migrate Gemini versions)
 * @param responseSchemaJson  JSON Schema enforced on the model output; null skips validation
 * @param verifiers           the deterministic verifier chain (Part 5.7)
 * @param scorer              the confidence scorer + epistemic labeller (Part 5.8)
 */
public record ExpertProfile(
        String id,
        String toolVersion,
        String promptVersion,
        ModelProfile model,
        String responseSchemaJson,
        VerifierChain verifiers,
        ConfidenceScorer scorer
) {
    public ExpertProfile {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("expert id required");
        if (toolVersion == null || toolVersion.isBlank())
            throw new IllegalArgumentException("toolVersion required");
        if (model == null) throw new IllegalArgumentException("model profile required");
        promptVersion = promptVersion == null ? "unknown" : promptVersion;
        verifiers = verifiers == null ? VerifierChain.defaultChain() : verifiers;
        scorer = scorer == null ? ConfidenceScorer.defaults() : scorer;
    }

    /** Profile with the platform defaults: default verifier chain + scorer. */
    public static ExpertProfile of(String id, String toolVersion, String promptVersion,
                                   ModelProfile model, String responseSchemaJson) {
        return new ExpertProfile(id, toolVersion, promptVersion, model, responseSchemaJson,
                VerifierChain.defaultChain(), ConfidenceScorer.defaults());
    }
}

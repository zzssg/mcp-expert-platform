package com.bank.platform.mcp.engine;

import com.bank.platform.mcp.engine.verify.EvidenceSet;

import java.util.List;

/**
 * The output of an expert's deterministic pre-processing (pipeline stages 1–8),
 * ready to hand to the engine. It pairs the assembled prompt with the
 * <strong>exact</strong> {@link EvidenceSet} the model was allowed to use — the
 * verifier layer checks every model claim against this set and nothing else, which
 * is what makes "Gemini proposes, verifiers dispose" enforceable (Part 5.7).
 *
 * <p>{@code notes} carries honest limitations the deterministic pre-pass already
 * knows about (e.g. "diff truncated to fit budget", "no source context provided");
 * the engine merges them into the result's {@code limitations} so the caller sees a
 * complete picture (tenet T3).
 *
 * @param systemPrompt stable, cacheable role/rules block
 * @param userPrompt   dynamic task material handed to the model
 * @param evidence     the citation index + known symbols + fact-grade signals
 * @param notes        deterministic limitations discovered during pre-processing
 */
public record PreparedInput(String systemPrompt, String userPrompt, EvidenceSet evidence,
                            List<String> notes) {
    public PreparedInput {
        if (userPrompt == null || userPrompt.isBlank())
            throw new IllegalArgumentException("userPrompt required");
        if (evidence == null)
            throw new IllegalArgumentException("evidence set required (use EvidenceSet.of(...))");
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** Convenience for experts with no deterministic limitations to report. */
    public PreparedInput(String systemPrompt, String userPrompt, EvidenceSet evidence) {
        this(systemPrompt, userPrompt, evidence, List.of());
    }
}

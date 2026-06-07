package com.bank.platform.mcp.engine.parse;

import com.bank.platform.mcp.contract.Finding;

import java.util.List;
import java.util.Map;

/**
 * The normalized, deterministic view of one model response after parse + schema
 * validation + bounded repair (Part 5.6). Findings here are the model's
 * <em>proposals</em>; their epistemic labels and confidence are provisional and
 * are overwritten downstream by the verifier chain and confidence scorer
 * (Parts 5.7–5.8). This record carries the signals the scorer needs:
 * {@code schemaScore} (S_schema) and {@code repairAttempts}.
 *
 * @param parsed           true if the output parsed into JSON at all
 * @param valid            true if it satisfied the response schema (after any repair)
 * @param schemaScore      S_schema in [0,1]: 1.0 on clean first parse, decaying per repair
 * @param repairAttempts   number of repair passes applied before success (0 = clean)
 * @param findings         model-proposed findings (provisional labels/confidence)
 * @param payload          tool-specific payload object, kept generic at this layer
 * @param modelConfidence  the model's self-reported overall confidence (discounted later)
 * @param limitations      model-declared limitations/gaps
 * @param schemaErrors     human-readable schema validation messages (empty when valid)
 */
public record ParsedCandidate(
        boolean parsed,
        boolean valid,
        double schemaScore,
        int repairAttempts,
        List<Finding> findings,
        Map<String, Object> payload,
        double modelConfidence,
        List<String> limitations,
        List<String> schemaErrors
) {
    public ParsedCandidate {
        findings = findings == null ? List.of() : List.copyOf(findings);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        schemaErrors = schemaErrors == null ? List.of() : List.copyOf(schemaErrors);
    }

    /** Total failure to parse — the engine maps this to a structured ERROR result. */
    public static ParsedCandidate unparseable(String error) {
        return new ParsedCandidate(false, false, 0.0, 0, List.of(), Map.of(), 0.0,
                List.of(), List.of(error));
    }
}

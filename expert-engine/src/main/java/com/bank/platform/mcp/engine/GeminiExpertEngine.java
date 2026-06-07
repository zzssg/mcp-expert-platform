package com.bank.platform.mcp.engine;

import com.bank.platform.mcp.contract.Diagnostics;
import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Status;
import com.bank.platform.mcp.contract.Usage;
import com.bank.platform.mcp.engine.client.GeminiClient;
import com.bank.platform.mcp.engine.client.GeminiException;
import com.bank.platform.mcp.engine.client.GeminiRequest;
import com.bank.platform.mcp.engine.client.GeminiResponse;
import com.bank.platform.mcp.engine.confidence.ConfidenceScorer;
import com.bank.platform.mcp.engine.parse.ParsedCandidate;
import com.bank.platform.mcp.engine.parse.ResponseParser;
import com.bank.platform.mcp.engine.verify.VerifierReport;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared Gemini framework's keystone (Part 5): pipeline stages 9–12. It takes a
 * {@link PreparedInput} (the expert's deterministic pre-work) plus an
 * {@link ExpertProfile} and produces the normalized {@link ExpertResult} envelope
 * every expert returns — identical for every tool, only the profile differs (T7).
 *
 * <p>Flow: build request → call model → parse + schema-validate (+ bounded repair)
 * → verifier chain (drop/cap, per-finding grounding) → confidence scorer + epistemic
 * labelling → assemble envelope with usage, diagnostics, and an honest {@link Status}.
 *
 * <p>Crucially, the model only ever <em>proposes</em>: every finding it returns is
 * re-grounded against the {@link PreparedInput#evidence()} set and may be dropped or
 * downgraded. Transport failures degrade to a structured {@code ERROR} result, never
 * a thrown exception across the engine boundary (Part 5.9).
 *
 * <p>Stateless and thread-safe; share one instance across requests.
 */
public final class GeminiExpertEngine {

    private final GeminiClient client;
    private final ResponseParser parser;

    public GeminiExpertEngine(GeminiClient client) {
        this(client, new ResponseParser());
    }

    public GeminiExpertEngine(GeminiClient client, ResponseParser parser) {
        if (client == null) throw new IllegalArgumentException("client required");
        this.client = client;
        this.parser = parser;
    }

    public ExpertResult run(PreparedInput input, ExpertProfile profile) {
        if (input == null) throw new IllegalArgumentException("input required");
        if (profile == null) throw new IllegalArgumentException("profile required");

        long startNanos = System.nanoTime();

        GeminiResponse response;
        try {
            response = client.generate(new GeminiRequest(
                    input.systemPrompt(), input.userPrompt(),
                    profile.responseSchemaJson(), profile.model()));
        } catch (GeminiException e) {
            return errorResult(profile, elapsedMs(startNanos),
                    "model call failed: " + e.getMessage());
        }

        ParsedCandidate candidate = parser.parse(response.rawJson(), profile.responseSchemaJson());
        if (!candidate.parsed()) {
            return errorResult(profile, elapsedMs(startNanos),
                    "unparseable model output: " + firstError(candidate));
        }

        VerifierReport report = profile.verifiers().verify(candidate.findings(), input.evidence());
        double consistency = 1.0; // self-consistency sampling (N>1) is a future hook, Part 4.1
        ConfidenceScorer.Scored scored = profile.scorer().score(
                candidate.findings(), report,
                candidate.schemaScore(), consistency, candidate.modelConfidence());

        Status status = decideStatus(candidate, scored.findings().size());
        List<String> limitations = mergeLimitations(input, candidate);

        return ExpertResult.builder(profile.id(), profile.toolVersion())
                .modelVersion(response.modelVersion())
                .promptVersion(profile.promptVersion())
                .status(status)
                .payload(candidate.payload())
                .findings(scored.findings())
                .overallConfidence(scored.overallConfidence())
                .limitations(limitations)
                .usage(new Usage(response.inputTokens(), response.outputTokens(), 0, response.cached()))
                .diagnostics(new Diagnostics(candidate.repairAttempts(), 1, elapsedMs(startNanos)))
                .build();
    }

    /**
     * Honest status reporting (Part 2.8):
     * <ul>
     *   <li>{@code ERROR}   — could not parse (handled earlier);</li>
     *   <li>{@code PARTIAL} — parsed but failed schema, or repair was needed;</li>
     *   <li>{@code EMPTY}   — clean parse, no findings survived verification;</li>
     *   <li>{@code OK}      — clean parse with surviving findings.</li>
     * </ul>
     */
    private static Status decideStatus(ParsedCandidate candidate, int keptFindings) {
        if (!candidate.valid() || candidate.repairAttempts() > 0) return Status.PARTIAL;
        if (keptFindings == 0) return Status.EMPTY;
        return Status.OK;
    }

    /**
     * Honest, complete limitations (tenet T3): deterministic pre-pass notes first
     * (e.g. budget truncation), then the model's own declared limitations, then any
     * parse/schema caveat the engine observed.
     */
    private static List<String> mergeLimitations(PreparedInput input, ParsedCandidate candidate) {
        List<String> limitations = new ArrayList<>(input.notes());
        limitations.addAll(candidate.limitations());
        if (!candidate.valid() && !candidate.schemaErrors().isEmpty()) {
            limitations.add("Model output failed schema validation: " + firstError(candidate));
        } else if (candidate.repairAttempts() > 0) {
            limitations.add("Model output required " + candidate.repairAttempts()
                    + " repair pass(es) before it parsed.");
        }
        return limitations;
    }

    private ExpertResult errorResult(ExpertProfile profile, long latencyMs, String reason) {
        return ExpertResult.builder(profile.id(), profile.toolVersion())
                .promptVersion(profile.promptVersion())
                .status(Status.ERROR)
                .findings(List.of())
                .overallConfidence(0.0)
                .limitations(List.of(reason))
                .usage(Usage.none())
                .diagnostics(Diagnostics.of(latencyMs))
                .build();
    }

    private static String firstError(ParsedCandidate candidate) {
        return candidate.schemaErrors().isEmpty() ? "unknown" : candidate.schemaErrors().get(0);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}

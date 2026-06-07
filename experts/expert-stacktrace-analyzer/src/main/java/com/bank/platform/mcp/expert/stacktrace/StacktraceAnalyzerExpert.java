package com.bank.platform.mcp.expert.stacktrace;

import com.bank.platform.mcp.analysis.diff.CitationIndex;
import com.bank.platform.mcp.analysis.signal.DecodeResult;
import com.bank.platform.mcp.analysis.signal.Signal;
import com.bank.platform.mcp.analysis.signal.SignalDecoder;
import com.bank.platform.mcp.analysis.stacktrace.Frame;
import com.bank.platform.mcp.analysis.stacktrace.FrameOwnerResolver;
import com.bank.platform.mcp.analysis.stacktrace.RootFrameResolver;
import com.bank.platform.mcp.analysis.stacktrace.StackTraceParser;
import com.bank.platform.mcp.analysis.stacktrace.ThrowableInfo;
import com.bank.platform.mcp.contract.CostClass;
import com.bank.platform.mcp.contract.ExpertDescriptor;
import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.RagProfile;
import com.bank.platform.mcp.engine.AbstractGeminiExpert;
import com.bank.platform.mcp.engine.ExpertProfile;
import com.bank.platform.mcp.engine.GeminiExpertEngine;
import com.bank.platform.mcp.engine.PreparedInput;
import com.bank.platform.mcp.engine.client.ModelProfile;
import com.bank.platform.mcp.engine.prompt.PromptAsset;
import com.bank.platform.mcp.engine.verify.EvidenceSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code stacktrace_analyzer} (Part 4.10 / 8.3): given one exception + its frames,
 * identify the most probable root cause and likely fix. This is the clearest
 * demonstration of tenet T6 — the load-bearing work is deterministic Java:
 * <ol>
 *   <li>parse the trace into a typed {@code Caused by} chain;</li>
 *   <li>resolve the <em>true</em> root frame (first APPLICATION frame at/below the
 *       deepest cause — not the JDK top line a human usually misreads);</li>
 *   <li>decode well-known Oracle/Spring/Kafka/JVM signals as FACTs.</li>
 * </ol>
 * Gemini is handed a compact digest plus a <strong>closed vocabulary</strong> of
 * citable tokens (the real frame symbols and the FACT signal codes). It may only
 * cite from that vocabulary; the symbol/feed verifiers drop anything it invents.
 */
public final class StacktraceAnalyzerExpert extends AbstractGeminiExpert<StacktraceTask> {

    public static final String ID = "stacktrace_analyzer";
    public static final String VERSION = "1.0.0";
    public static final String PROMPT_VERSION = "v1";

    private static final String ASSET_BASE = "prompts/stacktrace_analyzer/v1/";
    private static final String SYSTEM_ASSET = ASSET_BASE + "system.md";
    private static final String INPUT_SCHEMA = ASSET_BASE + "input.schema.json";
    private static final String OUTPUT_SCHEMA = ASSET_BASE + "output.schema.json";

    /** Cap the citable-frame list so a deep trace cannot blow the prompt budget. */
    private static final int MAX_LISTED_FRAMES = 40;

    private final RootFrameResolver rootFrameResolver = new RootFrameResolver();
    private final SignalDecoder signalDecoder = new SignalDecoder();
    private final ExpertProfile profile;

    /** Uses the default output-token cap from {@link ModelProfile#analysis}. */
    public StacktraceAnalyzerExpert(GeminiExpertEngine engine, String modelId) {
        this(engine, modelId, ModelProfile.analysis(modelId).maxOutputTokens());
    }

    public StacktraceAnalyzerExpert(GeminiExpertEngine engine, String modelId, int maxOutputTokens) {
        super(engine);
        String outputSchema = PromptAsset.load(OUTPUT_SCHEMA);
        PromptAsset.load(SYSTEM_ASSET);
        this.profile = ExpertProfile.of(ID, VERSION, PROMPT_VERSION,
                new ModelProfile(modelId, "analysis", 0.0, maxOutputTokens), outputSchema);
    }

    @Override
    protected Class<StacktraceTask> taskType() {
        return StacktraceTask.class;
    }

    @Override
    protected ExpertProfile profile() {
        return profile;
    }

    @Override
    public ExpertDescriptor descriptor() {
        return new ExpertDescriptor(
                ID, VERSION,
                "Stack Trace Analyzer",
                "Finds the true root cause and likely fix for a single Java exception, anchored on the "
                        + "deterministically-resolved application root frame and decoded FACT signals.",
                "Use for a single exception and its frames (one Caused-by chain). Use log_analyzer instead "
                        + "when you have a window of many log lines needing correlation/clustering.",
                INPUT_SCHEMA, OUTPUT_SCHEMA,
                RagProfile.NONE, CostClass.LOW,
                Set.of("expert.stacktrace.invoke"),
                "prompts/stacktrace_analyzer/v1", "gemini-2.5-pro/analysis");
    }

    @Override
    protected PreparedInput preProcess(ExpertRequest request, StacktraceTask task) {
        // 1. Deterministic pre-pass.
        FrameOwnerResolver ownerResolver = new FrameOwnerResolver(Set.copyOf(task.applicationPackages()));
        ThrowableInfo throwable = new StackTraceParser(ownerResolver).parse(task.stackTrace());
        Optional<Frame> rootFrame = rootFrameResolver.resolve(throwable);
        DecodeResult decoded = signalDecoder.decode(throwable);

        // 2. Closed citation vocabulary: only real frame symbols and FACT signal codes
        //    may be cited; everything else is rejected by the verifiers (Part 5.7).
        Set<String> knownSymbols = collectSymbols(throwable);
        Set<String> signalCodes = new LinkedHashSet<>();
        for (Signal s : decoded.signals()) signalCodes.add(s.code());

        CitationIndex citations = new CitationIndex();
        for (StacktraceTask.SourceFile f : task.sourceContext()) {
            if (f.path() != null && f.content() != null) citations.addFile(f.path(), f.content());
        }
        EvidenceSet evidence = new EvidenceSet(citations, knownSymbols, signalCodes);

        // 3. Prompt + honest deterministic limitations.
        String system = PromptAsset.load(SYSTEM_ASSET);
        String user = buildDigest(throwable, rootFrame, decoded);
        return new PreparedInput(system, user, evidence, buildNotes(task, rootFrame, decoded));
    }

    private Set<String> collectSymbols(ThrowableInfo throwable) {
        Set<String> symbols = new LinkedHashSet<>();
        for (ThrowableInfo t : throwable.chain()) {
            for (Frame f : t.frames()) {
                symbols.add(f.symbol());
                symbols.add(f.declaringClass());
            }
        }
        return symbols;
    }

    private List<String> buildNotes(StacktraceTask task, Optional<Frame> rootFrame, DecodeResult decoded) {
        List<String> notes = new ArrayList<>();
        if (task.applicationPackages().isEmpty()) {
            notes.add("No application package prefixes supplied; the application root frame may be "
                    + "misidentified. Provide applicationPackages (e.g. com.bank) for a precise root frame.");
        }
        if (rootFrame.isEmpty()) {
            notes.add("No application frame could be identified in the trace; root cause is best-effort.");
        }
        if (decoded.signals().isEmpty()) {
            notes.add("No well-known signal (ORA-/Spring/Kafka/JVM) decoded; classification is weaker.");
        }
        if (task.sourceContext().isEmpty()) {
            notes.add("No source provided; findings are grounded in the trace only, not the source lines.");
        }
        return notes;
    }

    private String buildDigest(ThrowableInfo throwable, Optional<Frame> rootFrame, DecodeResult decoded) {
        StringBuilder sb = new StringBuilder(2048);
        ThrowableInfo top = throwable;
        sb.append("# Incident: ").append(top.type());
        if (top.message() != null && !top.message().isBlank()) sb.append(": ").append(top.message());
        sb.append("\n\n");

        sb.append("## Exception chain (outermost → root cause)\n");
        int i = 1;
        for (ThrowableInfo t : throwable.chain()) {
            sb.append(i++).append(". ").append(t.type());
            if (t.message() != null && !t.message().isBlank()) sb.append(": ").append(t.message());
            sb.append('\n');
        }
        sb.append('\n');

        sb.append("## Deterministic application root frame\n");
        sb.append(rootFrame.map(f -> f.symbol() + "  (" + f.ref() + ")  [" + f.owner() + "]")
                .orElse("none identified")).append("\n\n");

        sb.append("## Citable frames — evidence type \"symbol\", ref is the frame symbol\n");
        int listed = 0;
        for (ThrowableInfo t : throwable.chain()) {
            for (Frame f : t.frames()) {
                if (listed++ >= MAX_LISTED_FRAMES) break;
                sb.append("- ").append(f.symbol()).append("  (").append(f.ref()).append(")  [")
                        .append(f.owner()).append("]\n");
            }
            if (listed >= MAX_LISTED_FRAMES) break;
        }
        sb.append('\n');

        sb.append("## Citable signals (FACT) — evidence type \"feed\", ref is the code\n");
        if (decoded.signals().isEmpty()) {
            sb.append("(none decoded)\n");
        } else {
            for (Signal s : decoded.signals()) {
                sb.append("- ").append(s.code()).append(" — ").append(s.meaning())
                        .append("  [").append(s.category()).append("]\n");
            }
        }
        sb.append("\n## Deterministic dominant failure class: ").append(decoded.failureClass()).append("\n\n");

        sb.append("Identify the most probable root cause(s) as findings, anchored on the root frame and the "
                + "FACT signals above. Cite ONLY the symbols and signal codes listed here (plus file:line "
                + "spans if source was provided). Root-cause findings without runtime data are at most "
                + "INFERENCE. Return ONLY the JSON object required by the schema.");
        return sb.toString();
    }
}

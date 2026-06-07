package com.bank.platform.mcp.expert.codereview;

import com.bank.platform.mcp.analysis.diff.CitationIndex;
import com.bank.platform.mcp.analysis.diff.DiffFile;
import com.bank.platform.mcp.analysis.diff.UnifiedDiff;
import com.bank.platform.mcp.analysis.diff.UnifiedDiffParser;
import com.bank.platform.mcp.contract.CostClass;
import com.bank.platform.mcp.contract.ExpertDescriptor;
import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.RagProfile;
import com.bank.platform.mcp.engine.AbstractGeminiExpert;
import com.bank.platform.mcp.engine.ExpertProfile;
import com.bank.platform.mcp.engine.GeminiExpertEngine;
import com.bank.platform.mcp.engine.PreparedInput;
import com.bank.platform.mcp.engine.budget.BudgetPlan;
import com.bank.platform.mcp.engine.budget.BudgetPlanner;
import com.bank.platform.mcp.engine.budget.Section;
import com.bank.platform.mcp.engine.client.ModelProfile;
import com.bank.platform.mcp.engine.prompt.PromptAsset;
import com.bank.platform.mcp.engine.prompt.PromptModel;
import com.bank.platform.mcp.engine.prompt.PromptTemplate;
import com.bank.platform.mcp.engine.verify.EvidenceSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code code_review_expert} (Part 4.1): reviews a unified diff for correctness,
 * concurrency, resource-safety, and security defects in a Java/Spring/Oracle
 * banking codebase. It is a thin profile over the shared engine — the only
 * per-expert logic is the deterministic pre-pass (parse the diff into a citation
 * index so every finding can be grounded) and the prompt assembly. Everything
 * after the model call (parse, verify, score, label) is the engine's job.
 */
public final class CodeReviewExpert extends AbstractGeminiExpert<CodeReviewTask> {

    public static final String ID = "code_review_expert";
    public static final String VERSION = "1.4.0";
    public static final String PROMPT_VERSION = "v3";

    private static final String ASSET_BASE = "prompts/code_review/v3/";
    private static final String SYSTEM_ASSET = ASSET_BASE + "system.md";
    private static final String DEVELOPER_ASSET = ASSET_BASE + "developer.md";
    private static final String INPUT_SCHEMA = ASSET_BASE + "input.schema.json";
    private static final String OUTPUT_SCHEMA = ASSET_BASE + "output.schema.json";

    /** Tokens held back from the input budget for the system block + fixed framing. */
    private static final int OVERHEAD_RESERVE_TOKENS = 400;

    private final UnifiedDiffParser diffParser = new UnifiedDiffParser();
    private final BudgetPlanner budgetPlanner = BudgetPlanner.heuristic();
    private final PromptTemplate developerTemplate;
    private final ExpertProfile profile;

    /** Uses the default output-token cap from {@link ModelProfile#analysis}. */
    public CodeReviewExpert(GeminiExpertEngine engine, String modelId) {
        this(engine, modelId, ModelProfile.analysis(modelId).maxOutputTokens());
    }

    public CodeReviewExpert(GeminiExpertEngine engine, String modelId, int maxOutputTokens) {
        super(engine);
        // Fail fast at construction if the prompt assets are missing/malformed (Part 2.3).
        String outputSchema = PromptAsset.load(OUTPUT_SCHEMA);
        PromptAsset.load(SYSTEM_ASSET);
        this.developerTemplate = PromptTemplate.load(DEVELOPER_ASSET);
        this.profile = ExpertProfile.of(ID, VERSION, PROMPT_VERSION,
                new ModelProfile(modelId, "analysis", 0.0, maxOutputTokens), outputSchema);
    }

    @Override
    protected Class<CodeReviewTask> taskType() {
        return CodeReviewTask.class;
    }

    @Override
    protected ExpertProfile profile() {
        return profile;
    }

    @Override
    public ExpertDescriptor descriptor() {
        return new ExpertDescriptor(
                ID, VERSION,
                "Code Review Expert",
                "Reviews a unified git diff for correctness, concurrency, resource-safety, "
                        + "and security defects, with file:line evidence and confidence per finding.",
                "Use for a pull request or unified git diff when you need defect findings grounded "
                        + "in the changed lines. Prefer stacktrace_analyzer for a single exception, and "
                        + "architecture_inspector for cross-module structural concerns.",
                INPUT_SCHEMA, OUTPUT_SCHEMA,
                RagProfile.NONE, CostClass.MEDIUM,
                Set.of("expert.code_review.invoke"),
                "prompts/code_review/v3", "gemini-2.5-pro/analysis");
    }

    @Override
    protected PreparedInput preProcess(ExpertRequest request, CodeReviewTask task) {
        // 1. Deterministic Java pre-pass: parse the diff into a citation index so the
        //    verifier layer can resolve every "path:Ln" the model cites (Part 4.1 step 1/5).
        UnifiedDiff diff = diffParser.parse(task.diff());
        CitationIndex citations = CitationIndex.fromDiff(diff);
        for (CodeReviewTask.ChangedFile f : task.changedFiles()) {
            if (f.path() != null && f.afterContent() != null) {
                citations.addFile(f.path(), f.afterContent());
            }
        }
        EvidenceSet evidence = EvidenceSet.of(citations);

        // 2. Prompt build: stable cached system block + a template-rendered developer block
        //    whose budget-fitted body is injected as {{{body}}} (Part 2.7 + T5).
        String system = PromptAsset.load(SYSTEM_ASSET);

        List<String> filePaths = new ArrayList<>();
        for (DiffFile f : diff.files()) filePaths.add(f.path());

        PromptModel model = PromptModel.create()
                .put("emphasis", profileEmphasis(task.reviewProfile()))
                .put("hasStandards", !task.standards().isEmpty())
                .strings("standards", task.standards())
                .put("hasFiles", !filePaths.isEmpty())
                .strings("files", filePaths)
                .put("body", ""); // placeholder for the overhead estimate

        // Reserve budget for the system block + rendered framing; fit the rest (tenet T5).
        int reserve = OVERHEAD_RESERVE_TOKENS
                + budgetPlanner.estimate(system)
                + budgetPlanner.estimate(developerTemplate.render(model));
        int sectionBudget = Math.max(0, request.budget().maxInputTokens() - reserve);
        BudgetPlan plan = budgetPlanner.plan(sectionBudget, buildSections(task));

        model.put("body", plan.text());
        String user = developerTemplate.render(model);

        List<String> notes = new ArrayList<>();
        if (plan.limitationNote() != null) notes.add(plan.limitationNote());
        return new PreparedInput(system, user, evidence, notes);
    }

    /**
     * Variable-size body material, prioritized so the diff survives and off-hunk context
     * is sacrificed first. The framing (emphasis, standards, file list) is templated, not
     * budgeted — it is small and always shown.
     */
    private List<Section> buildSections(CodeReviewTask task) {
        List<Section> sections = new ArrayList<>();

        // The diff is the primary evidence: highest priority, trimmed only as a last resort.
        sections.add(Section.elastic("diff",
                "## Unified diff (cite lines from here as `path:Lstart-Lend`)\n"
                        + "```diff\n" + nullToEmpty(task.diff()) + "\n```",
                100));

        // Full after-image contents are off-hunk context only: lowest priority, dropped first.
        for (CodeReviewTask.ChangedFile f : task.changedFiles()) {
            if (f.afterContent() == null) continue;
            sections.add(Section.elastic("file:" + f.path(),
                    "## After-image of " + f.path() + " (off-hunk context only)\n"
                            + "```java\n" + f.afterContent() + "\n```",
                    30));
        }
        return sections;
    }

    private static String profileEmphasis(CodeReviewTask.ReviewProfile p) {
        return switch (p) {
            case SECURITY -> "security defects first (injection, sensitive-data logging, authz gaps, "
                    + "insecure deserialization, hardcoded secrets)";
            case CONCURRENCY -> "concurrency defects first (unsynchronized shared mutable state, "
                    + "non-atomic compound ops, lock-ordering, thread-unsafe Spring singletons)";
            case PERFORMANCE -> "performance defects first (N+1 access, unbounded allocations, "
                    + "blocking calls on hot paths, resource churn)";
            case DEFAULT -> "balanced review across correctness, concurrency, resource-safety, and security";
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

package com.bank.platform.mcp.engine;

import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.ExpertResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * The template-method base that makes every expert thin (tenet T7, Part 2.10). It
 * fixes the shared control flow — deserialize the tool-specific task, run the
 * deterministic pre-pass into a {@link PreparedInput}, hand it to the shared
 * {@link GeminiExpertEngine}, then optionally post-process — so a concrete expert
 * supplies only:
 * <ul>
 *   <li>{@link #taskType()} — the typed task record;</li>
 *   <li>{@link #profile()} — its {@link ExpertProfile} (model, schema, verifiers);</li>
 *   <li>{@link #preProcess(ExpertRequest, Object)} — the Java pre-pass + prompt build.</li>
 * </ul>
 * {@link #execute(ExpertRequest)} is {@code final}: experts cannot bypass the
 * verify/score pipeline.
 *
 * @param <T> the expert's typed task payload
 */
public abstract class AbstractGeminiExpert<T> implements Expert {

    /** Shared, lenient mapper: experts add task fields without breaking older clients. */
    private static final ObjectMapper TASK_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final GeminiExpertEngine engine;

    protected AbstractGeminiExpert(GeminiExpertEngine engine) {
        if (engine == null) throw new IllegalArgumentException("engine required");
        this.engine = engine;
    }

    @Override
    public final ExpertResult execute(ExpertRequest request) {
        if (request == null) throw new IllegalArgumentException("request required");
        T task = parseTask(request.task());
        PreparedInput input = preProcess(request, task);
        ExpertResult result = engine.run(input, profile());
        return postProcess(request, task, result);
    }

    /** Deserialize the generic task map into the expert's typed record. */
    protected T parseTask(Map<String, Object> task) {
        return TASK_MAPPER.convertValue(task, taskType());
    }

    protected abstract Class<T> taskType();

    protected abstract ExpertProfile profile();

    /** The deterministic pre-pass (pipeline stages 1–8): scope evidence, build the prompt. */
    protected abstract PreparedInput preProcess(ExpertRequest request, T task);

    /** Optional tool-specific post-processing (default: identity). */
    protected ExpertResult postProcess(ExpertRequest request, T task, ExpertResult result) {
        return result;
    }
}

package com.bank.platform.mcp.engine.prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The typed value bag a {@link PromptTemplate} renders against (Part 2.7: "Variables
 * are typed"). Values are restricted to a small, auditable set — strings, booleans,
 * numbers, nested models, and lists of scalars or models — which is exactly what the
 * logic-light templater can render. There is no way to inject behaviour: the
 * template can only read these values, never execute code.
 */
public final class PromptModel {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public static PromptModel create() {
        return new PromptModel();
    }

    public PromptModel put(String key, String value) {
        values.put(key, value);
        return this;
    }

    public PromptModel put(String key, boolean value) {
        values.put(key, value);
        return this;
    }

    public PromptModel put(String key, Number value) {
        values.put(key, value);
        return this;
    }

    /** A nested object, addressed by an object section {@code {{#key}}…{{/key}}}. */
    public PromptModel put(String key, PromptModel value) {
        values.put(key, value);
        return this;
    }

    /** A list of scalars, iterated with {@code {{#key}}{{.}}{{/key}}}. */
    public PromptModel strings(String key, List<String> items) {
        values.put(key, items == null ? List.of() : List.copyOf(items));
        return this;
    }

    /** A list of objects, iterated with {@code {{#key}}{{field}}{{/key}}}. */
    public PromptModel objects(String key, List<PromptModel> items) {
        values.put(key, items == null ? List.of() : List.copyOf(items));
        return this;
    }

    Map<String, Object> values() {
        return values;
    }
}

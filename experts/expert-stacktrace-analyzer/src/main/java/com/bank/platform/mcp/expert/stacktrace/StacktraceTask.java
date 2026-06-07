package com.bank.platform.mcp.expert.stacktrace;

import java.util.List;

/**
 * The tool-specific task for {@code stacktrace_analyzer} (Part 4.10 input). The raw
 * exception text is the primary evidence; {@code applicationPackages} let the
 * deterministic root-frame resolver tell the bank's own code from framework/JDK
 * frames; optional {@code sourceContext} lets findings cite {@code file:line} spans.
 *
 * @param stackTrace          the raw Java stack trace (top exception + Caused by chain)
 * @param applicationPackages package prefixes that mark application frames, e.g. {@code com.bank}
 * @param sourceContext       optional source files for off-trace citation
 */
public record StacktraceTask(
        String stackTrace,
        List<String> applicationPackages,
        List<SourceFile> sourceContext
) {
    public StacktraceTask {
        applicationPackages = applicationPackages == null ? List.of() : List.copyOf(applicationPackages);
        sourceContext = sourceContext == null ? List.of() : List.copyOf(sourceContext);
    }

    /** Full content of a source file referenced by the trace, for citation resolution. */
    public record SourceFile(String path, String content) {}
}

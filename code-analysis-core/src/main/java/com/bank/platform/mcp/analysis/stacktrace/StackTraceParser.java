package com.bank.platform.mcp.analysis.stacktrace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic Java stack-trace parser (Part 8.3 step 1). Produces a structured
 * {@link ThrowableInfo} chain (top exception + ordered {@code Caused by} causes +
 * suppressed), each with typed frames. Handles {@code ... N more} elision,
 * {@code (Native Method)} / {@code (Unknown Source)}, and nested module prefixes
 * like {@code app//com.bank.Foo}. Owners are resolved via {@link FrameOwnerResolver}.
 */
public final class StackTraceParser {

    private static final Pattern HEADER = Pattern.compile("^(?:Caused by:\\s*)?(?:Suppressed:\\s*)?([\\w.$]+)(?::\\s?(.*))?$");
    private static final Pattern AT_FRAME = Pattern.compile(
            "^\\s*at\\s+(?:[\\w.$]+/)?(?<cls>[\\w.$]+)\\.(?<method>[\\w$<>]+)\\((?<src>[^)]*)\\)\\s*$");
    private static final Pattern SRC = Pattern.compile("^(?<file>[^:]+):(?<line>\\d+)$");
    private static final Pattern MORE = Pattern.compile("^\\s*\\.\\.\\.\\s+(\\d+)\\s+more\\s*$");

    private final FrameOwnerResolver ownerResolver;

    public StackTraceParser(FrameOwnerResolver ownerResolver) {
        this.ownerResolver = ownerResolver;
    }

    public ThrowableInfo parse(String trace) {
        if (trace == null || trace.isBlank()) {
            return new ThrowableInfo("UnknownException", "", List.of(), null, List.of());
        }
        String[] lines = trace.split("\n", -1);
        Node root = null;
        Node current = null;
        // Stack tracks indentation for suppressed/cause nesting (best-effort).
        Deque<Node> stack = new ArrayDeque<>();

        for (String raw : lines) {
            String line = stripTrailing(raw);
            if (line.isBlank()) continue;

            String trimmed = line.strip();
            Matcher fm = AT_FRAME.matcher(line);
            if (fm.matches()) {
                if (current != null) current.frames.add(toFrame(fm));
                continue;
            }
            Matcher mm = MORE.matcher(line);
            if (mm.matches()) {
                continue; // elided frames shared with the enclosing trace
            }

            boolean isCause = trimmed.startsWith("Caused by:");
            boolean isSuppressed = trimmed.startsWith("Suppressed:");
            Matcher hm = HEADER.matcher(trimmed);
            if (hm.matches() && looksLikeThrowable(hm.group(1))) {
                Node node = new Node(hm.group(1), hm.group(2) == null ? "" : hm.group(2));
                if (root == null) {
                    root = node;
                    current = node;
                    stack.push(node);
                } else if (isCause) {
                    current.cause = node;
                    current = node;
                } else if (isSuppressed) {
                    current.suppressed.add(node);
                    // Suppressed frames follow until the next header; keep current pointed at it.
                    current = node;
                } else {
                    // A second top-level throwable header without Caused/Suppressed: treat as cause.
                    current.cause = node;
                    current = node;
                }
            }
            // else: message continuation or noise — ignored to stay deterministic.
        }
        if (root == null) {
            return new ThrowableInfo("UnknownException", trace.strip(), List.of(), null, List.of());
        }
        return root.build();
    }

    private Frame toFrame(Matcher fm) {
        String cls = fm.group("cls");
        String method = fm.group("method");
        String src = fm.group("src");
        String file = null;
        int lineNo = -1;
        Matcher sm = SRC.matcher(src);
        if (sm.matches()) {
            file = sm.group("file");
            lineNo = Integer.parseInt(sm.group("line"));
        }
        return new Frame(cls, method, file, lineNo, ownerResolver.classify(cls));
    }

    private static boolean looksLikeThrowable(String type) {
        return type != null && (type.endsWith("Exception") || type.endsWith("Error")
                || type.endsWith("Throwable") || type.contains("Exception")
                || type.contains("Error"));
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\r' || s.charAt(end - 1) == ' ')) end--;
        return s.substring(0, end);
    }

    /** Mutable builder node; converted to the immutable record tree at the end. */
    private static final class Node {
        final String type;
        final String message;
        final List<Frame> frames = new ArrayList<>();
        final List<Node> suppressed = new ArrayList<>();
        Node cause;

        Node(String type, String message) {
            this.type = type;
            this.message = message;
        }

        ThrowableInfo build() {
            List<ThrowableInfo> sup = new ArrayList<>();
            for (Node s : suppressed) sup.add(s.build());
            return new ThrowableInfo(type, message, frames, cause == null ? null : cause.build(), sup);
        }
    }
}

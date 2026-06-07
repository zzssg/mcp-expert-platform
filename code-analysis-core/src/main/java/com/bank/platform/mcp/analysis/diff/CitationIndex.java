package com.bank.platform.mcp.analysis.diff;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code path:Lstart-Lend} citations against the after-image of a diff
 * (and/or supplied full-file contents). This is the deterministic substrate the
 * CitationResolver and QuoteMatch verifiers (Part 5.7) run on: it answers
 * "does this cited line exist?" and "does the quoted text actually appear there?".
 *
 * <p>A finding that cites a line not present here is a fabrication and is dropped
 * or downgraded — this is how epistemic labels stay trustworthy.
 */
public final class CitationIndex {

    /** path -> (afterImageLineNumber -> lineContent) */
    private final Map<String, TreeMap<Integer, String>> byPath = new HashMap<>();

    private static final Pattern REF = Pattern.compile(
            "^(?<path>[^:]+):L?(?<from>\\d+)(?:-L?(?<to>\\d+))?$");

    /** Builds an index from a parsed diff (context + added lines form the after-image). */
    public static CitationIndex fromDiff(UnifiedDiff diff) {
        CitationIndex idx = new CitationIndex();
        for (DiffFile f : diff.files()) {
            TreeMap<Integer, String> map = idx.byPath.computeIfAbsent(f.path(), k -> new TreeMap<>());
            for (Hunk h : f.hunks()) {
                for (DiffLine l : h.lines()) {
                    if (l.inAfterImage() && l.newLine() > 0) {
                        map.put(l.newLine(), l.content());
                    }
                }
            }
        }
        return idx;
    }

    /** Adds full file content (1-based line numbering) — used when changedFiles carry afterContent. */
    public CitationIndex addFile(String path, String content) {
        if (path == null || content == null) return this;
        TreeMap<Integer, String> map = byPath.computeIfAbsent(path, k -> new TreeMap<>());
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            map.putIfAbsent(i + 1, lines[i]); // diff lines take precedence if already present
        }
        return this;
    }

    /** True if every line in the cited range exists in the after-image. */
    public boolean exists(String ref) {
        Parsed p = parse(ref).orElse(null);
        if (p == null) return false;
        TreeMap<Integer, String> map = byPath.get(p.path);
        if (map == null) return false;
        for (int ln = p.from; ln <= p.to; ln++) {
            if (!map.containsKey(ln)) return false;
        }
        return true;
    }

    /** Returns the concatenated source text for the cited range, if fully resolvable. */
    public Optional<String> resolve(String ref) {
        Parsed p = parse(ref).orElse(null);
        if (p == null) return Optional.empty();
        TreeMap<Integer, String> map = byPath.get(p.path);
        if (map == null) return Optional.empty();
        StringBuilder sb = new StringBuilder();
        for (int ln = p.from; ln <= p.to; ln++) {
            String line = map.get(ln);
            if (line == null) return Optional.empty();
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return Optional.of(sb.toString());
    }

    /**
     * QuoteMatch: returns true if {@code quote} (whitespace-normalized) is contained
     * in the resolved span. A null/blank quote is treated as "no quote to verify" → true.
     */
    public boolean quoteMatches(String ref, String quote) {
        if (quote == null || quote.isBlank()) return true;
        return resolve(ref)
                .map(span -> norm(span).contains(norm(quote)))
                .orElse(false);
    }

    private static String norm(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private Optional<Parsed> parse(String ref) {
        if (ref == null) return Optional.empty();
        Matcher m = REF.matcher(ref.trim());
        if (!m.matches()) return Optional.empty();
        int from = Integer.parseInt(m.group("from"));
        int to = m.group("to") == null ? from : Integer.parseInt(m.group("to"));
        if (to < from) { int t = from; from = to; to = t; }
        return Optional.of(new Parsed(m.group("path"), from, to));
    }

    private record Parsed(String path, int from, int to) {}
}

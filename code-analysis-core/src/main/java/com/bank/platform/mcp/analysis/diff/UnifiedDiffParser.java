package com.bank.platform.mcp.analysis.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic unified-diff parser (Part 4.1 step 1, "Java pre-pass"). Parses a
 * {@code git diff} into files/hunks/lines and resolves the after-image line number
 * of every context/added line, so the verifier layer can resolve {@code file:Ln}
 * citations and confirm quoted spans. Pure Java, no external dependencies.
 *
 * <p>Tolerant of the common decorations git emits (index lines, mode changes,
 * rename headers, {@code \ No newline at end of file}). Unknown headers are skipped.
 */
public final class UnifiedDiffParser {

    private static final Pattern FILE_HEADER = Pattern.compile("^diff --git a/(.+?) b/(.+)$");
    private static final Pattern OLD_PATH = Pattern.compile("^--- (?:a/)?(.+)$");
    private static final Pattern NEW_PATH = Pattern.compile("^\\+\\+\\+ (?:b/)?(.+)$");
    private static final Pattern HUNK = Pattern.compile(
            "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    public UnifiedDiff parse(String diff) {
        if (diff == null || diff.isBlank()) return new UnifiedDiff(List.of());
        List<DiffFile> files = new ArrayList<>();
        String[] lines = diff.split("\n", -1);

        String oldPath = null;
        String newPath = null;
        List<Hunk> hunks = new ArrayList<>();
        List<DiffLine> hunkLines = null;
        int oldNo = 0;
        int newNo = 0;
        int[] hunkHeader = null; // oldStart, oldCount, newStart, newCount

        for (String raw : lines) {
            Matcher fh = FILE_HEADER.matcher(raw);
            if (fh.matches()) {
                flushHunk(hunks, hunkHeader, hunkLines);
                hunkLines = null; hunkHeader = null;
                if (newPath != null || oldPath != null) {
                    files.add(new DiffFile(oldPath, newPath, new ArrayList<>(hunks)));
                }
                hunks = new ArrayList<>();
                oldPath = fh.group(1);
                newPath = fh.group(2);
                continue;
            }
            Matcher op = OLD_PATH.matcher(raw);
            if (op.matches()) { oldPath = normalize(op.group(1)); continue; }
            Matcher np = NEW_PATH.matcher(raw);
            if (np.matches()) { newPath = normalize(np.group(1)); continue; }

            Matcher hh = HUNK.matcher(raw);
            if (hh.matches()) {
                flushHunk(hunks, hunkHeader, hunkLines);
                int os = Integer.parseInt(hh.group(1));
                int oc = hh.group(2) == null ? 1 : Integer.parseInt(hh.group(2));
                int ns = Integer.parseInt(hh.group(3));
                int nc = hh.group(4) == null ? 1 : Integer.parseInt(hh.group(4));
                hunkHeader = new int[]{os, oc, ns, nc};
                hunkLines = new ArrayList<>();
                oldNo = os;
                newNo = ns;
                continue;
            }

            if (hunkLines == null) continue; // outside any hunk (index/mode/rename lines)
            if (raw.startsWith("\\")) continue; // "\ No newline at end of file"

            char c = raw.isEmpty() ? ' ' : raw.charAt(0);
            String content = raw.isEmpty() ? "" : raw.substring(1);
            switch (c) {
                case '+' -> { hunkLines.add(new DiffLine(DiffLine.Kind.ADDED, content, -1, newNo)); newNo++; }
                case '-' -> { hunkLines.add(new DiffLine(DiffLine.Kind.DELETED, content, oldNo, -1)); oldNo++; }
                case ' ' -> { hunkLines.add(new DiffLine(DiffLine.Kind.CONTEXT, content, oldNo, newNo)); oldNo++; newNo++; }
                default -> { /* unrecognized; ignore */ }
            }
        }
        flushHunk(hunks, hunkHeader, hunkLines);
        if (newPath != null || oldPath != null) {
            files.add(new DiffFile(oldPath, newPath, hunks));
        }
        return new UnifiedDiff(files);
    }

    private static void flushHunk(List<Hunk> hunks, int[] header, List<DiffLine> lines) {
        if (header != null && lines != null) {
            hunks.add(new Hunk(header[0], header[1], header[2], header[3], lines));
        }
    }

    private static String normalize(String p) {
        if (p == null) return null;
        int tab = p.indexOf('\t');
        return tab >= 0 ? p.substring(0, tab) : p;
    }
}

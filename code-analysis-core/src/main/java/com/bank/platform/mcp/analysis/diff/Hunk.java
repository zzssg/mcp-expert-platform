package com.bank.platform.mcp.analysis.diff;

import java.util.List;

/** One {@code @@ -a,b +c,d @@} hunk and its lines. */
public record Hunk(int oldStart, int oldCount, int newStart, int newCount, List<DiffLine> lines) {
    public Hunk {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}

package com.bank.platform.mcp.analysis.diff;

import java.util.List;

/** Parsed representation of a complete unified git diff. */
public record UnifiedDiff(List<DiffFile> files) {
    public UnifiedDiff {
        files = files == null ? List.of() : List.copyOf(files);
    }
}

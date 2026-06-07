package com.bank.platform.mcp.analysis.diff;

import java.util.List;

/** A single changed file in a unified diff. {@code newPath} is the post-change path. */
public record DiffFile(String oldPath, String newPath, List<Hunk> hunks) {
    public DiffFile {
        hunks = hunks == null ? List.of() : List.copyOf(hunks);
    }

    /** Convenience: the path used for citations (the after-image path). */
    public String path() {
        return newPath != null ? newPath : oldPath;
    }
}

package com.bank.platform.mcp.analysis.diff;

/** A single line within a hunk, carrying its resolved line number in the after-image. */
public record DiffLine(Kind kind, String content, int oldLine, int newLine) {
    public enum Kind { CONTEXT, ADDED, DELETED }

    /** Lines present in the post-change file (citation targets resolve against these). */
    public boolean inAfterImage() {
        return kind == Kind.CONTEXT || kind == Kind.ADDED;
    }
}

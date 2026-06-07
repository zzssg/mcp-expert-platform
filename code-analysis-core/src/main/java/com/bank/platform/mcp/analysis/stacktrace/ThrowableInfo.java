package com.bank.platform.mcp.analysis.stacktrace;

import java.util.List;

/**
 * A parsed throwable: its type, message, frames, and (recursively) its cause and
 * suppressed throwables. The {@code Caused by:} chain is preserved in order so the
 * deepest cause can be found (Part 8.3 step 1).
 */
public record ThrowableInfo(
        String type,
        String message,
        List<Frame> frames,
        ThrowableInfo cause,
        List<ThrowableInfo> suppressed
) {
    public ThrowableInfo {
        frames = frames == null ? List.of() : List.copyOf(frames);
        suppressed = suppressed == null ? List.of() : List.copyOf(suppressed);
    }

    /** Walks the cause chain to the deepest (root) throwable. Guards against cycles. */
    public ThrowableInfo deepestCause() {
        ThrowableInfo cur = this;
        int guard = 0;
        while (cur.cause() != null && cur.cause() != cur && guard++ < 64) {
            cur = cur.cause();
        }
        return cur;
    }

    /** All throwables in the chain, outermost first. */
    public List<ThrowableInfo> chain() {
        java.util.List<ThrowableInfo> out = new java.util.ArrayList<>();
        ThrowableInfo cur = this;
        int guard = 0;
        while (cur != null && guard++ < 64) {
            out.add(cur);
            if (cur.cause() == cur) break;
            cur = cur.cause();
        }
        return out;
    }
}

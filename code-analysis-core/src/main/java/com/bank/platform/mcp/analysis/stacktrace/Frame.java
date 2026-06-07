package com.bank.platform.mcp.analysis.stacktrace;

/** A parsed stack frame with its resolved owner classification. */
public record Frame(String declaringClass, String method, String file, int line, FrameOwner owner) {

    /** Citation-style reference for the frame, e.g. {@code PaymentService.java:52}. */
    public String ref() {
        if (file == null || line <= 0) return declaringClass + "#" + method;
        return file + ":" + line;
    }

    /** Symbol reference, e.g. {@code com.bank.PaymentService#settle}. */
    public String symbol() {
        return declaringClass + "#" + method;
    }
}

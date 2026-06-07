package com.bank.platform.mcp.engine.client;

/**
 * A failure talking to the model (timeout, transport, safety block, quota). The
 * engine catches this and degrades to a structured {@code ERROR} result rather
 * than leaking transport exceptions to the caller (Part 5.9 resilience).
 *
 * <p>{@link #retryable()} separates <em>transient</em> failures (timeouts, 5xx,
 * connection resets, throttling) — worth a retry and a signal of service ill-health
 * for the circuit breaker — from <em>terminal</em> ones (safety block, malformed
 * request, auth) that retrying cannot fix and that should not trip the breaker.
 * Constructors default to {@code retryable=true}; use {@link #nonRetryable} for the
 * terminal case.
 */
public class GeminiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean retryable;

    public GeminiException(String message) {
        this(message, null, true);
    }

    public GeminiException(String message, Throwable cause) {
        this(message, cause, true);
    }

    public GeminiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** A terminal failure that retrying cannot fix (safety, auth, malformed request). */
    public static GeminiException nonRetryable(String message) {
        return new GeminiException(message, null, false);
    }

    public static GeminiException nonRetryable(String message, Throwable cause) {
        return new GeminiException(message, cause, false);
    }

    public boolean retryable() {
        return retryable;
    }
}

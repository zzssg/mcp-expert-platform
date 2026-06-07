package com.bank.platform.mcp.engine.resilience;

import java.time.Duration;

/**
 * Tuning for {@link ResilientGeminiClient} (Part 5.9). One immutable record covers
 * the three policies that wrap a model call:
 * <ul>
 *   <li><b>retry</b> — {@code maxAttempts} with exponential backoff, only on
 *       transient ({@code retryable}) failures;</li>
 *   <li><b>per-attempt timeout</b> — {@code callTimeout}, so one slow call cannot
 *       burn a request's whole deadline;</li>
 *   <li><b>circuit breaker</b> — a count-based window of {@code slidingWindowSize}
 *       calls trips open at {@code failureRateThreshold}% and stays open for
 *       {@code circuitOpenWait} before probing with {@code permittedCallsInHalfOpen}.</li>
 * </ul>
 *
 * @param maxAttempts             total attempts including the first (≥ 1)
 * @param initialBackoff          first retry wait; doubles (×{@code backoffMultiplier}) thereafter
 * @param backoffMultiplier       exponential backoff growth factor (≥ 1.0)
 * @param callTimeout             per-attempt wall-clock cap
 * @param slidingWindowSize       breaker window size (also the minimum calls before it evaluates)
 * @param failureRateThreshold    breaker open threshold, as a percentage (0–100)
 * @param circuitOpenWait         how long the breaker stays open before half-open probing
 * @param permittedCallsInHalfOpen probe calls allowed while half-open
 */
public record ResilienceConfig(
        int maxAttempts,
        Duration initialBackoff,
        double backoffMultiplier,
        Duration callTimeout,
        int slidingWindowSize,
        float failureRateThreshold,
        Duration circuitOpenWait,
        int permittedCallsInHalfOpen
) {
    public ResilienceConfig {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        if (backoffMultiplier < 1.0) throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
        if (slidingWindowSize < 1) throw new IllegalArgumentException("slidingWindowSize must be >= 1");
        if (failureRateThreshold <= 0 || failureRateThreshold > 100)
            throw new IllegalArgumentException("failureRateThreshold must be in (0,100]");
        if (permittedCallsInHalfOpen < 1)
            throw new IllegalArgumentException("permittedCallsInHalfOpen must be >= 1");
        if (initialBackoff == null || callTimeout == null || circuitOpenWait == null)
            throw new IllegalArgumentException("durations must not be null");
    }

    /** Production defaults: 3 attempts, 30s per-attempt cap, open at 50% over a 20-call window. */
    public static ResilienceConfig defaults() {
        return new ResilienceConfig(
                3, Duration.ofMillis(200), 2.0,
                Duration.ofSeconds(30),
                20, 50.0f, Duration.ofSeconds(30), 3);
    }

    public ResilienceConfig withMaxAttempts(int n) {
        return new ResilienceConfig(n, initialBackoff, backoffMultiplier, callTimeout,
                slidingWindowSize, failureRateThreshold, circuitOpenWait, permittedCallsInHalfOpen);
    }

    public ResilienceConfig withInitialBackoff(Duration d) {
        return new ResilienceConfig(maxAttempts, d, backoffMultiplier, callTimeout,
                slidingWindowSize, failureRateThreshold, circuitOpenWait, permittedCallsInHalfOpen);
    }

    public ResilienceConfig withCallTimeout(Duration d) {
        return new ResilienceConfig(maxAttempts, initialBackoff, backoffMultiplier, d,
                slidingWindowSize, failureRateThreshold, circuitOpenWait, permittedCallsInHalfOpen);
    }

    public ResilienceConfig withCircuitBreaker(int windowSize, float failureRateThreshold, Duration openWait) {
        return new ResilienceConfig(maxAttempts, initialBackoff, backoffMultiplier, callTimeout,
                windowSize, failureRateThreshold, openWait, permittedCallsInHalfOpen);
    }
}

package com.bank.platform.mcp.engine.resilience;

import com.bank.platform.mcp.engine.client.GeminiClient;
import com.bank.platform.mcp.engine.client.GeminiException;
import com.bank.platform.mcp.engine.client.GeminiRequest;
import com.bank.platform.mcp.engine.client.GeminiResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Decorates any {@link GeminiClient} with the production resilience policies of
 * Part 5.9 — <b>retry</b>, <b>circuit breaker</b>, and a <b>per-attempt timeout</b>
 * — without entangling them with parsing or verification. Because it is itself a
 * {@code GeminiClient}, the engine is unaware it exists: {@code new
 * GeminiExpertEngine(new ResilientGeminiClient(vertexClient))}.
 *
 * <p>Decoration order is {@code retry( circuitBreaker( timeout( call ) ) )}: each
 * attempt has its own deadline; the breaker records each attempt's success/failure;
 * retry re-attempts the whole thing on transient failures only. Every failure mode
 * is normalized to a {@link GeminiException} so the engine's single catch clause
 * degrades cleanly to a structured {@code ERROR} result:
 * <ul>
 *   <li>a slow attempt → cancelled and surfaced as a retryable timeout;</li>
 *   <li>retries exhausted → the last underlying {@link GeminiException};</li>
 *   <li>breaker open → a terminal (non-retryable) "circuit open" exception, fast.</li>
 * </ul>
 *
 * <p>The per-attempt timeout runs the blocking call on a virtual thread (Part 2.1),
 * so a stalled model call costs a parked carrier-free thread, not a pooled one.
 * Stateless and thread-safe; share one instance.
 */
public final class ResilientGeminiClient implements GeminiClient, AutoCloseable {

    private final GeminiClient delegate;
    private final ResilienceConfig config;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final ExecutorService executor;
    private final boolean ownsExecutor;

    public ResilientGeminiClient(GeminiClient delegate) {
        this(delegate, ResilienceConfig.defaults(), "gemini");
    }

    public ResilientGeminiClient(GeminiClient delegate, ResilienceConfig config, String name) {
        this(delegate, config, name, Executors.newVirtualThreadPerTaskExecutor(), true);
    }

    /** Inject a shared {@link ExecutorService} (its lifecycle is the caller's). */
    public ResilientGeminiClient(GeminiClient delegate, ResilienceConfig config, String name,
                                 ExecutorService executor) {
        this(delegate, config, name, executor, false);
    }

    private ResilientGeminiClient(GeminiClient delegate, ResilienceConfig config, String name,
                                  ExecutorService executor, boolean ownsExecutor) {
        if (delegate == null) throw new IllegalArgumentException("delegate required");
        if (config == null) throw new IllegalArgumentException("config required");
        this.delegate = delegate;
        this.config = config;
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
        this.circuitBreaker = CircuitBreaker.of(name, circuitBreakerConfig(config));
        this.retry = Retry.of(name, retryConfig(config));
    }

    @Override
    public GeminiResponse generate(GeminiRequest request) throws GeminiException {
        Supplier<GeminiResponse> timed = () -> callWithTimeout(request);
        Supplier<GeminiResponse> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, timed);
        Supplier<GeminiResponse> retried = Retry.decorateSupplier(retry, guarded);
        try {
            return retried.get();
        } catch (GeminiException e) {
            throw e; // already normalized (transient timeout, or the delegate's own failure)
        } catch (CallNotPermittedException e) {
            throw GeminiException.nonRetryable("circuit open for model '" + circuitBreaker.getName()
                    + "'; failing fast", e);
        } catch (RuntimeException e) {
            throw new GeminiException("model call failed: " + e.getMessage(), e);
        }
    }

    /** One attempt, bounded by {@code callTimeout}, run on a virtual thread. */
    private GeminiResponse callWithTimeout(GeminiRequest request) {
        Future<GeminiResponse> future = executor.submit(() -> delegate.generate(request));
        try {
            return future.get(config.callTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new GeminiException("model call exceeded " + config.callTimeout().toMillis()
                    + "ms timeout", e); // retryable
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof GeminiException ge) throw ge; // preserve retryable flag
            throw new GeminiException("model call failed: " + cause, cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw GeminiException.nonRetryable("interrupted awaiting model response", e);
        }
    }

    /** Exposed so platform-observability can bind metrics/events (Part 2.9). */
    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public Retry retry() {
        return retry;
    }

    @Override
    public void close() {
        if (ownsExecutor) executor.shutdownNow();
    }

    private static CircuitBreakerConfig circuitBreakerConfig(ResilienceConfig c) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(c.slidingWindowSize())
                // Evaluate the rate only once the window has enough calls to be meaningful.
                .minimumNumberOfCalls(c.slidingWindowSize())
                .failureRateThreshold(c.failureRateThreshold())
                .waitDurationInOpenState(c.circuitOpenWait())
                .permittedNumberOfCallsInHalfOpenState(c.permittedCallsInHalfOpen())
                // Terminal (input-driven) failures must not be read as service ill-health.
                .ignoreException(ResilientGeminiClient::isTerminal)
                .build();
    }

    private static RetryConfig retryConfig(ResilienceConfig c) {
        return RetryConfig.custom()
                .maxAttempts(c.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        c.initialBackoff().toMillis(), c.backoffMultiplier()))
                // Retry only transient failures; never a terminal one or a fast-fail open circuit.
                .retryOnException(t -> t instanceof GeminiException ge && ge.retryable())
                .build();
    }

    private static boolean isTerminal(Throwable t) {
        return t instanceof GeminiException ge && !ge.retryable();
    }
}

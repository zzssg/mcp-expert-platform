package com.bank.platform.mcp.engine.resilience;

import com.bank.platform.mcp.engine.client.GeminiClient;
import com.bank.platform.mcp.engine.client.GeminiException;
import com.bank.platform.mcp.engine.client.GeminiRequest;
import com.bank.platform.mcp.engine.client.GeminiResponse;
import com.bank.platform.mcp.engine.client.ModelProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the three Part 5.9 policies through the {@link GeminiClient} seam, using
 * a programmable in-test delegate. Backoff is set to a few ms so the suite stays fast.
 */
class ResilientGeminiClientTest {

    private static final GeminiRequest REQUEST =
            new GeminiRequest("sys", "user", null, ModelProfile.analysis("gemini-test"));

    /** A delegate whose behaviour (fail N times / always fail / sleep) the test scripts. */
    private static final class StubClient implements GeminiClient {
        final AtomicInteger calls = new AtomicInteger();
        final int failuresBeforeSuccess;
        final boolean retryable;
        final long sleepMs;

        StubClient(int failuresBeforeSuccess, boolean retryable, long sleepMs) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.retryable = retryable;
            this.sleepMs = sleepMs;
        }

        @Override
        public GeminiResponse generate(GeminiRequest request) {
            int n = calls.incrementAndGet();
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GeminiException("interrupted", e);
                }
            }
            if (n <= failuresBeforeSuccess) {
                throw retryable
                        ? new GeminiException("transient #" + n)
                        : GeminiException.nonRetryable("terminal #" + n);
            }
            return new GeminiResponse("{\"findings\":[]}", 1, 1, false, "gemini-test-1", "STOP");
        }
    }

    private ResilienceConfig fastConfig() {
        return ResilienceConfig.defaults().withInitialBackoff(Duration.ofMillis(10));
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        var stub = new StubClient(2, true, 0); // fail twice, succeed on the 3rd
        try (var client = new ResilientGeminiClient(stub, fastConfig().withMaxAttempts(3), "t1")) {
            GeminiResponse response = client.generate(REQUEST);

            assertThat(response.modelVersion()).isEqualTo("gemini-test-1");
            assertThat(stub.calls).hasValue(3);
        }
    }

    @Test
    void doesNotRetryTerminalFailures() {
        var stub = new StubClient(Integer.MAX_VALUE, false, 0);
        try (var client = new ResilientGeminiClient(stub, fastConfig().withMaxAttempts(3), "t2")) {
            assertThatThrownBy(() -> client.generate(REQUEST))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("terminal #1");

            assertThat(stub.calls).hasValue(1); // exactly one attempt — no retry
        }
    }

    @Test
    void timesOutASlowAttempt() {
        var stub = new StubClient(0, true, 1_000); // would succeed, but too slow
        var config = fastConfig().withMaxAttempts(1).withCallTimeout(Duration.ofMillis(100));
        try (var client = new ResilientGeminiClient(stub, config, "t3")) {
            assertThatThrownBy(() -> client.generate(REQUEST))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("timeout");
        }
    }

    @Test
    void opensCircuitAfterRepeatedFailuresAndFailsFast() {
        var stub = new StubClient(Integer.MAX_VALUE, true, 0);
        var config = fastConfig()
                .withMaxAttempts(1)
                .withCircuitBreaker(2, 100.0f, Duration.ofSeconds(10));
        try (var client = new ResilientGeminiClient(stub, config, "t4")) {
            // Two failing calls fill the window at a 100% failure rate -> breaker opens.
            assertThatThrownBy(() -> client.generate(REQUEST)).isInstanceOf(GeminiException.class);
            assertThatThrownBy(() -> client.generate(REQUEST)).isInstanceOf(GeminiException.class);
            assertThat(stub.calls).hasValue(2);

            // Third call must fail fast WITHOUT reaching the delegate.
            assertThatThrownBy(() -> client.generate(REQUEST))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("circuit open");
            assertThat(stub.calls).hasValue(2);
        }
    }
}

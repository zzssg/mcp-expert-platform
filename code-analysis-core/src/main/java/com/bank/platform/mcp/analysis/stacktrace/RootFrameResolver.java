package com.bank.platform.mcp.analysis.stacktrace;

import java.util.Optional;

/**
 * Finds the true root frame (Part 8.3 step 2): the first APPLICATION frame at or
 * below the deepest cause — NOT the JDK top frame. This single deterministic step
 * fixes the most common human mistake of reading the top line of a trace.
 *
 * <p>Fallback order: deepest-cause app frame → any app frame in the chain →
 * deepest-cause first non-JDK frame → deepest-cause top frame.
 */
public final class RootFrameResolver {

    public Optional<Frame> resolve(ThrowableInfo throwable) {
        if (throwable == null) return Optional.empty();
        ThrowableInfo deepest = throwable.deepestCause();

        Optional<Frame> appInDeepest = firstOwner(deepest, FrameOwner.APPLICATION);
        if (appInDeepest.isPresent()) return appInDeepest;

        // Search the whole chain for any application frame.
        for (ThrowableInfo t : throwable.chain()) {
            Optional<Frame> app = firstOwner(t, FrameOwner.APPLICATION);
            if (app.isPresent()) return app;
        }

        // No app frame: prefer the deepest cause's first non-JDK frame.
        for (Frame f : deepest.frames()) {
            if (f.owner() != FrameOwner.JDK) return Optional.of(f);
        }
        return deepest.frames().isEmpty() ? Optional.empty() : Optional.of(deepest.frames().get(0));
    }

    private static Optional<Frame> firstOwner(ThrowableInfo t, FrameOwner owner) {
        for (Frame f : t.frames()) {
            if (f.owner() == owner) return Optional.of(f);
        }
        return Optional.empty();
    }
}

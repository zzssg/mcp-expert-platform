package com.bank.platform.mcp.engine.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal classpath loader for versioned prompt assets (Part 2.7). Prompt bundles
 * live as resources under {@code prompts/<expert>/<version>/} (a stable
 * {@code system.md} for context caching, plus the JSON schemas) and are loaded
 * once and cached. This is the first, deliberately small brick of the {@code prompt/}
 * layer; templating/variable substitution lands when the first expert needs it.
 *
 * <p>Assets are immutable build artifacts, so a process-lifetime cache is correct
 * and avoids re-reading the jar on every call.
 */
public final class PromptAsset {

    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private PromptAsset() {}

    /** Loads a UTF-8 classpath resource (e.g. {@code prompts/code_review/v3/system.md}). */
    public static String load(String classpathRef) {
        return CACHE.computeIfAbsent(classpathRef, PromptAsset::read);
    }

    private static String read(String ref) {
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        InputStream stream = ctx == null ? null : ctx.getResourceAsStream(ref);
        if (stream == null) {
            stream = PromptAsset.class.getClassLoader().getResourceAsStream(ref);
        }
        if (stream == null) {
            throw new IllegalStateException("prompt asset not found on classpath: " + ref);
        }
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading prompt asset: " + ref, e);
        }
    }
}

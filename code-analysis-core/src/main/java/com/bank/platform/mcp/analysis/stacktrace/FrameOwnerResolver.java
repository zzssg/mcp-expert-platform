package com.bank.platform.mcp.analysis.stacktrace;

import java.util.List;
import java.util.Set;

/**
 * Classifies a frame's declaring class as APPLICATION / FRAMEWORK / JDK by package
 * prefix (Part 8.3 step 2). Application package prefixes are supplied per tenant
 * (e.g. {@code com.bank}); everything else falls back to a maintained list of
 * well-known framework/JDK prefixes.
 */
public final class FrameOwnerResolver {

    private static final List<String> JDK_PREFIXES = List.of(
            "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.");

    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "org.springframework", "org.apache.kafka", "org.apache.catalina",
            "org.apache.tomcat", "org.hibernate", "oracle.", "com.zaxxer.hikari",
            "reactor.", "io.netty", "com.fasterxml.jackson", "org.slf4j", "ch.qos.logback",
            "io.micrometer", "org.junit", "feign.", "com.netflix");

    private final Set<String> applicationPrefixes;

    public FrameOwnerResolver(Set<String> applicationPrefixes) {
        this.applicationPrefixes = applicationPrefixes == null ? Set.of() : Set.copyOf(applicationPrefixes);
    }

    public FrameOwner classify(String declaringClass) {
        if (declaringClass == null) return FrameOwner.UNKNOWN;
        for (String p : applicationPrefixes) {
            if (declaringClass.startsWith(p)) return FrameOwner.APPLICATION;
        }
        for (String p : JDK_PREFIXES) {
            if (declaringClass.startsWith(p)) return FrameOwner.JDK;
        }
        for (String p : FRAMEWORK_PREFIXES) {
            if (declaringClass.startsWith(p)) return FrameOwner.FRAMEWORK;
        }
        // Unknown third-party code: treat as FRAMEWORK for root-frame purposes
        // (we only want to land on the app's own code as the root).
        return FrameOwner.UNKNOWN;
    }
}

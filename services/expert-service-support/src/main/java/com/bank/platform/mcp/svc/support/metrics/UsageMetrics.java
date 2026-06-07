package com.bank.platform.mcp.svc.support.metrics;

import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.contract.Status;
import com.bank.platform.mcp.contract.Usage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-process, thread-safe aggregation of token usage per expert tool (Part 2.9 token
 * counters). Every {@link ExpertResult} is folded in by {@link MeteredExpert}; the
 * {@link #snapshot()} / {@link #totals()} feed the {@code /api/usage} endpoint and the
 * usage dashboard. Counters are process-lifetime (reset on restart) — a proper
 * time-series sink is a later Part-9 concern.
 */
public class UsageMetrics {

    private final ConcurrentMap<String, Counters> byTool = new ConcurrentHashMap<>();

    /** Folds one result's usage + status into the per-tool counters. */
    public void record(ExpertResult result) {
        if (result == null) return;
        String tool = result.tool() == null ? "unknown" : result.tool();
        byTool.computeIfAbsent(tool, t -> new Counters()).add(result.usage(), result.status());
    }

    /** Per-tool usage, sorted by tool id. */
    public List<ToolUsage> snapshot() {
        List<ToolUsage> out = new ArrayList<>(byTool.size());
        byTool.forEach((tool, c) -> out.add(c.toUsage(tool)));
        out.sort(Comparator.comparing(ToolUsage::tool));
        return out;
    }

    /** Aggregate across all tools. */
    public Totals totals() {
        long calls = 0, ok = 0, partial = 0, empty = 0, error = 0, in = 0, out = 0, cached = 0;
        for (Counters c : byTool.values()) {
            calls += c.calls.sum();
            ok += c.ok.sum();
            partial += c.partial.sum();
            empty += c.empty.sum();
            error += c.error.sum();
            in += c.inputTokens.sum();
            out += c.outputTokens.sum();
            cached += c.cachedCalls.sum();
        }
        return new Totals(calls, ok, partial, empty, error, in, out, in + out, cached);
    }

    /** Clears all counters (used by tests / an admin reset). */
    public void reset() {
        byTool.clear();
    }

    private static final class Counters {
        final LongAdder calls = new LongAdder();
        final LongAdder ok = new LongAdder();
        final LongAdder partial = new LongAdder();
        final LongAdder empty = new LongAdder();
        final LongAdder error = new LongAdder();
        final LongAdder inputTokens = new LongAdder();
        final LongAdder outputTokens = new LongAdder();
        final LongAdder cachedCalls = new LongAdder();
        volatile Instant lastUpdated;

        void add(Usage usage, Status status) {
            calls.increment();
            if (usage != null) {
                inputTokens.add(usage.inputTokens());
                outputTokens.add(usage.outputTokens());
                if (usage.cached()) cachedCalls.increment();
            }
            switch (status == null ? Status.OK : status) {
                case OK -> ok.increment();
                case PARTIAL -> partial.increment();
                case EMPTY -> empty.increment();
                case ERROR -> error.increment();
            }
            lastUpdated = Instant.now();
        }

        ToolUsage toUsage(String tool) {
            long in = inputTokens.sum();
            long out = outputTokens.sum();
            return new ToolUsage(tool, calls.sum(), ok.sum(), partial.sum(), empty.sum(), error.sum(),
                    in, out, in + out, cachedCalls.sum(), lastUpdated);
        }
    }

    /** Per-tool usage row. Serialized directly to JSON by the endpoint. */
    public record ToolUsage(String tool, long calls, long ok, long partial, long empty, long error,
                            long inputTokens, long outputTokens, long totalTokens, long cachedCalls,
                            Instant lastUpdated) {}

    /** Aggregate totals across all tools. */
    public record Totals(long calls, long ok, long partial, long empty, long error,
                         long inputTokens, long outputTokens, long totalTokens, long cachedCalls) {}
}

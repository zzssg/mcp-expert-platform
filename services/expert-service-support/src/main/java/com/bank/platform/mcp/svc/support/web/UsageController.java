package com.bank.platform.mcp.svc.support.web;

import com.bank.platform.mcp.svc.support.metrics.UsageMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Exposes the in-process {@link UsageMetrics} as JSON for the usage dashboard
 * ({@code static/usage.html}). One read-only endpoint, no side effects.
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageMetrics metrics;

    public UsageController(UsageMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping
    public UsageReport usage() {
        return new UsageReport(Instant.now(), metrics.totals(), metrics.snapshot());
    }

    /** The response envelope: when it was generated, the totals, and the per-tool rows. */
    public record UsageReport(Instant generatedAt,
                              UsageMetrics.Totals totals,
                              List<UsageMetrics.ToolUsage> tools) {}
}

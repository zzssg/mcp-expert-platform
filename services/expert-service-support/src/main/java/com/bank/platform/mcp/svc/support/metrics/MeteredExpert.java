package com.bank.platform.mcp.svc.support.metrics;

import com.bank.platform.mcp.contract.ExpertDescriptor;
import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.ExpertResult;
import com.bank.platform.mcp.engine.Expert;

/**
 * Transparent {@link Expert} decorator that records every call's token usage into
 * {@link UsageMetrics} (Part 2.9). Because it is itself an {@code Expert}, the MCP
 * tool layer is unaware of it — wrap the real expert once at wiring time and metering
 * happens for free, with no change to the engine or the experts.
 */
public final class MeteredExpert implements Expert {

    private final Expert delegate;
    private final UsageMetrics metrics;

    public MeteredExpert(Expert delegate, UsageMetrics metrics) {
        if (delegate == null) throw new IllegalArgumentException("delegate required");
        if (metrics == null) throw new IllegalArgumentException("metrics required");
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public ExpertDescriptor descriptor() {
        return delegate.descriptor();
    }

    @Override
    public ExpertResult execute(ExpertRequest request) {
        ExpertResult result = delegate.execute(request);
        metrics.record(result);
        return result;
    }
}

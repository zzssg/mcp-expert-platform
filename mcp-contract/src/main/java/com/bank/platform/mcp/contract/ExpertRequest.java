package com.bank.platform.mcp.contract;

import java.util.Map;

/**
 * Shared input envelope (Part 3.1). {@code task} is the tool-specific payload,
 * kept as a generic map at the contract boundary so the contract module carries
 * no per-tool coupling; each expert deserializes it into its own typed task.
 */
public record ExpertRequest(
        String requestId,
        RepoRef repo,
        Options options,
        Budget budget,
        Map<String, Object> task
) {
    public ExpertRequest {
        if (requestId == null || requestId.isBlank())
            throw new IllegalArgumentException("requestId required");
        options = options == null ? Options.defaults() : options;
        budget = budget == null ? Budget.defaults() : budget;
        task = task == null ? Map.of() : Map.copyOf(task);
    }
}

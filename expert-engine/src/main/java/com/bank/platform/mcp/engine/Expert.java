package com.bank.platform.mcp.engine;

import com.bank.platform.mcp.contract.ExpertDescriptor;
import com.bank.platform.mcp.contract.ExpertRequest;
import com.bank.platform.mcp.contract.ExpertResult;

/**
 * One specialist capability (Part 2.10). An {@code Expert} is a stateless analyzer:
 * it advertises a {@link ExpertDescriptor} (for the gateway catalog) and turns a
 * validated {@link ExpertRequest} into the universal {@link ExpertResult} envelope.
 * It plans nothing and calls no other expert — coordination lives in the
 * orchestrator (tenet T1) — which keeps experts cacheable and independently testable.
 */
public interface Expert {

    /** Declarative metadata used to register and discover this tool. */
    ExpertDescriptor descriptor();

    /** Analyze one request. Never throws across this boundary — failures become ERROR results. */
    ExpertResult execute(ExpertRequest request);
}

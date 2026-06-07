package com.bank.platform.mcp.contract;

import java.util.List;
import java.util.Set;

/**
 * Declarative metadata describing an expert (Part 2.3). Validated fail-fast at
 * boot. The gateway aggregates descriptors from each expert service into the
 * unified, tenant-filtered MCP tools/list catalog.
 *
 * @param id            stable tool id, e.g. "code_review_expert"
 * @param version       semver; surfaced in the tool-name suffix if a breaking change
 * @param title         short human title
 * @param summary       one-line description shown to clients
 * @param usageHint     tuned for the orchestrator — when to pick this tool over another (Part 2.4)
 * @param inputSchema   JSON Schema (Draft 2020-12) as a classpath resource path
 * @param outputSchema  JSON Schema resource path
 * @param rag           always NONE in this build
 * @param costClass     expected cost band
 * @param tenantScopes  RBAC scopes required to call the tool
 * @param promptAssetRef versioned prompt-asset reference, e.g. "prompts/code_review/v3"
 * @param modelProfileRef model profile reference, e.g. "gemini-2.5-pro/analysis"
 */
public record ExpertDescriptor(
        String id,
        String version,
        String title,
        String summary,
        String usageHint,
        String inputSchema,
        String outputSchema,
        RagProfile rag,
        CostClass costClass,
        Set<String> tenantScopes,
        String promptAssetRef,
        String modelProfileRef
) {
    public ExpertDescriptor {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("descriptor.id required");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("descriptor.version required");
        rag = rag == null ? RagProfile.NONE : rag;
        costClass = costClass == null ? CostClass.MEDIUM : costClass;
        tenantScopes = tenantScopes == null ? Set.of() : Set.copyOf(tenantScopes);
    }

    /** Major version drives the routing key (toolId, majorVersion) at the gateway (Part 2.5). */
    public int majorVersion() {
        return Integer.parseInt(version.split("\\.")[0]);
    }

    /** MCP tool name; suffixed with the major version when > 1 to signal a breaking contract. */
    public String mcpToolName() {
        int major = majorVersion();
        return major <= 1 ? id : id + "_v" + major;
    }

    public static List<String> requiredAssets(ExpertDescriptor d) {
        return List.of(d.inputSchema(), d.outputSchema(), d.promptAssetRef());
    }
}

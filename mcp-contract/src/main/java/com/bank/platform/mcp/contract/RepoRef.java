package com.bank.platform.mcp.contract;

/** Scopes repository reads (and, in RAG-enabled deployments, retrieval). */
public record RepoRef(String id, String ref, String commit, String lang, String buildTool) {}

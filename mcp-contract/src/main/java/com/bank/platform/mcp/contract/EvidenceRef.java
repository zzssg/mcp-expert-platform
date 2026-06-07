package com.bank.platform.mcp.contract;

/**
 * A pointer to the exact span that grounds a finding. {@code ref} is a stable
 * citation target resolvable by the verifier layer: {@code file:Lstart-Lend} in a
 * diff/file, a {@code chunkId}, or a log offset. {@code quote} is the literal text
 * the model claims is present at {@code ref}; QuoteMatch verifies it.
 */
public record EvidenceRef(String type, String ref, String quote) {
    public EvidenceRef {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("evidence.type required");
        if (ref == null || ref.isBlank()) throw new IllegalArgumentException("evidence.ref required");
    }
}

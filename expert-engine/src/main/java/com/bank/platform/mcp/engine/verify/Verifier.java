package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.contract.Finding;

/**
 * A deterministic check over a single finding + the evidence it was allowed to use
 * (Part 5.7). Verifiers never invent; they can only PASS, DOWNGRADE, or DROP.
 */
public interface Verifier {
    String name();
    VerifierResult verify(Finding finding, EvidenceSet evidence);
}

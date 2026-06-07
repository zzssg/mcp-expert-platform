package com.bank.platform.mcp.engine.verify;

import java.util.List;
import java.util.Map;

/** The whole verifier pass over a candidate result, keyed by finding id. */
public record VerifierReport(Map<String, FindingVerdict> byFinding, List<String> contradictions) {
    public FindingVerdict verdictFor(String findingId) {
        return byFinding.get(findingId);
    }
}

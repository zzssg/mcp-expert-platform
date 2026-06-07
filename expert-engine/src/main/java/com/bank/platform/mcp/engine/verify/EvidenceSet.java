package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.analysis.diff.CitationIndex;

import java.util.Set;

/**
 * The exact, immutable set of material that was given to the model for one call:
 * the citation index over the inputs/changed files, the set of symbols known to
 * exist, and the set of decoded-signal codes that are FACT-grade. Verifiers
 * (Part 5.7) check the model's findings against ONLY this set — nothing the model
 * cites may be accepted unless it resolves here. This is what makes "Gemini
 * proposes, verifiers dispose" enforceable.
 */
public record EvidenceSet(
        CitationIndex citations,
        Set<String> knownSymbols,
        Set<String> factSignalCodes
) {
    public EvidenceSet {
        knownSymbols = knownSymbols == null ? Set.of() : Set.copyOf(knownSymbols);
        factSignalCodes = factSignalCodes == null ? Set.of() : Set.copyOf(factSignalCodes);
    }

    public static EvidenceSet of(CitationIndex citations) {
        return new EvidenceSet(citations, Set.of(), Set.of());
    }

    public boolean symbolKnown(String symbol) {
        return symbol != null && knownSymbols.contains(symbol);
    }

    public boolean signalIsFact(String code) {
        return code != null && factSignalCodes.contains(code);
    }
}

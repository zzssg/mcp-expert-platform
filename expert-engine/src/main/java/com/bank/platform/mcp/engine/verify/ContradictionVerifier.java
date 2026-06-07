package com.bank.platform.mcp.engine.verify;

import com.bank.platform.mcp.contract.Finding;

import java.util.List;

/**
 * ContradictionDetector (Part 5.7): flags findings that cite the SAME span but make
 * opposite claims (a cheap, deterministic contradiction heuristic). Operates over
 * the whole finding set, so it is applied by {@link VerifierChain} as a batch pass
 * rather than per-finding; here it exposes a static batch method.
 */
public final class ContradictionVerifier {

    public static final String NAME = "ContradictionDetector";

    private ContradictionVerifier() {}

    /** Returns the ids of findings that contradict another finding on the same cited span. */
    public static java.util.Set<String> detect(List<Finding> findings) {
        java.util.Set<String> flagged = new java.util.HashSet<>();
        for (int i = 0; i < findings.size(); i++) {
            for (int j = i + 1; j < findings.size(); j++) {
                Finding a = findings.get(i);
                Finding b = findings.get(j);
                if (sharesSpan(a, b) && opposed(a, b)) {
                    flagged.add(a.id());
                    flagged.add(b.id());
                }
            }
        }
        return flagged;
    }

    private static boolean sharesSpan(Finding a, Finding b) {
        var sa = a.evidence().stream().map(e -> e.ref()).toList();
        return b.evidence().stream().anyMatch(e -> sa.contains(e.ref()));
    }

    /** Very conservative: opposite category + one asserts safe, other asserts unsafe wording. */
    private static boolean opposed(Finding a, Finding b) {
        String ta = (a.title() + " " + a.detail()).toLowerCase();
        String tb = (b.title() + " " + b.detail()).toLowerCase();
        boolean aSafe = ta.contains("no issue") || ta.contains("is safe") || ta.contains("correct");
        boolean bUnsafe = tb.contains("vulnerab") || tb.contains("bug") || tb.contains("leak") || tb.contains("race");
        boolean bSafe = tb.contains("no issue") || tb.contains("is safe") || tb.contains("correct");
        boolean aUnsafe = ta.contains("vulnerab") || ta.contains("bug") || ta.contains("leak") || ta.contains("race");
        return (aSafe && bUnsafe) || (bSafe && aUnsafe);
    }
}

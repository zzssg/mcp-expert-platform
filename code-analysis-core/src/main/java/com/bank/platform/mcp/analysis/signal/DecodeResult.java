package com.bank.platform.mcp.analysis.signal;

import java.util.List;

/** Output of {@link SignalDecoder}: the decoded signals plus the dominant failure class. */
public record DecodeResult(List<Signal> signals, FailureClass failureClass) {
    public DecodeResult {
        signals = signals == null ? List.of() : List.copyOf(signals);
        failureClass = failureClass == null ? FailureClass.UNCLASSIFIED : failureClass;
    }
}

package com.bank.platform.mcp.engine.confidence;

/**
 * Maps raw model self-confidence to a calibrated value using a reliability curve
 * learned from the golden set (Part 5.8). Default is a conservative shrink toward
 * the base rate — overconfident raw values are pulled down. Replace with a fitted
 * isotonic/Platt curve once golden-set data exists.
 */
@FunctionalInterface
public interface ModelCalibration {
    double calibrate(double rawSelfConfidence);

    /** Conservative default: shrink 40% toward 0.5 (the no-information prior). */
    static ModelCalibration conservativeShrink() {
        return raw -> {
            double r = Math.max(0, Math.min(1, raw));
            return 0.5 + 0.6 * (r - 0.5);
        };
    }
}

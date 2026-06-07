package com.bank.platform.mcp.engine.confidence;

/**
 * The per-finding sub-scores fed into the confidence formula (Part 3.6). Each is in
 * [0,1]. {@code sModel} is the model's self-reported confidence AFTER calibration
 * discounting — raw self-confidence is never used directly (LLMs are overconfident).
 */
public record Signals(
        double sSchema,
        double sGrounding,
        double sConsistency,
        double sModel,
        double sHeuristic
) {}

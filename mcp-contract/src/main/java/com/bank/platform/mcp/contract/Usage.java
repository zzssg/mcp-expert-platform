package com.bank.platform.mcp.contract;

/** Token accounting + retrieval stats surfaced for cost metering (Part 2.8, 5.10). */
public record Usage(int inputTokens, int outputTokens, int retrievedChunks, boolean cached) {
    public static Usage none() {
        return new Usage(0, 0, 0, false);
    }
}

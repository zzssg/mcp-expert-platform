package com.bank.platform.mcp.analysis.signal;

/** Coarse failure classification used to narrow root-cause space before the LLM (Part 8.3). */
public enum FailureClass {
    ORA_CONSTRAINT_VIOLATION,
    ORA_CONNECTION,
    ORA_OTHER,
    SPRING_WIRING,
    KAFKA_SERDE,
    KAFKA_REBALANCE,
    KAFKA_COMMIT,
    CONNECTION_POOL_EXHAUSTION,
    NULL_DEREFERENCE,
    CLASS_CAST,
    OUT_OF_MEMORY,
    SERIALIZATION,
    TIMEOUT,
    UNCLASSIFIED
}

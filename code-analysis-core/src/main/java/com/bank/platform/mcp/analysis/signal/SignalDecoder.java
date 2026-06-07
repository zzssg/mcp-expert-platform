package com.bank.platform.mcp.analysis.signal;

import com.bank.platform.mcp.analysis.stacktrace.ThrowableInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodes well-known Oracle / Spring / Kafka / HikariCP / JVM signals from a parsed
 * throwable chain (Part 8.3 step 3). All emitted signals are FACTs — they come from
 * a maintained table or a structural type match, never from model memory.
 *
 * <p>The dominant {@link FailureClass} is chosen by priority (most specific /
 * actionable first) so the downstream prompt is anchored on the strongest signal.
 */
public final class SignalDecoder {

    private static final Pattern ORA = Pattern.compile("ORA-(\\d{5})");

    /** Maintained ORA lookup. Extend freely; entries here are FACT-grade. */
    private static final Map<String, OraEntry> ORA_TABLE = new LinkedHashMap<>();
    static {
        ORA_TABLE.put("00001", new OraEntry("unique constraint violated", FailureClass.ORA_CONSTRAINT_VIOLATION));
        ORA_TABLE.put("00060", new OraEntry("deadlock detected while waiting for resource", FailureClass.ORA_OTHER));
        ORA_TABLE.put("00942", new OraEntry("table or view does not exist", FailureClass.ORA_OTHER));
        ORA_TABLE.put("01400", new OraEntry("cannot insert NULL into a NOT NULL column", FailureClass.ORA_CONSTRAINT_VIOLATION));
        ORA_TABLE.put("01401", new OraEntry("inserted value too large for column", FailureClass.ORA_OTHER));
        ORA_TABLE.put("02291", new OraEntry("integrity constraint violated - parent key not found", FailureClass.ORA_CONSTRAINT_VIOLATION));
        ORA_TABLE.put("02292", new OraEntry("integrity constraint violated - child record found", FailureClass.ORA_CONSTRAINT_VIOLATION));
        ORA_TABLE.put("04068", new OraEntry("existing state of packages has been discarded", FailureClass.ORA_OTHER));
        ORA_TABLE.put("08177", new OraEntry("can't serialize access for this transaction", FailureClass.ORA_OTHER));
        ORA_TABLE.put("12170", new OraEntry("TNS connect timeout occurred", FailureClass.ORA_CONNECTION));
        ORA_TABLE.put("12519", new OraEntry("TNS: no appropriate service handler found", FailureClass.ORA_CONNECTION));
        ORA_TABLE.put("12541", new OraEntry("TNS: no listener", FailureClass.ORA_CONNECTION));
        ORA_TABLE.put("28000", new OraEntry("the account is locked", FailureClass.ORA_OTHER));
    }

    public DecodeResult decode(ThrowableInfo throwable) {
        if (throwable == null) return new DecodeResult(List.of(), FailureClass.UNCLASSIFIED);
        List<Signal> signals = new ArrayList<>();
        FailureClass dominant = FailureClass.UNCLASSIFIED;

        for (ThrowableInfo t : throwable.chain()) {
            String type = t.type() == null ? "" : t.type();
            String msg = t.message() == null ? "" : t.message();
            String hay = type + " : " + msg;

            // --- Oracle ORA-NNNNN ---
            Matcher om = ORA.matcher(hay);
            while (om.find()) {
                String code = om.group(1);
                OraEntry e = ORA_TABLE.get(code);
                String meaning = e != null ? e.meaning : "Oracle error ORA-" + code;
                FailureClass fc = e != null ? e.failureClass : FailureClass.ORA_OTHER;
                signals.add(Signal.fact("ORA-" + code, meaning, fc));
                dominant = morePriority(dominant, fc);
            }

            // --- Spring wiring ---
            if (type.endsWith("NoSuchBeanDefinitionException")) {
                signals.add(Signal.fact(simple(type), "No bean of the required type/name is defined", FailureClass.SPRING_WIRING));
                dominant = morePriority(dominant, FailureClass.SPRING_WIRING);
            } else if (type.endsWith("UnsatisfiedDependencyException")) {
                signals.add(Signal.fact(simple(type), "A required dependency could not be wired", FailureClass.SPRING_WIRING));
                dominant = morePriority(dominant, FailureClass.SPRING_WIRING);
            } else if (type.endsWith("BeanCreationException")) {
                signals.add(Signal.fact(simple(type), "A bean failed to initialise during context startup", FailureClass.SPRING_WIRING));
                dominant = morePriority(dominant, FailureClass.SPRING_WIRING);
            }

            // --- Kafka ---
            if (type.endsWith("RecordDeserializationException") || type.endsWith("SerializationException")) {
                signals.add(Signal.fact(simple(type), "Kafka record could not be (de)serialized — likely poison pill / schema mismatch", FailureClass.KAFKA_SERDE));
                dominant = morePriority(dominant, FailureClass.KAFKA_SERDE);
            } else if (type.endsWith("CommitFailedException")) {
                signals.add(Signal.fact(simple(type), "Kafka offset commit failed — consumer likely fell out of the group", FailureClass.KAFKA_COMMIT));
                dominant = morePriority(dominant, FailureClass.KAFKA_COMMIT);
            } else if (type.endsWith("RebalanceInProgressException")) {
                signals.add(Signal.fact(simple(type), "A consumer-group rebalance is in progress", FailureClass.KAFKA_REBALANCE));
                dominant = morePriority(dominant, FailureClass.KAFKA_REBALANCE);
            }

            // --- HikariCP / pool ---
            if (type.endsWith("SQLTransientConnectionException")
                    || (msg.contains("Connection is not available") && msg.contains("request timed out"))) {
                signals.add(Signal.fact(simple(type.isBlank() ? "HikariPool" : type),
                        "Connection pool exhausted — no connection available within the timeout", FailureClass.CONNECTION_POOL_EXHAUSTION));
                dominant = morePriority(dominant, FailureClass.CONNECTION_POOL_EXHAUSTION);
            }

            // --- JVM core ---
            if (type.endsWith("NullPointerException")) {
                String detail = msg.isBlank() ? "Null dereference" : "Null dereference: " + msg;
                signals.add(Signal.fact("NullPointerException", detail, FailureClass.NULL_DEREFERENCE));
                dominant = morePriority(dominant, FailureClass.NULL_DEREFERENCE);
            } else if (type.endsWith("ClassCastException")) {
                signals.add(Signal.fact("ClassCastException", msg.isBlank() ? "Invalid cast" : msg, FailureClass.CLASS_CAST));
                dominant = morePriority(dominant, FailureClass.CLASS_CAST);
            } else if (type.endsWith("OutOfMemoryError")) {
                String space = msg.isBlank() ? "JVM heap/native memory exhausted" : "OutOfMemoryError: " + msg;
                signals.add(Signal.fact("OutOfMemoryError", space, FailureClass.OUT_OF_MEMORY));
                dominant = morePriority(dominant, FailureClass.OUT_OF_MEMORY);
            }

            // --- generic timeouts ---
            if (type.endsWith("TimeoutException") || msg.toLowerCase().contains("timed out")) {
                signals.add(Signal.fact(simple(type.isBlank() ? "Timeout" : type), "An operation timed out", FailureClass.TIMEOUT));
                dominant = morePriority(dominant, FailureClass.TIMEOUT);
            }
        }
        return new DecodeResult(signals, dominant);
    }

    private static String simple(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) return "";
        int i = fqcn.lastIndexOf('.');
        return i >= 0 ? fqcn.substring(i + 1) : fqcn;
    }

    /** Priority ordering: more specific/actionable classes win as the dominant label. */
    private static FailureClass morePriority(FailureClass current, FailureClass candidate) {
        return priority(candidate) > priority(current) ? candidate : current;
    }

    private static int priority(FailureClass fc) {
        return switch (fc) {
            case ORA_CONSTRAINT_VIOLATION -> 100;
            case CONNECTION_POOL_EXHAUSTION -> 95;
            case KAFKA_SERDE -> 90;
            case ORA_CONNECTION -> 85;
            case SPRING_WIRING -> 80;
            case OUT_OF_MEMORY -> 78;
            case KAFKA_COMMIT -> 70;
            case KAFKA_REBALANCE -> 65;
            case NULL_DEREFERENCE -> 60;
            case CLASS_CAST -> 55;
            case SERIALIZATION -> 50;
            case ORA_OTHER -> 45;
            case TIMEOUT -> 40;
            case UNCLASSIFIED -> 0;
        };
    }

    private record OraEntry(String meaning, FailureClass failureClass) {}
}

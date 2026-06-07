package com.bank.platform.mcp.engine.parse;

import com.bank.platform.mcp.contract.Epistemic;
import com.bank.platform.mcp.contract.EvidenceRef;
import com.bank.platform.mcp.contract.Finding;
import com.bank.platform.mcp.contract.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pipeline stage 10 (Part 2.6 / 5.6): turn the model's raw text into a validated,
 * normalized {@link ParsedCandidate}. The model is told to emit strict JSON, but
 * production models occasionally wrap it in markdown fences or add a stray prose
 * preamble, so we apply <strong>bounded</strong> repair — never unbounded
 * re-prompting here — and record how much repair was needed so the confidence
 * scorer can penalize a non-clean parse via {@code S_schema}.
 *
 * <p>Repair ladder (each step tried in order, first that parses wins):
 * <ol>
 *   <li>parse the raw output as-is (clean — schemaScore 1.0);</li>
 *   <li>strip ```json / ``` code fences;</li>
 *   <li>extract the outermost {@code { … }} object.</li>
 * </ol>
 * Deterministic and dependency-light; no network, no model round-trip.
 */
public final class ResponseParser {

    private static final double SCHEMA_DECAY = 0.8; // S_schema *= 0.8 per repair pass
    private static final Pattern FENCE =
            Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL);

    private final ObjectMapper mapper;
    private final JsonSchemaFactory schemaFactory;

    public ResponseParser() {
        this(new ObjectMapper());
    }

    public ResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    /**
     * @param raw                the model's raw output
     * @param responseSchemaJson JSON Schema to validate against; null/blank skips validation
     */
    public ParsedCandidate parse(String raw, String responseSchemaJson) {
        if (raw == null || raw.isBlank()) {
            return ParsedCandidate.unparseable("empty model output");
        }

        JsonNode root = null;
        int repairAttempts = 0;
        for (String candidate : repairLadder(raw)) {
            try {
                root = mapper.readTree(candidate);
                if (root != null && !root.isMissingNode()) break;
            } catch (Exception ignored) {
                // try the next repair rung
            }
            repairAttempts++;
        }
        if (root == null || !root.isObject()) {
            return ParsedCandidate.unparseable("output did not parse to a JSON object");
        }

        List<String> schemaErrors = validate(root, responseSchemaJson);
        boolean valid = schemaErrors.isEmpty();
        double schemaScore = valid ? Math.pow(SCHEMA_DECAY, repairAttempts) : 0.0;

        return new ParsedCandidate(
                true,
                valid,
                schemaScore,
                repairAttempts,
                extractFindings(root),
                extractPayload(root),
                readDouble(root, "overallConfidence", 0.5),
                readStringList(root, "limitations"),
                schemaErrors);
    }

    private List<String> repairLadder(String raw) {
        List<String> rungs = new ArrayList<>(3);
        rungs.add(raw.trim());
        Matcher m = FENCE.matcher(raw);
        if (m.find()) {
            rungs.add(m.group(1).trim());
        }
        int open = raw.indexOf('{');
        int close = raw.lastIndexOf('}');
        if (open >= 0 && close > open) {
            rungs.add(raw.substring(open, close + 1).trim());
        }
        return rungs;
    }

    private List<String> validate(JsonNode root, String responseSchemaJson) {
        if (responseSchemaJson == null || responseSchemaJson.isBlank()) {
            return List.of();
        }
        try {
            JsonSchema schema = schemaFactory.getSchema(responseSchemaJson);
            Set<ValidationMessage> messages = schema.validate(root);
            if (messages.isEmpty()) return List.of();
            List<String> errors = new ArrayList<>(messages.size());
            for (ValidationMessage vm : messages) errors.add(vm.getMessage());
            return errors;
        } catch (Exception e) {
            return List.of("schema validation failed: " + e.getMessage());
        }
    }

    private List<Finding> extractFindings(JsonNode root) {
        JsonNode arr = root.get("findings");
        if (arr == null || !arr.isArray()) return List.of();
        List<Finding> findings = new ArrayList<>(arr.size());
        int idx = 0;
        for (JsonNode node : arr) {
            idx++;
            findings.add(toFinding(node, idx));
        }
        return findings;
    }

    private Finding toFinding(JsonNode node, int idx) {
        String id = readString(node, "id", "f-" + idx);
        String category = readString(node, "category", "general");
        Severity severity = parseSeverity(readString(node, "severity", "LOW"));
        // The model's epistemic/confidence are PROPOSALS; the verifier+scorer overwrite them.
        Epistemic epistemic = parseEpistemic(readString(node, "epistemic", "HYPOTHESIS"));
        double confidence = clamp01(readDouble(node, "confidence", 0.5));
        String title = readString(node, "title", "");
        String detail = readString(node, "detail", "");
        String recommendation = readString(node, "recommendation", null);
        return new Finding(id, category, severity, epistemic, confidence,
                title, detail, extractEvidence(node), recommendation);
    }

    private List<EvidenceRef> extractEvidence(JsonNode finding) {
        JsonNode arr = finding.get("evidence");
        if (arr == null || !arr.isArray()) return List.of();
        List<EvidenceRef> refs = new ArrayList<>(arr.size());
        for (JsonNode e : arr) {
            String type = readString(e, "type", null);
            String ref = readString(e, "ref", null);
            if (type == null || type.isBlank() || ref == null || ref.isBlank()) {
                continue; // an evidence entry without a resolvable target is worthless — skip it
            }
            refs.add(new EvidenceRef(type, ref, readString(e, "quote", null)));
        }
        return refs;
    }

    private Map<String, Object> extractPayload(JsonNode root) {
        JsonNode payload = root.get("payload");
        if (payload == null || !payload.isObject()) return Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.convertValue(payload, LinkedHashMap.class);
        return map == null ? Map.of() : map;
    }

    // --- typed, null-safe JSON readers ------------------------------------------

    private static Severity parseSeverity(String s) {
        try {
            return Severity.valueOf(s.trim().toUpperCase());
        } catch (RuntimeException e) {
            return Severity.LOW;
        }
    }

    private static Epistemic parseEpistemic(String s) {
        try {
            return Epistemic.valueOf(s.trim().toUpperCase());
        } catch (RuntimeException e) {
            return Epistemic.HYPOTHESIS;
        }
    }

    private static String readString(JsonNode node, String field, String dflt) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? dflt : v.asText();
    }

    private static double readDouble(JsonNode node, String field, double dflt) {
        JsonNode v = node.get(field);
        return (v == null || !v.isNumber()) ? dflt : v.asDouble();
    }

    private static List<String> readStringList(JsonNode node, String field) {
        JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray()) return List.of();
        List<String> out = new ArrayList<>(arr.size());
        for (JsonNode n : arr) {
            if (n != null && !n.isNull()) out.add(n.asText());
        }
        return out;
    }

    private static double clamp01(double d) {
        return Math.max(0.0, Math.min(1.0, d));
    }
}

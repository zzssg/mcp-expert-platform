package com.bank.platform.mcp.contract;

import java.util.List;
import java.util.Map;

/**
 * The universal output envelope (Part 2.8 / 3.2). Every expert returns exactly
 * this shape; only {@code payload} varies by tool.
 */
public record ExpertResult(
        String schemaVersion,
        String tool,
        String toolVersion,
        String modelVersion,
        String promptVersion,
        Status status,
        Map<String, Object> payload,
        List<Finding> findings,
        double overallConfidence,
        List<String> limitations,
        Usage usage,
        Diagnostics diagnostics
) {
    public ExpertResult {
        schemaVersion = schemaVersion == null ? "1.0" : schemaVersion;
        findings = findings == null ? List.of() : List.copyOf(findings);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static Builder builder(String tool, String toolVersion) {
        return new Builder(tool, toolVersion);
    }

    public static final class Builder {
        private final String tool;
        private final String toolVersion;
        private String modelVersion = "unknown";
        private String promptVersion = "unknown";
        private Status status = Status.OK;
        private Map<String, Object> payload = Map.of();
        private List<Finding> findings = List.of();
        private double overallConfidence;
        private List<String> limitations = List.of();
        private Usage usage = Usage.none();
        private Diagnostics diagnostics = Diagnostics.of(0);

        private Builder(String tool, String toolVersion) {
            this.tool = tool;
            this.toolVersion = toolVersion;
        }

        public Builder modelVersion(String v) { this.modelVersion = v; return this; }
        public Builder promptVersion(String v) { this.promptVersion = v; return this; }
        public Builder status(Status s) { this.status = s; return this; }
        public Builder payload(Map<String, Object> p) { this.payload = p; return this; }
        public Builder findings(List<Finding> f) { this.findings = f; return this; }
        public Builder overallConfidence(double c) { this.overallConfidence = c; return this; }
        public Builder limitations(List<String> l) { this.limitations = l; return this; }
        public Builder usage(Usage u) { this.usage = u; return this; }
        public Builder diagnostics(Diagnostics d) { this.diagnostics = d; return this; }

        public ExpertResult build() {
            return new ExpertResult("1.0", tool, toolVersion, modelVersion, promptVersion,
                    status, payload, findings, overallConfidence, limitations, usage, diagnostics);
        }
    }
}

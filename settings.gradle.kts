rootProject.name = "mcp-expert-platform"

// Module layout (Part 2.10). RAG modules (rag-core, rag-core/opensearch) are
// intentionally EXCLUDED from this scope. Every expert runs with RagProfile.NONE;
// the engine carries no RetrievalService wiring.

// --- Implemented & compiling -------------------------------------------------
include("mcp-contract")          // universal envelope + enums (pure Java)
include("code-analysis-core")    // deterministic analyzers (diff, stacktrace, signal) — no LLM
include("expert-engine")         // shared Gemini framework (verify + confidence done; rest in progress)

include("experts:expert-code-review")          // thin code_review_expert profile over the engine
include("experts:expert-stacktrace-analyzer")  // thin stacktrace_analyzer profile over the engine
include("services:expert-service-support")     // shared Spring glue: model egress + engine beans
include("services:expert-svc-code")            // Spring Boot MCP server (code-group experts)
include("services:expert-svc-incident")        // Spring Boot MCP server (incident-group experts)

// --- Build-out plan (uncomment as each module is added) ----------------------
// include("platform-observability")          // OTel / metrics / audit helpers

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // maven { url = uri("https://repo.spring.io/milestone") } // if pinning a Spring AI milestone
    }
}

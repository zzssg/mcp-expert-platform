# MCP Gemini Expert Platform

Production-oriented implementation of the MCP platform that exposes Gemini-powered
expert tools to GitHub Copilot Agent and Claude Sonnet (orchestrator), per the
design in [`docs/MCP-Gemini-Expert-Platform-Architecture.md`](docs/MCP-Gemini-Expert-Platform-Architecture.md).

## Scope of this build
- **RAG is excluded** by request. Every expert runs with `RagProfile.NONE`; there is
  no `rag-core` module and no `RetrievalService` wiring. The `ragEnabled`/`RagProfile`
  hooks remain in the contract only for forward-compatibility.
- **Build tool is Gradle** (Kotlin DSL) with a version catalog (`gradle/libs.versions.toml`).
- Java 21 LTS, Spring Boot 3.4.x, Spring AI 1.1.x (the "production-safe now" column of Part 11).

## Module layout
```
mcp-contract/          Universal envelope + enums (pure Java, no deps)
code-analysis-core/    Deterministic analyzers: diff/citation, stacktrace, signal decode (no LLM)
expert-engine/         Shared Gemini framework: prompt build, parse, verify, confidence, resilience
experts/
  expert-code-review/        thin profile over the engine
  expert-stacktrace-analyzer/ thin profile over the engine
services/
  expert-svc-code/      Spring Boot MCP server (code_review_expert)
  expert-svc-incident/  Spring Boot MCP server (stacktrace_analyzer)
platform-observability/ OTel/metrics/audit helpers
```

## Implementation status
| Area | State |
|------|-------|
| `mcp-contract` | Complete, compiles |
| `code-analysis-core` (diff, stacktrace, signal) | Complete, compiles, smoke-tested |
| `expert-engine` — verify + confidence (anti-hallucination core) | Complete, compiles, smoke-tested |
| `expert-engine` — client + parse + **`GeminiExpertEngine` orchestration** | Complete, compiles, tested (3 engine tests) |
| `expert-engine` — `Expert`/`AbstractGeminiExpert` template + `PromptAsset` loader | Complete, compiles, tested |
| `expert-engine` — `resilience/` (retry + circuit breaker + per-attempt timeout) | Complete, compiles, tested (4 tests) |
| `expert-engine` — `budget/` (token estimate + context reduction) | Complete, compiles, tested (5 tests) |
| `expert-engine` — `prompt/` templater (logic-light Mustache subset) | Complete, compiles, tested (12 tests) |
| `experts/expert-code-review` | Complete, compiles, tested (4 tests) — budget-fitted, template-rendered |
| `experts/expert-stacktrace-analyzer` | Complete, compiles, tested (3 tests) — over the deterministic root-frame/signal cores |
| `expert-engine` — `VertexRestGeminiClient` (REST adapter to Gemini/Vertex proxy) | Complete, compiles, tested (4 tests) |
| `services/expert-service-support` (shared model egress + engine auto-config) | Complete, compiles |
| `services/expert-svc-code` (Spring Boot MCP server, WebMVC) | Complete, compiles, context test (3 tests) — `code_review_expert` |
| `services/expert-svc-incident` (Spring Boot MCP server, WebMVC) | Complete, compiles, context test (3 tests) — `stacktrace_analyzer` |
| other `experts/*` profiles (`log_analyzer`, `sql_expert`, …) | Not started |

Total: **43 tests, all passing; full `gradle build` green offline.**

### Token budgets
Configurable per service via `platform.expert.budget` (env-bound): `max-input-tokens`
(drives prompt context reduction — the diff is kept, off-hunk context is trimmed/dropped),
`max-output-tokens` (the model's output cap), and `deadline` (per-attempt model-call
timeout). All three are honored end-to-end.

### Token usage dashboard
Each service meters the token usage of every expert call (a `MeteredExpert` decorator
folds each `ExpertResult.usage()` into a thread-safe `UsageMetrics` registry) and exposes:
- `GET /api/usage` — JSON: per-tool calls, OK/PARTIAL/EMPTY/ERROR counts, input/output/total
  tokens, cached calls, plus aggregate totals;
- `GET /usage.html` — a self-contained static dashboard that polls `/api/usage` every 5s.

So `http://localhost:8080/usage.html` (code) and `http://localhost:8081/usage.html`
(incident) show live token usage by expert tool. Counters are process-lifetime.

### Local config via `.env`
Copy `.env.example` to `.env` (git-ignored) and fill in your proxy/model/budget values.
A `DotEnvEnvironmentPostProcessor` (in `expert-service-support`) loads it at startup as the
lowest-precedence property source — so `${VERTEX_BASE_URL}`, `${EXPERT_*}` etc. resolve from
it, while real OS env vars and `-D` system properties still override it. It is found by
walking up from the working directory, so `bootRun` from any module picks up the repo-root
`.env`. No external dependency.

### Reaching the model
The platform talks to Gemini through one governed egress: a Vertex AI `generateContent`
REST endpoint (typically an in-house LLM proxy), configured via `platform.expert.vertex`
(`base-url`, `project`, `location`, Bearer `token`). The `gRPC` Vertex SDK / Spring AI
Vertex starter are deliberately **not** used — they cannot target an arbitrary REST proxy
base URL. When the proxy is unconfigured the server still boots and serves its catalog,
returning `ERROR` on calls.

The deterministic cores and the verifier/confidence layer are the load-bearing,
hardest-to-get-right elements and were built and verified first (see the design
doc's closing note). Two JDK-only smoke tests demonstrate: (1) the root-frame
resolver landing on the application frame of a Spring→Oracle trace and decoding
`ORA-00001`; (2) the verifier chain dropping a fabricated finding while keeping and
re-labelling a grounded one.

## Building
This was scaffolded in an environment without network access to Maven Central, so
the Gradle wrapper JAR is not bundled. Generate it once in your environment:

```bash
gradle wrapper --gradle-version 8.12   # or use your enterprise Gradle install
./gradlew build
```

Add your enterprise Maven mirror (and the Spring milestone repo if you pin a Spring
AI milestone) in `settings.gradle.kts` under `dependencyResolutionManagement`.

## Notes
- Constructor injection only; no field injection (Part 11.5).
- The `GeminiClient` abstraction (`ModelProfile`) makes the Gemini 2.5 → 3.x move a
  config change, not a code change (Part 5.10 / 9.5).

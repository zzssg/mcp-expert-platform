# Using the expert MCP servers from GitHub Copilot (IntelliJ IDEA)

This platform ships **two MCP servers**. Point GitHub Copilot at both and it discovers
every expert tool the project exposes:

| MCP server (config name)  | URL (SSE)                     | Port | MCP tool exposed     |
|---------------------------|-------------------------------|------|----------------------|
| `code-experts`            | `http://localhost:8080/sse`   | 8080 | `code_review_expert` |
| `incident-experts`        | `http://localhost:8081/sse`   | 8081 | `stacktrace_analyzer`|

Both are Spring AI **WebMVC** MCP servers, so the transport is **SSE** at the default
`/sse` endpoint (override with `spring.ai.mcp.server.sse-endpoint`).

The ready-to-use config is [`copilot-mcp.json`](./copilot-mcp.json).

---

## 1. Start the servers

From the repo root (Gemini reached through your Vertex REST proxy — see the root
README, "Reaching the model"):

```bash
# Terminal 1 — code-group experts on :8080
VERTEX_BASE_URL=https://llm-proxy.your-bank.internal \
VERTEX_PROJECT=your-project VERTEX_LOCATION=us-central1 \
VERTEX_TOKEN=$YOUR_BEARER_TOKEN GEMINI_MODEL_ID=gemini-2.5-pro \
./gradlew :services:expert-svc-code:bootRun

# Terminal 2 — incident-group experts on :8081
VERTEX_BASE_URL=https://llm-proxy.your-bank.internal \
VERTEX_PROJECT=your-project VERTEX_LOCATION=us-central1 \
VERTEX_TOKEN=$YOUR_BEARER_TOKEN GEMINI_MODEL_ID=gemini-2.5-pro \
./gradlew :services:expert-svc-incident:bootRun
```

On Windows PowerShell:

```powershell
$env:VERTEX_BASE_URL="https://llm-proxy.your-bank.internal"; $env:VERTEX_PROJECT="your-project"
$env:VERTEX_LOCATION="us-central1"; $env:VERTEX_TOKEN="<bearer>"; $env:GEMINI_MODEL_ID="gemini-2.5-pro"
.\gradlew :services:expert-svc-code:bootRun
```

> The servers **boot and serve their tool catalog even without the Vertex variables** —
> handy for verifying the Copilot wiring. Tool *calls* then return a structured `ERROR`
> ("No model is configured …") until the proxy is set.

Confirm each is up:

```bash
curl -N http://localhost:8080/sse    # should open an SSE stream (Ctrl-C to stop)
curl -N http://localhost:8081/sse
```

## 2. Register the servers with Copilot in IntelliJ IDEA

GitHub Copilot reads MCP servers from a JSON file with a top-level `servers` object
(the same schema as VS Code). In JetBrains IDEs:

1. Open the **Copilot Chat** tool window and switch the chat to **Agent** mode.
2. Use the tools/MCP menu → **Edit / Configure MCP servers** to open `mcp.json`.
   It lives in the per-user Copilot config directory, e.g.:
   - **Windows:** `%LOCALAPPDATA%\github-copilot\intellij\mcp.json`
   - **macOS/Linux:** `~/.config/github-copilot/intellij/mcp.json`
3. Paste the contents of [`copilot-mcp.json`](./copilot-mcp.json) (merge the two entries
   into your existing `servers` map if you already have one) and save.
4. Reload/▷ the servers from the MCP menu. Both should report **connected**, and the
   tools `code_review_expert` and `stacktrace_analyzer` appear in the Agent's tool list.

## 3. Use the tools

In **Agent** mode, Copilot calls the tools automatically when the task fits, or you can
nudge it:

- *"Use `code_review_expert` to review the staged diff for SQL-injection and resource
  leaks."* — paste a unified diff, or ask Copilot to produce one from your changes.
- *"Run `stacktrace_analyzer` on this exception (applicationPackages: `com.bank`)"* —
  paste the full stack trace including the `Caused by:` chain.

Each result comes back as the platform envelope: findings with `file:line` (or frame/
signal) **evidence**, an **epistemic** label (FACT / INFERENCE / HYPOTHESIS), and a
**confidence** — so Copilot fuses grounded findings and surfaces honest limitations.

---

## Fallback: stdio bridge (older Copilot builds without remote MCP)

If your Copilot version only supports `stdio` MCP servers, use the
[`mcp-remote`](https://www.npmjs.com/package/mcp-remote) bridge (needs Node.js on PATH).
See [`copilot-mcp.stdio-bridge.json`](./copilot-mcp.stdio-bridge.json):

```json
{
  "servers": {
    "code-experts":     { "command": "npx", "args": ["-y", "mcp-remote", "http://localhost:8080/sse"] },
    "incident-experts": { "command": "npx", "args": ["-y", "mcp-remote", "http://localhost:8081/sse"] }
  }
}
```

## Notes

- **No client-side auth** is required for the MCP connection in this build — the servers
  bind to `localhost` for local development. (Edge mTLS/OAuth is the gateway's job in the
  full architecture, Part 9.) If you front the servers with auth, add an `Authorization`
  header per server entry.
- **Ports** are set in each service's `application.yml` (`server.port`); change them there
  and in the config if they collide with other apps.
- This config exposes **all** MCP tools currently implemented. As `log_analyzer` and
  `sql_expert` land in `expert-svc-incident`, they appear automatically under
  `incident-experts` — no config change needed.

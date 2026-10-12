# Agent SDK — Specification (shared context)

> **This is the durable, shared context. Every phase plan references it.**
> Read this in full before executing any `plans/phase-*.md`. Do not re-derive or
> re-litigate the decisions recorded here — especially the **Non-goals**.

## 0. Scope

**In scope:** the **CLI-hook** connectors under `apps/mcp-endpoint-shield/*-cli-hooks/`
(Claude, Codex, Gemini, Cursor, GitHub Copilot, kiro) — the ones that install a
shell wrapper → python hook into an agent's home dir.

**Out of scope (for now):** library/plugin integrations (hermes, langchain, litellm,
vertex-ai-adk, opencode/amp JS plugins, neovim). Different integration surface; deferred.

## 1. Problem — and what already exists

Akto ingests AI-agent traffic (prompts, responses, tool calls) from many agent CLIs
via per-agent **hooks**. A **partial consolidation** already exists in
`shared/akto_ingestion_utility.py`. Verified by inspection:

- **Already shared and adopted:** `installer_headers` + `resolve_session_info` (the
  session→trace→span id logic) imported by nearly every `validate-*.py`;
  `run_observability_hook` adopted by the observability path; kiro uses shared
  blocking runners.
- **Duplicated with drift (measured):**
  - `akto_machine_id.py` — **9 copies, ~7 distinct versions**.
  - `akto-hooks.py` (observability entry) — **7 copies, all different**.
  - `akto-validate-*.py` enforcement path — copied per agent with local
    `build_*` / `call_guardrails` / `apply_warn_resubmit_flow`.
  - `*-wrapper.sh`/`.ps1` + `settings.json`/`hooks.json` — copied per agent+hook. A
    stray `# Generic Akto Cursor hook wrapper` comment survives in gemini/codex — the
    literal signature of unreviewed copy-paste. **The wrappers contain no logic** —
    they are pure `export`-lines + one `exec` (verified).

### Two root causes (do not lose these)

1. **Shape:** the utility is a function library with env-driven, stringly-typed
   config (`AKTO_CONNECTOR` → `_SESSION_FIELD_MAP` lookups). It cannot cleanly express
   per-agent variation in the enforcement path — **path** (`/v1/messages` vs
   `/gemini/chat`), **hook header** (`x-claude-hook` vs `x-codex-hook`), **response
   envelope** (gemini wraps `{"result":…,"usageMetadata":…}`), **block dialect**
   (`{"decision":"block"}` vs `{"continue":false}` vs `{"decision":"deny"}` vs exit-2
   vs stdout-injection), **warn/resubmit flow**. So connectors copied it.
2. **Packaging:** each connector dir ships as a **self-contained bundle** into the
   agent's home (`exec python3 "$HOME/.gemini/hooks/$1"`). With no packaging
   discipline, common files (`akto_machine_id.py`, `akto-hooks.py`, wrappers) were
   copied so each bundle stands alone — and copies drift, untested.

Result: copy-paste, zero tests, breakage found by customers.

## 2. Goal — and the boundary

**Evolve the utility into a single installable SDK package** that hides all per-agent
complexity, so the only thing that changes often — the Akto payload shaping (and,
on the backend, guardrail policy) — is a small, agent-agnostic surface anyone can edit.

The SDK **hides both ends** of a hook:

```
agent event ─▶ [SDK: hidden adapter parses]  ─▶  Turn
                                                 │
                          ┌──────────────────────┘
                          ▼
        [ DEVELOPER SURFACE — agent-agnostic, changes often ]
                 build_akto_payload(turn) → payload
                (guardrail POLICY lives on the backend, not here)
                          │
                          ▼  payload / Decision
     [SDK: hidden — send to backend, enforce verdict, emit block dialect]
```

- **SDK-maintainer** (rare): adds/fixes a **hidden per-agent adapter** when a new
  agent lands or an existing one changes.
- **Business-logic developer** (frequent, "anyone"): edits the shaping surface
  against `Turn`; never opens an adapter, a manifest, or a dialect.

Target end-state for a connector — **the only per-agent artifacts are**:
`adapters/<agent>_adapter.py` (hidden) + `manifests/<agent>.toml`. From the manifest,
**only `settings.json` is generated** (see §5.1 — there are no per-agent wrappers).

This is **not** greenfield and **not** "leave the utility as-is": reuse the substance,
replace the shape, fix the packaging. **The SDK adds no new functionality** — it
relocates and de-duplicates logic that already exists (see §3).

## 3. Everything here already exists today (this is a refactor)

| SDK piece | Where it lives today |
|---|---|
| shaping (`build_akto_payload`) | `build_ingestion_payload` / `build_validation_request` / `build_akto_request` (copied) |
| send to backend (`backend_client`) | `post_payload_json` + `build_http_proxy_url` (copied) |
| guardrail enforcement | `call_guardrails` + `apply_warn_resubmit_flow` + `guardrailsResult` parsing (copied) |
| session identity | `resolve_session_info` + `installer_headers` (already shared) |
| device identity | `akto_machine_id.py` (9 copies) |
| ingest-only | `run_observability_hook` (already shared; 7 entry copies) |
| transcript reading | `get_last_user_prompt` / `extract_text_*` / chunk buffer (copied) |
| install config | the `export` lines inside every `*-wrapper.sh` / `.ps1` (copied) |
| the guardrail **decision** itself | **already on the backend (cyborg)** — the client only calls and interprets |

## 4. Rationale (decided — do not reopen)

- **Full content + inline enforcement are required.** Guardrails see the complete
  prompt/response and block **synchronously**, in the agent's loop, within a budget.
- **OpenTelemetry cannot be the capture path** (pre-stable; opt-in/truncated/redacted/
  async; no session concept). OTel is an optional *output* only (phase 5).
- **Langfuse / opentelemetry-hooks are observability-only** — they standardize on OTel
  precisely because they never block. We build the enforcement layer they omit.
- **Enforcement mechanism is abstractable; capability is not.** Adapters *report*
  capability (`can_block`); the engine degrades where an agent can't block.

## 5. Architecture & directory (names say their purpose)

```
agent_sdk/                        ◀── SDK: hidden complexity (SDK-maintainer only)
  contract.py                     # shared vocabulary: Turn, Decision, Manifest, …
  hook_runner.py                  # runs ONE hook event end-to-end (orchestrator)
  generate_settings.py            # manifest → the agent's settings.json/hooks.json
  engine/                         # shared machinery (stable)
    session_identity.py           # same session/trace/span id across a turn (was resolve_session_info)
    transcript_reader.py          # recover prompt/response from agent logs & streams
    device_identity.py            # who/what machine — the ONE copy (was machine_id ×9)
    backend_client.py             # build URL + POST /api/http-proxy + parse reply
    guardrail_enforcement.py      # verdict → block/allow/warn + resubmit flow
    ingest_only.py                # fire-and-forget observability (was run_observability_hook)
    config.py                     # read ~/.akto/config.json (was per-agent env exports)
  adapters/                       # per-agent, HIDDEN
    claude_adapter.py  codex_adapter.py  gemini_adapter.py  cursor_adapter.py  …

business_logic/                   ◀── DEVELOPER SURFACE (agent-agnostic, changes often)
  build_akto_payload.py           # Turn → /api/http-proxy payload
  # guardrail POLICY is NOT here — it runs on the backend; the engine only calls it

manifests/                        # one tiny file per connector (paths / hooks / OS)
  claude.toml  codex.toml  …
generated/                        # GENERATED settings.json per agent (never hand-edited)
tests/
  fixtures/<connector>/*.json   characterization/   test_*.py
```

**Packaging:** the SDK is one installable Python package. The installer places the
**package once** + writes **one shared `~/.akto/config.json`** + the **generated
`settings.json` per agent home** — not N self-contained dirs carrying copies.

### 5.1 Install & invocation model (no per-agent wrappers)

The current `*-wrapper.sh`/`.ps1` files are pure `export`-lines + one `exec` (§1). Both
halves dissolve:

- **The `export` lines are config.** They are install-level and identical across agents
  (URL, token, mode, timeout, context source, log level, SSL). They move into **one
  shared `~/.akto/config.json`**, read by `engine/config.py`. (JSON, not TOML/bash —
  the reader is Python, JSON is stdlib on the 3.8 floor and cross-platform, which also
  removes the `.ps1` twin. The only per-agent `export` — `AKTO_CONNECTOR` — becomes an
  argument; its short tag is derived, not stored.)
- **The `exec` line is the invocation.** It moves into the agent's `settings.json`,
  which invokes the SDK directly with the connector and hook kind as **arguments**:
  ```json
  { "hooks": { "UserPromptSubmit": [
      { "command": "python3 -m agent_sdk.hook_runner claude prompt" } ] } }
  ```

So there are **no generated wrappers**. Optionally, one **static** launcher
(`akto-run`, installed once, referenced by explicit path) can front the `python -m`
call to handle interpreter/venv resolution and Windows — it is a single shared asset,
**not** generated per agent. The only per-agent generated artifact is `settings.json`.

- **Per agent:** `settings.json` (generated from the manifest).
- **Shared, written once:** the SDK package, `~/.akto/config.json`, optional `akto-run`.
- **Passed as args:** connector id + hook kind.

**Runtime path (who owns each step):**
```
settings.json → python3 -m agent_sdk.hook_runner claude prompt   # (or via akto-run)
  → engine.config read ~/.akto/config.json                       # was env exports
  → claude_adapter.parse(event) → Turn                            # hidden adapter
  → engine.session_identity (session/trace/span)                  # engine
  → business_logic.build_akto_payload(turn)                       # DEVELOPER surface
  → engine.backend_client POST (guardrail eval on BE)             # engine → backend
  → engine.guardrail_enforcement + adapter.emit_block             # engine + hidden dialect
  → engine.backend_client ingest                                  # engine
```

**Strangler-fig (three seams):**
1. SDK emits the **existing** `/api/http-proxy` payload → backend untouched (ph 1–4).
2. Each connector migrates onto its adapter one at a time, shadow-diffed vs its current
   copied output before its old files are deleted.
3. At cutover per connector: generated `settings.json` + shared `config.json` replace
   the hand-copied `settings.json` **and the wrappers are deleted** (`.sh`, `.ps1`,
   per-hook variants, and their `export` lines) — nothing regenerates them.

## 6. Canonical model (the interface the utility lacks) — `contract.py`

Typed contract replacing raw dicts (`input_data`, `session_info`, `payload`). Frozen
once defined; changes require a SPEC revision.

```python
class HookKind(Enum):
    PROMPT = "prompt"; RESPONSE = "response"
    PRE_TOOL = "pre_tool"; POST_TOOL = "post_tool"
    MCP_REQUEST = "mcp_request"; MCP_RESPONSE = "mcp_response"

@dataclass
class ToolCall:
    name: str; arguments: dict | None = None; result: str | None = None

@dataclass
class Turn:                     # the normalized envelope
    connector: str; source: str; kind: HookKind
    session_id: str; message_id: str = ""; conversation_id: str | None = None
    prompt: str = ""; response: str = ""
    model: str | None = None
    input_tokens: int | None = None; output_tokens: int | None = None
    user_email: str | None = None; device_id: str | None = None
    timestamp_ms: int = 0; tool: ToolCall | None = None
    raw: dict | None = None

@dataclass
class Decision:
    allow: bool; reason: str = ""; behaviour: str = "block"  # block|warn|alert

@dataclass
class Caps:
    can_block: bool; can_warn: bool = True; max_latency_ms: int = 5000

@dataclass
class Endpoint:                 # per-agent variation that caused the copies
    path: str; hook_header: str

@dataclass
class Manifest:                 # drives settings.json generation
    connector: str; home: str; os: list[str]
    hooks: dict[str, HookKind]  # agent event name -> kind

@dataclass
class Discovery:
    mcp_servers: list[dict] = field(default_factory=list)
    skills: list[dict] = field(default_factory=list)
    subagents: list[dict] = field(default_factory=list)

class Adapter(Protocol):        # the ONLY per-agent code (hidden in the SDK)
    connector: str
    source: str
    message_id_strategy: str    # "passthrough" | "transcript_uuid" | "turn_counter"
    def parse(self, event: dict, kind: HookKind) -> Turn: ...
    def capabilities(self, kind: HookKind) -> Caps: ...
    def endpoint(self, kind: HookKind) -> Endpoint: ...
    def emit_block(self, decision: Decision, kind: HookKind) -> None: ...
    def wrap_response(self, turn: Turn) -> str: ...     # per-agent response envelope
    def transcript_uuid(self, event: dict) -> str: ...  # only if strategy needs it
    def discovery_sources(self) -> list[str]: ...
```

## 7. Constraints & Non-goals

**Constraints**
- **Python 3.8+ floor**, standard-library-only in the engine. The hooks run under the
  customer's `python3` (we don't control the version); existing connectors state 3.8+
  (some 3.6+), and no code uses 3.10/3.11 features. **CI must run the 3.8 floor**, not
  just the local interpreter — passing on a newer local Python does not prove
  compatibility. Corollary: **no `tomllib`** (3.11-only) at runtime.
- **Config format:** runtime config read on the customer machine (`~/.akto/config.json`)
  is **JSON** (stdlib on every version, cross-platform → no `.ps1` twin). Manifests are
  parsed at *build time* on our machines, so they may be `.toml`. Build-time vs runtime
  is the dividing line.
- **Reuse, don't rewrite** the proven functions (§3); change behavior only where a
  characterization test proves equivalence.
- **Fail-open:** engine errors never crash the agent or block traffic. Blocking is a
  deliberate guardrail verdict only.
- Preserve the `/api/http-proxy` payload contract through phase 4.
- Package as **one installable unit**; adapters depend on the engine, never copy it.

**Non-goals (do NOT do these)**
- ❌ Library/plugin integrations (§0) — deferred.
- ❌ Greenfield a parallel module ignoring `akto_ingestion_utility.py`. Evolve it.
- ❌ Re-extract `installer_headers` / `resolve_session_info` / the observability runner
  — already shared+adopted. Wrap, don't duplicate.
- ❌ Keep per-dir bundling of common files (`device_identity`, `ingest_only`, wrappers).
- ❌ **Generate or hand-write per-agent wrappers.** Wrappers are eliminated (§5.1):
  config → one `config.json`, invocation → `settings.json` args. Only `settings.json`
  is generated per agent; the launcher (if any) is a single static asset.
- ❌ Expose adapters as the developer surface — they are hidden SDK internals.
- ❌ Put guardrail **policy** in the client — it stays on the backend.
- ❌ OTel for capture (phase-5-optional export only).
- ❌ External/public API — internal to Akto.
- ❌ Change the backend payload contract in phases 1–4.
- ❌ Depend on `o11y-dev/opentelemetry-hooks`.
- ❌ Drop multi-turn data or collapse request+response incorrectly.

## 8. Test philosophy (every phase carries its own suite)

The utility and the copies have **zero tests today** — the unambiguous gap behind
"random breakage." Tests come first in every refactor step.

- **Characterization** — pin the *current* output of the existing utility, one copied
  enforcement path (claude), the chosen canonical `device_identity`, and the
  `ingest_only` entry — **before** changing anything. Phase-1 safety net.
- **Unit** — `contract.py` + engine logic with injected `backend_client` (no network/fs).
- **Fixture/contract** — recorded real hook events per agent → assert `Turn` fields.
- **Generation golden** — manifest → `settings.json` golden output.
- **Shadow** — SDK path runs beside the old hook; diff before cutover.
- **Smoke (live)** — CI runs the real agent against a canned prompt (phase 5).
- **Canary** — runtime parse-empty rate per connector + alert (phase 5).
- **Floor** — CI runs the suite on Python 3.8 (§7), not just the local interpreter.

Acceptance is executable: a phase is done only when its suite is green in CI.

## 9. Phases (each independent + individually shippable)

| Phase | Deliverable | Suite | Exit gate |
|---|---|---|---|
| **1 — Contract + safety net** | `contract.py` (typed model + `Adapter`/`Endpoint`/`Manifest`); characterization tests pinning current utility, one copied path, canonical `device_identity`, `ingest_only` entry | characterization + contract unit (on 3.8 floor) | contract defined; current behavior locked; nothing else changed |
| **2 — Engine + runner** | installable package; `hook_runner` drives the enforcement path *through* the adapter, **reusing** utility functions; consolidate `device_identity` + `ingest_only` into the engine behind shims; `engine/config.py` reads `~/.akto/config.json`; generalize warn/resubmit | unit w/ fake adapter + fake `backend_client` | full flow runs with a dummy adapter; characterization green; package installs |
| **3 — Reference adapter (Claude) + settings generation + shadow** | `claude_adapter.py` + manifest; `generate_settings` (manifest → settings.json); `config.json` for the install; shadow-diff vs copied `claude-cli-hooks`; live smoke | fixtures + settings-generation golden + shadow + smoke | shadow clean N days → Claude on SDK; its copied py **and all wrappers** deleted |
| **4 — Migrate rest** | codex, gemini(streaming), cursor, github, kiro — one slice each: adapter + manifest + generated settings | per-agent fixtures + generation golden + shadow | all CLI connectors on SDK; local `build_*`/`call_guardrails`/`warn`, copied `device_identity`, **and every `*-wrapper.sh`/`.ps1` + `export` config** deleted |
| **5 — Discovery + detection** | discovery interface + canary + smoke matrix + optional OTel export | discovery fixtures + canary tests | simulated drift trips canary/CI, names one adapter |

## 10. Definition of done (per phase)

1. Code merged behind the strangler seam (old path runs until cutover).
2. Phase's suite green in CI (including the 3.8 floor); characterization tests never regress.
3. No regression in connectors still on the old path.
4. Exit gate in §9 met.

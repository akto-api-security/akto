# Phase 1 — Contract + safety net

> Prereq: read `../SPEC.md` (scope §0 — CLI-hook connectors only; boundary §2 — the
> SDK hides per-agent complexity, the developer surface is just shaping). This phase
> is **independent and self-verifying**: it defines the typed interface the utility
> lacks and **locks the current behavior of the existing code with tests** — without
> changing that behavior. No connector and no runtime path is modified. It is the
> safety net every later phase refactors against.

## Why this phase first

The utility and the copied paths have **zero tests**. Before reshaping anything
(phases 2–4) we must prove a refactor is behavior-preserving. So phase 1 delivers two
things and changes no behavior:
1. the **typed contract** (`contract.py` — canonical model + `Adapter`/`Endpoint`/`Manifest`), and
2. **characterization tests** pinning the current output of the existing logic —
   including the files that are duplicated with drift.

Nothing here is new functionality (SPEC §3): every pinned behavior already exists.

## In scope

- `agent_sdk/contract.py` — the canonical model (SPEC §6): `HookKind`, `ToolCall`,
  `Turn`, `Decision`, `Caps`, `Endpoint`, `Manifest`, `Discovery`, and the `Adapter`
  protocol. **Types only — no logic, no I/O.**
- **Characterization tests** around existing, unchanged code (map each to SPEC §3):
  1. `shared/akto_ingestion_utility.py::build_ingestion_payload` — golden output.
     (future home: `business_logic/build_akto_payload.py`)
  2. `installer_headers` — golden header dict per connector field map
     (`_SESSION_FIELD_MAP` + default). (future home: `engine/session_identity.py`)
  3. `resolve_session_info` pure branches — passthrough / transcript_uuid /
     turn_counter given fixed inputs (inject state-file + transcript via temp files /
     monkeypatch). (future home: `engine/session_identity.py`)
  4. One complex **copied enforcement path**:
     `claude-cli-hooks/akto-validate-response.py::build_ingestion_payload` — golden for
     a captured Claude Stop event, so phase 3's `claude_adapter` can reproduce it
     byte-for-byte.
  5. **Canonical `device_identity`**: 9 drifted `akto_machine_id.py` variants exist.
     **Choose one canonical version** (document why) and pin its
     `get_machine_id`/`get_username` behavior. (future home: `engine/device_identity.py`)
  6. **`ingest_only` entry**: pin the shared `run_observability_hook` behavior the 7
     copied `akto-hooks.py` entries should collapse to. (future home: `engine/ingest_only.py`)
  7. Token parity vs the Java consumer `AgentQueryRecord.parseUsageTokens`
     (usage.input_tokens, prompt_tokens, fallback to length).

## Out of scope (later phases)

- `hook_runner`, driving the adapter, packaging → Phase 2.
- Consolidating `device_identity` / `ingest_only` copies into the engine → Phase 2
  (phase 1 only *picks + pins* the canonical behavior).
- `generate_settings` (manifest → settings.json; no wrappers — see SPEC §5.1) → Phase 3.
- Any real adapter implementation → Phase 3+.
- **Changing any current behavior.** Phase 1 is additive types + tests only.

## Deliverables (files)

```
agent_sdk/
  __init__.py
  contract.py            # SPEC §6 types (dataclasses + Adapter Protocol). No logic.
tests/
  fixtures/
    claude/stop_event.json          # captured real Claude Stop hook event
    claude/expected_payload.json    # golden output of the current copied builder
  test_contract.py                  # dataclass defaults, Turn<->dict, Protocol shape
  characterization/
    test_build_ingestion_payload.py # pins shared build_ingestion_payload
    test_session_identity.py        # pins installer_headers + resolve_session_info branches
    test_copied_claude_payload.py    # pins claude-cli-hooks copied builder (golden)
    test_device_identity.py          # pins chosen canonical machine_id behavior
    test_ingest_only.py             # pins run_observability_hook path
    test_token_parity.py            # usage/prompt_tokens/fallback cases
  DRIFT_REPORT.md                    # md5 map of machine_id / akto-hooks copies +
                                     # which differ from canonical (input to ph 2/4)
conftest.py                         # fixture loaders; freeze time via injected now_ms
```

## Test requirements (must all be green)

- **Determinism:** freeze `time.time()` / env via monkeypatch so goldens are stable;
  note any nondeterminism found for phase 2.
- `test_contract.py`: dataclass defaults; `Turn` round-trips to/from dict; the
  `Adapter` protocol has exactly the SPEC §6 methods; `Manifest.hooks` maps
  event-name → `HookKind`.
- `characterization/*`: each golden captures **current** output. Inject fakes for
  network/fs; do **not** modify source to make it testable in this phase.
- `test_device_identity.py`: pin the chosen canonical version; `DRIFT_REPORT.md` lists
  every copy's md5 and whether it matches canonical.
- `test_token_parity.py`: `usage.input_tokens`/`output_tokens`, OpenAI-style
  `prompt_tokens`/`completion_tokens`, malformed JSON → length fallback.

## Acceptance criteria (executable gates)

1. `pytest tests/ -q` green.
2. `agent_sdk/contract.py` imports nothing beyond `dataclasses`, `enum`, `typing`
   (enforced by a test inspecting imports).
3. **Zero changes to any existing runtime file** — no diffs under `*-cli-hooks/`,
   `shared/`, or other connectors (CI check asserts phase-1 touches only
   `agent_sdk/contract.py`, `agent_sdk/__init__.py`, `tests/`, `conftest.py`).
   Characterization tests import the existing modules read-only.
4. Golden files exist for: shared `build_ingestion_payload`, `installer_headers` (all
   connector maps), the claude copied builder, and canonical `device_identity`. These
   are the contracts phases 2–4 must not break.
5. `DRIFT_REPORT.md` enumerates the `machine_id` (9) and `akto-hooks` (7) copies with
   md5s and canonical-match status.

## Notes for the building agent

- **Do not refactor in this phase.** The value is the contract + the safety net.
  Reshaping happens in phase 2, *guarded by these tests*.
- Reuse-not-rewrite (SPEC §7): `installer_headers`, `resolve_session_info`, the
  observability runner, token logic already work and are adopted — pin them.
- Names in `agent_sdk/` follow SPEC §5 (purpose-driven). This phase only creates
  `contract.py`; the `engine/`, `adapters/`, `business_logic/` homes noted above are
  where each pinned behavior *will* move in later phases.
- Grounding sources:
  - `shared/akto_ingestion_utility.py` — functions to pin.
  - `claude-cli-hooks/akto-validate-response.py` — copied builder to pin as golden.
  - `*/akto_machine_id.py` (9 copies) — pick + pin canonical, drift-report the rest.
  - `*/akto-hooks.py` (7 copies) — pin the shared entry they should collapse to.
  - `ai-agent-framework-shield/pkg/claude` Java `AgentQueryRecord.java` — the
    header/host/token contract payloads must satisfy (host `<id>.ai-agent.<connector>`
    so `serviceId` resolves correctly — the drift bug to guard against).
- No third-party deps beyond the repo's existing test runner.

"""Canonical model — the shared vocabulary every layer speaks.

This is the frozen contract described in SPEC.md §6. It is TYPES ONLY: no I/O, no
network, no filesystem, no logic beyond trivial (de)serialization. The whole point
is that adapters, the engine, and the business-logic surface all agree on these
shapes instead of passing raw dicts around.

Allowed imports: dataclasses, enum, typing (enforced by tests/test_contract.py).
"""
from __future__ import annotations

from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any, Callable, Protocol, runtime_checkable


class HookKind(Enum):
    """Which lifecycle event a hook fired on. Agent-agnostic; adapters map each
    agent's own event names onto these via the Manifest."""

    PROMPT = "prompt"            # user prompt submitted — the turn opens here
    RESPONSE = "response"        # assistant response complete
    PRE_TOOL = "pre_tool"
    POST_TOOL = "post_tool"
    MCP_REQUEST = "mcp_request"
    MCP_RESPONSE = "mcp_response"


@dataclass
class ToolCall:
    """A single tool invocation captured on a PRE_TOOL / POST_TOOL / MCP_* turn."""

    name: str
    arguments: dict | None = None
    result: str | None = None


@dataclass
class Turn:
    """The normalized envelope. One agent hook event, translated by the adapter into
    this shape, is all the engine and business logic ever see — never the raw event."""

    connector: str
    source: str
    kind: HookKind
    session_id: str
    message_id: str = ""
    conversation_id: str | None = None
    prompt: str = ""
    response: str = ""
    model: str | None = None
    input_tokens: int | None = None
    output_tokens: int | None = None
    user_email: str | None = None
    device_id: str | None = None
    timestamp_ms: int = 0
    tool: ToolCall | None = None
    raw: dict | None = None

    def to_dict(self) -> dict:
        d = asdict(self)
        d["kind"] = self.kind.value
        return d

    @classmethod
    def from_dict(cls, d: dict) -> "Turn":
        d = dict(d)
        d["kind"] = d["kind"] if isinstance(d["kind"], HookKind) else HookKind(d["kind"])
        if d.get("tool"):
            d["tool"] = d["tool"] if isinstance(d["tool"], ToolCall) else ToolCall(**d["tool"])
        return cls(**d)


@dataclass
class Decision:
    """The outcome of a guardrail check. `behaviour` selects how a denial is applied."""

    allow: bool
    reason: str = ""
    behaviour: str = "block"     # "block" | "warn" | "alert"


@dataclass
class Caps:
    """What an agent's hook can actually do for a given HookKind. The engine reads
    this to degrade gracefully — e.g. an agent that cannot block a prompt only warns."""

    can_block: bool
    can_warn: bool = True
    max_latency_ms: int = 5000


@dataclass
class Endpoint:
    """The per-agent wire details that used to force copy-pasted payload builders:
    the path the traffic is reported under and the agent's hook header name."""

    path: str
    hook_header: str


@dataclass
class Manifest:
    """Declares how an agent is installed. Drives generation of the wrapper script
    and the agent's settings.json/hooks.json — no hand-written shell/JSON."""

    connector: str
    home: str
    os: list[str]
    hooks: dict[str, HookKind]   # agent event name -> HookKind


@dataclass
class Discovery:
    """What an agent has configured, surfaced from the adapter's discovery_sources."""

    mcp_servers: list[dict] = field(default_factory=list)
    skills: list[dict] = field(default_factory=list)
    subagents: list[dict] = field(default_factory=list)


@runtime_checkable
class Adapter(Protocol):
    """The ONLY per-agent code. Implementations live in agent_sdk/adapters/ and are
    hidden SDK internals — the business-logic developer never touches them.

    Each method isolates one axis of per-agent variation so nothing leaks into the
    engine or the business-logic surface."""

    connector: str
    source: str
    message_id_strategy: str     # "passthrough" | "transcript_uuid" | "turn_counter"

    def parse(self, event: dict, kind: HookKind) -> Turn: ...
    def capabilities(self, kind: HookKind) -> Caps: ...
    def endpoint(self, kind: HookKind) -> Endpoint: ...
    def emit_block(self, decision: Decision, kind: HookKind) -> None: ...
    def wrap_response(self, turn: Turn) -> str: ...
    def transcript_uuid(self, event: dict) -> str: ...
    def discovery_sources(self) -> list[str]: ...


# Method names the Adapter protocol requires — used by tests to lock the contract.
ADAPTER_METHODS: tuple[str, ...] = (
    "parse",
    "capabilities",
    "endpoint",
    "emit_block",
    "wrap_response",
    "transcript_uuid",
    "discovery_sources",
)

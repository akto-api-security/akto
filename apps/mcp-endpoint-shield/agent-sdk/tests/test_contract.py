"""Unit tests for the canonical contract (agent_sdk/contract.py).

Pure: no network, no fs, no agents. Locks the shape of the model and enforces the
"types only" acceptance gate.
"""
import ast
import os

import agent_sdk
from agent_sdk import (
    ADAPTER_METHODS,
    Adapter,
    Caps,
    Decision,
    Endpoint,
    HookKind,
    Manifest,
    ToolCall,
    Turn,
)

_CONTRACT_PATH = os.path.join(os.path.dirname(agent_sdk.__file__), "contract.py")
_ALLOWED_IMPORT_ROOTS = {"dataclasses", "enum", "typing", "__future__"}


def test_turn_defaults():
    t = Turn(connector="claude_code_cli", source="ENDPOINT", kind=HookKind.PROMPT,
             session_id="s1")
    assert t.message_id == ""
    assert t.conversation_id is None
    assert t.prompt == "" and t.response == ""
    assert t.input_tokens is None and t.output_tokens is None
    assert t.tool is None
    assert t.timestamp_ms == 0


def test_turn_round_trips_through_dict():
    t = Turn(
        connector="codex_cli", source="ENDPOINT", kind=HookKind.POST_TOOL,
        session_id="s2", message_id="m2", prompt="hi", response="yo",
        input_tokens=3, output_tokens=5,
        tool=ToolCall(name="bash", arguments={"cmd": "ls"}, result="a\nb"),
    )
    again = Turn.from_dict(t.to_dict())
    assert again == t
    assert again.kind is HookKind.POST_TOOL
    assert isinstance(again.tool, ToolCall)


def test_to_dict_serializes_enum_to_value():
    d = Turn(connector="c", source="ENDPOINT", kind=HookKind.RESPONSE,
             session_id="s").to_dict()
    assert d["kind"] == "response"          # JSON-friendly, not the enum object


def test_manifest_maps_event_names_to_hookkinds():
    m = Manifest(
        connector="claude_code_cli", home="~/.claude", os=["macos", "linux"],
        hooks={"UserPromptSubmit": HookKind.PROMPT, "Stop": HookKind.RESPONSE},
    )
    assert m.hooks["UserPromptSubmit"] is HookKind.PROMPT
    assert m.hooks["Stop"] is HookKind.RESPONSE


def test_decision_and_caps_and_endpoint_defaults():
    assert Decision(allow=False, reason="x").behaviour == "block"
    c = Caps(can_block=True)
    assert c.can_warn is True and c.max_latency_ms == 5000
    e = Endpoint(path="/v1/messages", hook_header="x-claude-hook")
    assert e.path == "/v1/messages"


def test_adapter_protocol_lists_exact_methods():
    # ADAPTER_METHODS is the locked surface; a duck-typed object with these methods
    # (plus the three attributes) must satisfy the runtime-checkable protocol.
    class _Stub:
        connector = "x"
        source = "ENDPOINT"
        message_id_strategy = "turn_counter"

    for name in ADAPTER_METHODS:
        setattr(_Stub, name, lambda self, *a, **k: None)

    assert isinstance(_Stub(), Adapter)


def test_incomplete_adapter_fails_protocol_check():
    class _Missing:
        connector = "x"
        source = "ENDPOINT"
        message_id_strategy = "turn_counter"
        def parse(self, event, kind): ...   # missing the rest

    assert not isinstance(_Missing(), Adapter)


def test_contract_imports_types_only():
    """Acceptance gate #2: contract.py imports nothing beyond dataclasses/enum/typing."""
    with open(_CONTRACT_PATH, encoding="utf-8") as f:
        tree = ast.parse(f.read())
    roots = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            roots.update(a.name.split(".")[0] for a in node.names)
        elif isinstance(node, ast.ImportFrom):
            if node.module:
                roots.add(node.module.split(".")[0])
    assert roots <= _ALLOWED_IMPORT_ROOTS, f"disallowed imports in contract.py: {roots - _ALLOWED_IMPORT_ROOTS}"

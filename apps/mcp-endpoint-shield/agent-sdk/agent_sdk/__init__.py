"""Agent SDK — internal package. See ../SPEC.md.

Phase 1 exposes only the canonical contract. Engine, adapters, and the business-logic
surface arrive in later phases.
"""
from agent_sdk.contract import (
    ADAPTER_METHODS,
    Adapter,
    Caps,
    Decision,
    Discovery,
    Endpoint,
    HookKind,
    Manifest,
    ToolCall,
    Turn,
)

__all__ = [
    "ADAPTER_METHODS",
    "Adapter",
    "Caps",
    "Decision",
    "Discovery",
    "Endpoint",
    "HookKind",
    "Manifest",
    "ToolCall",
    "Turn",
]

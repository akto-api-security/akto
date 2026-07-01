"""Split a request's user-prompt text into instruction units vs. data/context,
before anything is embedded for intent classification.

Why this exists: classifying the *whole* prompt conflates the user's actual
ask with whatever data it operates on — a large pasted document, log dump, or
tool output can dominate the embedding and make the classifier latch onto the
data's topic instead of the user's intent. This module extracts only the ask.

Cascade, cheapest/most-confident tier first (stops as soon as a tier confidently
places a fragment):
    1. Explicit payload structure   — payload.extract_payload_structure()
    2. Source metadata/content type — folded into (1); a content_type sibling
       overrides key/role-based placement regardless of NL-likeness.
    3. Deterministic structural parsing — fenced code / log lines / quoted
       blocks / attachment markers extracted out of whatever's still ambiguous.
    4. Lightweight heuristic segmenter — imperative-mood sentence splitting on
       whatever tiers 1-3 couldn't structurally place.

Pure stdlib + regex (Pyodide-safe, same constraint as payload.py — shared with
the Cloudflare Worker path). No LLM, no ML model: bounded, cheap, deterministic
enough to run on every request at low latency.

Every tier here uses the SAME generic, agent-agnostic lexicon/key-set by
default — real per-agent traffic often doesn't match it (e.g. an agent that
sends {"userAsk": ..., "payload": ...} gets no benefit from the generic
_INSTRUCTION_KEYS at all, since "userAsk" isn't in it, and falls through to
the lower-confidence tier-4 heuristic every time). `structure_profile` closes
that gap: intent/prefilter.py builds it from intent/corpus.py's own history of
this agent's tier-1 (structured) extractions — which JSON/chat-role keys and
which leading verbs have repeatedly produced a confident instruction unit for
THIS agent — and passes it in here as an *addition* to the generic lexicon,
never a replacement. No LLM, no model: it's the worker learning from its own
past structural matches, not from the offline LLM-labeling service's output.

Public API:
    segment(text, capability_profile=None, structure_profile=None) ->
        {"units", "unit_sources", "data_spans", "extraction_confidence", "method"}
    split_instruction_units(text, extra_verbs=frozenset()) -> list[str]
"""

import re
from typing import Any, Dict, List, Optional, Tuple

from . import payload

# Confidence assigned to the *weakest* tier used to extract any instruction
# text for this request — conservative: one shaky heuristic fragment lowers
# trust in the whole extraction, matching the codebase's fail-open philosophy.
_TIER_CONFIDENCE = {"structured": 1.0, "deterministic": 0.85, "heuristic": 0.5}
_TIER_WEAKEST_FIRST = ("heuristic", "deterministic", "structured")

_MAX_SENTENCES = 64  # bound tier-4 cost on pathologically long ambiguous text

_SENTENCE_SPLIT = re.compile(r"(?<=[.!?])\s+|\n+")

# A small imperative-verb lexicon: sentences whose first word is one of these
# are treated as instruction-like. Deliberately narrow/high-precision — a
# false negative here just means a genuinely-instructional sentence lands in
# data_spans (safe: it can no longer skip the cascade, but was already ESCALATE
# in the previous unit/tier). A false positive risks misreading data as ask.
_IMPERATIVE_LEXICON = {
    "please", "can", "could", "would", "will", "get", "fetch", "list", "show",
    "find", "delete", "remove", "update", "create", "book", "summarize",
    "summarise", "explain", "tell", "set", "change", "add", "edit", "cancel",
    "check", "send", "email", "generate", "write", "give", "help", "make",
    "search", "look", "review", "process", "analyze", "analyse", "compare",
    "translate", "convert", "extract", "calculate", "schedule",
}
_SECOND_PERSON_RE = re.compile(r"(?i)\byou\b|\byour\b")
_LEADING_WORD_RE = re.compile(r"[A-Za-z']+")

# Splits a multi-action instruction on conjunctions / list markers. Only used
# as a *candidate* split — split_instruction_units() keeps it only when every
# resulting side independently has its own leading verb.
_CONJUNCTION_SPLIT_RE = re.compile(
    r"(?i)\s*(?:,?\s+and then\s+|,?\s+then\s+|\s+and\s+|;\s*|\n\s*[-*]\s*|\n\s*\d+[.)]\s*)\s*"
)


def _leading_word(sentence: str) -> str:
    m = _LEADING_WORD_RE.match(sentence.strip())
    return m.group(0).lower() if m else ""


def _has_leading_verb(sentence: str, extra_verbs: frozenset = frozenset()) -> bool:
    word = _leading_word(sentence)
    return word in _IMPERATIVE_LEXICON or word in extra_verbs


def _looks_imperative(sentence: str, extra_verbs: frozenset = frozenset()) -> bool:
    """Looser than _has_leading_verb: also counts questions and 2nd-person
    address as instruction-like. Used for tier-4 whole-sentence placement."""
    s = sentence.strip()
    if not s:
        return False
    if s.endswith("?"):
        return True
    if _has_leading_verb(s, extra_verbs):
        return True
    return bool(_SECOND_PERSON_RE.search(s))


def _split_sentences(text: str) -> List[str]:
    return [s.strip() for s in _SENTENCE_SPLIT.split(text) if s.strip()][:_MAX_SENTENCES]


def _extract_structural_spans(text: str) -> Tuple[str, List[str]]:
    """Pull fenced-code/log-line/quoted-block/attachment-marker spans out of
    `text`; return (remaining_text, extracted_data_spans). Tier 3."""
    spans: List[str] = []
    remaining = text
    for pattern in payload._STRUCTURAL_DATA_PATTERNS:
        for m in pattern.finditer(remaining):
            spans.append(m.group(0))
        if spans:
            remaining = pattern.sub(" ", remaining)
    return remaining.strip(), spans


def segment(text: str, capability_profile: Optional[Dict[str, Any]] = None,
           structure_profile: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """Split request text into instruction units + data spans.

    Never raises — any internal error degrades to extraction_confidence=0.0
    (the caller's decision policy treats that as "escalate", the safe default).
    `capability_profile` is accepted for future use (e.g. recognizing
    agent-declared-capability verbs in tier 4) but is not required for
    correctness today; segmentation degrades gracefully without it.
    `structure_profile` (see intent/prefilter.py's get_structure_profile) adds
    this agent's own learned instruction keys/verbs on top of the generic
    lexicon — also optional, also degrades gracefully to generic-only.

    `unit_sources`/`unit_methods` in the return are parallel to `units`:
    `unit_sources` is the originating JSON/chat-role key for a tier-1
    (structured) unit, or "" for a tier-3/4 unit (no reliable key signal);
    `unit_methods` is THAT unit's own tier, independent of the overall
    (weakest-tier-wins) `method` summary below — a request can mix tier-1 and
    tier-4 units, and intent/corpus.py's build_structure_profile() needs the
    per-unit tier to avoid discarding a genuinely-structured unit's key just
    because some other unit in the same request needed the heuristic tier.
    """
    extra_instruction_keys = frozenset((structure_profile or {}).get("instruction_keys") or ())
    extra_data_keys = frozenset((structure_profile or {}).get("data_keys") or ())
    extra_verbs = frozenset((structure_profile or {}).get("instruction_verbs") or ())

    try:
        struct = payload.extract_payload_structure(
            text or "", extra_instruction_keys=extra_instruction_keys, extra_data_keys=extra_data_keys)
    except Exception:
        return {"units": [], "unit_sources": [], "unit_methods": [], "data_spans": [],
                "extraction_confidence": 0.0, "method": "error"}

    units: List[str] = [t for t, _ in struct["instruction_candidates"]]
    unit_sources: List[str] = [src.get("key", "") for _, src in struct["instruction_candidates"]]
    unit_methods: List[str] = ["structured"] * len(units)
    data_spans: List[str] = [t for t, _ in struct["data_candidates"]]
    tiers_used = set()
    if units or data_spans:
        tiers_used.add("structured")

    residual: List[str] = []
    for frag, _src in struct["ambiguous_candidates"]:
        remaining, spans = _extract_structural_spans(frag)
        if spans:
            tiers_used.add("deterministic")
            data_spans.extend(spans)
        if remaining:
            residual.append(remaining)

    for frag in residual:
        tiers_used.add("heuristic")
        for sentence in _split_sentences(frag):
            if _looks_imperative(sentence, extra_verbs):
                units.append(sentence)
                unit_sources.append("")  # no reliable key signal from a heuristic-tier unit
                unit_methods.append("heuristic")
            else:
                data_spans.append(sentence)

    if not tiers_used:
        method, confidence = "structured", 1.0
    else:
        method = next(t for t in _TIER_WEAKEST_FIRST if t in tiers_used)
        confidence = _TIER_CONFIDENCE[method]

    return {"units": units, "unit_sources": unit_sources, "unit_methods": unit_methods,
            "data_spans": data_spans, "extraction_confidence": confidence, "method": method}


def split_instruction_units(text: str, extra_verbs: frozenset = frozenset()) -> List[str]:
    """Split a multi-action instruction into independent units.

    Splits on coordinating conjunctions/list markers, but keeps the split only
    when every resulting side independently has its own leading verb —
    "update the record and its timestamp" (one action, two objects) stays
    whole; "delete these records and email me a summary" (two actions) splits.
    `extra_verbs` is this agent's learned verb lexicon (see segment()).
    """
    if not text or not text.strip():
        return []
    parts = [p.strip() for p in _CONJUNCTION_SPLIT_RE.split(text) if p.strip()]
    if len(parts) <= 1:
        return [text.strip()]
    if all(_has_leading_verb(p, extra_verbs) for p in parts):
        return parts
    return [text.strip()]

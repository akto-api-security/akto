"""Strip a request payload down to natural-language text, then chunk it.

The embedder and intent classifier should see the *prompt language a user wrote*
— not API plumbing (model ids, tool schemas, UUIDs, timestamps). Feeding them
clean NL both lifts the semantic-cache hit-rate (less incidental structure to
perturb the embedding) and sharpens task/risk intent.

Pure stdlib + regex on purpose: this runs under Pyodide (Cloudflare Worker) and
in the FastAPI container, so no numpy / blingfire / nltk. Sentence splitting is a
tuned regex; everything is bounded so a hostile payload can't blow up cost.

Public API:
    normalize(text, max_chunks) -> list[(chunk_text, weight)]
        strip_to_nl + chunk, ready to embed/classify. weight reflects how much a
        chunk should count toward the request decision (user prompt > metadata).
"""

import json
import re
from typing import Any, List, Tuple

# Keys whose string values are almost always the user's prompt language.
_NL_KEYS = {
    "prompt", "input", "query", "message", "content", "text", "question",
    "instruction", "instructions", "system", "user", "prompt_text", "query_text",
    "task", "goal", "ask", "request", "body",
}

# Chat-message roles → weight. assistant is model output (down-weight), tool
# results are machine data (drop). user/system carry the real intent.
_ROLE_WEIGHTS = {"user": 1.0, "system": 0.9, "assistant": 0.3, "tool": 0.0}

_WEIGHT_NL_KEY = 1.0      # value under a known NL key
_WEIGHT_HEURISTIC = 0.6   # NL-looking value under an unknown key
_WEIGHT_BARE = 1.0        # non-JSON payload, taken whole

# Machine-string rejects: a leaf matching any of these is not prompt language.
_MACHINE_PATTERNS = [
    re.compile(r"^[0-9a-fA-F]{16,}$"),                       # hash / hex blob
    re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"),          # UUID
    re.compile(r"^[A-Za-z0-9+/]{40,}={0,2}$"),               # base64 blob
    re.compile(r"^[a-z]+://"),                                # url/scheme-only
    re.compile(r"^(gpt-|claude-|qwen|gemini-|text-embedding)"),  # model ids
    re.compile(r"^\d{4}-\d{2}-\d{2}[T ]"),                   # ISO timestamp
    re.compile(r"^[a-z][a-z0-9_]*$"),                         # single enum token
]

# Bounds so deeply nested / huge payloads stay cheap and safe.
_MAX_DEPTH = 4
_MAX_NODES = 5000
_MAX_TOTAL_CHARS = 16000
_MAX_CHUNK_TOKENS = 40

# Sentence boundary: terminal punctuation followed by whitespace, or a newline.
_SENTENCE_SPLIT = re.compile(r"(?<=[.!?])\s+|\n+")
_WORD = re.compile(r"\S+")


def _is_nl_string(s: str) -> bool:
    """True when a string leaf reads like natural language, not a machine token."""
    s = s.strip()
    if len(s) < 3:
        return False
    for pat in _MACHINE_PATTERNS:
        if pat.match(s):
            return False
    # NL gate: at least 3 word-ish tokens, or sentence punctuation present.
    if len(_WORD.findall(s)) >= 3:
        return True
    return bool(re.search(r"[.!?]", s))


def _looks_like_json(s: str) -> bool:
    t = s.lstrip()
    return t[:1] in ("{", "[")


def _walk(node: Any, parent_key: str, depth: int, state: dict,
          out: List[Tuple[str, float]]) -> None:
    """Collect (text, weight) NL leaves from a parsed JSON value, in document order."""
    if state["nodes"] >= _MAX_NODES or state["chars"] >= _MAX_TOTAL_CHARS or depth > _MAX_DEPTH:
        return
    state["nodes"] += 1

    if isinstance(node, dict):
        # Chat-message shape: {"role": ..., "content": ...} → weight by role.
        role = node.get("role")
        if isinstance(role, str) and "content" in node:
            weight = _ROLE_WEIGHTS.get(role.lower(), _WEIGHT_HEURISTIC)
            if weight > 0:
                _walk(node["content"], "content", depth + 1, state, _weighted(out, weight))
            # still walk other keys (rare, but cheap and bounded)
            for k, v in node.items():
                if k not in ("role", "content"):
                    _walk(v, str(k), depth + 1, state, out)
            return
        for k, v in node.items():
            _walk(v, str(k), depth + 1, state, out)
        return

    if isinstance(node, list):
        for item in node:
            _walk(item, parent_key, depth + 1, state, out)
        return

    if isinstance(node, str):
        s = node.strip()
        if not s:
            return
        # Double-encoded JSON inside a string leaf → recurse (bounded by depth).
        if _looks_like_json(s):
            try:
                _walk(json.loads(s), parent_key, depth + 1, state, out)
                return
            except (ValueError, TypeError):
                pass
        key_is_nl = parent_key.lower() in _NL_KEYS
        if key_is_nl:
            weight = _WEIGHT_NL_KEY
        elif _is_nl_string(s):
            weight = _WEIGHT_HEURISTIC
        else:
            return
        take = s[: _MAX_TOTAL_CHARS - state["chars"]]
        if take:
            out.append((take, weight))
            state["chars"] += len(take)
    # numbers / bools / null → dropped


class _Weighted(list):
    """Append proxy that scales every appended weight by `factor` (for chat roles)."""

    def __init__(self, base: List[Tuple[str, float]], factor: float):
        super().__init__()
        self._base = base
        self._factor = factor

    def append(self, item: Tuple[str, float]) -> None:  # type: ignore[override]
        text, weight = item
        self._base.append((text, weight * self._factor))


def _weighted(base: List[Tuple[str, float]], factor: float) -> List[Tuple[str, float]]:
    return _Weighted(base, factor)


def strip_to_nl(text: str) -> List[Tuple[str, float]]:
    """Reduce a payload to weighted natural-language fragments.

    JSON payloads are walked and only prompt-language leaves are kept; a non-JSON
    body is treated as one fragment. Returns [] only for genuinely empty input.
    """
    if not text or not text.strip():
        return []
    stripped = text.strip()
    if _looks_like_json(stripped):
        try:
            parsed = json.loads(stripped)
        except (ValueError, TypeError):
            return [(stripped[:_MAX_TOTAL_CHARS], _WEIGHT_BARE)]
        out: List[Tuple[str, float]] = []
        _walk(parsed, "", 0, {"nodes": 0, "chars": 0}, out)
        # Fail-safe: parsed JSON with no NL leaves → fall back to the raw body so
        # the request is still classified (flagged low-signal by the caller).
        if not out:
            return [(stripped[:_MAX_TOTAL_CHARS], _WEIGHT_HEURISTIC)]
        return out
    return [(stripped[:_MAX_TOTAL_CHARS], _WEIGHT_BARE)]


def _sentences(fragment: str) -> List[str]:
    return [s.strip() for s in _SENTENCE_SPLIT.split(fragment) if s.strip()]


def _merge_sentences(sentences: List[str]) -> List[str]:
    """Greedily merge sentences into ≤ _MAX_CHUNK_TOKENS-token chunks."""
    chunks: List[str] = []
    cur: List[str] = []
    cur_tokens = 0
    for sent in sentences:
        n = len(_WORD.findall(sent))
        if cur and cur_tokens + n > _MAX_CHUNK_TOKENS:
            chunks.append(" ".join(cur))
            cur, cur_tokens = [], 0
        cur.append(sent)
        cur_tokens += n
    if cur:
        chunks.append(" ".join(cur))
    return chunks


def normalize(text: str, max_chunks: int = 16) -> List[Tuple[str, float]]:
    """strip_to_nl + sentence chunking → weighted chunks ready to embed/classify.

    When the chunk count exceeds max_chunks, the highest-weight chunks are kept
    (user-prompt chunks before incidental metadata) while preserving order.
    """
    fragments = strip_to_nl(text)
    chunks: List[Tuple[str, float]] = []
    for frag, weight in fragments:
        for ch in _merge_sentences(_sentences(frag)):
            chunks.append((ch, weight))

    if max_chunks > 0 and len(chunks) > max_chunks:
        # Keep the indices of the top-weight chunks, then emit in original order.
        keep = sorted(range(len(chunks)), key=lambda i: chunks[i][1], reverse=True)[:max_chunks]
        keep_set = set(keep)
        chunks = [chunks[i] for i in range(len(chunks)) if i in keep_set]
    return chunks

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
        strip_to_nl + chunk, ready to embed/classify. Feeds the semantic verdict
        cache (cache.py) — unchanged by the instruction/data split below.
    extract_payload_structure(text) -> {"instruction_candidates", "data_candidates",
                                        "ambiguous_candidates"}
        Splits (rather than weights-and-merges) a payload into what the user is
        asking for vs. the data/context it operates on, so intent/segmenter.py
        can classify only the instruction text — never blending in attached
        documents/logs/tool output, which would otherwise dominate the
        embedding and misdirect the intent classifier.
"""

import json
import re
from typing import Any, Dict, List, Optional, Tuple

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
_MAX_DEPTH = 6
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


# --------------------------------------------------------------------------- #
# Instruction / data structural split (tier 1+2 of intent/segmenter.py's
# extraction cascade). Sibling to strip_to_nl: that function weights-and-merges
# every NL leaf into one list for the semantic cache; this one *splits* leaves
# into separate buckets so the intent classifier is never handed an embedding
# that blends the user's ask with the data it operates on.
# --------------------------------------------------------------------------- #
Fragment = Tuple[str, Dict[str, Optional[str]]]  # (text, {"key": ..., "content_type": ...})

# Keys whose string values are (almost) always the user's actual ask.
_INSTRUCTION_KEYS = {
    "instruction", "instructions", "prompt", "query", "task", "ask",
    "question", "prompt_text", "query_text", "request"
}
# Keys whose string values are (almost) always data/context the ask operates
# on — even when the text reads as natural language (e.g. a prose document).
_DATA_KEYS = {
    "document", "documents", "data", "context", "attachment", "attachments",
    "file", "files", "logs", "log", "output", "tool_result", "tool_results",
    "results","background"
}
# content_type/mime_type/type/source_type values that force a fragment to data
# regardless of key/role (tier 2: source metadata and content type).
_DATA_CONTENT_TYPES = {
    "application/json", "application/xml", "text/csv", "text/x-log",
    "text/x-code", "application/octet-stream", "log", "code", "json",
    "xml", "csv", "attachment", "file","tool_result", "tool_use",
}
_CONTENT_TYPE_KEYS = {"content_type", "mime_type", "type", "source_type"}

# Tier-3 structural data markers: fenced code, timestamp-prefixed log lines,
# triple-quoted blocks, attachment markers. A structural (not heuristic) signal
# that a fragment is data, regardless of how natural-language-like it reads.
_FENCED_CODE_RE = re.compile(r"```[\s\S]*?```")
_LOG_LINE_RE = re.compile(r"(?m)^\s*\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}")
_QUOTED_BLOCK_RE = re.compile(r'"""[\s\S]*?"""|\'\'\'[\s\S]*?\'\'\'')
_ATTACHMENT_MARKER_RE = re.compile(r"(?im)^-{2,}\s*(BEGIN|END)\s+(FILE|ATTACHMENT)\s*-{2,}\s*$")
_STRUCTURAL_DATA_PATTERNS = (_FENCED_CODE_RE, _LOG_LINE_RE, _QUOTED_BLOCK_RE, _ATTACHMENT_MARKER_RE)


def has_structural_data_marker(text: str) -> bool:
    """True when text contains a fenced code block, log-line, quoted block, or
    attachment marker — data, never an instruction, no matter how NL it reads."""
    return any(p.search(text) for p in _STRUCTURAL_DATA_PATTERNS)


def _source(key: str, content_type: Optional[str] = None) -> Dict[str, Optional[str]]:
    return {"key": key, "content_type": content_type}


def _is_data_content_type(ct: Optional[str]) -> bool:
    return bool(ct) and ct.strip().lower() in _DATA_CONTENT_TYPES


class _SplitState:
    __slots__ = ("nodes", "chars")

    def __init__(self) -> None:
        self.nodes = 0
        self.chars = 0


def _emit(s: str, key: str, content_type: Optional[str], state: _SplitState,
         bucket: List[Fragment]) -> None:
    take = s[: _MAX_TOTAL_CHARS - state.chars]
    if take:
        bucket.append((take, _source(key, content_type)))
        state.chars += len(take)


def _walk_force_data(node: Any, parent_key: str, depth: int, state: _SplitState,
                     data: List[Fragment], content_type: Optional[str] = None) -> None:
    """Dump every string leaf under `node` into `data`, no NL/key gating.

    Used for assistant/tool chat-message content: already known to be data
    (prior model output or tool results), so every leaf goes to `data`
    regardless of what key or shape it's nested under.
    """
    if state.nodes >= _MAX_NODES or state.chars >= _MAX_TOTAL_CHARS or depth > _MAX_DEPTH:
        return
    state.nodes += 1
    if isinstance(node, dict):
        for k, v in node.items():
            _walk_force_data(v, str(k), depth + 1, state, data, content_type)
        return
    if isinstance(node, list):
        for item in node:
            _walk_force_data(item, parent_key, depth + 1, state, data, content_type)
        return
    if isinstance(node, str):
        s = node.strip()
        if s:
            _emit(s, parent_key, content_type, state, data)


def _walk_split(node: Any, parent_key: str, depth: int, state: _SplitState,
                instr: List[Fragment], data: List[Fragment],
                ambiguous: List[Fragment], content_type: Optional[str] = None,
                default: Optional[List[Fragment]] = None,
                extra_instruction_keys: frozenset = frozenset(),
                extra_data_keys: frozenset = frozenset()) -> None:
    """Bucket NL leaves into instruction / data / ambiguous, in document order.

    `default` is where an NL-looking leaf with no explicit key/content-type
    signal lands — `ambiguous` at the top level, but `instr` once we're inside
    a role="user" chat message (a user turn IS the ask, by construction; an
    explicit data key/content-type found within it still overrides to `data`).
    System-role chat content is skipped entirely here — it's the system
    prompt, handled separately by intent/prefilter.py's capability-profile
    cache, never part of the instruction/data split for the current request.

    `extra_instruction_keys`/`extra_data_keys` are per-agent additions to the
    generic `_INSTRUCTION_KEYS`/`_DATA_KEYS` lexicon — keys intent/corpus.py has
    observed this specific agent's traffic repeatedly using for instructions or
    data (see intent/prefilter.py's structure-profile cache). Empty by default,
    so behavior is unchanged for an agent with no learned profile yet.

    Fine-grained intra-string splitting (e.g. a fenced code block embedded in
    the middle of an otherwise-instructional sentence) is NOT done here — that
    granularity is intent/segmenter.py's tier-3 job, operating on the text of
    each fragment this function hands back.
    """
    if default is None:
        default = ambiguous
    if state.nodes >= _MAX_NODES or state.chars >= _MAX_TOTAL_CHARS or depth > _MAX_DEPTH:
        return
    state.nodes += 1

    if isinstance(node, dict):
        ct = content_type
        for ck in _CONTENT_TYPE_KEYS:
            v = node.get(ck)
            if isinstance(v, str):
                ct = v
                break

        role = node.get("role")
        if isinstance(role, str) and "content" in node:
            r = role.lower()
            if r == "system":
                pass
            elif r == "user":
                _walk_split(node["content"], "content", depth + 1, state, instr, data, ambiguous, ct, instr,
                            extra_instruction_keys, extra_data_keys)
            else:  # assistant, tool, or anything else non-user/system → data
                _walk_force_data(node["content"], f"role:{r}", depth + 1, state, data, ct)
            for k, v in node.items():
                if k not in ("role", "content") and k not in _CONTENT_TYPE_KEYS:
                    _walk_split(v, str(k), depth + 1, state, instr, data, ambiguous, ct, default,
                                extra_instruction_keys, extra_data_keys)
            return

        for k, v in node.items():
            if k in _CONTENT_TYPE_KEYS:
                continue
            _walk_split(v, str(k), depth + 1, state, instr, data, ambiguous, ct, default,
                        extra_instruction_keys, extra_data_keys)
        return

    if isinstance(node, list):
        for item in node:
            _walk_split(item, parent_key, depth + 1, state, instr, data, ambiguous, content_type, default,
                        extra_instruction_keys, extra_data_keys)
        return

    if isinstance(node, str):
        s = node.strip()
        if not s:
            return
        if _looks_like_json(s):
            try:
                _walk_split(json.loads(s), parent_key, depth + 1, state, instr, data, ambiguous, content_type, default,
                            extra_instruction_keys, extra_data_keys)
                return
            except (ValueError, TypeError):
                pass
        key = parent_key.lower()
        if _is_data_content_type(content_type):
            _emit(s, key, content_type, state, data)
        elif key in _INSTRUCTION_KEYS or key in extra_instruction_keys:
            _emit(s, key, content_type, state, instr)
        elif key in _DATA_KEYS or key in extra_data_keys:
            _emit(s, key, content_type, state, data)
        elif _is_nl_string(s):
            _emit(s, key, content_type, state, default)
        # else: machine-token leaf, dropped entirely (same as strip_to_nl)


def extract_payload_structure(text: str, extra_instruction_keys: Optional[frozenset] = None,
                              extra_data_keys: Optional[frozenset] = None) -> Dict[str, List[Fragment]]:
    """Split a request body into instruction / data / ambiguous fragments.

    Tier 1+2 of intent/segmenter.py's extraction cascade: explicit payload
    structure (known instruction/data keys, chat roles) plus content-type/
    source-metadata overrides. Whatever lands in "ambiguous_candidates" still
    needs tier 3 (structural regex) or tier 4 (heuristic) segmentation.

    extra_instruction_keys/extra_data_keys layer a per-agent learned key
    lexicon on top of the generic one (see intent/prefilter.py's structure
    profile) — omit for the generic-only behavior.
    """
    instr: List[Fragment] = []
    data: List[Fragment] = []
    ambiguous: List[Fragment] = []
    if not text or not text.strip():
        return {"instruction_candidates": instr, "data_candidates": data, "ambiguous_candidates": ambiguous}
    stripped = text.strip()
    if _looks_like_json(stripped):
        try:
            parsed = json.loads(stripped)
        except (ValueError, TypeError):
            ambiguous.append((stripped[:_MAX_TOTAL_CHARS], _source("")))
            return {"instruction_candidates": instr, "data_candidates": data, "ambiguous_candidates": ambiguous}
        _walk_split(parsed, "", 0, _SplitState(), instr, data, ambiguous,
                   extra_instruction_keys=extra_instruction_keys or frozenset(),
                   extra_data_keys=extra_data_keys or frozenset())
        if not instr and not data and not ambiguous:
            ambiguous.append((stripped[:_MAX_TOTAL_CHARS], _source("")))
        return {"instruction_candidates": instr, "data_candidates": data, "ambiguous_candidates": ambiguous}
    ambiguous.append((stripped[:_MAX_TOTAL_CHARS], _source("")))
    return {"instruction_candidates": instr, "data_candidates": data, "ambiguous_candidates": ambiguous}

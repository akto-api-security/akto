"""TokenLimit — tiktoken with the embedded cl100k_base vocab.

tiktoken's Rust core loads in the Worker, but get_encoding() tries to download
the vocab over a (blocked) network call. We instead build the Encoding directly
from the vocab embedded by scripts/gen_vocab.py. The encoding is parsed once per
isolate (≈100k ranks) and cached in a module global.

Config:
  - limit: int                 (required — max allowed tokens)
  - encoding_name: str         (default "cl100k_base"; only this is bundled)
"""

from typing import Any

# cl100k_base pattern + special tokens (from tiktoken_ext/openai_public.py).
_CL100K_PAT = (
    r"""(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\r\n\p{L}\p{N}]?+\p{L}+|\p{N}{1,3}"""
    r"""| ?[^\s\p{L}\p{N}]++[\r\n]*|\s*[\r\n]|\s+(?!\S)|\s+"""
)
_CL100K_SPECIAL = {
    "<|endoftext|>": 100257,
    "<|fim_prefix|>": 100258,
    "<|fim_middle|>": 100259,
    "<|fim_suffix|>": 100260,
    "<|endofprompt|>": 100276,
}

_ENCODING = None  # built lazily, reused across requests within an isolate


def _get_encoding():
    global _ENCODING
    if _ENCODING is None:
        import base64

        import tiktoken

        import vocab_blob

        ranks = {}
        for line in vocab_blob.raw_bytes().splitlines():
            line = line.strip()
            if not line:
                continue
            token, rank = line.split()
            ranks[base64.b64decode(token)] = int(rank)
        _ENCODING = tiktoken.Encoding(
            name="cl100k_base",
            pat_str=_CL100K_PAT,
            mergeable_ranks=ranks,
            special_tokens=_CL100K_SPECIAL,
        )
    return _ENCODING


def scan(scanner_type: str, text: str, config: dict[str, Any]) -> dict[str, Any]:
    limit: int | None = config.get("limit")
    enc = _get_encoding()
    # disallowed_special=() so tiktoken counts special-token text literally
    # instead of raising — matches llm_guard's permissive counting.
    n_tokens = len(enc.encode(text, disallowed_special=()))

    over = limit is not None and n_tokens > int(limit)
    return {
        "is_valid": not over,
        "risk_score": 1.0 if over else 0.0,
        "sanitized_text": text,
        "details": {
            "scanner_type": scanner_type,
            "token_count": n_tokens,
            "limit": limit,
        },
    }

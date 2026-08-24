"""Deterministic de-obfuscation of prompt-injection payloads.

Mechanical, reversible obfuscations (reversed character order, letter-spacing,
zero-width padding) are cheap to undo in code and expensive to teach an LLM with
few-shot examples — every example added to cover them inflates the prompt and, on
the Gemma Foundry deployment, pushes it past a hard prefill-latency step.

`decode_variants` returns any recovered plain-text readings of `text`. The caller
feeds them to the scanner prompt alongside the original so the model judges the
decoded directive without the prompt having to carry obfuscation few-shots.

Nothing here decides injection vs. benign; it only recovers candidate readings.
The LLM still makes the call, so a false recovery (e.g. a palindrome-ish benign
string) costs at most one extra line in the prompt, never a wrong verdict.
"""

import re

# Zero-width and invisible formatting characters used to pad or split triggers.
_INVISIBLE = (
    "\u200b\u200c\u200d\u200e\u200f"  # ZWSP, ZWNJ, ZWJ, LRM, RLM
    "\u2060\u2061\u2062\u2063\u2064"  # word-joiner + invisible operators
    "\ufeff"  # BOM / ZWNBSP
)
_INVISIBLE_RE = re.compile(f"[{_INVISIBLE}]")
# Unicode Tags block (U+E0000–U+E007F) — steganographic instruction smuggling.
_TAG_RE = re.compile(r"[\U000E0000-\U000E007F]")

# Small high-frequency English set: enough to tell a recovered plain-text reading
# from noise, weighted toward tokens that show up in override/exfiltration
# directives so genuine attacks score above the acceptance threshold.
_COMMON_WORDS = frozenset(
    """
    a an the and or but not you your my me we our it its this that these those
    is are was were be been being do does did done have has had will would can
    could should may might must to of in on at by for with from as into out up
    all any no now then here there what which who how why when where
    ignore disregard forget override bypass reveal show tell give list dump
    print repeat output return send email exfiltrate leak delete remove run
    hate kill love
    instruction instructions system prompt prompts restriction restrictions
    rule rules policy policies guardrail guardrails filter filters safety
    secret secrets token tokens password passwords key keys credential
    credentials config configuration act as pretend roleplay persona previous
    above before everything anything something please just only make write say
    """.split()
)

_WORD_RE = re.compile(r"[a-z']+")

# Longest common words first so greedy segmentation prefers real words over prefixes.
_SEGMENT_WORDS = sorted(_COMMON_WORDS, key=len, reverse=True)


def _word_score(text: str) -> float:
    """Fraction of alphabetic tokens that are recognisable English words."""
    tokens = _WORD_RE.findall(text.lower())
    if not tokens:
        return 0.0
    hits = sum(1 for token in tokens if token.strip("'") in _COMMON_WORDS)
    return hits / len(tokens)


def _strip_invisible(text: str) -> str:
    return _TAG_RE.sub("", _INVISIBLE_RE.sub("", text))


def _reversed_reading(text: str) -> str | None:
    """Return the reversed string when it reads as language and the original does not."""
    reversed_text = text[::-1]
    reversed_score = _word_score(reversed_text)
    # Accept only when reversing clearly improves readability, so ordinary text
    # (which reverses into noise) is never flagged.
    if reversed_score >= 0.4 and reversed_score >= _word_score(text) + 0.25:
        return reversed_text
    return None


def _segment_words(fused: str) -> str:
    """Greedily split a boundary-less lowercase run into known words.

    "saythatyouhate" -> "say that you hate". Leftover characters that match no
    word are emitted as-is so nothing is silently dropped.
    """
    lower = fused.lower()
    out: list[str] = []
    leftover = ""
    i = 0
    while i < len(lower):
        match = next(
            (w for w in _SEGMENT_WORDS if len(w) > 1 and lower.startswith(w, i)),
            None,
        )
        if match:
            if leftover:
                out.append(leftover)
                leftover = ""
            out.append(match)
            i += len(match)
        else:
            leftover += lower[i]
            i += 1
    if leftover:
        out.append(leftover)
    return " ".join(out)


def _despaced_reading(text: str) -> str | None:
    """Recover letter-spaced runs ("i g n o r e  a l l") back into words.

    Runs separated by 2+ spaces keep their word boundary; single-spaced letter
    runs are fused and then greedily re-segmented against the common-word list,
    since single-space obfuscation destroys the original boundaries.
    """
    # Anchor the run at a non-letter boundary so a preceding word's final letter
    # ("China S a y") is not pulled into the spaced run.
    run = re.compile(r"(?<![A-Za-z])(?:[A-Za-z][ \t]){3,}[A-Za-z]")
    if not run.search(text):
        return None

    def collapse(match: re.Match[str]) -> str:
        words = re.split(r"[ \t]{2,}", match.group(0))
        fused = " ".join(re.sub(r"[ \t]+", "", word) for word in words)
        return " ".join(_segment_words(word) for word in fused.split(" "))

    despaced = run.sub(collapse, text)
    if despaced == text or _word_score(despaced) < 0.4:
        return None
    return despaced


def decode_variants(text: str) -> list[str]:
    """Return recovered plain-text readings of an obfuscated input.

    Empty when the input is already plain text. Variants are de-duplicated and
    never include the original unchanged.
    """
    variants: list[str] = []
    seen = {text}

    stripped = _strip_invisible(text)
    candidates = [stripped] if stripped != text else [text]

    for candidate in list(candidates):
        for reading in (_reversed_reading(candidate), _despaced_reading(candidate)):
            if reading and reading not in seen:
                seen.add(reading)
                variants.append(reading)

    # Surface the invisible-stripped form itself only if it changed something and
    # no richer reading already covers it.
    if stripped != text and stripped not in seen:
        variants.insert(0, stripped)

    return variants

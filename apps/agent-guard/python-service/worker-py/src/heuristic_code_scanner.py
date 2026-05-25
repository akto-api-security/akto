"""
Layered heuristic code detection scanner.

Detection layers (in order, with early exit):
  Layer 1: Structural markers (fenced blocks, shebangs)
  Layer 2: Per-language keyword heuristics with chunked scanning
  Layer 3: Line structure analysis (indentation, braces, semicolons)
  Layer 4: Pygments token density across 22 language lexers (optional)

Interface mirrors llm_guard scanners:
    scanner.scan(text) -> (sanitized_text, is_valid, risk_score)
    is_valid=False  -> code detected (request should be blocked)
    is_valid=True   -> no code detected (request passes)
    risk_score in [0.0, 1.0]
"""

import re
import logging

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Layer 1 — Structural markers
# ---------------------------------------------------------------------------

_FENCE   = re.compile(r'`{3}|~{3}')
_SHEBANG = re.compile(r'^#!/')

# ---------------------------------------------------------------------------
# Layer 2 — Per-language keyword families (all compiled at import time)
# ---------------------------------------------------------------------------

LANG_PATTERNS: dict[str, re.Pattern] = {
    'python': re.compile(
        r'\bdef [a-zA-Z_]\w*\s*\('
        r'|\bimport [a-zA-Z_]'
        r'|\bclass [A-Z]\w*[:(]'
        r'|\belif\b'
        r'|\blambda\b'
        r'|\bfrom [a-zA-Z_]\w* import\b'
        r'|\basync def\b'
        r'|\b__init__\b'
        r'|\bself\.[a-zA-Z_]'
        r'|\b__name__\b'
        r'|\bwith open\b'
        r'|\blist comprehension|\[[^\]]{5,}\bfor \w+ in\b'
    ),
    'js': re.compile(
        r'\bfunction\s*\w*\s*\('
        r'|\bconst [a-zA-Z_$][\w$]* ?[=;,\[(]'
        r'|\blet [a-zA-Z_$][\w$]* ?[=:;,\[(]'
        r'|\bvar [a-zA-Z_$][\w$]* ?='
        r'|\b=>\s*[{(\w]'
        r'|\bconsole\.(?:log|error|warn)\b'
        r'|\b\.(?:reduce|filter|map|forEach|then|catch)\s*\('
        r'|\brequire\s*\('
        r'|\bmodule\.exports\b'
        r'|\bnew Promise\b'
        r'|\basync function\b'
        r'|\bawait\b'
    ),
    'typescript': re.compile(
        r'\binterface [A-Z]\w*\s*\{'
        r'|\btype [A-Z]\w* ='
        r'|: (?:string|number|boolean|void|any|never|unknown|object)\b'
        r'|\bas const\b'
        r'|\benum [A-Z]\w*\b'
        r'|\bReadonly<'
        r'|\bPartial<'
        r'|\bRecord<'
        r'|\bPromise<'
    ),
    'java_cs': re.compile(
        r'\bpublic static\b'
        r'|\bprivate (?:static |final |)\w'
        r'|\bvoid [a-zA-Z_]\w*\s*\('
        r'|\bnew [A-Z]\w*\s*\('
        r'|\bSystem\.(?:out|err)\b'
        r'|\bpublic class\b'
        r'|\b@Override\b'
        r'|\b@Autowired\b'
        r'|\bList<\w'
        r'|\bArrayList<'
        r'|\bHashMap<'
        r'|\bstatic void main\b'
        r'|\busing System\b'
        r'|\bnamespace [A-Z]'
    ),
    'cpp': re.compile(
        r'#include\s*[<"][a-zA-Z_]'
        r'|\bcout\s*<<'
        r'|\bcin\s*>>'
        r'|\bstd::'
        r'|\bint main\s*\('
        r'|\bnullptr\b'
        r'|\bconst_cast\b'
        r'|\bdynamic_cast\b'
        r'|\btemplate\s*<'
        r'|\bvector<'
        r'|\bshared_ptr<'
        r'|\bunique_ptr<'
        r'|::\w+\s*\('
    ),
    'go': re.compile(
        r'\bpackage main\b'
        r'|\bfunc [a-zA-Z_]\w*\s*\('
        r'|\bfmt\.(?:Println|Printf|Sprintf|Errorf)\b'
        r'|\bchan [a-zA-Z_]'
        r'|\bgo func\b'
        r'|\b:= '
        r'|\bdefer\b'
        r'|\bgoroutine\b'
        r'|\berrors\.New\b'
        r'|\bcontext\.Context\b'
        r'|\binterface\s*\{'
    ),
    'rust': re.compile(
        r'\bfn [a-zA-Z_]\w*\s*[(<]'
        r'|\blet mut\b'
        r'|\bmatch\s+\w'
        r'|\bimpl [A-Z]\w*'
        r'|\buse std::'
        r'|\bprintln!\s*\('
        r'|\bResult<'
        r'|\bOption<'
        r'|\bVec<'
        r'|\bBox<'
        r'|\bArc<'
        r'|\bMutex<'
        r'|\b->\s*(?:Result|Option|Vec|bool|i32|u32|String)\b'
        r'|\bderiving\b|\b#\[derive'
    ),
    'php': re.compile(
        r'<\?php'
        r'|\$[a-zA-Z_]\w*\s*='
        r'|\becho\s+'
        r'|\bfunction\s+[a-zA-Z_]\w*\s*\('
        r'|\bnamespace\s+[A-Z]'
        r'|\buse\s+[A-Z]\w*(?:\\[A-Za-z]\w*)+'  # PHP: use App\Http\Controller
        r'|\$this->'
        r'|\bnew\s+[A-Z]\w*\s*\('
        r'|\barray\s*\('
        r'|\bforeach\s*\('
    ),
    'ruby': re.compile(
        r'\bdef [a-zA-Z_]\w*\b'
        r'|\bend\b(?!\s*(?:of|to|the|up|in|user))'
        r'|\bputs\b'
        r'|\brequire\s+[\'"]'
        r'|\battr_(?:accessor|reader|writer)\b'
        r'|\bdo\s*\|[^|]+\|'
        r'|\b\.each\s*(?:do|\{)'
        r'|\bgemfile\b|\bGemfile\b'
        r'|\bmodule [A-Z]\w*'
    ),
    'bash': re.compile(
        r'#!/(?:bin|usr)'
        r'|\bsudo\s+'
        r'|\bapt-get\s+'
        r'|\byum\s+install\b'
        r'|\bgrep\s+-[cnirlvEA]'
        r'|\bawk\s+[\'"{]'
        r'|\bset\s+-[eo]'
        r'|\$\([^)]{2,}\)'
        r'|\$\{[^}]+\}'
        r'|\bchmod\s+[0-7]'
        r'|\becho\s+"[^"]*"\s*>>'
        r'|\bif\s*\[\s*[^]]*\s*\]'
        r'|\bfor\s+\w+\s+in\s+(?:\*|\$|\{[0-9])'  # for X in *.ext / $(...) / {1..N}
    ),
    'sql': re.compile(
        r'\bSELECT\b.{0,80}\bFROM\b'
        r'|\bINSERT\s+INTO\b'
        r'|\bCREATE\s+TABLE\b'
        r'|\bALTER\s+TABLE\b'
        r'|\bDROP\s+TABLE\b'
        r'|\bGROUP\s+BY\b'
        r'|\bINNER\s+JOIN\b'
        r'|\bLEFT\s+JOIN\b'
        r'|\bWHERE\s+\w+\s*='
        r'|\bHAVING\s+'
        r'|\bORDER\s+BY\b',
        re.IGNORECASE,
    ),
    'css': re.compile(
        r'\{[^}]{0,120}\b(?:display|margin|padding|color|font-size|background|border|flex|grid|position|width|height)\s*:[^}]*\}'
        r'|@(?:media|keyframes|import)\s'
        r'|(?:\.[\w-]+|#[\w-]+)\s*\{'
        r'|:(?:hover|focus|active|before|after|root)\b'
    ),
    'docker': re.compile(
        r'^FROM\s+\w'
        r'|^RUN\s+\w'
        r'|^CMD\s+[\["\w]'
        r'|^EXPOSE\s+\d'
        r'|^WORKDIR\s+/'
        r'|^ENV\s+\w'
        r'|^COPY\s+\S'
        r'|^ENTRYPOINT\s+',
        re.MULTILINE,
    ),
    'yaml': re.compile(
        r'^(?:apiVersion|kind|metadata|spec|image|ports|services|name|version|environment|volumes|networks|depends_on):\s'
        r'|^  - [a-zA-Z_]'
        r'|^[a-zA-Z_][\w-]*:\n\s{2}',
        re.MULTILINE,
    ),
    'json': re.compile(
        r'(?m)^\s*"[^"]+"\s*:\s*(?:"[^"]*"|\d+|true|false|null|\[|\{)'
        r'|\{\s*"[^"]+"\s*:'
    ),
    'regex_code': re.compile(
        r'\(\?P<\w+>'         # named capture group (?P<name>...)
        r'|\(\?:'             # non-capturing group (?:...)
        r'|\\\w\{[0-9]'       # quantified escape: \d{1,3}, \w{5}
        r'|\[\^[^\]]{2,}\]'   # negated character class [^\s]
        r'|\(\?[=!<]'         # lookahead/lookbehind (?= (?! (?<
    ),
    'kotlin': re.compile(
        r'\bfun [a-zA-Z_]\w*\s*\('
        r'|\bval\s+[a-zA-Z_]\w*\s*(?::|=)'
        r'|\bvar\s+[a-zA-Z_]\w*\s*(?::|=)'
        r'|\bdata class\b'
        r'|\bobject\s+[A-Z]\w*'
        r'|\bwhen\s*\('
        r'|\bcompanion object\b'
        r'|\bnullable\b|\?\.'
    ),
    'swift': re.compile(
        r'\bfunc [a-zA-Z_]\w*\s*\('
        r'|\bvar\s+[a-zA-Z_]\w*\s*:'
        r'|\blet\s+[a-zA-Z_]\w*\s*='
        r'|\bguard\s+let\b'
        r'|\bif\s+let\b'
        r'|\bstruct [A-Z]\w*\b'
        r'|\bprotocol [A-Z]\w*\b'
        r'|\bextension [A-Z]\w*\b'
        r'|\boptional\b|\bnil\b'
    ),
    'scala': re.compile(
        r'\bdef [a-zA-Z_]\w*\s*[:([]'
        r'|\bval\s+[a-zA-Z_]\w*\s*(?::|=)'
        r'|\bvar\s+[a-zA-Z_]\w*\s*(?::|=)'
        r'|\bobject [A-Z]\w*\b'
        r'|\btrait [A-Z]\w*\b'
        r'|\bcase class\b'
        r'|\bimplicit\b'
        r'|\bSeq\[|List\[|Map\['
    ),
}

# Single-pattern that flags unambiguous code declarations regardless of language
_HIGH_SIGNAL = re.compile(
    r'\bdef [a-zA-Z_]\w*\s*\('
    r'|\bclass [A-Z]\w*[:({\s]'
    r'|\bpackage main\b'
    r'|<\?php'
    r'|\bfn [a-zA-Z_]\w*\s*[(<]'
    r'|\bfunc [a-zA-Z_]\w*\s*\('
    r'|\bpublic class\b'
    r'|\bpublic static void\b'
    r'|\bdata class\b'
    r'|\bcase class\b'
    r'|\binterface [A-Z]\w*\s*\{'
)

# ---------------------------------------------------------------------------
# Layer 3 — Line structure
# ---------------------------------------------------------------------------

_INDENTED_LINE = re.compile(r'^(    |\t)\S')
_BRACE_ONLY    = re.compile(r'^\s*[{}]\s*$')
_SEMICOLON_EOL = re.compile(r';\s*$')

# ---------------------------------------------------------------------------
# Layer 4 — Pygments token density
# ---------------------------------------------------------------------------

try:
    from pygments import lex as _pygments_lex
    from pygments.token import Token as _Token
    from pygments.lexers import (
        PythonLexer, JavascriptLexer, BashLexer,
        JavaLexer, CLexer, CppLexer,
        GoLexer, RustLexer, SqlLexer,
        CssLexer, PhpLexer, RubyLexer,
    )
    _PYGMENTS_AVAILABLE = True
    # 12 lexers chosen for maximum language diversity; instantiated once
    _ALL_LEXERS = [
        PythonLexer(), JavascriptLexer(), BashLexer(),
        JavaLexer(), CLexer(), CppLexer(),
        GoLexer(), RustLexer(), SqlLexer(),
        CssLexer(), PhpLexer(), RubyLexer(),
    ]
    # Tokens that represent actual code constructs (not found in plain prose)
    _CODE_TOKEN_TYPES = frozenset([
        _Token.Keyword,
        _Token.Keyword.Declaration,
        _Token.Keyword.Type,
        _Token.Keyword.Namespace,
        _Token.Name.Function,
        _Token.Name.Class,
        _Token.Name.Namespace,
        _Token.Name.Builtin,
        _Token.Name.Decorator,
        _Token.Operator,
        _Token.Punctuation,
    ])
except ImportError:
    _PYGMENTS_AVAILABLE = False
    _ALL_LEXERS = []
    _CODE_TOKEN_TYPES = frozenset()


def _token_density(text: str, lexer) -> float:
    tokens = list(_pygments_lex(text, lexer))
    if not tokens:
        return 0.0
    meaningful = sum(
        1 for ttype, _ in tokens
        if any(ttype is t or ttype in t for t in _CODE_TOKEN_TYPES)
    )
    return meaningful / len(tokens)


def _pygments_max_density(text: str) -> float:
    sample = text[:800]   # 800 chars is sufficient; keeps 12-lexer pass under 5ms
    best = 0.0
    for lexer in _ALL_LEXERS:
        d = _token_density(sample, lexer)
        if d > best:
            best = d
    return best


# ---------------------------------------------------------------------------
# Chunking constants
# ---------------------------------------------------------------------------

CHUNK_SIZE = 600
CHUNK_STEP = 500
MAX_CHUNKS = 30   # scans up to 15,100 chars; worst-case for 100k+ input < 10ms


# ---------------------------------------------------------------------------
# Core detection logic
# ---------------------------------------------------------------------------

def _scan_heuristic(text: str, use_pygments: bool = True) -> tuple[bool, float, str]:
    """
    Returns (has_code, confidence, layer_hit).
    has_code=True  -> code was detected
    confidence in [0.0, 1.0]
    layer_hit is a debug label
    """
    # Layer 1 — structural markers
    if _FENCE.search(text):
        return True, 1.0, "layer1_fence"
    if _SHEBANG.match(text):
        return True, 1.0, "layer1_shebang"

    # Layer 2 — keyword heuristics over chunks
    pos = 0
    chunks_scanned = 0
    accumulated: dict[str, int] = {}

    while pos < len(text) and chunks_scanned < MAX_CHUNKS:
        chunk = text[pos: pos + CHUNK_SIZE]
        chunk_hits: dict[str, int] = {}

        for lang, pat in LANG_PATTERNS.items():
            n = len(pat.findall(chunk))
            if n:
                chunk_hits[lang] = n
                accumulated[lang] = accumulated.get(lang, 0) + n

        # 2+ matches for the same language in one chunk
        if any(v >= 2 for v in chunk_hits.values()):
            return True, 0.9, "layer2_same_lang"

        # 2+ distinct language families in one chunk
        if len(chunk_hits) >= 2:
            return True, 0.85, "layer2_multi_lang"

        # Single high-signal declaration pattern
        if _HIGH_SIGNAL.search(chunk):
            return True, 0.95, "layer2_high_signal"

        pos += CHUNK_STEP
        chunks_scanned += 1

    # Accumulated signals across all scanned chunks
    if len(accumulated) >= 2 or sum(accumulated.values()) >= 3:
        score = min(0.5 + sum(accumulated.values()) * 0.1, 0.9)
        return True, score, "layer2_accumulated"

    # Layer 3 — line structure analysis
    lines  = text.splitlines()
    n      = len(lines)
    if n > 0:
        # Sample up to 30 lines: first 10, middle 10, last 10
        if n <= 30:
            sample = lines
        else:
            mid = n // 2
            sample = lines[:10] + lines[mid - 5: mid + 5] + lines[-10:]
        n_s = len(sample)
        indented  = sum(1 for l in sample if _INDENTED_LINE.match(l))
        braces    = sum(1 for l in sample if _BRACE_ONLY.match(l))
        semicols  = sum(1 for l in sample if _SEMICOLON_EOL.search(l))
        struct = min(
            (indented / n_s) * 0.4
            + (braces  / n_s) * 0.5
            + (semicols / n_s) * 0.4,
            1.0,
        )
        if struct >= 0.25:
            return True, struct, "layer3_structure"

    # Layer 4 — pygments token density
    if use_pygments and _PYGMENTS_AVAILABLE:
        density = _pygments_max_density(text)
        if density >= 0.25:
            return True, density, "layer4_pygments"

    return False, 0.0, "none"


# ---------------------------------------------------------------------------
# Public scanner class
# ---------------------------------------------------------------------------

class HeuristicCodeScanner:
    """
    Fast layered heuristic code detection scanner.

    scan(text) -> (text, is_valid, risk_score)
      is_valid=False  code detected, request should be blocked
      is_valid=True   no code, request passes
      risk_score in [0.0, 1.0]
    """

    def __init__(self, threshold: float = 0.5, use_pygments: bool = True, **kwargs):
        # scanner_service.py auto-injects keys like modelConfigs from DEFAULT_CONFIG
        # for every request. Accept and ignore them rather than crashing on
        # scanner_class(**config). The two args we actually use are above.
        self.threshold    = threshold
        self.use_pygments = use_pygments and _PYGMENTS_AVAILABLE
        if kwargs:
            logger.debug("HeuristicCodeScanner: ignoring unrecognized config keys: %s",
                         sorted(kwargs.keys()))
        logger.debug(
            "HeuristicCodeScanner init threshold=%.2f pygments=%s",
            threshold, self.use_pygments,
        )

    def scan(self, text: str) -> tuple[str, bool, float]:
        has_code, confidence, layer = _scan_heuristic(text, self.use_pygments)

        if not has_code:
            return text, True, 0.0

        # Normalize confidence above threshold to [0, 1]
        denom = max(1.0 - self.threshold, 1e-6)
        risk  = round(min((confidence - self.threshold) / denom, 1.0), 2)
        risk  = max(0.0, risk)

        is_valid = confidence < self.threshold
        logger.debug(
            "HeuristicCodeScanner: confidence=%.2f layer=%s is_valid=%s risk=%.2f",
            confidence, layer, is_valid, risk,
        )
        return text, is_valid, risk

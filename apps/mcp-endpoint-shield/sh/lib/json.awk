# json.awk — JSON reader/writer for the Akto shell hooks.
#
# POSIX awk only (tested against BWK awk 20200816, gawk and mawk). No gawk
# extensions: no gensub(), no length(array), no RS regexes.
#
# The design point that makes shell hooks safe: values are moved around in their
# ORIGINAL encoded form. `get` returns the raw JSON text of a value — a string
# comes back still quoted and still escaped — so a prompt or tool argument can be
# spliced straight into an outgoing payload without ever being decoded and
# re-encoded. Escaping bugs on user content are therefore not possible; the only
# strings we ever encode are ones we construct ourselves (mode=escape).
#
# Performance note: substr(s, i, 1) is O(len(s)) in BWK awk, so stepping a cursor
# through a large document with substr is quadratic — a 200KB prompt took seconds.
# The document is therefore exploded into a character array ONCE (split with an
# empty separator, which is C-speed) and all scanning indexes that array. Output is
# emitted through a flushing buffer for the same reason: repeated string
# concatenation reallocates, so it is flushed every BUFMAX bytes rather than grown
# to the full length. Awks without empty-separator split fall back to the substr
# scan, which is correct but slower.
#
# Modes (select with -v mode=...):
#   get     -v path=a.b.c   print the raw JSON value at that path; exit 1 if absent
#   type    -v path=a.b.c   print one of: object array string number boolean null
#   unquote                 read a JSON string on stdin, print its inner bytes with
#                           escapes STILL INTACT (safe to concatenate into another
#                           JSON string body)
#   escape                  read raw bytes on stdin, print them as the inner body of
#                           a JSON string (no surrounding quotes)
#   textblocks              read a transcript entry's message.content (a string, or an
#                           array of content blocks) and print one JSON string holding
#                           the concatenated text of its type=="text" blocks
#
# Paths are dot-separated object keys. Array indexing is not supported: nothing in
# the hook contracts needs it, and leaving it out keeps the scanner small.

function fail() { failed = 1; exit 1 }

# Character access, O(1) when the array is available.
function ch(p) {
    if (HAVE_CHARS) return CH[p]
    return substr(s, p, 1)
}

# Emit through a flushing buffer instead of growing one string.
function emit(t) {
    buf = buf t
    if (length(buf) >= BUFMAX) { printf "%s", buf; buf = "" }
}
function emitFlush() { if (length(buf) > 0) { printf "%s", buf; buf = "" } }

function skipws(   c) {
    while (i <= n) {
        c = ch(i)
        if (c == " " || c == "\t" || c == "\n" || c == "\r") i++
        else return
    }
}

# Scan a JSON string starting at position i (which must be '"'), returning the raw
# text INCLUDING both quotes. Backslash escapes are stepped over two bytes at a
# time so an escaped quote never terminates the scan.
function scanString(   start, c) {
    start = i
    i++
    while (i <= n) {
        c = ch(i)
        if (c == "\\") { i += 2; continue }
        if (c == "\"") { i++; return substr(s, start, i - start) }
        i++
    }
    fail()
}

# Scan a balanced {...} or [...] starting at i. Strings are consumed via
# scanString so braces inside them do not affect the depth count.
function scanContainer(   start, depth, c) {
    start = i
    depth = 0
    while (i <= n) {
        c = ch(i)
        if (c == "\"") { scanString(); continue }
        if (c == "{" || c == "[") { depth++; i++; continue }
        if (c == "}" || c == "]") {
            depth--
            i++
            if (depth == 0) return substr(s, start, i - start)
            continue
        }
        i++
    }
    fail()
}

function scanValue(   c, start) {
    skipws()
    c = ch(i)
    if (c == "\"") return scanString()
    if (c == "{" || c == "[") return scanContainer()
    start = i
    while (i <= n) {
        c = ch(i)
        if (c == "," || c == "}" || c == "]" || c == " " || c == "\t" || c == "\n" || c == "\r") break
        i++
    }
    if (i == start) fail()
    return substr(s, start, i - start)
}

# Position i at the value of `key` within the object starting at i.
# Returns 1 on success, 0 if the key is absent or the current value is not an object.
function seekKey(key,   c, k) {
    skipws()
    if (ch(i) != "{") return 0
    i++
    skipws()
    if (ch(i) == "}") { i++; return 0 }

    while (i <= n) {
        skipws()
        if (ch(i) != "\"") return 0
        k = scanString()
        k = substr(k, 2, length(k) - 2)
        skipws()
        if (ch(i) != ":") return 0
        i++
        skipws()
        # Keys in every hook contract we speak are plain ASCII, so comparing the
        # still-escaped key text is exact.
        if (k == key) return 1
        scanValue()
        skipws()
        c = ch(i)
        if (c == ",") { i++; continue }
        return 0
    }
    return 0
}

function typeOf(v,   c) {
    c = substr(v, 1, 1)
    if (c == "\"") return "string"
    if (c == "{")  return "object"
    if (c == "[")  return "array"
    if (v == "true" || v == "false") return "boolean"
    if (v == "null") return "null"
    return "number"
}

# JSON-escape one byte for use inside a string body.
function escByte(c,   d) {
    if (c == "\"") return "\\\""
    if (c == "\\") return "\\\\"
    if (c == "\n") return "\\n"
    if (c == "\r") return "\\r"
    if (c == "\t") return "\\t"
    if (c == "\b") return "\\b"
    if (c == "\f") return "\\f"
    d = index(CTRL, c)
    # CTRL holds \001..\037 at positions 1..31; \000 cannot appear in awk input.
    if (d > 0) return sprintf("\\u%04x", d)
    return c
}

BEGIN {
    CTRL = ""
    for (b = 1; b <= 31; b++) CTRL = CTRL sprintf("%c", b)
    BUFMAX = 8192
    buf = ""
    # gawk, mawk and BWK awk 2020+ all split on an empty separator; probe rather
    # than assume, and fall back to substr indexing when it is unsupported.
    HAVE_CHARS = (split("ab", _probe, "") == 2)
}

# Slurp stdin by re-joining records. RS="^$" is a gawk extension that BWK awk does
# not honour, so rebuild the input instead — this preserves embedded newlines
# exactly, which matters for multi-line prompts. Chunked so that a document
# arriving as many lines does not re-copy the accumulator on every record.
{
    chunk = chunk $0 "\n"
    if (length(chunk) >= 65536) { s = s chunk; chunk = "" }
}

END {
    if (failed) exit 1
    s = s chunk
    # Drop the single trailing newline added by the last record.
    if (substr(s, length(s), 1) == "\n") s = substr(s, 1, length(s) - 1)
    n = length(s)
    i = 1
    if (HAVE_CHARS && n > 0) n = split(s, CH, "")

    if (mode == "escape") {
        for (p = 1; p <= n; p++) emit(escByte(ch(p)))
        emitFlush()
        exit 0
    }

    # textblocks: given a transcript entry's message.content — which is either a
    # plain string or an array of content blocks — print a single valid JSON string
    # holding the concatenated text. Block bodies are already escaped, so joining
    # their inner bytes and re-wrapping in quotes yields a correct JSON string
    # without decoding anything.
    if (mode == "textblocks") {
        skipws()
        c = ch(i)
        if (c == "\"") { printf "%s", scanString(); exit 0 }
        if (c != "[") { exit 1 }
        i++
        out = ""
        while (i <= n) {
            skipws()
            if (ch(i) == "]") break
            elem = scanValue()
            # Keep only {"type":"text", ...} blocks, matching the Python hooks.
            if (substr(elem, 1, 1) == "{") {
                save_s = s; save_i = i; save_n = n; save_chars = HAVE_CHARS
                s = elem; HAVE_CHARS = 0; n = length(s); i = 1
                if (seekKey("type")) {
                    tv = scanValue()
                    if (tv == "\"text\"") {
                        i = 1
                        if (seekKey("text")) {
                            txt = scanValue()
                            if (substr(txt, 1, 1) == "\"")
                                out = out substr(txt, 2, length(txt) - 2)
                        }
                    }
                }
                s = save_s; i = save_i; n = save_n; HAVE_CHARS = save_chars
            }
            skipws()
            if (ch(i) == ",") { i++; continue }
        }
        # Python's _extract_text_from_entry() strips the joined text; do the same on
        # the escaped form so the mirrored prompt matches byte for byte.
        while (1) {
            if (substr(out, length(out) - 1) == "\\n" ||
                substr(out, length(out) - 1) == "\\t" ||
                substr(out, length(out) - 1) == "\\r") { out = substr(out, 1, length(out) - 2); continue }
            if (substr(out, length(out)) == " ") { out = substr(out, 1, length(out) - 1); continue }
            break
        }
        printf "\"%s\"", out
        exit 0
    }

    if (mode == "unquote") {
        skipws()
        if (ch(i) != "\"") exit 1
        v = scanString()
        printf "%s", substr(v, 2, length(v) - 2)
        exit 0
    }

    # get / type
    if (path != "") {
        cnt = split(path, parts, ".")
        for (p = 1; p <= cnt; p++) {
            if (!seekKey(parts[p])) exit 1
        }
    }
    v = scanValue()
    if (mode == "type") printf "%s", typeOf(v)
    else printf "%s", v
    exit 0
}

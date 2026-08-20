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
# Modes (select with -v mode=...):
#   get     -v path=a.b.c   print the raw JSON value at that path; exit 1 if absent
#   type    -v path=a.b.c   print one of: object array string number boolean null
#   unquote                 read a JSON string on stdin, print its inner bytes with
#                           escapes STILL INTACT (safe to concatenate into another
#                           JSON string body)
#   escape                  read raw bytes on stdin, print them as the inner body of
#                           a JSON string (no surrounding quotes)
#
# Paths are dot-separated object keys. Array indexing is not supported: nothing in
# the hook contracts needs it, and leaving it out keeps the scanner small.

function fail() { failed = 1; exit 1 }

function skipws(   c) {
    while (i <= n) {
        c = substr(s, i, 1)
        if (c == " " || c == "\t" || c == "\n" || c == "\r") i++
        else return
    }
}

# Scan a JSON string starting at s[i] (which must be '"'), returning the raw text
# INCLUDING both quotes. Backslash escapes are stepped over two bytes at a time so
# an escaped quote never terminates the scan.
function scanString(   start, c) {
    start = i
    i++
    while (i <= n) {
        c = substr(s, i, 1)
        if (c == "\\") { i += 2; continue }
        if (c == "\"") { i++; return substr(s, start, i - start) }
        i++
    }
    fail()
}

# Scan a balanced {...} or [...] starting at s[i], returning the raw text. Strings
# are consumed via scanString so braces inside them do not affect the depth count.
function scanContainer(   start, depth, c) {
    start = i
    depth = 0
    while (i <= n) {
        c = substr(s, i, 1)
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

# Scan any value at s[i], returning its raw text.
function scanValue(   c, start) {
    skipws()
    c = substr(s, i, 1)
    if (c == "\"") return scanString()
    if (c == "{" || c == "[") return scanContainer()
    start = i
    while (i <= n) {
        c = substr(s, i, 1)
        if (c == "," || c == "}" || c == "]" || c == " " || c == "\t" || c == "\n" || c == "\r") break
        i++
    }
    if (i == start) fail()
    return substr(s, start, i - start)
}

# Position i at the value of `key` within the object starting at s[i].
# Returns 1 on success, 0 if the key is absent or the current value is not an object.
function seekKey(key,   c, k) {
    skipws()
    if (substr(s, i, 1) != "{") return 0
    i++
    skipws()
    if (substr(s, i, 1) == "}") { i++; return 0 }

    while (i <= n) {
        skipws()
        if (substr(s, i, 1) != "\"") return 0
        k = scanString()
        k = substr(k, 2, length(k) - 2)
        skipws()
        if (substr(s, i, 1) != ":") return 0
        i++
        skipws()
        # Keys in every hook contract we speak are plain ASCII, so comparing the
        # still-escaped key text is exact.
        if (k == key) return 1
        scanValue()
        skipws()
        c = substr(s, i, 1)
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
}

# Slurp stdin by re-joining records. RS="^$" is a gawk extension that BWK awk does
# not honour, so rebuild the input instead — this preserves embedded newlines
# exactly, which matters for multi-line prompts.
{ s = s $0 "\n" }

END {
    if (failed) exit 1
    # Drop the single trailing newline added by the last record.
    if (substr(s, length(s), 1) == "\n") s = substr(s, 1, length(s) - 1)
    n = length(s)
    i = 1

    if (mode == "escape") {
        out = ""
        for (p = 1; p <= n; p++) out = out escByte(substr(s, p, 1))
        printf "%s", out
        exit 0
    }

    if (mode == "unquote") {
        skipws()
        if (substr(s, i, 1) != "\"") exit 1
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

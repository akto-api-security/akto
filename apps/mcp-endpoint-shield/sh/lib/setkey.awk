# setkey.awk — set one top-level key in a JSON object, preserving everything else.
#
#   awk -v key=hooks -v valfile=new.json -f setkey.awk < settings.json
#
# Used by the installer to merge the Akto hook block into a shared settings file.
# Every other key is copied through as its ORIGINAL bytes, so unrelated settings
# survive exactly. The existing installers shell out to jq for this and, when jq is
# missing, overwrite the whole file — losing the user's other settings. This has no
# such dependency and no such failure mode.
#
# Prints the rewritten object on success. On unparseable input it prints nothing
# and exits 1, so the caller can leave the original file untouched.

function ch(p) { if (HAVE_CHARS) return CH[p]; return substr(s, p, 1) }

function skipws(   c) {
    while (i <= n) {
        c = ch(i)
        if (c == " " || c == "\t" || c == "\n" || c == "\r") i++
        else return
    }
}

function scanString(   start, c) {
    start = i
    i++
    while (i <= n) {
        c = ch(i)
        if (c == "\\") { i += 2; continue }
        if (c == "\"") { i++; return substr(s, start, i - start) }
        i++
    }
    bad = 1
    return ""
}

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
    bad = 1
    return ""
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
    if (i == start) { bad = 1; return "" }
    return substr(s, start, i - start)
}

BEGIN {
    HAVE_CHARS = (split("ab", _probe, "") == 2)
    bad = 0
    newval = ""
    while ((getline line < valfile) > 0) {
        if (newval != "") newval = newval "\n"
        newval = newval line
    }
    close(valfile)
    if (newval == "") {
        print "setkey.awk: empty or unreadable valfile" > "/dev/stderr"
        fatal = 1
        exit 1
    }
}

{ chunk = chunk $0 "\n" }

END {
    # exit from BEGIN still runs END; without this the block below would print
    # {"key":} with no value and the caller would overwrite a good file with it.
    if (fatal) exit 1
    if (bad) exit 1
    s = chunk
    if (substr(s, length(s), 1) == "\n") s = substr(s, 1, length(s) - 1)
    n = length(s)
    i = 1
    if (HAVE_CHARS && n > 0) n = split(s, CH, "")

    skipws()
    # Only genuinely empty input becomes a fresh object. Input that has content but
    # is not an object is a parse failure, NOT an invitation to replace the file —
    # otherwise a hand-edited or corrupted settings file would be silently
    # overwritten, which is the data loss this script exists to avoid.
    if (n == 0) { printf "{\"%s\":%s}\n", key, newval; exit 0 }
    if (ch(i) != "{") { exit 1 }

    i++
    out = "{"
    first = 1
    replaced = 0
    skipws()
    if (ch(i) != "}") {
        while (i <= n) {
            skipws()
            if (ch(i) != "\"") { bad = 1; break }
            k = scanString()
            if (bad) break
            kname = substr(k, 2, length(k) - 2)
            skipws()
            if (ch(i) != ":") { bad = 1; break }
            i++
            v = scanValue()
            if (bad) break
            if (kname == key) { v = newval; replaced = 1 }
            if (!first) out = out ","
            out = out k ":" v
            first = 0
            skipws()
            if (ch(i) == ",") { i++; continue }
            break
        }
    }
    if (bad) exit 1
    if (!replaced) {
        if (!first) out = out ","
        out = out "\"" key "\":" newval
    }
    printf "%s}\n", out
    exit 0
}

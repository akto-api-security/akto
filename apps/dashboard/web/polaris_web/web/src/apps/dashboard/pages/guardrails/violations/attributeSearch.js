const HEADER_KEY_RE = /^(content-type|content-length|host|user-agent|authorization|x-[\w-]+|aws-[\w-]+|bedrock-[\w-]+)$/i;

export function escapeRegex(value) {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function jsonKeyValuePattern(key, value, exact) {
    const k = escapeRegex(key);
    const v = escapeRegex(value);
    const keyPat = `\\\\*"${k}\\\\*"`;
    const colon = "\\s*:\\s*";
    if (exact) {
        return `${keyPat}${colon}\\\\*"${v}\\\\*"`;
    }
    return `${keyPat}${colon}\\\\*"?[^"\\\\]*${v}`;
}

export function envelopeKeys(side = "any", part = "any") {
    const sides = side === "any" ? ["request", "response"] : [side];
    const parts = part === "any" ? ["Payload", "Headers"] : [part === "headers" ? "Headers" : "Payload"];
    const keys = [];
    sides.forEach((s) => {
        parts.forEach((p) => keys.push(`${s}${p}`));
    });
    return keys;
}

// Nested request/response JSON is stored as an escaped string, e.g.
// "requestHeaders":"{\"bedrock-region\":\"us-east-1\"}".
// [^"]* stops at the first escaped quote (usually the first header key), so
// walk JSON-string contents (unescaped chars or backslash-escapes) instead.
function insideJsonString(inner) {
    return `"(?:[^"\\\\]|\\\\.)*?${inner}`;
}

function scopedPattern(keys, inner) {
    if (!keys.length || keys.length === 4) return inner;
    return `(?:${keys.map((k) => `"${k}"\\s*:\\s*(?:${insideJsonString(inner)}|\\{[^}]*${inner})`).join("|")})`;
}

export function toLatestApiOrigRegex(freeText, clauses = []) {
    const parts = [];
    (clauses || []).forEach((clause) => {
        if (!clause?.key || clause.value == null || String(clause.value) === "") return;
        const inner = jsonKeyValuePattern(clause.key, clause.value, clause.exact);
        parts.push(scopedPattern(envelopeKeys(clause.side, clause.part), inner));
    });
    const text = (freeText || "").trim();
    if (text.length >= 3) parts.push(escapeRegex(text));
    if (parts.length === 0) return null;
    const body = parts.length === 1 ? parts[0] : parts.map((p) => `(?=.*${p})`).join("");
    return `(?i)${body}`;
}

export function addAdvancedFilter(filters, next) {
    if (!next?.key || next.value == null || String(next.value) === "") return filters || [];
    const exists = (filters || []).some((f) => (
        f.key === next.key && f.value === next.value && (f.side || "any") === (next.side || "any") && (f.part || "any") === (next.part || "any")
    ));
    if (exists) return filters || [];
    return [...(filters || []), {
        key: next.key,
        value: String(next.value),
        exact: next.exact !== false,
        side: next.side || "any",
        part: next.part || "any",
    }];
}

export function filterLabel(clause) {
    const side = clause.side && clause.side !== "any" ? clause.side : "";
    const part = clause.part && clause.part !== "any" ? clause.part : "";
    const scope = [side, part].filter(Boolean).join(" ");
    const op = clause.exact ? "is" : "contains";
    const prefix = scope ? `${scope} ` : "";
    return `${prefix}${clause.key} ${op} ${clause.value}`;
}

const CHIP_LABEL_MAX = 48;

export function filterChipLabel(clause) {
    const full = filterLabel(clause);
    if (full.length <= CHIP_LABEL_MAX) return full;
    return `${full.slice(0, CHIP_LABEL_MAX - 1)}…`;
}

export function parseSelectionToFilter(text, side) {
    if (!text || !String(text).trim()) return null;
    const trimmed = String(text).trim().replace(/^[,;]+|[,;]+$/g, "").replace(/,$/, "");
    const kv = trimmed.match(/^"?([A-Za-z0-9_.-]+)"?\s*[:=]\s*"?([\s\S]+?)"?\s*$/);
    if (kv) {
        const key = kv[1];
        const value = kv[2].replace(/^["']|["']$/g, "").trim();
        if (!value) return null;
        const part = HEADER_KEY_RE.test(key) ? "headers" : "payload";
        return { key, value, exact: true, side: side || "any", part };
    }
    return null;
}

export function filterFromEditorSelection(text, line, side) {
    return parseSelectionToFilter(text, side) || parseSelectionToFilter(line, side);
}

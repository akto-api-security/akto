const PLACEHOLDER_RE = /\[[A-Z_]*REDACTED\]/g;
const MIN_TEXT_LEN = 4;

function escapeVariants(value) {
    return [value, value.replace(/\\/g, "\\\\")];
}

function leafVariants(value) {
    return [...escapeVariants(value), ...escapeVariants(JSON.stringify(value).slice(1, -1))];
}

function isMeaningfulText(value) {
    return value.replace(/^[^A-Za-z0-9]+|[^A-Za-z0-9]+$/g, "").length >= MIN_TEXT_LEN;
}

function findInSides(value, sides, variantsOf) {
    for (const variant of variantsOf(value)) {
        if (!variant) continue;
        for (const side of sides) {
            if (side.text && side.text.includes(variant)) {
                const count = side.text.split(variant).length - 1;
                return { location: side.location, phrase: variant, count };
            }
        }
    }
    return null;
}

function splitPieces(evidenceLine) {
    const pieces = [];
    let last = 0;
    let match;
    PLACEHOLDER_RE.lastIndex = 0;
    while ((match = PLACEHOLDER_RE.exec(evidenceLine)) !== null) {
        if (match.index > last) {
            pieces.push({ kind: "text", value: evidenceLine.slice(last, match.index) });
        }
        pieces.push({ kind: "placeholder", value: match[0] });
        last = match.index + match[0].length;
    }
    if (last < evidenceLine.length) {
        pieces.push({ kind: "text", value: evidenceLine.slice(last) });
    }
    return pieces;
}

function resolvePieces(value, sides, variantsOf) {
    const pieces = splitPieces(value);
    const resolved = pieces.map(() => null);

    pieces.forEach((piece, i) => {
        if (piece.kind !== "text" || !isMeaningfulText(piece.value)) return;
        resolved[i] = findInSides(piece.value, sides, variantsOf);
    });

    pieces.forEach((piece, i) => {
        if (piece.kind !== "placeholder") return;
        const found = findInSides(piece.value, sides, variantsOf);
        if (!found) return;
        const isUnique = found.count === 1;
        const neighborLocated = (i > 0 && resolved[i - 1]) || (i < pieces.length - 1 && resolved[i + 1]);
        if (isUnique || neighborLocated) {
            resolved[i] = found;
        }
    });

    return resolved.filter(Boolean);
}

function fragmentEnd(text, start) {
    let depth = 0;
    let inString = false;
    for (let i = start; i < text.length; i++) {
        const c = text[i];
        if (inString) {
            if (c === "\\") {
                i++;
            } else if (c === '"') {
                inString = false;
                if (depth === 0) return i;
            }
        } else if (c === '"') {
            inString = true;
        } else if (c === "{" || c === "[") {
            depth++;
        } else if ((c === "}" || c === "]") && --depth === 0) {
            return i;
        }
    }
    return -1;
}

function collectLeaves(value, out) {
    if (typeof value === "string") {
        out.push(value);
    } else if (value && typeof value === "object") {
        Object.values(value).forEach((v) => collectLeaves(v, out));
    }
    return out;
}

function splitEmbeddedJson(line) {
    const parts = [];
    let plainStart = 0;
    for (let i = 0; i < line.length; i++) {
        if (!'{["'.includes(line[i])) continue;
        const end = fragmentEnd(line, i);
        let parsed;
        try {
            parsed = JSON.parse(line.slice(i, end + 1));
        } catch (e) {
            continue;
        }
        if (i > plainStart) parts.push({ leaf: false, value: line.slice(plainStart, i) });
        collectLeaves(parsed, []).forEach((value) => parts.push({ leaf: true, value }));
        plainStart = end + 1;
        i = end;
    }
    if (plainStart < line.length) parts.push({ leaf: false, value: line.slice(plainStart) });
    return parts;
}

export function buildEvidenceVulnerabilitySegments(evidenceLine, requestText, responseText) {
    if (!evidenceLine) return [];

    const sides = [
        { location: "RESPONSE", text: responseText || "" },
        { location: "REQUEST", text: requestText || "" },
    ];

    const whole = findInSides(evidenceLine, sides, escapeVariants);
    if (whole) {
        return [{ phrase: whole.phrase, location: whole.location, primary: true }];
    }

    return splitEmbeddedJson(evidenceLine)
        .flatMap((part) => {
            const variantsOf = part.leaf ? leafVariants : escapeVariants;
            const found = isMeaningfulText(part.value) && findInSides(part.value, sides, variantsOf);
            return found ? [found] : resolvePieces(part.value, sides, variantsOf);
        })
        .map((r) => ({ phrase: r.phrase, location: r.location, primary: true }));
}

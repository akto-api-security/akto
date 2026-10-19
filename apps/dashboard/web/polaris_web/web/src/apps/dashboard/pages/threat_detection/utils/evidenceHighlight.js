const PLACEHOLDER_RE = /\[[A-Z_]*REDACTED\]/g;
const MIN_TEXT_LEN = 4;

function escapeVariants(value) {
    return [value, value.replace(/\\/g, "\\\\")];
}

function isMeaningfulText(value) {
    return value.replace(/^[^A-Za-z0-9]+|[^A-Za-z0-9]+$/g, "").length >= MIN_TEXT_LEN;
}

function findInSides(value, sides) {
    for (const variant of escapeVariants(value)) {
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

export function buildEvidenceVulnerabilitySegments(evidenceLine, requestText, responseText) {
    if (!evidenceLine) return [];

    const sides = [
        { location: "RESPONSE", text: responseText || "" },
        { location: "REQUEST", text: requestText || "" },
    ];

    const whole = findInSides(evidenceLine, sides);
    if (whole) {
        return [{ phrase: whole.phrase, location: whole.location, primary: true }];
    }

    const pieces = splitPieces(evidenceLine);
    const resolved = pieces.map(() => null);

    pieces.forEach((piece, i) => {
        if (piece.kind !== "text" || !isMeaningfulText(piece.value)) return;
        resolved[i] = findInSides(piece.value, sides);
    });

    pieces.forEach((piece, i) => {
        if (piece.kind !== "placeholder") return;
        const found = findInSides(piece.value, sides);
        if (!found) return;
        const isUnique = found.count === 1;
        const neighborLocated = (i > 0 && resolved[i - 1]) || (i < pieces.length - 1 && resolved[i + 1]);
        if (isUnique || neighborLocated) {
            resolved[i] = found;
        }
    });

    return resolved
        .filter(Boolean)
        .map((r) => ({ phrase: r.phrase, location: r.location, primary: true }));
}

// Regex patterns to detect parameterized segments
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const INT_RE = /^\d+$/
const PARAM_RE = /^\{.+\}$/

function normalizeSegment(seg) {
    if (PARAM_RE.test(seg) || UUID_RE.test(seg) || INT_RE.test(seg)) return '{param}'
    return seg
}

// Build flat rows with path arrays for AG Grid treeData
// Max depth: 4 segments. Params normalized to {param}.
function buildRestFlatRows(endpoints) {
    return endpoints
        .map(ep => {
            if (!ep.endpoint) return null
            const segments = ep.endpoint.split('/').filter(Boolean).map(normalizeSegment)
            if (segments.length === 0) return null
            const path = segments.slice(0, 4)
            return { ...ep, path }
        })
        .filter(Boolean)
}

export { buildRestFlatRows, normalizeSegment }

import func from '@/util/func'

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const INT_RE = /^\d+$/
const PARAM_RE = /^\{.+\}$/

function normalizeSegment(seg) {
    if (PARAM_RE.test(seg) || UUID_RE.test(seg) || INT_RE.test(seg)) return '{param}'
    return seg
}

function buildRestFlatRows(endpoints) {
    return endpoints
        .map(ep => {
            if (!ep.endpoint) return null
            const path = func.convertToRelativePath(ep.endpoint)
                .split('/')
                .filter(Boolean)
                .map(normalizeSegment)
                .slice(0, 4)
            if (path.length === 0) return null
            return { ...ep, path }
        })
        .filter(Boolean)
}

export { buildRestFlatRows, normalizeSegment }

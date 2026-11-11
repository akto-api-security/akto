import threatApi from '../../threat_detection/api'
import { UNASSIGNED_OWNER } from '../../threat_detection/utils/caseManagement'

// How many rows each list panel loads first, and per "Show more".
const POLICIES_PAGE = 6
const HEATMAP_PAGE = 6
const ATTENTION_PAGE = 5

// Events of one severity, out of a fetchCountBySeverity response.
function severityCount(resp, severity) {
    return (resp?.categoryCounts || [])
        .filter((c) => String(c.subCategory || c.severity || '').toUpperCase() === severity)
        .reduce((sum, c) => sum + (c.count || 0), 0)
}

// Events matching a status (and optionally severity / owner). A count only needs `total`, so limit 1;
// events are counted ungrouped, the same way the status totals are.
export function fetchEvents(start, end, { status, severity, owners, limit = 1 }) {
    return threatApi.fetchSuspectSampleData(
        0, [], [], [], [], { detectedAt: -1 }, start, end, [], limit, status,
        undefined, undefined, undefined, undefined, undefined,
        false, severity, undefined, undefined, undefined, undefined, undefined,
        true, { caseOwners: owners },
    )
}

// Share of events the guardrails blocked, out of every event in the range.
function stoppedShare(total) {
    const all = total.blocked + total.alerted + total.flagged
    return { pct: all > 0 ? Math.round((total.blocked / all) * 100) : 0, blocked: total.blocked, all }
}

function percentChange(current, previous) {
    return previous > 0 ? Math.round(((current - previous) / previous) * 100) : null
}

// "3h 12m": the two largest units of a duration in seconds.
export function formatDuration(seconds) {
    if (seconds < 60) return `${seconds}s`
    const minutes = Math.floor(seconds / 60)
    if (minutes < 60) return `${minutes}m`
    const hours = Math.floor(minutes / 60)
    if (hours < 24) return `${hours}h ${minutes % 60}m`
    return `${Math.floor(hours / 24)}d ${hours % 24}h`
}

// A page of rows from the backend and how many rows there are in all. A failed answer has none.
const toPage = (resp, extra = {}) => ({ rows: resp?.rows || [], total: resp?.totalRows || 0, ...extra })

// The three list panels page on the backend: ask for the rows from `skip` on.
const PAGES = {
    topPolicies: async (start, end, skip) => toPage(await threatApi.fetchGuardrailTopPolicies(start, end, {}, skip, POLICIES_PAGE)),
    heatmap: async (start, end, skip) => {
        const resp = await threatApi.fetchGuardrailHeatmap(start, end, {}, skip, HEATMAP_PAGE)
        return toPage(resp, { columns: resp?.columns || [], otherPolicies: resp?.otherPolicies || [] })
    },
    attention: async (start, end, skip) => toPage(await threatApi.fetchGuardrailNeedsAttention(start, end, {}, skip, ATTENTION_PAGE)),
}

// panel: 'topPolicies' | 'heatmap' | 'attention'
export function loadPage(panel, start, end, skip) {
    return PAGES[panel](start, end, skip)
}

// One object per overview panel. A failed call leaves its panel empty instead of failing the page.
export async function fetchOverview(start, end) {
    const span = end - start
    const unassigned = [UNASSIGNED_OWNER]
    const results = await Promise.allSettled([
        threatApi.getDailyThreatActorsCount(start, end, []),
        threatApi.getDailyThreatActorsCount(start - span, start, []),
        threatApi.fetchCountBySeverity(start, end, 'ACTIVE'),
        threatApi.fetchCountBySeverity(start, end, 'UNDER_REVIEW'),
        fetchEvents(start, end, { status: 'UNDER_REVIEW', owners: unassigned }),
        fetchEvents(start, end, { status: 'UNDER_REVIEW', owners: unassigned, severity: ['CRITICAL'] }),
        threatApi.fetchGuardrailActionCounts(start, end),
        threatApi.fetchGuardrailTriageTime(start, end),
        loadPage('topPolicies', start, end, 0),
        loadPage('heatmap', start, end, 0),
        loadPage('attention', start, end, 0),
    ])
    const [daily, prevDaily, sevActive, sevReview, reviewUnassigned, reviewCritUnassigned, actions, triage, topPolicies, heatmap, attention] =
        results.map((r) => (r.status === 'fulfilled' ? r.value : null))

    const total = daily?.totalAnalysed || 0
    const active = daily?.totalActiveStatus || 0
    const ignored = daily?.totalIgnoredStatus || 0
    const critActive = severityCount(sevActive, 'CRITICAL')

    return {
        // Open = active + in review. Only cases in review can have an owner, so every active event is unassigned.
        critUnassigned: critActive + (reviewCritUnassigned?.total || 0),
        kpis: {
            violations: { value: total, changePct: percentChange(total, prevDaily?.totalAnalysed || 0) },
            open: {
                value: active + (daily?.totalUnderReviewStatus || 0),
                critical: critActive + severityCount(sevReview, 'CRITICAL'),
                unassigned: active + (reviewUnassigned?.total || 0),
            },
            ignored: { value: ignored, pct: total > 0 ? Math.round((ignored / total) * 1000) / 10 : 0 },
            stopped: actions ? stoppedShare(actions.total) : null,
            triage: triage?.triage || null,
        },
        // One point per day, in ms like the other charts.
        trend: (actions?.days || []).map((d) => ({ ...d, ts: d.ts * 1000 })),
        topPolicies: topPolicies || toPage(null),
        heatmap: heatmap || toPage(null, { columns: [], otherPolicies: [] }),
        attention: attention || toPage(null),
    }
}

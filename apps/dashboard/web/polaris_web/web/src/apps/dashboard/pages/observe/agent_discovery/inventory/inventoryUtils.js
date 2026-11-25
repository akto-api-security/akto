const DAY_SECONDS = 86400
const YEAR_SECONDS = 365 * DAY_SECONDS

export const agentPath = (id) => `/dashboard/agentic-posture/agent/${id}`

export const INVENTORY_PATH = '/dashboard/observe/agent-inventory'
// The agent page as opened from Inventory; agentPath is the same page under Posture.
export const inventoryAgentPath = (id) => `${INVENTORY_PATH}/agent/${id}`
export const ALL_ASSETS_URL = `${INVENTORY_PATH}?tab=all`
// Where the table keeps its own Source filter and sort between visits (its default key for this page).
export const TABLE_STATE_KEY = `${INVENTORY_PATH}/`

// The page's tab and filter live in the URL, so back from an agent lands on the same view.
export const TAB_NAMES = ['summary', 'analytics', 'all']
const FILTER_KEYS = ['flag', 'flagLabel', 'platform', 'team', 'type', 'quad', 'used', 'week', 'tool']

// Only the All assets tab has a filter; a filter in the URL of another tab is ignored.
export function viewOf(params) {
    const tab = Math.max(TAB_NAMES.indexOf(params.get('tab')), 0)
    const filter = {}
    if (tab === TAB_NAMES.length - 1) {
        for (const key of FILTER_KEYS) {
            const value = key === 'week' ? Number(params.get(key) || NaN) : params.get(key)
            if (value || value === 0) filter[key] = value
        }
    }
    return { tab, filter }
}

export function paramsOf(tab, filter) {
    const params = new URLSearchParams({ tab: TAB_NAMES[tab] })
    for (const key of FILTER_KEYS) {
        if (filter[key] !== undefined && filter[key] !== '') params.set(key, String(filter[key]))
    }
    return params
}

// Red, yellow and green for the three posture groups: the hex for the charts, the Badge tone for the labels beside them.
export const POSTURE_COLORS = { atRisk: '#F24122', attention: '#F2B322', healthy: '#16A679' }
export const POSTURE_TONES = { atRisk: 'critical', attention: 'attention', healthy: 'success' }
// The three posture groups, as the flags the backend filters on.
export const POSTURE_FLAGS = ['atRisk', 'attention', 'healthy']

// One color per platform, shared by the plot, the bars and the table. "Other" is always grey.
const PLATFORM_PALETTE = ['#6D3BEF', '#00A0AC', '#B98900', '#2C6ECB', '#C0267F', '#8A5A2B']
const OTHER_COLOR = '#8C9196'
export function platformColors(platforms) {
    let next = 0
    return Object.fromEntries(platforms.map((p) => [p.name, p.name === 'Other' ? OTHER_COLOR : PLATFORM_PALETTE[next++ % PLATFORM_PALETTE.length]]))
}

const FLAG_LABELS = {
    atRisk: 'At risk',
    attention: 'Needs attention',
    healthy: 'Healthy',
    high: 'Risk score 50+',
    noPolicy: 'No guardrail policy',
    dormant: 'Dormant 30+ days',
    new: 'Created this week',
    shadow: 'Shadow AI',
    unreviewed: 'Unreviewed',
    noTeam: 'No owning team',
    exposed: 'Exposed to the internet',
    privileged: 'Has privileged tools',
}

export const QUAD_LABELS = {
    hiA: 'Active, high risk',
    hiD: 'Dormant, high risk',
    loA: 'Active, lower risk',
    loD: 'Dormant, lower risk',
}

export const USED_LABELS = { today: 'Today', w: '1–7 days', m: '8–30 days', q: '31–90 days', old: '90+ days', never: 'Never' }

// One removable chip per filter in use: any of { flag, platform, team, type, quad, used, week, tool }.
export function filterChips(filter) {
    const labels = {
        flag: filter.flagLabel || FLAG_LABELS[filter.flag],
        platform: `Source: ${filter.platform}`,
        team: `Team: ${filter.team}`,
        type: `Type: ${filter.type}`,
        quad: QUAD_LABELS[filter.quad],
        used: `Last used: ${USED_LABELS[filter.used]}`,
        week: filter.week === 0 ? 'Created this week' : `Created ${filter.week} weeks ago`,
        tool: `Tool: ${filter.tool}`,
    }
    return Object.keys(labels).filter((key) => filter[key] !== undefined && filter[key] !== '').map((key) => ({ key, label: labels[key] }))
}

// "Oct 24", with the year once it is more than a year old. Measured from when the data was made.
export function createdOn(ts, generatedAt) {
    const options = generatedAt - ts > YEAR_SECONDS ? { month: 'short', day: 'numeric', year: 'numeric' } : { month: 'short', day: 'numeric' }
    return new Date(ts * 1000).toLocaleDateString('en-US', options)
}

// "Today", "3 days ago", or the date once it is a month old.
export function ago(ts, generatedAt) {
    if (!ts) return 'Never'
    const days = Math.floor((generatedAt - ts) / DAY_SECONDS)
    if (days < 1) return 'Today'
    if (days === 1) return 'Yesterday'
    return days < 30 ? `${days} days ago` : createdOn(ts, generatedAt)
}

// For use inside a sentence: "today", "3 days ago", "never", but a date keeps its capital.
export function lastUsedText(ts, generatedAt) {
    const text = ago(ts, generatedAt)
    return /^(Today|Yesterday|Never)/.test(text) || /days ago$/.test(text) ? text.toLowerCase() : text
}

// A host name has no break point at its dots, so a narrow column would split it mid-word.
export const breakAtDots = (text) => text.replace(/\./g, '.\u200b')
export const plural = (count, word) => `${count.toLocaleString()} ${word}${count === 1 ? '' : 's'}`
export const percent = (part, total) => (total > 0 ? Math.round((part / total) * 100) : 0)

const DAY_SECONDS = 86400
const YEAR_SECONDS = 365 * DAY_SECONDS

export const agentPath = (id) => `/dashboard/agentic-posture/agent/${id}`

// Red, yellow and green for the three posture groups: the hex for the charts, the Badge tone for the labels beside them.
export const POSTURE_COLORS = { atRisk: '#F24122', attention: '#F2B322', healthy: '#16A679' }
export const POSTURE_TONES = { atRisk: 'critical', attention: 'attention', healthy: 'success' }

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

// A filter is any of { flag, platform, team, type, quad, used, week, tool }; team "none" means no owning team. The table keeps the assets that pass all of them.
// Combination flags carry their own `flagLabel`, since their names come with the data.
export function matches(asset, filter) {
    return (!filter.flag || asset.flags.includes(filter.flag))
        && (!filter.platform || asset.platform === filter.platform)
        && (!filter.team || (filter.team === 'none' ? asset.teams.length === 0 : asset.teams.includes(filter.team)))
        && (!filter.type || asset.type === filter.type)
        && (!filter.quad || asset.quad === filter.quad)
        && (!filter.used || asset.used === filter.used)
        && (filter.week === undefined || asset.week === filter.week)
        && (!filter.tool || asset.toolNames.includes(filter.tool))
}

// One removable chip per filter in use.
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

export const plural = (count, word) => `${count.toLocaleString()} ${word}${count === 1 ? '' : 's'}`
export const percent = (part, total) => (total > 0 ? Math.round((part / total) * 100) : 0)

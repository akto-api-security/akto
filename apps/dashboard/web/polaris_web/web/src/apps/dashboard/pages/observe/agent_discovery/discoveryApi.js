import { useState } from 'react'
import dashboardApi from '../../dashboard/api'

const WINDOW_SECONDS = 7 * 86400
const SNOOZE_MS = 24 * 60 * 60 * 1000

// The overview and "since last week" ask for the same window, so the backend loads its shared data once for both.
// changes is null when its call failed, so the page can say so instead of leaving a gap; a failed overview fails the page.
export async function fetchDiscovery() {
    const end = Math.floor(Date.now() / 1000)
    const start = end - WINDOW_SECONDS
    const [overview, changes] = await Promise.all([
        dashboardApi.fetchArgusDiscoveryOverview(start, end),
        dashboardApi.fetchArgusPostureChanges(start, end, 'all').then((resp) => resp?.rows || null).catch(() => null),
    ])
    return { ...overview, changes }
}

const snoozeKey = () => `agentDiscoverySnoozed:${window.ACTIVE_ACCOUNT}`

// { todoId: snoozedUntilMs } without the ones that have run out.
function readSnoozed() {
    try {
        const saved = JSON.parse(localStorage.getItem(snoozeKey()) || '{}')
        return Object.fromEntries(Object.entries(saved).filter(([, until]) => until > Date.now()))
    } catch {
        return {}
    }
}

// Snoozed to-dos are remembered in this browser only, for a day.
export function useSnoozed() {
    const [snoozed, setSnoozed] = useState(readSnoozed)
    const update = (next) => {
        setSnoozed(next)
        try { localStorage.setItem(snoozeKey(), JSON.stringify(next)) } catch { /* storage blocked: it lasts until the page reloads */ }
    }
    return {
        isSnoozed: (id) => id in snoozed,
        snooze: (id) => update({ ...snoozed, [id]: Date.now() + SNOOZE_MS }),
        clear: () => update({}),
    }
}

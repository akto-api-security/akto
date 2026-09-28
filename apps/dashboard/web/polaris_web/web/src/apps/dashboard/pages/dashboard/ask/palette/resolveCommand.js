// Pure, dependency-free typed-query resolution for the palette's "typing" view. Mirrors the
// approved design's own matching rule exactly (design_handoff_ask_akto_overlay/README.md,
// "3. Home view (typing)" and the prototype's `results()` method) rather than the earlier
// fuzzy-scored resolver this file used to hold: "Ask Akto: '‹query›'" is always first and
// selected by default; "Go to" is pages whose label or breadcrumb CONTAINS the query; "Suggested
// questions" is prompts matching ANY WORD of the query. No fuzzy scoring, no askFirst reordering
// — the design is explicit that the ask option always leads.
import { COMMANDS, INTENTS, DOMAIN_LABELS, SUGGESTED_PROMPTS_BY_DOMAIN } from "./commandRegistry"

function normalize(s) {
    return String(s || "").toLowerCase().replace(/[^a-z0-9 ]/g, " ").trim()
}

function safeGate(entry) {
    try { return !!entry.gate() } catch { return false }
}

function askOption(query, domain) {
    return {
        id: "__ask__",
        kind: "ASK_AKTO",
        label: `Ask Akto: "${query}"`,
        sub: `Answer with live data from ${DOMAIN_LABELS[domain] || domain}`,
        query,
    }
}

/**
 * Builds the typed-results list for one query: [ask, ...go-to pages, ...suggested questions].
 * The first "Go to" row and the first "Suggested questions" row carry a `header` for the
 * section label; every other row's `header` is null. Returns [] for an empty query — the home
 * view renders tiles/prompts instead in that case, not this list.
 */
export function buildResults(query, domain, { commands = COMMANDS, intents = INTENTS, prompts } = {}) {
    const q = (query || "").trim()
    if (!q) return []
    const qn = normalize(q)
    const promptList = prompts || SUGGESTED_PROMPTS_BY_DOMAIN[domain] || []

    const out = [askOption(q, domain)]

    const intentHits = intents
        .filter(safeGate)
        .map((intent) => {
            const m = q.match(intent.pattern)
            if (!m) return null
            const built = intent.build(m)
            return { id: `intent_${intent.id}`, kind: "PAGE", ...built }
        })
        .filter(Boolean)

    const pageHits = commands
        .filter(safeGate)
        .filter((c) => normalize(c.label).includes(qn) || normalize(c.breadcrumb || "").includes(qn))
        .map((c) => ({ id: c.id, kind: "PAGE", label: c.label, breadcrumb: c.breadcrumb, route: c.route, params: c.params }))

    const pages = [...intentHits, ...pageHits]
    pages.forEach((p, i) => out.push({ ...p, header: i === 0 ? "Go to" : null }))

    const words = qn.split(" ").filter(Boolean)
    const promptHits = promptList.filter((p) => words.some((w) => normalize(p).includes(w)))
    promptHits.forEach((p, i) => out.push({
        id: `prompt_${p}`,
        kind: "PROMPT",
        label: p,
        header: i === 0 ? "Suggested questions" : null,
        query: p,
    }))

    return out
}

// Pure, dependency-free typed-query resolution for the palette's "typing" view. Mirrors the
// approved design's own matching rule exactly (design_handoff_ask_akto_overlay/README.md,
// "3. Home view (typing)" and the prototype's `results()` method) rather than the earlier
// fuzzy-scored resolver this file used to hold: "Ask Akto: '‹query›'" is always first and
// selected by default; "Go to" is pages whose label or breadcrumb CONTAINS the query; "Suggested
// questions" is prompts matching ANY WORD of the query. No fuzzy scoring, no askFirst reordering
// — the design is explicit that the ask option always leads.
import { COMMANDS, INTENTS, categoryLabel, suggestedPrompts } from "./commandRegistry"
import { mapLabel } from "@/apps/main/labelHelper"

// Registry copy is written in API wording; only segments naming the API term follow the category
// (e.g. "Endpoint" in a breadcrumb is a section name, not the entity)
function localize(text, category) {
    if (!text) return text
    return text.split(" › ").map((part) => (/\bAPIs?\b/.test(part) ? mapLabel(part, category) : part)).join(" › ")
}

function normalize(s) {
    return String(s || "").toLowerCase().replace(/[^a-z0-9 ]/g, " ").trim()
}

function safeGate(entry) {
    try { return !!entry.gate() } catch { return false }
}

function askOption(query, category) {
    return {
        id: "__ask__",
        kind: "ASK_AKTO",
        label: `Ask Akto: "${query}"`,
        sub: `Answer with live data from ${categoryLabel(category)}`,
        query,
    }
}

/**
 * Builds the typed-results list for one query: [ask, ...go-to pages, ...suggested questions].
 * The first "Go to" row and the first "Suggested questions" row carry a `header` for the
 * section label; every other row's `header` is null. Returns [] for an empty query — the home
 * view renders tiles/prompts instead in that case, not this list.
 */
export function buildResults(query, category, { commands = COMMANDS, intents = INTENTS, prompts } = {}) {
    const q = (query || "").trim()
    if (!q) return []
    const qn = normalize(q)
    const promptList = prompts || suggestedPrompts(category)

    const out = [askOption(q, category)]

    const intentHits = intents
        .filter(safeGate)
        .map((intent) => {
            const m = q.match(intent.pattern)
            if (!m) return null
            const built = intent.build(m)
            return { id: `intent_${intent.id}`, kind: "PAGE", ...built, label: localize(built.label, category), breadcrumb: localize(built.breadcrumb, category) }
        })
        .filter(Boolean)

    const pageHits = commands
        .filter(safeGate)
        .map((c) => ({ c, label: localize(c.label, category), breadcrumb: localize(c.breadcrumb, category) }))
        // match the registry wording too, so typing "api" still finds a page in other categories
        .filter(({ c, label, breadcrumb }) => [c.label, c.breadcrumb, label, breadcrumb].some((t) => normalize(t || "").includes(qn)))
        .map(({ c, label, breadcrumb }) => ({ id: c.id, kind: "PAGE", label, breadcrumb, route: c.route, params: c.params }))

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

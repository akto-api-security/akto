// Pure, dependency-free intent resolution. No fuzzy library exists in this repo's package.json
// (only lodash and highcharts; the only existing search, Headers.js, is a plain .includes()) —
// this is the natural unit-test target once a runner exists (there is none under web/src today;
// see the plan's Part 6).
import { COMMANDS, INTENTS } from "./commandRegistry"

export const MIN_MATCH = 0.25
export const STRONG_MATCH = 0.7

function normalize(s) {
    return String(s || "").toLowerCase().replace(/[^a-z0-9 ]/g, " ").trim()
}

// A full subsequence match of q inside t, scored by contiguity: one unbroken run scores highest
// (0.7), a scattered match scores lower the more runs it takes. Returns 0 when q is not a
// subsequence of t at all.
function subsequenceScore(q, t) {
    if (!q || !t) return 0
    let qi = 0
    let runs = 0
    let inRun = false
    for (let ti = 0; ti < t.length && qi < q.length; ti++) {
        if (t[ti] === q[qi]) {
            qi++
            if (!inRun) { runs++; inRun = true }
        } else {
            inRun = false
        }
    }
    if (qi < q.length) return 0
    return runs <= 1 ? 0.7 : 0.3 + 0.4 * (1 / runs)
}

// Exact substring of the target -> 1.0; otherwise a subsequence score. Used directly against a
// command's label (its highest-weight signal).
export function fuzzyScore(query, target) {
    const q = normalize(query)
    const t = normalize(target)
    if (!q || !t) return 0
    if (t.includes(q)) return 1.0
    return subsequenceScore(q, t)
}

// A command's score is the best of: its label (fuzzyScore, up to 1.0), or any of its keywords
// (exact substring -> 0.85, else a subsequence match scaled slightly below the label tiers so a
// keyword hit never outranks a real label match).
export function scoreCommand(query, command) {
    const q = normalize(query)
    if (!q) return 0
    let best = fuzzyScore(query, command.label)
    for (const kw of command.keywords || []) {
        const kwNorm = normalize(kw)
        if (!kwNorm) continue
        if (kwNorm.includes(q)) { best = Math.max(best, 0.85); continue }
        const s = subsequenceScore(q, kwNorm)
        if (s > 0) best = Math.max(best, s * 0.85)
    }
    return best
}

function safeGate(entry) {
    try { return !!entry.gate() } catch { return false }
}

export function visibleCommands(commands = COMMANDS) {
    return commands.filter(safeGate)
}

function askOption(query) {
    return { id: "__ask__", kind: "ASK_AKTO", label: `Ask Akto: "${query}"`, query }
}

/**
 * Two-tier resolution: tier 1 intents (deterministic regex, first match per intent wins), tier 2
 * fuzzy commands (RBAC-filtered, top 6), tier 3 "Ask Akto" — always present, so an empty options
 * array can never make Enter a silent no-op.
 *
 * askFirst is the one boolean that decides what Enter does when the user hasn't explicitly
 * picked an option: no intent matched AND no command scored a STRONG_MATCH. "critical issues"
 * navigates; "why is my posture bad" asks the AI.
 */
export function resolve(query, { commands = COMMANDS, intents = INTENTS } = {}) {
    const q = (query || "").trim()
    if (!q) return { options: [askOption("")], askFirst: true }

    const intentHits = intents
        .filter(safeGate)
        .map((intent) => {
            const m = q.match(intent.pattern)
            if (!m) return null
            const built = intent.build(m)
            return { id: intent.id, kind: "INTENT", score: 1, ...built }
        })
        .filter(Boolean)

    const commandHits = visibleCommands(commands)
        .map((c) => ({
            id: c.id,
            kind: "COMMAND",
            label: c.label,
            route: c.route,
            params: c.params,
            score: scoreCommand(q, c),
        }))
        .filter((o) => o.score >= MIN_MATCH)
        .sort((a, b) => b.score - a.score)
        .slice(0, 6)

    const askFirst = intentHits.length === 0 && !(commandHits[0]?.score >= STRONG_MATCH)
    // DOM/highlight order matters, not just membership: Listbox auto-highlights whichever option
    // is first, so that alone is what makes Enter do the right thing with no other code — put
    // "Ask Akto" first exactly when askFirst says the AI should win.
    const options = askFirst
        ? [askOption(q), ...intentHits, ...commandHits]
        : [...intentHits, ...commandHits, askOption(q)]
    return { options, askFirst }
}

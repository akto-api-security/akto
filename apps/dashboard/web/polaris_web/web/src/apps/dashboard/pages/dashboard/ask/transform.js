import observeTransform from "../../observe/transform"

// Pure response -> view-model mapping for the Ask overlay. Nothing here computes a number —
// every value rendered comes straight from AskOverlayResponse (see
// com.akto.service.insights.AskOverlayResponse on the dashboard side).

// The four tile tones the design defines, as Polaris color token names (the *-ask-* ones are
// declared in askOverlay.css). A severity that isn't CRITICAL/HIGH/MEDIUM (LOW, or a plain stat
// with no severity, e.g. "Tokens used this month") renders as "Info" — the design has no "Low".
const TILE_TONES = {
    CRITICAL: { label: "Critical", background: "bg-critical-subdued-hover", border: "border-ask-critical", dot: "bg-critical-strong", text: "text-ask-critical" },
    HIGH: { label: "High", background: "bg-ask-high", border: "border-ask-high", dot: "bg-ask-high-strong", text: "text-ask-high" },
    MEDIUM: { label: "Medium", background: "bg-caution-subdued-hover", border: "border-ask-medium", dot: "bg-caution", text: "text-caution" },
}
const INFO_TONE = { label: "Info", background: "bg-ask-info", border: "border-ask-info", dot: "bg-ask-info-strong", text: "text-ask-info" }

export function tileTone(severity) {
    return TILE_TONES[String(severity || "").toUpperCase()] || INFO_TONE
}

function formatCount(value) {
    if (value === undefined || value === null) return "—"
    return observeTransform.formatNumberWithCommas(value)
}

// Puts the AI-picked tiles first, in the order picked, with the question the AI wrote; every other
// tile keeps its place after them. Values are never touched — the curation carries no numbers. A
// pick for a tile that isn't on screen is ignored.
export function applyCuration(tiles, curation) {
    if (curation?.status !== "OK" || !curation.picks?.length) return tiles
    const byId = new Map(tiles.map((t) => [t.id, t]))
    const leadIds = new Set()
    const lead = []
    for (const pick of curation.picks) {
        const tile = byId.get(pick.tileId)
        if (!tile || leadIds.has(tile.id)) continue
        leadIds.add(tile.id)
        lead.push({ ...tile, prompt: pick.prompt || tile.prompt })
    }
    return [...lead, ...tiles.filter((t) => !leadIds.has(t.id))]
}

// Recommendation and insight tiles have different backend shapes (Recommendation vs
// InsightTile) but render identically on the overlay — one unified view-model so the tile
// component never has to branch on where a tile came from. Tile ids (rec_<id>, insight_<id>) must
// match AskTileCurationService's, which the curation's picks refer to.
export function toTileViewModels(overlay) {
    const recommendationTiles = (overlay?.recommendations || []).map((r) => ({
        id: `rec_${r.id}`,
        kind: "recommendation",
        label: r.label,
        value: formatCount(r.count),
        severity: r.severity || null,
        prompt: r.promptTemplate,
        route: r.route || null,
        params: r.params || {},
    }))

    const insightTiles = (overlay?.insightTiles || []).map((t) => {
        const metric = (t.metrics || [])[0]
        // `formatted` is already display-ready (it can carry units, e.g. "42%"); only a bare
        // numeric value needs formatting here.
        const value = metric?.formatted
            || (typeof metric?.value === "number" ? formatCount(metric.value) : null)
            || t.headline
            || ""
        return {
            id: `insight_${t.insightId}`,
            kind: "insight",
            label: t.title,
            value,
            severity: t.severity || null,
            prompt: `Tell me more about "${t.title}" — ${t.headline || ""}`.trim(),
            route: t.primaryCta?.route || null,
            params: t.primaryCta?.params || {},
        }
    })

    return [...recommendationTiles, ...insightTiles]
}

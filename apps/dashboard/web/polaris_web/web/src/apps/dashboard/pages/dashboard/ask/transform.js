// Pure response -> view-model mapping for the Ask overlay. Nothing here computes a number —
// every value rendered comes straight from AskOverlayResponse (see
// com.akto.service.insights.AskOverlayResponse on the dashboard side).

// The four tile tints the design defines (design_handoff_ask_akto_overlay/README.md, "Worth
// doing now" tile table) — CSS classes in askOverlay.css. A backend severity that isn't
// CRITICAL/HIGH/MEDIUM (LOW, or a plain stat with no severity at all, e.g. "Tokens used this
// month") renders as the neutral "Info" tint — the design has no fifth "Low" tint.
const SEVERITY_TO_TILE_TINT = {
    CRITICAL: "critical",
    HIGH: "high",
    MEDIUM: "medium",
}

export function tileTint(severity) {
    return SEVERITY_TO_TILE_TINT[String(severity || "").toUpperCase()] || "info"
}

export function tileSeverityLabel(severity) {
    const tint = tileTint(severity)
    return tint === "info" ? "Info" : tint.charAt(0).toUpperCase() + tint.slice(1)
}

// Recommendation and insight tiles have different backend shapes (Recommendation vs
// InsightTile) but render identically on the overlay — one unified view-model so the tile
// component never has to branch on where a tile came from.
export function toTileViewModels(overlay) {
    const recommendationTiles = (overlay?.recommendations || []).map((r) => ({
        id: `rec_${r.id}`,
        kind: "recommendation",
        label: r.label,
        value: r.count === undefined || r.count === null ? "—" : String(r.count),
        severity: r.severity || null,
        prompt: r.promptTemplate,
        route: r.route || null,
        params: r.params || {},
    }))

    const insightTiles = (overlay?.insightTiles || []).map((t) => {
        const metric = (t.metrics || [])[0]
        const value = metric?.formatted || t.headline || ""
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

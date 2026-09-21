// Pure response -> view-model mapping for the Ask overlay. Nothing here computes a number —
// every value rendered comes straight from AskOverlayResponse (see
// com.akto.service.insights.AskOverlayResponse on the dashboard side).

// Severity colors already live as CSS classes (.insight-severity-bar-*, components/layouts/
// style.css) — Highcharts needs a real color string for TrendSpark, so this is that same
// palette duplicated as JS. Keep the two in sync by hand; do not invent a design-token file for
// this alone.
export const SEVERITY_HEX = {
    CRITICAL: "#DF2909",
    HIGH: "#E45357",
    MEDIUM: "#EF864C",
    LOW: "#94969C",
    DEFAULT: "#E1E3E5",
}

export function severityHex(severity) {
    return SEVERITY_HEX[String(severity || "").toUpperCase()] || SEVERITY_HEX.DEFAULT
}

// Recommendation and insight tiles have different backend shapes (Recommendation vs
// InsightTile) but render identically on the overlay — one unified view-model so
// RecommendationTile.jsx never has to branch on where a tile came from.
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
        trend: null, // recommendations carry no trend series today — see TrendSpark's own guard
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
            trend: null,
        }
    })

    return [...recommendationTiles, ...insightTiles]
}

export function toChangeRows(overlay) {
    return (overlay?.whatChanged || []).map((c, idx) => ({
        id: idx,
        kind: c.kind,
        description: c.description,
        route: c.route || null,
        params: c.params || {},
        timestamp: c.timestamp,
    }))
}

export function toOmittedGroups(overlay) {
    return overlay?.omittedGroups || []
}

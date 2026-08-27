// Shared display mappings + CTA/evidence helpers for Atlas Insights (InsightResult contract —
// see apps/dashboard/src/main/java/com/akto/service/insights/InsightResult.java). Nothing here
// computes a number; every value rendered comes straight from the API response.
import func from "@/util/func";

export const STATUS_LABEL = {
    READY: "Ready",
    PARTIAL: "Partial",
    NO_DATA: "No data",
};

// Badge `status` prop values — undefined falls back to Polaris's default (subdued) badge.
export const STATUS_BADGE_STATUS = {
    READY: "success",
    PARTIAL: "attention",
    NO_DATA: undefined,
};

export const CATEGORY_LABEL = {
    ACTIONABLE: "Actionable",
    READ_ONLY: "Read-only",
};

// InsightId.Group (backend) — the two insight surfaces, which never mix in one list. Each
// carries the page whose own InsightsFlyout instance actually owns that group, so a pinned
// entry point elsewhere (e.g. the header popover) can send the reader to the right place.
export const INSIGHT_GROUP = {
    ATLAS_DISCOVERY: "ATLAS_DISCOVERY",
    GUARDRAIL_VIOLATIONS: "GUARDRAIL_VIOLATIONS",
};

export const INSIGHT_GROUP_LABEL = {
    [INSIGHT_GROUP.ATLAS_DISCOVERY]: "Observe",
    [INSIGHT_GROUP.GUARDRAIL_VIOLATIONS]: "Guardrails",
};

export const INSIGHT_GROUP_ROUTE = {
    [INSIGHT_GROUP.ATLAS_DISCOVERY]: "/dashboard/observe/agentic-assets",
    [INSIGHT_GROUP.GUARDRAIL_VIOLATIONS]: "/dashboard/guardrails/violations",
};

// Query param the target page reads on mount to auto-open its InsightsFlyout — see
// AgenticAssetsPage.jsx / ViolationsPage.jsx. Empty string opens straight to the list.
export const INSIGHT_DEEP_LINK_PARAM = "insight";

export function statusLabel(status) {
    return STATUS_LABEL[status] || status || "";
}

export function categoryLabel(category) {
    return CATEGORY_LABEL[category] || category || "";
}

// A CTA's route is already a full frontend path (InsightRoutes.java); this only appends params.
export function buildCtaHref(cta) {
    if (!cta?.route) return null;
    const params = cta.params || {};
    const query = Object.entries(params)
        .filter(([, v]) => v !== null && v !== undefined && v !== "")
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
        .join("&");
    return query ? `${cta.route}?${query}` : cta.route;
}

const MAX_AI_CHAT_MARKDOWN_CHARS = 4000;

// Ask Akto chat context for one insight (see McpAgentAction.chatAndStoreConversation's
// "insight_result" branch) — sends the exact fields already rendered on this page (metrics,
// evidence, concern/impact/remediation, the AI narrative) rather than an id to re-fetch, so the
// chat is grounded in precisely what the user is looking at right now.
export function buildInsightChatMetadata(detail) {
    const markdown = detail?.markdown || "";
    return {
        type: "insight_result",
        data: {
            title: detail?.title,
            headline: detail?.headline,
            status: detail?.status,
            severity: detail?.severity,
            metrics: detail?.metrics || [],
            evidence: detail?.evidence || [],
            caveats: detail?.caveats || [],
            dataGaps: detail?.dataGaps || [],
            concern: detail?.concern,
            impact: detail?.impact,
            remediation: detail?.remediation,
            markdown: markdown.length > MAX_AI_CHAT_MARKDOWN_CHARS
                ? markdown.slice(0, MAX_AI_CHAT_MARKDOWN_CHARS) + "…"
                : markdown,
        },
    };
}

function humanizeColumnKey(key) {
    const spaced = String(key || "").replace(/([a-z0-9])([A-Z])/g, "$1 $2");
    return spaced.charAt(0).toUpperCase() + spaced.slice(1).toLowerCase();
}

// Evidence columns carrying a Unix-epoch-seconds value (InsightUtil doesn't format these
// server-side since evidence rows are raw data, not a `formatted` metric string) — render as a
// relative date via func.prettifyEpoch instead of the generic numeric formatter below.
const EPOCH_SECONDS_COLUMNS = new Set(["firstSeen"]);

// Builds AgGridTable columnDefs from an Evidence.columns list, right-aligning any column whose
// values are numbers across the (already-bounded, <= EVIDENCE_ROW_CAP) row set.
export function buildEvidenceColumnDefs(evidence) {
    const columns = evidence?.columns || [];
    const rows = evidence?.rows || [];
    return columns.map((col) => {
        if (EPOCH_SECONDS_COLUMNS.has(col)) {
            return {
                field: col,
                headerName: humanizeColumnKey(col),
                flex: 1,
                minWidth: 110,
                filter: false,
                sortable: false,
                valueFormatter: (p) => (typeof p.value === "number" ? func.prettifyEpoch(p.value) : p.value ?? ""),
            };
        }
        const isNumeric = rows.some((row) => typeof row?.[col] === "number");
        return {
            field: col,
            headerName: humanizeColumnKey(col),
            flex: 1,
            minWidth: 110,
            filter: false,
            sortable: false,
            cellStyle: isNumeric ? { textAlign: "right", justifyContent: "flex-end" } : undefined,
            valueFormatter: isNumeric
                ? (p) => (typeof p.value === "number" ? p.value.toLocaleString() : p.value ?? "")
                : (p) => (p.value ?? ""),
        };
    });
}

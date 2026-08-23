// Shared display mappings + CTA/evidence helpers for Atlas Insights (InsightResult contract —
// see apps/dashboard/src/main/java/com/akto/service/insights/InsightResult.java). Nothing here
// computes a number; every value rendered comes straight from the API response.

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

function humanizeColumnKey(key) {
    const spaced = String(key || "").replace(/([a-z0-9])([A-Z])/g, "$1 $2");
    return spaced.charAt(0).toUpperCase() + spaced.slice(1).toLowerCase();
}

// Builds AgGridTable columnDefs from an Evidence.columns list, right-aligning any column whose
// values are numbers across the (already-bounded, <= EVIDENCE_ROW_CAP) row set.
export function buildEvidenceColumnDefs(evidence) {
    const columns = evidence?.columns || [];
    const rows = evidence?.rows || [];
    return columns.map((col) => {
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

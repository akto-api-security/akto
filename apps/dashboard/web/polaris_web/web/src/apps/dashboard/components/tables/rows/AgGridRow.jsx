import React from "react";
import { TYPE_STYLES } from "@/apps/dashboard/pages/observe/agentic/agenticStyles";

// ─── TypeBadge ────────────────────────────────────────────────────────────────

function TypeBadge({ type }) {
    if (!type) return null;
    const s = TYPE_STYLES[type] || { bg: "#F3F4F6", color: "#374151", border: "#E5E7EB" };
    // TYPE_STYLES colours are outside the Polaris Badge status set — custom span justified
    return (
        <span style={{
            display: "inline-flex", alignItems: "center",
            padding: "1px 7px", borderRadius: 12,
            fontSize: 11, fontWeight: 500, lineHeight: "18px",
            background: s.bg, color: s.color,
            border: `1px solid ${s.border}`,
            whiteSpace: "nowrap",
        }}>
            {type}
        </span>
    );
}

// ─── AgGridRow ────────────────────────────────────────────────────────────────
// Shared innerRenderer for AG Grid autoGroupColumnDef (tree / group rows).
// Equivalent to GithubRow for GithubSimpleTable.
//
// Props:
//   icon       — React node: <img src="/os-mac.svg" /> or a Polaris <Icon>
//   label      — Primary label text
//   typeBadge  — String type rendered as TypeBadge (uses TYPE_STYLES palette)
//   childCount — Shows a count pill when > 0 (group rows)
//   warning    — React node appended after label (e.g. personal-account icon)
//   isBold     — Renders label as semibold (default false)

export default function AgGridRow({ icon, label, typeBadge, childCount, warning, isBold = false }) {
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6, height: "100%", width: "100%", overflow: "hidden" }}>
            {icon && <span style={{ display: "flex", alignItems: "center", flexShrink: 0 }}>{icon}</span>}
            <span style={{
                fontSize: 12, fontWeight: isBold ? 600 : 400,
                color: "#202223", whiteSpace: "nowrap",
                overflow: "hidden", textOverflow: "ellipsis",
            }}>
                {label}
            </span>
            {typeBadge && <TypeBadge type={typeBadge} />}
            {childCount > 0 && (
                <span style={{
                    display: "inline-flex", alignItems: "center", justifyContent: "center",
                    minWidth: 20, height: 20, padding: "0 6px", borderRadius: 10,
                    fontSize: 11, fontWeight: 600, flexShrink: 0,
                    background: "#F1F2F3", color: "#6D7175",
                }}>
                    {childCount}
                </span>
            )}
            {warning}
        </div>
    );
}

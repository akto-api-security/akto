import React from "react";
import { Badge, Box } from "@shopify/polaris";
import "../../layouts/style.css";

// ─── TypeBadge ────────────────────────────────────────────────────────────────
// Polaris <Badge> coloured per asset type via the .agentic-type-<KEY> classes
// (components/layouts/style.css) — same convention as the dashboard .badge-wrapper-* classes.

const TYPE_CLASS = {
    "AI Agent": "agentic-type-AGENT",
    "MCP Server": "agentic-type-MCP",
    "LLM": "agentic-type-LLM",
    "Skill": "agentic-type-SKILL",
};

export function TypeBadge({ type }) {
    if (!type) return null;
    return (
        <Box as="span" className={TYPE_CLASS[type] || "agentic-type-DEFAULT"}>
            <Badge size="small">{type}</Badge>
        </Box>
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

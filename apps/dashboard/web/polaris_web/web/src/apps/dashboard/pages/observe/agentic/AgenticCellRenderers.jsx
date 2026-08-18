import React from "react";
import { Badge, Box, HorizontalStack, Text } from "@shopify/polaris";
import observeFunc from "../transform";
import { getRiskStatus } from "./agenticPageBuilders";
import AssetIcon from "./AssetIcon";
import { TypeBadge } from "@/apps/dashboard/components/tables/rows/AgGridRow";
import "../../../components/layouts/style.css";

// ─── Shared badges ────────────────────────────────────────────────────────────
// All badges are Polaris <Badge>. Where the colour isn't in Polaris' status set
// (asset type, risk gradient) we colour the Badge via a `.agentic-type-*` /
// `.badge-wrapper-*` class (style.css) — the same technique TestRunResultFlyout
// uses for severity. No inline CSS, no custom spans.
// TypeBadge lives in the shared AgGridRow component; re-exported here for the flyouts.
export { TypeBadge };

export function RiskPill({ score }) {
    if (!score) return null;
    return <Badge size="small" status={getRiskStatus(score)}>{score}</Badge>;
}

// Severity badge — same pattern as TestRunResultFlyout: a .badge-wrapper-<SEVERITY>
// wrapper colours the Polaris <Badge>, and status comes from observeFunc.getColor.
// `children` lets callers show a count instead of the severity label (violation pills).
export function SeverityBadge({ severity, children }) {
    const sev = String(severity || "").toUpperCase();
    if (!sev) return null;
    const label = children != null ? children : (sev.charAt(0) + sev.slice(1).toLowerCase());
    return (
        <Box as="span" className={`badge-wrapper-${sev}`}>
            <Badge size="small" status={observeFunc.getColor(sev)}>{label}</Badge>
        </Box>
    );
}

// ─── Shared schema / param cell renderers ─────────────────────────────────────
// Used by the MCP/Skill schema tables inside AgenticAssetFlyout.

export function ParamNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <Text variant="bodySm" fontWeight="semibold">{data.name}</Text>
            {data.required
                ? <Badge status="critical">required</Badge>
                : <Badge>optional</Badge>
            }
        </HorizontalStack>
    );
}

export function ParamTypeCellRenderer({ data }) {
    if (!data) return null;
    return <Badge status="info">{data.type}</Badge>;
}

export function ParamDescCellRenderer({ data }) {
    if (!data) return null;
    return (
        <Text variant="bodySm" color="subdued" truncate>
            {data.desc}
        </Text>
    );
}

// ─── Agentic Assets table renderers ───────────────────────────────────────────
// Extracted from AgenticAssetsPage to keep that page lean. Inline styles are the AG
// Grid cell-renderer exception (grid sandbox — Polaris tokens don't reach in).

export function AssetNameCellRenderer({ data }) {
    if (!data) return null;
    // Match old UI: personal-account + local-MCP tags for non-Skill rows; malicious tag for Skills
    const isSkill = data.type === "Skill";
    // Skill/Plugin rows fan out from the agent collection, so they'd inherit its markers otherwise.
    const isFanout = isSkill || data.type === "Plugin";
    const showLocalMcp = data.hasLocalMcpServer && !isFanout;
    const showPersonal = data.hasPersonalAccount && !isFanout;
    const showMalicious = data.isMalicious && isSkill;
    // Misconfigured is an Agent/MCP-server-only concept — Skill (and Plugin) rows never show it.
    const showMisconfigured = data.hasMisconfiguredConfig && !isFanout;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <AssetIcon type={data.type} assetTagValue={data.assetTagValue} size={24} />
            <Box width="100%" overflowX="hidden">
                <Text variant="bodySm" fontWeight="medium" truncate>{data.name}</Text>
            </Box>
            {showPersonal && <Badge size="small" status="warning">Contains personal account</Badge>}
            {showLocalMcp && <Badge size="small" status="critical">Local MCP Server</Badge>}
            {showMisconfigured && <Badge size="small" status="attention">Misconfigured</Badge>}
            {showMalicious && <Badge size="small" status="critical">Malicious</Badge>}
        </HorizontalStack>
    );
}

// type badge in its own column — used as both renderer and Set Filter display
export function TypeBadgeCellRenderer({ value }) {
    if (!value) return null;
    return <TypeBadge type={value} />;
}

// Plugin rows only — the parent/child relationship every other row (Agent -> MCP servers, in the
// flyout's tree) shows via nesting, but a plugin row IS the leaf: this column names its agent
// directly instead, since "Type" on a plugin row is always just "Plugin".
export function PluginAgentCellRenderer({ value }) {
    if (!value) return null;
    // Already formatted server-side (McpClientRegistry.formatDisplayName) — e.g. "Claude".
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <AssetIcon type="AI Agent" assetTagValue={value} size={20} />
            <Text variant="bodySm">{value}</Text>
        </HorizontalStack>
    );
}

export function RiskScoreCellRenderer({ value, data }) {
    if (value == null) return null;
    return observeFunc.wrapRiskScoreTooltip(<RiskPill score={value} />, value, data?.baseRiskScore, data?.baseRiskScoreReason);
}

export function ViolationsCellRenderer({ value }) {
    const dash = <Text variant="bodyMd" color="subdued">-</Text>;
    if (!value) return dash;
    const parts = ["critical", "high", "medium", "low"].filter((k) => value[k] > 0);
    if (!parts.length) return dash;
    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            {parts.map((k) => <SeverityBadge key={k} severity={k}>{value[k]}</SeverityBadge>)}
        </HorizontalStack>
    );
}

export function InteractionsCellRenderer({ value, data }) {
    if (value == null) return <Text variant="bodySm" color="subdued">-</Text>;
    const detail = data?.aiInteractionsDetail;
    const title = detail
        ? `Input: ${Number(detail.totalInputTokens || 0).toLocaleString("en-US")} · Output: ${Number(detail.totalOutputTokens || 0).toLocaleString("en-US")}`
        : undefined;
    return (
        <Box title={title}>
            <Text variant="bodySm">{Number(value).toLocaleString("en-US")}</Text>
        </Box>
    );
}


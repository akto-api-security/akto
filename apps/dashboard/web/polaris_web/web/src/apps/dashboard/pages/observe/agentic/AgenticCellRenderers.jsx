import React, { useState } from "react";
import { createPortal } from "react-dom";
import { Badge, Box, HorizontalStack, Text, Tooltip } from "@shopify/polaris";
import McpRedIcon from "@/assets/McpRedIcon.svg";
import PersonLockIcon from "@/assets/PersonLockIcon.svg";
import MaliciousSkillIcon from "@/assets/MaliciousSkill.svg";
import MisconfiguredConfigIcon from "@/assets/MisconfiguredConfigIcon.svg";
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


function MarkerIcon({ src, label, size = 16 }) {
    return (
        <Tooltip content={label} dismissOnMouseOut activatorWrapper="div">
            <img src={src} width={size} height={size} alt={label} style={{ flexShrink: 0, display: "block" }} />
        </Tooltip>
    );
}

export function AssetNameCellRenderer({ data }) {
    if (!data) return null;
    // Match old UI: personal-account + local-MCP markers for non-Skill rows; malicious marker for Skills
    const isSkill = data.type === "Skill";
    const showLocalMcp = data.hasLocalMcpServer && !isSkill;
    const showPersonal = data.hasPersonalAccount && !isSkill;
    const showMalicious = data.isMalicious && isSkill;
    const showMisconfigured = data.hasMisconfiguredConfig && !isSkill;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <AssetIcon type={data.type} assetTagValue={data.assetTagValue} size={24} />
            <Box width="100%" overflowX="hidden">
                <Text variant="bodySm" fontWeight="medium" truncate>{data.name}</Text>
            </Box>
            {showLocalMcp && <MarkerIcon src={McpRedIcon} label="Local MCP Server" size={24} />}
            {showPersonal && <MarkerIcon src={PersonLockIcon} label="Contains personal account" size={24} />}
            {showMisconfigured && <MarkerIcon src={MisconfiguredConfigIcon} label="Misconfigured config" size={24} />}
            {showMalicious && <MarkerIcon src={MaliciousSkillIcon} label="Malicious skill" size={24} />}
        </HorizontalStack>
    );
}

// type badge in its own column — used as both renderer and Set Filter display
export function TypeBadgeCellRenderer({ value }) {
    if (!value) return null;
    return <TypeBadge type={value} />;
}

export function RiskScoreCellRenderer({ value }) {
    if (value == null) return null;
    return <RiskPill score={value} />;
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

export function GroupCellRenderer({ data }) {
    const [tipPos, setTipPos] = useState(null);
    if (!data?.groups?.length) return <Text variant="bodySm" color="subdued">-</Text>;

    const primary = data.groups[0];
    const rest = data.groups.slice(1);

    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <Text variant="bodySm">{primary.name} [{primary.count}]</Text>
            {rest.length > 0 && (
                <>
                    {/* count chip — neutral palette pill, hover reveals a portal tooltip */}
                    <Box
                        as="span"
                        background="bg-strong"
                        borderRadius="4"
                        paddingInlineStart="2"
                        paddingInlineEnd="2"
                        onMouseEnter={(e) => {
                            const r = e.currentTarget.getBoundingClientRect();
                            setTipPos({ top: r.bottom + 6, left: r.left });
                        }}
                        onMouseLeave={() => setTipPos(null)}
                    >
                        <Text variant="bodySm" color="subdued" fontWeight="semibold">+{rest.length}</Text>
                    </Box>
                    {/* createPortal renders into document.body — escapes AG Grid's overflow:hidden */}
                    {tipPos && createPortal(
                        <Box
                            position="fixed"
                            background="bg"
                            borderColor="border"
                            borderWidth="1"
                            borderRadius="2"
                            padding="2"
                            shadow="lg"
                            zIndex="9999"
                            style={{ top: tipPos.top, left: tipPos.left, whiteSpace: "nowrap", pointerEvents: "none" }}
                        >
                            {rest.map((g) => (
                                <Text key={g.name} variant="bodySm">{g.name} [{g.count}]</Text>
                            ))}
                        </Box>,
                        document.body,
                    )}
                </>
            )}
        </HorizontalStack>
    );
}

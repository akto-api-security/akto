import { useEffect, useState } from "react";
import { Text, VerticalStack } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import { CLIENT_TYPES } from "../../observe/agentic/mcpClientHelper";
import { formatAssetDisplayName } from "../violations/ViolationsPage";
import ItemListCard from "@/apps/dashboard/components/shared/ItemListCard";
import { OverviewLoadError } from "./OverviewCard";
import { formatCount, plural } from "./atlasOverviewUtils";

const ASSET_TYPE_LABELS = {
    [CLIENT_TYPES.LLM]: "Browser AI apps",
    [CLIENT_TYPES.AI_AGENT]: "AI agents & CLIs",
    [CLIENT_TYPES.MCP_SERVER]: "MCP servers",
    [CLIENT_TYPES.SKILL]: "Skills",
    [CLIENT_TYPES.PLUGIN]: "Plugins",
    [CLIENT_TYPES.SAAS_AGENT]: "SaaS agents",
    Other: "Others",
};
const TREND_UP_COLOR = "#8E1F0B";
const TREND_DOWN_COLOR = "#0C5132";

function formatChange(pct) {
    if (pct === 0) return "0%";
    return pct > 0 ? `+${pct}%` : `−${Math.abs(pct)}%`;
}

function buildRows(assetTypes, periodDays, onOpenActivity) {
    return (assetTypes || [])
        .map((s) => {
            const examples = s.type === CLIENT_TYPES.MCP_SERVER
                ? (s.servers ? `${plural(s.servers, "server", "servers")} on ${plural(s.devices, "device", "devices")} · ${formatCount(s.unapproved)} unapproved` : "")
                : [...new Set((s.examples || []).map((x) => x.name || formatAssetDisplayName(x.host)).filter(Boolean))].join(", ");
            return {
                id: s.type,
                title: ASSET_TYPE_LABELS[s.type] || s.type,
                description: examples || undefined,
                count: s.count,
                onClick: () => onOpenActivity(),
                tag: (
                    <VerticalStack gap="0" inlineAlign="end">
                        <Text variant="bodyMd" fontWeight="semibold">{formatCount(s.count)}</Text>
                        {s.changePct !== undefined && s.changePct !== null && (
                            <Text variant="bodySm" color={s.changePct === 0 ? "subdued" : undefined}>
                                <span style={{ whiteSpace: "nowrap", ...(s.changePct === 0 ? {} : { color: s.changePct > 0 ? TREND_UP_COLOR : TREND_DOWN_COLOR }) }}>
                                    {`${formatChange(s.changePct)} vs previous ${periodDays} ${periodDays === 1 ? "day" : "days"}`}
                                </span>
                            </Text>
                        )}
                    </VerticalStack>
                ),
            };
        })
        .sort((a, b) => b.count - a.count);
}

export default function ViolationsSourcesCard({ data, pageSize, onOpenActivity }) {
    const [expanded, setExpanded] = useState(false);
    useEffect(() => setExpanded(false), [data, pageSize]);
    const rows = buildRows(data?.assetTypes, data?.periodDays, onOpenActivity);
    return (
        <CardWithHeader title="Where violations happen" hasData>
            {data === null ? <SpinnerCentered height="200px" />
                : data?.failed ? <OverviewLoadError />
                : <ItemListCard
                    items={rows}
                    limit={expanded ? undefined : pageSize}
                    onViewAll={!expanded && rows.length > pageSize ? () => setExpanded(true) : undefined}
                    onViewLess={expanded ? () => setExpanded(false) : undefined}
                    emptyText="No violations in this period."
                />}
        </CardWithHeader>
    );
}

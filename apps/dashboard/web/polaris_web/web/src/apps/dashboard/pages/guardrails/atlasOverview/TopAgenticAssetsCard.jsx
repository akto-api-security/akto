import { useEffect, useState } from "react";
import { HorizontalStack, Text } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import AssetTagBadge from "@/apps/dashboard/components/shared/AssetTagBadge";
import { CLIENT_TYPES } from "../../observe/agentic/mcpClientHelper";
import { ACTIVITY_TAB, SKILL_URL_PREFIX } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";
import ItemListCard from "@/apps/dashboard/components/shared/ItemListCard";
import { formatCount, plural } from "./atlasOverviewUtils";

const AGENTIC_ASSET_KINDS = {
    [CLIENT_TYPES.LLM]: "Browser AI app",
    [CLIENT_TYPES.AI_AGENT]: "AI agent CLI",
    [CLIENT_TYPES.SAAS_AGENT]: "SaaS agent",
    [CLIENT_TYPES.MCP_SERVER]: "MCP server",
    [CLIENT_TYPES.SKILL]: "Skill",
};
const AGENTIC_ASSET_STATUS_TAGS = {
    APPROVED: "Approved",
    BLOCKED: "Blocked",
    UNDER_REVIEW: "Under review",
    UNAPPROVED: "Unapproved",
};

function assetFilters(asset) {
    if (asset.type === CLIENT_TYPES.SKILL) return { eventStatus: ACTIVITY_TAB.SKILLS_EVALUATIONS, url: `${SKILL_URL_PREFIX}${asset.name}` };
    if (asset.host) return { host: asset.host };
    return { atlasTool: asset.key, atlasToolName: asset.name };
}

function buildRows(assets, onOpenActivity) {
    return (assets || []).map((a) => ({
        id: a.key,
        title: a.name,
        onClick: () => onOpenActivity(assetFilters(a)),
        description: [AGENTIC_ASSET_KINDS[a.type] || a.type, plural(a.people, "person", "people"), plural(a.devices, "device", "devices")].join(" · "),
        tag: (
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                {a.personal && <AssetTagBadge tag="Personal" />}
                <AssetTagBadge tag={AGENTIC_ASSET_STATUS_TAGS[a.status]} />
                <Text variant="bodyMd" fontWeight="semibold">{formatCount(a.count)}</Text>
            </HorizontalStack>
        ),
    }));
}

export default function TopAgenticAssetsCard({ data, pageSize, onOpenActivity }) {
    const [expanded, setExpanded] = useState(false);
    useEffect(() => setExpanded(false), [data, pageSize]);
    const rows = buildRows(data?.assets, onOpenActivity);
    return (
        <CardWithHeader title="Top agents, apps and servers involved" hasData>
            {data === null ? <SpinnerCentered height="200px" />
                : <ItemListCard
                    items={rows}
                    limit={expanded ? undefined : pageSize}
                    onViewAll={!expanded && rows.length > pageSize ? () => setExpanded(true) : undefined}
                    onViewLess={expanded ? () => setExpanded(false) : undefined}
                    emptyText="No agents, apps or servers involved in this period."
                />}
        </CardWithHeader>
    );
}

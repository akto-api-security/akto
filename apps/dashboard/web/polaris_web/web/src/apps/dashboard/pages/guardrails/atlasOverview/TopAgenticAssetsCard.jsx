import { HorizontalStack, Text } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import ItemListCard from "@/apps/dashboard/components/shared/ItemListCard";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import AssetTagBadge from "@/apps/dashboard/components/shared/AssetTagBadge";
import { CLIENT_TYPES } from "../../observe/agentic/mcpClientHelper";
import { formatCount, plural } from "./atlasOverviewUtils";

const TOP_AGENTIC_ASSETS = 5;
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

function buildRows(assets, onOpenActivity) {
    return (assets || []).map((a) => ({
        id: a.key,
        title: a.name,
        onClick: onOpenActivity,
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

export default function TopAgenticAssetsCard({ data, onOpenActivity }) {
    return (
        <CardWithHeader title="Top agents, apps and servers involved" hasData>
            {data === null ? <SpinnerCentered height="200px" />
                : <ItemListCard items={buildRows(data?.assets?.slice(0, TOP_AGENTIC_ASSETS), onOpenActivity)} emptyText="No agents, apps or servers involved in this period." />}
        </CardWithHeader>
    );
}

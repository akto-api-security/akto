import React from "react";
import { HorizontalStack, Box, Text, Badge } from "@shopify/polaris";
import { TYPE_STYLES } from "@/apps/dashboard/pages/observe/agentic/agenticStyles";

const TONE_MAP = {
    "#F0FDF4": "success",
    "#FFF7ED": "warning",
    "#FEF2F2": "critical",
    "#EFF6FF": "info",
    "#F5F3FF": "new",
};

function TypeBadge({ type }) {
    if (!type) return null;
    const s = TYPE_STYLES[type] || { bg: "#F3F4F6", color: "#374151", border: "#E5E7EB" };
    const tone = TONE_MAP[s.bg] || "new";
    return <Badge tone={tone}>{type}</Badge>;
}

export default function AgGridRow({ icon, label, typeBadge, childCount, warning, isBold = false }) {
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            {icon && <Box>{icon}</Box>}
            <Text
                as="span"
                variant="bodySm"
                fontWeight={isBold ? "semibold" : "regular"}
                truncate
            >
                {label}
            </Text>
            {typeBadge && <TypeBadge type={typeBadge} />}
            {childCount > 0 && (
                <Badge tone="new">{String(childCount)}</Badge>
            )}
            {warning}
        </HorizontalStack>
    );
}

import React from "react";
import { Badge, HorizontalStack, Text } from "@shopify/polaris";

// ─── Shared schema / param cell renderers ─────────────────────────────────────
// Used by both SkillsFlyout and McpFlyout — keep in sync with their column defs.

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

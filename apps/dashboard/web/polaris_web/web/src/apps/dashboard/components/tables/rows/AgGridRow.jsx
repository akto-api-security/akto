import React from "react";
import { HorizontalStack, Box, Text, Badge } from "@shopify/polaris";

export function TypeBadge({ type, tone = "new" }) {
    if (!type) return null;
    return <Badge tone={tone}>{type}</Badge>;
}

export function AgGridRowRenderer(params) {
    // AG Grid auto-merges cellRendererParams into params
    const label      = params.getLabel      ? params.getLabel(params)      : params.value;
    const icon       = params.getIcon       ? params.getIcon(params)       : undefined;
    const typeBadge  = params.getTypeBadge  ? params.getTypeBadge(params)  : undefined;
    const childCount = params.getChildCount ? params.getChildCount(params) : undefined;
    const warning    = params.getWarning    ? params.getWarning(params)    : undefined;
    const isBold     = params.isBold        ?? false;
    const typeBadgeTone = params.typeBadgeTone ?? "new";
    return <AgGridRow icon={icon} label={label} typeBadge={typeBadge} typeBadgeTone={typeBadgeTone} childCount={childCount} warning={warning} isBold={isBold} />;
}

export default function AgGridRow({ icon, label, typeBadge, typeBadgeTone = "new", childCount, warning, isBold = false }) {
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
            {typeBadge && <TypeBadge type={typeBadge} tone={typeBadgeTone} />}
            {childCount > 0 && (
                <Badge tone="new">{String(childCount)}</Badge>
            )}
            {warning}
        </HorizontalStack>
    );
}
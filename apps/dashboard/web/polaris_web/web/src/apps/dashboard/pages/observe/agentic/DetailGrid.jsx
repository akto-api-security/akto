import React from "react";
import { Box, HorizontalGrid, HorizontalStack, VerticalStack, Text, Tooltip } from "@shopify/polaris";

export default function DetailGrid({ heading, items = [], columns = 1, labelWidth = "140px" }) {
    const cell = (d) => {
        const textNode = (
            <Text variant="bodySm" fontWeight="semibold" color={d.isWarning ? "warning" : undefined} truncate>
                {d.value || "-"}
            </Text>
        );
        if (d.tooltip) {
            return <Tooltip content={d.tooltip} dismissOnMouseOut preferredPosition="above">{textNode}</Tooltip>;
        }
        return textNode;
    };

    return (
        <VerticalStack gap={columns > 1 ? "3" : "2"}>
            {heading && <Text variant="headingXs" color="subdued">{heading}</Text>}
            {columns > 1 ? (
                <HorizontalGrid columns={columns} gap="3">
                    {items.map((d) => (
                        <VerticalStack gap="1" key={d.label}>
                            <Text variant="bodySm" color="subdued">{d.label}</Text>
                            {cell(d)}
                        </VerticalStack>
                    ))}
                </HorizontalGrid>
            ) : (
                items.map((d) => (
                    <HorizontalStack key={d.label} gap="4" blockAlign="center">
                        <Box minWidth={labelWidth}><Text variant="bodySm" color="subdued">{d.label}</Text></Box>
                        {cell(d)}
                    </HorizontalStack>
                ))
            )}
        </VerticalStack>
    );
}

import React from "react";
import { Box, HorizontalGrid, HorizontalStack, Link, VerticalStack, Text } from "@shopify/polaris";
import InfoTooltipIcon from "@/apps/dashboard/components/shared/InfoTooltipIcon";
import TooltipText from "../../../components/shared/TooltipText";

export default function DetailGrid({ heading, items = [], columns = 1, labelWidth = "140px" }) {
    const renderLabel = (d) => (
        <HorizontalStack gap="1" blockAlign="center">
            <Text variant="bodySm" color="subdued">{d.label}</Text>
            <InfoTooltipIcon content={d.tooltip} />
        </HorizontalStack>
    );

    const cell = (d) => {
        const text = d.value || "-"
        const display = <TooltipText tooltip={text} text={text} />
        const content = (
            <Box maxWidth="150px">
                <Text variant="bodySm" fontWeight="semibold" color={d.isWarning ? "warning" : undefined}>
                    {d.href && d.value ? <Link url={d.href} external={d.external !== false}>{display}</Link> : display}
                </Text>
            </Box>
        );
        return content;
    };

    return (
        <VerticalStack gap={columns > 1 ? "3" : "2"}>
            {heading && <Text variant="headingXs" color="subdued">{heading}</Text>}
            {columns > 1 ? (
                <HorizontalGrid columns={columns} gap="3">
                    {items.map((d) => (
                        <VerticalStack gap="1" key={d.label}>
                            {renderLabel(d)}
                            {cell(d)}
                        </VerticalStack>
                    ))}
                </HorizontalGrid>
            ) : (
                items.map((d) => (
                    <HorizontalStack key={d.label} gap="4" blockAlign="center">
                        <Box minWidth={labelWidth}>{renderLabel(d)}</Box>
                        {cell(d)}
                    </HorizontalStack>
                ))
            )}
        </VerticalStack>
    );
}

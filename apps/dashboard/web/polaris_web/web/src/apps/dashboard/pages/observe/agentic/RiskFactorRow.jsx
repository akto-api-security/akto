import React from "react";
import { Box, HorizontalStack, VerticalStack, Text, Badge, Icon } from "@shopify/polaris";
import { ChevronRightMinor } from "@shopify/polaris-icons";

export function RiskFactorRow({ factor, onClick }) {
    const badgeStatus = factor.severity === "critical" ? "critical" : factor.severity === "high" ? "warning" : factor.severity === "medium" ? "attention" : "info";
    return (
        <Box
            onClick={onClick}
            paddingBlockStart="3"
            paddingBlockEnd="3"
            paddingInlineStart="3"
            paddingInlineEnd="3"
            borderRadius="2"
            className="agentic-clickable-row"
        >
            <HorizontalStack gap="4" align="start" blockAlign="start" wrap={false}>
                <Box width="72px">
                    <Badge status={badgeStatus}>{factor.severity.charAt(0).toUpperCase() + factor.severity.slice(1)}</Badge>
                </Box>
                <Box width="100%">
                    <VerticalStack gap="1">
                        <Text variant="bodySm" fontWeight="semibold">{factor.title}</Text>
                        <Text variant="bodySm" color="subdued">{factor.description}</Text>
                    </VerticalStack>
                </Box>
                <Box>
                    <Icon source={ChevronRightMinor} color="subdued" />
                </Box>
            </HorizontalStack>
        </Box>
    );
}

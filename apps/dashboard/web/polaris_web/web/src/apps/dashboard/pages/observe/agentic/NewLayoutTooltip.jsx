import React from "react";
import { Box, Text, Tooltip, Checkbox, Badge, HorizontalStack } from "@shopify/polaris";
import { NEW_LAYOUT_TOOLTIP } from "./constants";
export default function NewLayoutTooltip({ checked, onChange }) {

    const label = (
        <HorizontalStack gap="1">
            <Text as="span" variant="bodyMd" fontWeight="bold">New Layout</Text>
            <Badge status="info">Beta</Badge>
        </HorizontalStack>
    );
    const checkbox = <Checkbox label={label} checked={checked} onChange={onChange} />;

    if (checked) return checkbox;

    return (
        <Tooltip
            preferredPosition="below"
            dismissOnMouseOut
            content={
                <Box width="200px" padding="1">
                    <Text variant="bodySm">{NEW_LAYOUT_TOOLTIP}</Text>
                </Box>
            }
        >
            {checkbox}
        </Tooltip>
    );
}

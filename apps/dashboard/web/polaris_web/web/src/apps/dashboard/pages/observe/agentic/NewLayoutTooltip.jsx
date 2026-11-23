import React from "react";
import { Box, Text, Tooltip, Checkbox, Badge, HorizontalStack } from "@shopify/polaris";
import { NEW_LAYOUT_TOOLTIP, OLD_LAYOUT_TOOLTIP } from "./constants";

// Callers always pass/receive "new layout" semantics (`checked` = new layout is on).
// Default: a "New Layout (Beta)" checkbox. With `invert`: an "Old Layout" checkbox (new layout is the default).
export default function NewLayoutTooltip({ checked: newLayout, onChange: onNewLayoutChange, invert = false }) {
    const checked = invert ? !newLayout : newLayout;
    const onChange = invert ? (oldLayout) => onNewLayoutChange(!oldLayout) : onNewLayoutChange;

    const label = (
        <HorizontalStack gap="1">
            <Text as="span" variant="bodyMd" fontWeight="bold">{invert ? "Old Layout" : "New Layout"}</Text>
            {!invert && <Badge status="info">Beta</Badge>}
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
                    <Text variant="bodySm">{invert ? OLD_LAYOUT_TOOLTIP : NEW_LAYOUT_TOOLTIP}</Text>
                </Box>
            }
        >
            {checkbox}
        </Tooltip>
    );
}

import React from "react";
import { Box, Text, Tooltip, Checkbox } from "@shopify/polaris";
import { NEW_LAYOUT_TOOLTIP } from "./constants";
import func from "@/util/func";

export default function NewLayoutTooltip({ checked, onChange }) {
    if (!func.isDemoAccount()) return null;

    const checkbox = <Checkbox label="New Layout" checked={checked} onChange={onChange} />;

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

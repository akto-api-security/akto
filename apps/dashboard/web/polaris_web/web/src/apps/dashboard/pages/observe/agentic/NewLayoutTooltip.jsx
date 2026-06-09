import React from "react";
import { Box, Text, Tooltip } from "@shopify/polaris";
import { NEW_LAYOUT_TOOLTIP } from "./constants";

// Shared "New Layout" checkbox tooltip — renders below the target, fixed width, adaptive height.
export default function NewLayoutTooltip({ children }) {
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
            {children}
        </Tooltip>
    );
}

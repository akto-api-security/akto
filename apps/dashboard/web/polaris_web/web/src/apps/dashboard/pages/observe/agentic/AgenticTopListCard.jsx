import React from "react";
import { Box, Button, Card, DataTable, HorizontalStack, Text } from "@shopify/polaris";
import AssetIcon from "./AssetIcon";

export default function AgenticTopListCard({ title, columns = [], rows = [], emptyStateText = "No data available", renderIcon, activeRows, onClearSelection }) {
    const headings = columns.map((c) => c.label);

    // renderIcon(row) overrides the default AssetIcon (e.g. a user icon, or null for no icon).
    const iconFor = (row) => (renderIcon ? renderIcon(row) : <AssetIcon type={row.type} assetTagValue={row.assetTagValue} />);

    const tableRows = rows.length > 0
        ? rows.map((row) => {
            const isActive = activeRows?.has(row.name) ?? false;
            return [
                <Box key={`name-${row.id}`} className="agentic-list-cell-click" data-active={isActive} onClick={() => row.onClick?.(row)}>
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        {iconFor(row)}
                        <Box className="agentic-name-fill">
                            <Text variant="bodyMd" as="span" truncate>{row.name}</Text>
                        </Box>
                    </HorizontalStack>
                </Box>,
                <Box key={`val-${row.id}`} className="agentic-list-cell-click" data-active={isActive} onClick={() => row.onClick?.(row)}>
                    {row.renderValue(row)}
                </Box>,
            ];
        })
        : [[
            <Text key="empty" variant="bodySm" color="subdued">{emptyStateText}</Text>,
            <Box key="empty-val" />,
        ]];

    const hasSelection = (activeRows?.size ?? 0) > 0;

    return (
        <Card padding="0">
            <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingSm">{title}</Text>
                    {hasSelection && onClearSelection && (
                        <Button plain onClick={onClearSelection}>Clear selection</Button>
                    )}
                </HorizontalStack>
            </Box>
            <Box className="agentic-list-table">
                <DataTable
                    columnContentTypes={["text", "numeric"]}
                    headings={headings}
                    rows={tableRows}
                    increasedTableDensity
                    hoverable
                    verticalAlign="middle"
                />
            </Box>
        </Card>
    );
}

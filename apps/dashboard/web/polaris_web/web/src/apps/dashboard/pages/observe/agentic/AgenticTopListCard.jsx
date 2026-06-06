import React from "react";
import { Box, Card, DataTable, HorizontalStack, Text } from "@shopify/polaris";
import AssetIcon from "./AssetIcon";

export default function AgenticTopListCard({ title, columns = [], rows = [], emptyStateText = "No data available" }) {
    const headings = columns.map((c) => c.label);

    const tableRows = rows.length > 0
        ? rows.map((row) => [
            <Box key={`name-${row.id}`} onClick={() => row.onClick?.(row)}>
                <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                    <AssetIcon type={row.type} assetTagValue={row.assetTagValue} />
                    <Box minWidth="0" overflowX="hidden">
                        <Text variant="bodySm" as="span" truncate>{row.name}</Text>
                    </Box>
                </HorizontalStack>
            </Box>,
            <Box key={`val-${row.id}`} onClick={() => row.onClick?.(row)}>
                {row.renderValue(row)}
            </Box>,
        ])
        : [[
            <Text key="empty" variant="bodySm" color="subdued">{emptyStateText}</Text>,
            <Box key="empty-val" />,
        ]];

    return (
        <Card padding="0">
            <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3">
                <Text variant="headingSm">{title}</Text>
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

import { Badge, Box, HorizontalStack, Text } from "@shopify/polaris";
import { SeverityBadge } from "../../agentic/AgenticCellRenderers";
import { MethodBox } from "../../GetPrettifyEndpoint";
import observeFunc from "../../transform";

const CENTERED = { display: "flex", alignItems: "center" };

function PolicyCellRenderer({ data }) {
    if (!data) return null;
    return (
        <Box width="100%" overflowX="hidden">
            <Text variant="bodySm" fontWeight="semibold" truncate>{data.title}</Text>
        </Box>
    );
}

function DetectedCellRenderer({ value }) {
    return <Text variant="bodySm" color={value === "-" ? "subdued" : undefined} truncate>{value}</Text>;
}

function ActionCellRenderer({ value }) {
    if (!value) return <Text variant="bodySm" color="subdued">-</Text>;
    const blocked = value.toLowerCase() === "block";
    return <Badge size="small" status={blocked ? "critical" : "info"}>{blocked ? "Blocked" : "Alert only"}</Badge>;
}

function TargetCellRenderer({ data }) {
    if (!data) return null;
    if (!data.url) return <Text variant="bodySm" color="subdued">-</Text>;
    return (
        <Box width="100%" overflowX="hidden">
            <HorizontalStack gap="1" wrap={false} blockAlign="center">
                <MethodBox method={data.method} url={data.url} />
                <Box minWidth="0" width="100%">
                    <Text variant="bodyMd" fontWeight="medium" truncate>{observeFunc.getTruncatedUrl(data.url)}</Text>
                </Box>
            </HorizontalStack>
        </Box>
    );
}

function SeverityCellRenderer({ data }) {
    return data ? <SeverityBadge severity={data.severity} /> : null;
}

const detectedValue = (params) => {
    const rule = params.data?.ruleViolated;
    return rule && rule !== "-" ? rule : (params.data?.subCategory || "-");
};

export const ATLAS_VIOLATION_COLUMNS = [
    { field: "time", headerName: "Time", width: 140, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { ...CENTERED, fontSize: 12, color: "#6D7175" } },
    { field: "title", headerName: "Policy", flex: 1.2, minWidth: 110, sortable: false, cellRenderer: PolicyCellRenderer, cellStyle: CENTERED },
    { field: "ruleViolated", headerName: "Detected", flex: 0.9, minWidth: 90, sortable: false, valueGetter: detectedValue, cellRenderer: DetectedCellRenderer, cellStyle: CENTERED },
    { field: "severity", headerName: "Severity", width: 95, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: SeverityCellRenderer, cellStyle: CENTERED },
    { field: "behaviour", headerName: "Action", width: 100, sortable: false, cellRenderer: ActionCellRenderer, cellStyle: CENTERED },
    { field: "url", headerName: "Target", flex: 2.2, minWidth: 150, sortable: false, cellRenderer: TargetCellRenderer, cellStyle: CENTERED },
];

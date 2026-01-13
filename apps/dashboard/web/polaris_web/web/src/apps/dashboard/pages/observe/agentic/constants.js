import { Text } from "@shopify/polaris";
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";
import { ASSET_TAG_KEYS } from "./mcpClientHelper";

// Route constants
export const UNKNOWN_GROUP = "Unknown";
export const INVENTORY_PATH = '/dashboard/observe/inventory';
export const INVENTORY_FILTER_KEY = '/dashboard/observe/inventory/';

// All asset tag keys that can be used for grouping (cached)
export const ASSET_TAG_KEY_VALUES = Object.values(ASSET_TAG_KEYS);

// Table headers configuration
export const getHeaders = () => {
    const category = getDashboardCategory();
    return [
        {
            title: "",
            text: "",
            value: "iconComp",
            isText: CellType.TEXT,
            boxWidth: '24px'
        },
        {
            title: "Agentic asset",
            text: "Agentic asset",
            value: "groupName",
            filterKey: "groupName",
            textValue: "groupName",
            showFilter: true,
        },
        {
            title: "Type",
            text: "Type",
            value: "clientType",
            filterKey: "clientType",
            textValue: "clientType",
            showFilter: true,
            boxWidth: "120px",
        },
        {
            title: "Endpoints",
            text: "Endpoints",
            value: "collectionsCount",
            isText: CellType.TEXT,
            sortActive: true,
            boxWidth: "80px",
        },
        {
            title: mapLabel("Total endpoints", category),
            text: mapLabel("Total endpoints", category),
            value: "urlsCount",
            isText: CellType.TEXT,
            sortActive: true,
            boxWidth: "80px",
            filterKey: "urlsCount",
            showFilter: true,
        },
        {
            title: <HeadingWithTooltip content={<Text variant="bodySm">Maximum risk score of the endpoints in this group</Text>} title="Risk score" />,
            value: "riskScoreComp",
            textValue: "riskScore",
            numericValue: "riskScore",
            text: "Risk Score",
            sortActive: true,
            boxWidth: "80px",
        },
        {
            title: "Sensitive data",
            text: "Sensitive data",
            value: "sensitiveSubTypes",
            numericValue: "sensitiveInRespTypes",
            textValue: "sensitiveSubTypesVal",
            tooltipContent: <Text variant="bodySm">Types of data present in response of endpoints in this group</Text>,
            boxWidth: "160px",
        },
        {
            title: <HeadingWithTooltip content={<Text variant="bodySm">The most recent time an endpoint in this group was either discovered or seen again</Text>} title="Last traffic seen" />,
            text: "Last traffic seen",
            value: "lastTraffic",
            numericValue: "detectedTimestamp",
            isText: CellType.TEXT,
            sortActive: true,
            boxWidth: "80px",
        },
    ];
};

// Sort options for the table
export const sortOptions = [
    { label: "Endpoints", value: "urlsCount asc", directionLabel: "More", sortKey: "urlsCount", columnIndex: 1 },
    { label: "Endpoints", value: "urlsCount desc", directionLabel: "Less", sortKey: "urlsCount", columnIndex: 1 },
    { label: "Risk Score", value: "score asc", directionLabel: "High risk", sortKey: "riskScore", columnIndex: 2 },
    { label: "Risk Score", value: "score desc", directionLabel: "Low risk", sortKey: "riskScore", columnIndex: 2 },
    { label: "Collections", value: "collectionsCount asc", directionLabel: "More", sortKey: "collectionsCount", columnIndex: 3 },
    { label: "Collections", value: "collectionsCount desc", directionLabel: "Less", sortKey: "collectionsCount", columnIndex: 3 },
    { label: "Last traffic seen", value: "detected asc", directionLabel: "Recent first", sortKey: "detectedTimestamp", columnIndex: 5 },
    { label: "Last traffic seen", value: "detected desc", directionLabel: "Oldest first", sortKey: "detectedTimestamp", columnIndex: 5 },
];

// Resource name for the table
export const resourceName = {
    singular: "Agentic asset",
    plural: "Agentic assets",
};

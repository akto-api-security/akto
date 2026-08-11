import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { Badge, Box, HorizontalStack, Text, DataTable } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "@/apps/dashboard/components/tables/GithubServerTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip";
import TooltipText from "../../../components/shared/TooltipText";
import api from "../api";
import func from "@/util/func";
import transform from "../transform";
import PersistStore from "../../../../main/PersistStore";
import { fetchAndCacheAgenticTrafficRiskBundle, fetchAndCacheAgenticSensitiveInfo } from "./constants";
import { fetchEndpointShieldUsernameMap } from "../api_collections/endpointShieldHelper";

/**
 * Endpoint/device-wise view for ONE agentic asset — what clicking a row on the legacy Agentic
 * Assets page opens instead of navigating to Inventory (which needed the whole account's
 * collections just to build a filter). Matches AgentEndpointTreeTable.jsx's expandable
 * device -> children UI (used by Inventory's own agent-tree mode) field-for-field, but sourced
 * from fetchAgenticAssetEndpointsPage — server-paginated at the DEVICE level and scoped to just
 * this asset's own collectionIds, never account-wide (AgentEndpointTreeTable itself is fed by
 * ApiCollections.jsx's full getAllCollectionsBasic() fetch, which is exactly what this page exists
 * to avoid). Old-layout look (PageWithMultipleCards + GithubServerTable, standard back arrow),
 * not a flyout, per explicit direction.
 */

const CHILD_COL_WIDTH = { name: "200px", riskScore: "80px", sensitive: "160px", traffic: "80px", discovered: "80px" };

const PARENT_HEADERS = [
    { title: "", text: "", value: "collapsibleIcon", type: CellType.COLLAPSIBLE, boxWidth: "32px" },
    { title: "Endpoint ID", text: "Endpoint ID", value: "displayNameComp", textValue: "endpointId" },
    { title: "Username", text: "Username", value: "usernameComp", textValue: "username", boxWidth: "100px" },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Risk score of this device is the maximum risk score across its own collections</Text>} title="Risk score" />,
        text: "Risk score", value: "riskScoreComp", textValue: "riskScore", numericValue: "riskScore",
        sortActive: true, boxWidth: "80px",
    },
    {
        title: "Sensitive data", text: "Sensitive data", value: "sensitiveSubTypes",
        tooltipContent: <Text variant="bodySm">Types of sensitive data seen across this device's own collections</Text>,
        boxWidth: "160px",
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">The most recent time this device's traffic was seen</Text>} title="Last traffic seen" />,
        text: "Last traffic seen", value: "lastTraffic", numericValue: "lastSeenEpoch", sortActive: true, boxWidth: "80px",
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Time when this device was first discovered</Text>} title="Discovered" />,
        text: "Discovered", value: "discovered", numericValue: "startTs", sortActive: true, boxWidth: "80px",
    },
    { title: "Endpoint tags", text: "Endpoint tags", value: "endpointTagsComp", boxWidth: "160px" },
];

const SORT_OPTIONS = [
    { label: "Endpoint ID", value: "endpointId asc", directionLabel: "A-Z", sortKey: "endpointId", columnIndex: 2 },
    { label: "Endpoint ID", value: "endpointId desc", directionLabel: "Z-A", sortKey: "endpointId", columnIndex: 2 },
    { label: "Username", value: "username asc", directionLabel: "A-Z", sortKey: "username", columnIndex: 3 },
    { label: "Username", value: "username desc", directionLabel: "Z-A", sortKey: "username", columnIndex: 3 },
    { label: "Risk score", value: "riskScore desc", directionLabel: "Highest", sortKey: "riskScore", columnIndex: 4 },
    { label: "Risk score", value: "riskScore asc", directionLabel: "Lowest", sortKey: "riskScore", columnIndex: 4 },
    { label: "Last traffic seen", value: "lastSeenEpoch desc", directionLabel: "Newest", sortKey: "lastSeenEpoch", columnIndex: 6 },
    { label: "Last traffic seen", value: "lastSeenEpoch asc", directionLabel: "Oldest", sortKey: "lastSeenEpoch", columnIndex: 6 },
    { label: "Discovered", value: "startTs desc", directionLabel: "Newest", sortKey: "startTs", columnIndex: 7 },
    { label: "Discovered", value: "startTs asc", directionLabel: "Oldest", sortKey: "startTs", columnIndex: 7 },
];

const FILTERS_DEF = [
    { key: "endpointTags", label: "Endpoint tags", choices: [
        { label: "Contains personal account", value: "Contains personal account" },
        { label: "Local MCP Server", value: "Local MCP Server" },
        { label: "Misconfigured", value: "Misconfigured" },
        { label: "Malicious Skills", value: "Malicious Skills" },
    ] },
];

const resourceName = { singular: "endpoint", plural: "endpoints" };

// child.name is already the field the server picked (serviceName for agent/skill rows, the
// calling source's id for service/llm rows — see AgenticObserveAction.groupCollectionsByEndpointId's
// useServiceName branch) — no per-type column title to pick here, matching AgentEndpointTreeTable's
// own ChildrenTable, which never renders its childHeaders' titles either (headings=[] throughout,
// the parent GithubRow header row is the only visible header).
function ChildrenTable({ children, rowType, misconfiguredChildId }) {
    const navigate = useNavigate();

    const handleChildClick = useCallback((child) => {
        const bundlesSkills = (rowType === "agent" || rowType === "service") && (child.skillCount || 0) > 0;
        const scope = bundlesSkills ? "?agentic_view=skills" : "";
        navigate(`/dashboard/observe/inventory/${child.id}${scope}`);
    }, [navigate, rowType]);

    const handleConfigClick = useCallback(() => {
        if (!misconfiguredChildId) return;
        navigate(`/dashboard/observe/inventory/${misconfiguredChildId}?agentic_view=config`);
    }, [navigate, misconfiguredChildId]);

    const configRow = useMemo(() => {
        if (!misconfiguredChildId) return null;
        return [
            <div key="spacer-config" style={{ width: "32px", minWidth: "32px" }} />,
            <div key="name-config" style={{ cursor: "pointer", width: CHILD_COL_WIDTH.name }} onClick={handleConfigClick}>
                <HorizontalStack gap="1" align="start" wrap={false}>
                    <Text variant="bodyMd" as="span">config</Text>
                    <Badge size="small" status="attention">Misconfigured</Badge>
                </HorizontalStack>
            </div>,
            <div key="config-empty-risk" style={{ cursor: "pointer", width: CHILD_COL_WIDTH.riskScore }} onClick={handleConfigClick} />,
            <div key="config-empty-sensitive" style={{ cursor: "pointer", width: CHILD_COL_WIDTH.sensitive }} onClick={handleConfigClick} />,
            <div key="config-empty-traffic" style={{ cursor: "pointer", width: CHILD_COL_WIDTH.traffic }} onClick={handleConfigClick} />,
            <div key="config-empty-discovered" style={{ cursor: "pointer", width: CHILD_COL_WIDTH.discovered }} onClick={handleConfigClick} />,
        ];
    }, [misconfiguredChildId, handleConfigClick]);

    const rows = useMemo(() => (children || []).map((child) => {
        const childRiskScore = child.riskScore || 0;
        const displayValue = child.name || "-";
        return [
            <div key={`spacer-${child.id}`} style={{ width: "32px", minWidth: "32px" }} />,
            <div key={`name-${child.id}`} style={{ cursor: "pointer", width: CHILD_COL_WIDTH.name }} onClick={() => handleChildClick(child)}>
                <HorizontalStack gap="1" align="start" wrap={false}>
                    <Box maxWidth="200px"><TooltipText tooltip={displayValue} text={displayValue} /></Box>
                    {(child.skillCount || 0) > 0 && (
                        <Badge size="small" status="info">{`${child.skillCount} ${child.skillCount === 1 ? "skill" : "skills"}`}</Badge>
                    )}
                    {child.hasPersonalAccount && <Badge size="small" status="warning">Contains personal account</Badge>}
                    {child.hasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
                    {child.hasLocalMcpServer && <Badge size="small" status="critical">Local MCP Server</Badge>}
                </HorizontalStack>
            </div>,
            <div key={`risk-${child.id}`} style={{ cursor: "pointer", width: CHILD_COL_WIDTH.riskScore }} onClick={() => handleChildClick(child)}>
                <Badge status={transform.getStatus(childRiskScore)} size="small">{childRiskScore}</Badge>
            </div>,
            <div key={`sensitive-${child.id}`} style={{ cursor: "pointer", width: CHILD_COL_WIDTH.sensitive }} onClick={() => handleChildClick(child)}>
                {transform.prettifySubtypes(child.sensitiveInRespTypes || [])}
            </div>,
            <div key={`traffic-${child.id}`} style={{ cursor: "pointer", width: CHILD_COL_WIDTH.traffic }} onClick={() => handleChildClick(child)}>
                {func.prettifyEpoch(child.lastSeenEpoch || 0)}
            </div>,
            <div key={`discovered-${child.id}`} style={{ cursor: "pointer", width: CHILD_COL_WIDTH.discovered }} onClick={() => handleChildClick(child)}>
                {func.prettifyEpoch(child.startTs || 0)}
            </div>,
        ];
    }), [children, handleChildClick]);

    const columnContentTypes = useMemo(() => ["text", "text", "text", "text", "text", "text"], []);

    return (
        <td colSpan={PARENT_HEADERS.length} style={{ padding: "0px !important" }} className="control-row">
            <Box width="100%">
                <DataTable
                    rows={configRow ? [configRow, ...rows] : rows}
                    hasZebraStripingOnData
                    headings={[]}
                    columnContentTypes={columnContentTypes}
                />
            </Box>
        </td>
    );
}

function shapeEndpointRow(row, { rowType }) {
    const riskScore = row.riskScore || 0;
    const endpointTags = [
        ...(row.hasPersonalAccount ? ["Contains personal account"] : []),
        ...(row.hasLocalMcpServer ? ["Local MCP Server"] : []),
        ...(row.hasMisconfiguredConfig ? ["Misconfigured"] : []),
        ...(row.hasMaliciousSkill ? ["Malicious Skills"] : []),
    ];
    const children = row.children || [];
    const misconfiguredChildId = row.hasMisconfiguredConfig
        ? (children.find((c) => c.hasMisconfiguredConfig)?.id ?? null)
        : null;

    return {
        ...row,
        name: `endpoint-${row.endpointId}`,
        displayNameComp: (
            <HorizontalStack gap="1" align="start" wrap={false}>
                <Box maxWidth="200px">
                    <TooltipText tooltip={row.endpointId} text={row.endpointId} textProps={{ variant: "headingSm" }} />
                </Box>
                <Badge size="small" status="new">{row.childCount || children.length}</Badge>
                {row.hasPersonalAccount && <Badge size="small" status="warning">Contains personal account</Badge>}
                {row.hasLocalMcpServer && <Badge size="small" status="critical">Local MCP Server</Badge>}
                {row.hasMisconfiguredConfig && <Badge size="small" status="attention">Misconfigured</Badge>}
                {row.hasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
            </HorizontalStack>
        ),
        usernameComp: (
            <Box maxWidth="100px">
                <TooltipText tooltip={row.username || "-"} text={row.username || "-"} />
            </Box>
        ),
        riskScoreComp: <Badge status={transform.getStatus(riskScore)} size="small">{riskScore}</Badge>,
        sensitiveSubTypes: transform.prettifySubtypes(row.sensitiveInRespTypes || []),
        lastTraffic: func.prettifyEpoch(row.lastSeenEpoch || 0),
        discovered: func.prettifyEpoch(row.startTs || 0),
        endpointTagsComp: endpointTags.length > 0 ? endpointTags.join(", ") : "-",
        isTerminal: false,
        collapsibleRow: (
            <ChildrenTable
                children={children}
                rowType={rowType}
                misconfiguredChildId={misconfiguredChildId}
            />
        ),
    };
}

export default function AgenticAssetDevicesPage() {
    const [searchParams] = useSearchParams();
    const groupKey = searchParams.get("groupKey") || "";
    const rowType = searchParams.get("rowType") || "";
    const assetName = searchParams.get("name") || "Asset";
    const assetType = searchParams.get("type") || "";

    // True only until this one asset's own collectionIds resolve (lazy — mirrors the new layout's
    // flyout fetching fetchAgenticAssetDetail once per asset instead of shipping collectionIds on
    // every row of the main grid). Navigating here happens immediately on row-click, before this
    // resolves, so the "loading" feedback lives on this page instead of freezing the previous one.
    const [loading, setLoading] = useState(true);
    const collectionIdsRef = useRef([]);
    const enrichRef = useRef({ trafficMap: {}, riskScoreMap: {}, sensitiveMap: {}, usernameMap: {} });
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        (async () => {
            try {
                const [trafficRiskBundle, sensitiveMap, usernameMap, detail] = await Promise.all([
                    fetchAndCacheAgenticTrafficRiskBundle({ api, PersistStore }),
                    fetchAndCacheAgenticSensitiveInfo({ api, PersistStore }),
                    fetchEndpointShieldUsernameMap(),
                    api.fetchAgenticAssetDetail({ groupKey, rowType }),
                ]);
                if (cancelled) return;
                const { trafficMap = {}, riskScoreMap = {} } = trafficRiskBundle || {};
                enrichRef.current = { trafficMap, riskScoreMap, sensitiveMap: sensitiveMap || {}, usernameMap };
                collectionIdsRef.current = detail?.collectionIds || [];
            } catch {
                if (!cancelled) collectionIdsRef.current = [];
            } finally {
                if (!cancelled) { setLoading(false); setRefreshKey((k) => k + 1); }
            }
        })();
        return () => { cancelled = true; };
    }, [groupKey, rowType]);

    const fetchTableData = useCallback(async (sortKey, sortOrder, skip, limit, filtersObj, filterOperators, queryValue) => {
        const { trafficMap, riskScoreMap, sensitiveMap, usernameMap } = enrichRef.current;
        const mongoSortOrder = sortOrder === -1 ? 1 : -1;
        const endpointTags = filtersObj?.endpointTags;
        const res = await api.fetchAgenticAssetEndpointsPage({
            apiCollectionIds: collectionIdsRef.current,
            rowType,
            skip, limit, sortKey: sortKey || "riskScore", sortOrder: mongoSortOrder, queryValue,
            trafficMap, riskScoreMap, sensitiveMap, usernameMap,
            filters: endpointTags?.length ? { endpointTags } : undefined,
        });
        const rows = (res.endpoints || []).map((row) => shapeEndpointRow(row, { rowType }));
        return { value: rows, total: res.total || 0 };
    }, [rowType]);

    const disambiguateLabel = useCallback((key, value) => func.convertToDisambiguateLabelObj(value, null, 2), []);

    if (loading) {
        return (
            <PageWithMultipleCards
                title={assetName}
                titleMetadata={assetType ? <Badge>{assetType}</Badge> : undefined}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <PageWithMultipleCards
            title={assetName}
            titleMetadata={assetType ? <Badge>{assetType}</Badge> : undefined}
            components={[
                <GithubServerTable
                    key={`asset-endpoints-${groupKey}-${rowType}-${refreshKey}`}
                    fetchData={fetchTableData}
                    pageLimit={20}
                    sortOptions={SORT_OPTIONS}
                    resourceName={resourceName}
                    filters={FILTERS_DEF}
                    headers={PARENT_HEADERS}
                    selectable={false}
                    headings={PARENT_HEADERS}
                    useNewRow={true}
                    condensedHeight={true}
                    disambiguateLabel={disambiguateLabel}
                    supportsNegationFilter={false}
                />,
            ]}
        />
    );
}

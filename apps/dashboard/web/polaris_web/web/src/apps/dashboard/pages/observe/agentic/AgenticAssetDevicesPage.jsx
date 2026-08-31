import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { Badge, Box, Button, HorizontalStack, VerticalStack, Text, DataTable } from "@shopify/polaris";
import { ArrowLeftMinor } from "@shopify/polaris-icons";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import FlyLayout from "../../../components/layouts/FlyLayout";
import GithubServerTable from "@/apps/dashboard/components/tables/GithubServerTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip";
import TitleWithInfo from "../../../components/shared/TitleWithInfo";
import TooltipText from "../../../components/shared/TooltipText";
import api from "../api";
import func from "@/util/func";
import transform from "../transform";
import SkillComponentsView from "./SkillComponentsView";
import { fetchAgentMarkdownFromCollections } from "./PluginComponentsView";
import MisconfiguredBadge from "./MisconfiguredBadge";

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

const ENDPOINT_TAGS_FILTER_DEF = { key: "endpointTags", label: "Endpoint tags", choices: [
    { label: "Contains personal account", value: "Contains personal account" },
    { label: "Local MCP Server", value: "Local MCP Server" },
    { label: "Misconfigured", value: "Misconfigured" },
    { label: "Malicious Skills", value: "Malicious Skills" },
    { label: "Owner", value: "Owner" },
] };

const resourceName = { singular: "endpoint", plural: "endpoints" };

// child.name is already the field the server picked (serviceName for agent/skill rows, the
// calling source's id for service/llm rows — see AgenticObserveAction.groupCollectionsByEndpointId's
// useServiceName branch) — no per-type column title to pick here, matching AgentEndpointTreeTable's
// own ChildrenTable, which never renders its childHeaders' titles either (headings=[] throughout,
// the parent GithubRow header row is the only visible header).
function ChildrenTable({ children, rowType, misconfiguredChildId, onOpenBundle }) {
    const navigate = useNavigate();

    const handleChildClick = useCallback((child) => {
        // Plugin rows: the child IS the plugin's own metadata endpoint — open the bundled
        // components list instead of navigating to its raw endpoint in Inventory.
        if (rowType === "plugin") { onOpenBundle?.(); return; }
        const bundlesSkills = rowType === "skill" || ((rowType === "agent" || rowType === "service") && (child.skillCount || 0) > 0);
        const isPluginCollapsedService = rowType === "service" && !!child.owningPluginName;
        const scope = bundlesSkills ? "?agentic_view=skills" : (isPluginCollapsedService ? "?agentic_view=mcp" : "");
        navigate(`/dashboard/observe/inventory/${child.id}${scope}`);
    }, [navigate, rowType, onOpenBundle]);

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
                    <MisconfiguredBadge />
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
                    {child.type && <Badge size="small">{child.type}</Badge>}
                    {(child.skillCount || 0) > 0 && (
                        <Badge size="small" status="info">{`${child.skillCount} ${child.skillCount === 1 ? "skill" : "skills"}`}</Badge>
                    )}
                    {child.owningPluginName && <Badge size="small" status="info">{`Plugin: ${child.owningPluginName}`}</Badge>}
                    {child.hasPersonalAccount && <Badge size="small" status="warning">Contains personal account</Badge>}
                    {child.hasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
                    {child.hasLocalMcpServer && <Badge size="small" status="critical">Local MCP Server</Badge>}
                </HorizontalStack>
            </div>,
            <div key={`risk-${child.id}`} style={{ cursor: "pointer", width: CHILD_COL_WIDTH.riskScore }} onClick={() => handleChildClick(child)}>
                {transform.wrapRiskScoreTooltip(
                    <Badge status={transform.getStatus(childRiskScore)} size="small">{childRiskScore}</Badge>,
                    childRiskScore, child.baseRiskScore, child.baseRiskScoreReason
                )}
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

function PluginBundleContent({ bundle, pluginCollectionId, navigate }) {
    const [selectedSkill, setSelectedSkill] = useState(null);
    const [selectedAgent, setSelectedAgent] = useState(null);
    const servers = bundle?.mcpServers || [];
    const skills = bundle?.skills || [];
    const agents = bundle?.agents || [];

    const openServer = (name) => {
        const ids = bundle?.mcpServerCollectionIds?.[name] || [];
        if (!ids.length) return;
        navigate(`/dashboard/observe/inventory/${ids[0]}`);
    };

    if (selectedSkill) {
        return (
            <Box>
                <Box paddingBlockEnd="3">
                    <Button plain icon={ArrowLeftMinor} onClick={() => setSelectedSkill(null)} />
                </Box>
                <SkillComponentsView asset={{ collectionIds: [pluginCollectionId], name: selectedSkill }} hideOwningPlugin />
            </Box>
        );
    }

    if (selectedAgent) {
        return (
            <Box>
                <Box paddingBlockEnd="3">
                    <Button plain icon={ArrowLeftMinor} onClick={() => setSelectedAgent(null)} />
                </Box>
                <SkillComponentsView
                    asset={{ collectionIds: [pluginCollectionId], name: selectedAgent }}
                    hideOwningPlugin
                    entityLabel="agent"
                    fetchMarkdown={fetchAgentMarkdownFromCollections}
                />
            </Box>
        );
    }

    if (servers.length === 0 && skills.length === 0 && agents.length === 0) {
        return <Box padding="4"><Text variant="bodySm" color="subdued">No components reported for this plugin yet.</Text></Box>;
    }

    const rows = [
        ...servers.map((name) => [
            <div key={`srv-${name}`} style={{ cursor: "pointer" }} onClick={() => openServer(name)}><Text variant="bodyMd">{name}</Text></div>,
            <div key={`srv-type-${name}`} style={{ cursor: "pointer" }} onClick={() => openServer(name)}><Badge size="small">MCP Server</Badge></div>,
        ]),
        ...skills.map((name) => [
            <div key={`skl-${name}`} style={{ cursor: "pointer" }} onClick={() => setSelectedSkill(name)}><Text variant="bodyMd">{name}</Text></div>,
            <div key={`skl-type-${name}`} style={{ cursor: "pointer" }} onClick={() => setSelectedSkill(name)}><Badge size="small" status="info">Skill</Badge></div>,
        ]),
        ...agents.map((name) => [
            <div key={`agt-${name}`} style={{ cursor: "pointer" }} onClick={() => setSelectedAgent(name)}><Text variant="bodyMd">{name}</Text></div>,
            <div key={`agt-type-${name}`} style={{ cursor: "pointer" }} onClick={() => setSelectedAgent(name)}><Badge size="small" status="new">Agent</Badge></div>,
        ]),
    ];

    return (
        <DataTable
            columnContentTypes={["text", "text"]}
            headings={["Name", "Type"]}
            rows={rows}
            hasZebraStripingOnData
        />
    );
}

function shapeEndpointRow(row, { rowType, onOpenBundle }) {
    const riskScore = row.riskScore || 0;
    const endpointTags = [
        ...(row.hasPersonalAccount ? ["Contains personal account"] : []),
        ...(row.hasLocalMcpServer ? ["Local MCP Server"] : []),
        ...(row.hasMisconfiguredConfig ? ["Misconfigured"] : []),
        ...(row.hasMaliciousSkill ? ["Malicious Skills"] : []),
        ...(row.hasOwnerTag ? ["Owner"] : []),
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
                {row.hasMisconfiguredConfig && <MisconfiguredBadge />}
                {row.hasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
                {row.hasOwnerTag && <Badge size="small" status="success">Owner</Badge>}
                {row.hasOwnerTag && row.environmentName && (
                    <Badge size="small" status="info">{`Env: ${row.environmentName}`}</Badge>
                )}
            </HorizontalStack>
        ),
        usernameComp: (
            <Box maxWidth="100px">
                <TooltipText tooltip={row.username || "-"} text={row.username || "-"} />
            </Box>
        ),
        riskScoreComp: transform.wrapRiskScoreTooltip(
            <Badge status={transform.getStatus(riskScore)} size="small">{riskScore}</Badge>,
            riskScore, row.baseRiskScore, row.baseRiskScoreReason
        ),
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
                onOpenBundle={onOpenBundle}
            />
        ),
    };
}

export default function AgenticAssetDevicesPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const groupKey = searchParams.get("groupKey") || "";
    const rowType = searchParams.get("rowType") || "";
    const assetName = searchParams.get("name") || "Asset";
    const assetType = searchParams.get("type") || "";

    // Matches ApiCollections.jsx's own agent-tree header exactly (getFilteredPageTitle's
    // "<Type> - <Name>" template + TitleWithInfo's info icon) — that page and this one are the
    // two places this exact asset's device breakdown is shown, so the header should read the same
    // regardless of which one a click landed on.
    const [description, setDescription] = useState("");

    const pageTitle = useMemo(() => (
        <VerticalStack gap="1">
            <TitleWithInfo
                tooltipContent={`Viewing devices for ${assetType || "asset"} ${assetName}`}
                titleText={assetType ? `${assetType} - ${assetName}` : assetName}
                docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
            />
            {description && <Text variant="bodyMd">{description}</Text>}
        </VerticalStack>
    ), [assetType, assetName, description]);

    // Same "Explore mode" primaryAction ApiCollections.jsx always shows next to its title —
    // generic query-explorer shortcut, not scoped to this asset, kept only for header parity.
    const exploreModeAction = useMemo(() => (
        <Button primary onClick={() => navigate("/dashboard/observe/query_mode")}>Explore mode</Button>
    ), [navigate]);

    // True only until this one asset's own collectionIds resolve (lazy — mirrors the new layout's
    // flyout fetching fetchAgenticAssetDetail once per asset instead of shipping collectionIds on
    // every row of the main grid). Navigating here happens immediately on row-click, before this
    // resolves, so the "loading" feedback lives on this page instead of freezing the previous one.
    const [loading, setLoading] = useState(true);
    const collectionIdsRef = useRef([]);
    const [refreshKey, setRefreshKey] = useState(0);
    // Plugin rows only — the MCP servers/skills this plugin bundles, so old-UI can list them with a
    // direct redirect link (same server-side data new-UI's PluginComponentsView table shows).
    const [pluginBundle, setPluginBundle] = useState(null);
    const [showBundleFlyout, setShowBundleFlyout] = useState(false);
    const [owningPluginName, setOwningPluginName] = useState(null);

    // Endpoint ID/Username filter choices — matches AgentEndpointTreeTable.jsx's own enumerated
    // facets (this asset's device count is small enough to list directly, no distinct-values
    // endpoint needed). The server computes these from the full unfiltered set on every response,
    // so content is stable across calls for the same asset; only update state when it actually
    // changes to avoid a re-render every single fetch.
    const [filterChoices, setFilterChoices] = useState({ endpointIds: [], usernames: [] });
    const updateFilterChoicesIfChanged = useCallback((endpointIds, usernames) => {
        setFilterChoices((prev) => {
            const sameEndpointIds = prev.endpointIds.length === endpointIds.length && prev.endpointIds.every((v, i) => v === endpointIds[i]);
            const sameUsernames = prev.usernames.length === usernames.length && prev.usernames.every((v, i) => v === usernames[i]);
            return (sameEndpointIds && sameUsernames) ? prev : { endpointIds, usernames };
        });
    }, []);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        (async () => {
            try {
                // trafficMap/riskScoreMap/sensitiveMap are deliberately NOT fetched here — unlike
                // the list pages (Endpoints.jsx etc.), which fetch those account-wide maps once to
                // enrich every row of a big grid, this page only ever needs a handful of entries out
                // of them for one asset's own devices. fetchAgenticAssetEndpointsPage computes those,
                // plus usernameMap, server-side directly from ModuleInfo + AgentUsers (see
                // AgenticObserveAction.getOrComputeIdentityMapsCached) instead of requiring this page
                // to fetch the whole account's map just to read a few entries back out of it.
                const detail = await api.fetchAgenticAssetDetail({ groupKey, rowType });
                if (cancelled) return;
                collectionIdsRef.current = detail?.collectionIds || [];
                setDescription(detail?.description || "");
                setPluginBundle(rowType === "plugin" ? {
                    mcpServers: detail?.pluginMcpServers || [],
                    mcpServerCollectionIds: detail?.pluginMcpServerCollectionIds || {},
                    skills: detail?.pluginSkills || [],
                    agents: detail?.pluginAgents || [],
                } : null);
                setOwningPluginName(rowType !== "plugin" ? (detail?.owningPluginName || null) : null);
            } catch {
                if (!cancelled) { collectionIdsRef.current = []; setDescription(""); setPluginBundle(null); setOwningPluginName(null); }
            } finally {
                if (!cancelled) { setLoading(false); setRefreshKey((k) => k + 1); }
            }
        })();
        return () => { cancelled = true; };
    }, [groupKey, rowType]);

    const fetchTableData = useCallback(async (sortKey, sortOrder, skip, limit, filtersObj, filterOperators, queryValue) => {
        const mongoSortOrder = sortOrder === -1 ? 1 : -1;
        const filters = {};
        if (filtersObj?.endpointTags?.length) filters.endpointTags = filtersObj.endpointTags;
        if (filtersObj?.endpointId?.length) filters.endpointId = filtersObj.endpointId;
        if (filtersObj?.username?.length) filters.username = filtersObj.username;
        const res = await api.fetchAgenticAssetEndpointsPage({
            apiCollectionIds: collectionIdsRef.current,
            rowType,
            groupKey,
            skip, limit, sortKey: sortKey || "riskScore", sortOrder: mongoSortOrder, queryValue,
            filters: Object.keys(filters).length ? filters : undefined,
        });
        updateFilterChoicesIfChanged(res.distinctEndpointIds || [], res.distinctUsernames || []);
        const rows = (res.endpoints || []).map((row) => shapeEndpointRow(row, { rowType, onOpenBundle: () => setShowBundleFlyout(true) }));
        return { value: rows, total: res.total || 0 };
    }, [rowType, groupKey, updateFilterChoicesIfChanged]);

    const filtersDef = useMemo(() => {
        const defs = [ENDPOINT_TAGS_FILTER_DEF];
        if (filterChoices.endpointIds.length) {
            defs.push({ key: "endpointId", label: "Endpoint ID", choices: filterChoices.endpointIds.map((v) => ({ label: v, value: v })) });
        }
        if (filterChoices.usernames.length) {
            defs.push({ key: "username", label: "Username", choices: filterChoices.usernames.map((v) => ({ label: v, value: v })) });
        }
        return defs;
    }, [filterChoices]);

    const disambiguateLabel = useCallback((key, value) => func.convertToDisambiguateLabelObj(value, null, 2), []);

    if (loading) {
        return (
            <PageWithMultipleCards
                title={pageTitle}
                primaryAction={exploreModeAction}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <>
            <PageWithMultipleCards
                title={pageTitle}
                primaryAction={exploreModeAction}
                components={[
                    ...(owningPluginName ? [
                        <Box key="owning-plugin" paddingBlockEnd="2">
                            <HorizontalStack gap="1" blockAlign="center">
                                <Badge size="small" status="info">{owningPluginName}</Badge>
                                <Text variant="bodySm" color="subdued">{rowType === "skill" ? "uses this skill" : "uses this MCP Server"}</Text>
                            </HorizontalStack>
                        </Box>,
                    ] : []),
                    <GithubServerTable
                        key={`asset-endpoints-${groupKey}-${rowType}-${refreshKey}`}
                        fetchData={fetchTableData}
                        pageLimit={20}
                        sortOptions={SORT_OPTIONS}
                        resourceName={resourceName}
                        filters={filtersDef}
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
            {pluginBundle && (
                <FlyLayout
                    title="Plugin Components"
                    show={showBundleFlyout}
                    setShow={setShowBundleFlyout}
                    components={[
                        <PluginBundleContent
                            key="plugin-bundle"
                            bundle={pluginBundle}
                            pluginCollectionId={collectionIdsRef.current?.[0]}
                            navigate={navigate}
                        />,
                    ]}
                />
            )}
        </>
    );
}

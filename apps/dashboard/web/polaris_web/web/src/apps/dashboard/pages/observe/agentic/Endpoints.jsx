import React, { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { IndexFiltersMode, Box, Badge, HorizontalStack, Text } from "@shopify/polaris";
import { useNavigate } from "react-router-dom";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import api from "../api";
import func from "@/util/func";
import transform from "../transform";
import PersistStore from "../../../../main/PersistStore";
import LocalStore from "../../../../main/LocalStorageStore";
import { CollectionIcon } from "../../../components/shared/CollectionIcon";
import useTable from "@/apps/dashboard/components/tables/TableContext";
import NewLayoutTooltip from "./NewLayoutTooltip";
import {
    getHeaders,
    sortOptions,
    resourceName,
    INVENTORY_PATH,
    INVENTORY_FILTER_KEY,
    PAGE_LIMIT,
    groupCollectionsByAgent,
    groupCollectionsByService,
    groupCollectionsByLLM,
    groupCollectionsBySkill,
    extractEndpointId,
    buildAgenticInventoryFilterForRow,
    fetchAndCacheSkillApiData,
    skillCollectionKey,
    fetchAndCacheAgenticCollectionsBundle,
    fetchAndCacheAgenticSensitiveInfo,
} from "./constants";
import { CLIENT_TYPES, ROW_TYPES, hasPersonalAccountTag } from "./mcpClientHelper";

const definedTableTabs = ['All', 'AI Agents', 'MCP Servers', 'LLMs', 'Skills'];

// Restrict free-text search to the asset-name column only. Rows carry large nested objects
// (collections, etc.); a broad flatten-every-field match is slow enough to freeze the UI.
const SEARCH_KEYS = ["groupName"];

function Endpoints() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const agenticNewLayout = LocalStore((state) => state.agenticNewLayout);
    const setAgenticNewLayout = LocalStore((state) => state.setAgenticNewLayout);

    useEffect(() => {
        if (agenticNewLayout) {
            navigate("/dashboard/observe/agentic-assets", { replace: true });
        }
    }, [navigate, agenticNewLayout]);
    const [data, setData] = useState({ all: [], 'ai_agents': [], 'mcp_servers': [], llms: [], skills: [] });
    // Skill risk scores/badges arrive asynchronously (after the initial render). When they land,
    // applySkillRiskScores rewrites the source rows (data.skills) and bumps skillEnrichVersion; that
    // version is fed to the table as callFromOutside, which re-derives its rows from the now-enriched
    // source using the current search query — so badges appear without remounting or wiping the
    // search. Reading the source (not patching a stale snapshot) avoids the first-load race.
    const [skillEnrichVersion, setSkillEnrichVersion] = useState(0);
    const [summaryData, setSummaryData] = useState({ totalAssets: 0, totalEndpoints: 0 });

    const { tabsInfo } = useTable();
    const tableSelectedTab = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab = tableSelectedTab[window.location.pathname] || "ai_agents";
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected] = useState(func.getTableTabIndexById(1, definedTableTabs, initialSelectedTab));

    const setAllCollections = PersistStore((state) => state.setAllCollections);
    const filtersMap = PersistStore((state) => state.filtersMap);
    const setFiltersMap = PersistStore((state) => state.setFiltersMap);

    // Ref so the Skills tab effect can read current skills without being a dep
    const dataRef = useRef(data);
    useEffect(() => { dataRef.current = data; }, [data]);

    const tableCountObj = func.getTabsCount(definedTableTabs, data);
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo);

    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex);
    };

    const headers = useMemo(() => {
        const h = getHeaders();
        h[1] = { ...h[1], value: "groupNameDisplay" };
        return h;
    }, []);

    // Default the view to Risk score (highest first) instead of Name A-Z. Two quirks handled here
    // (page-scoped — the shared sortOptions export is left untouched for other pages):
    //  1. getInitialSortSelected picks sortOptions[0] when there's no persisted sort, so the
    //     highest-risk option must be first.
    //  2. The legacy client-side numeric sort is INVERTED vs its labels: func.sortFunc orders
    //     numbers as sortOrder*(a-b) and "desc" maps to sortOrder=1 → ascending (lowest first).
    //     So the value that actually shows highest-first for a numeric field is "riskScore asc".
    //     Swap the two Risk score option VALUES so each directionLabel matches real behavior.
    const riskFirstSortOptions = useMemo(() => {
        const swapped = sortOptions.map((o) => {
            if (o.sortKey !== "riskScore") return o;
            if (o.directionLabel === "Highest") return { ...o, value: "riskScore asc" };
            if (o.directionLabel === "Lowest") return { ...o, value: "riskScore desc" };
            return o;
        });
        const riskHighest = swapped.find((o) => o.sortKey === "riskScore" && o.directionLabel === "Highest");
        return riskHighest ? [riskHighest, ...swapped.filter((o) => o !== riskHighest)] : swapped;
    }, []);

    // Filter-only header (not a visible column) for the malicious/misconfigured tags we add on
    // skills. Kept out of `headings` so it drives the filter facet without rendering a column.
    const tagFilterHeader = useMemo(() => ({
        title: "Tag", text: "Tag", value: "assetTags",
        filterKey: "assetTags", filterLabel: "Tag", showFilter: true,
    }), []);

    // Only expose the tag filter once enriched rows actually carry tags. Adding the header changes
    // the transform filter-choices cache key, so the "Malicious" choice appears after async
    // enrichment rather than being cached-empty from the pre-enrichment first render.
    const hasAssetTags = useMemo(
        () => (data.skills || []).some((r) => (r.assetTags || []).length > 0),
        [data.skills],
    );
    const filterHeaders = useMemo(
        () => (hasAssetTags ? [...headers, tagFilterHeader] : headers),
        [headers, tagFilterHeader, hasAssetTags],
    );

    const getRiskScoreStatus = useCallback((riskScore) => {
        if (riskScore >= 4.5) return "critical";
        if (riskScore >= 4) return "attention";
        if (riskScore >= 2.5) return "warning";
        if (riskScore > 0) return "info";
        return "success";
    }, []);

    const prettifyGroupData = useCallback((groups) => {
        return groups.map((group) => {
            const showPersonal = group.hasPersonalAccount && group.rowType !== ROW_TYPES.SKILL;
            const showLocalMcp = group.hasLocalMcpServer && group.rowType !== ROW_TYPES.SKILL;
            const showMisconfigured = group.hasMisconfiguredConfig && group.rowType !== ROW_TYPES.SKILL;
            const groupNameDisplay = (showPersonal || showLocalMcp || showMisconfigured)
                ? (
                    <HorizontalStack gap="2" align="start" wrap={false}>
                        <Text>{group.groupName}</Text>
                        {showPersonal && <Badge size="small" status="warning">Contains personal account</Badge>}
                        {showLocalMcp && <Badge size="small" status="critical">Local MCP Server</Badge>}
                        {showMisconfigured && <Badge size="small" status="attention">Misconfigured</Badge>}
                    </HorizontalStack>
                )
                : group.groupName;
            return ({
            ...group,
            groupNameDisplay,
            iconComp: (
                <Box>
                    <CollectionIcon
                        hostName={group.firstCollection?.hostName}
                        assetTagValue={group.tagValue}
                        displayName={group.groupName}
                    />
                </Box>
            ),
            sensitiveSubTypes: transform.prettifySubtypes(group.sensitiveInRespTypes || [], false),
            riskScoreComp: group.riskScore !== null
                ? <Badge status={getRiskScoreStatus(group.riskScore)} size="small">{group.riskScore}</Badge>
                : "-",
            });
        });
    }, [getRiskScoreStatus]);

    const applySkillRiskScores = useCallback((scoreMap, maliciousSkillKeys, misconfiguredSkills, isMountedRef) => {
        if (!isMountedRef.current) return;
        setData((prev) => {
            const updatedSkills = prev.skills.map((row) => {
                const riskScore = scoreMap[row.groupName] || 0;
                // Scoped to this row's own collections: the skill is malicious only where it was
                // actually tagged, not because another user has a skill of the same name.
                const isMalicious = (row.collections || []).some((c) => maliciousSkillKeys.has(skillCollectionKey(c.id, row.groupName)));
                const isMisconfigured = misconfiguredSkills.has(row.groupName);
                const groupNameDisplay = (
                    <HorizontalStack gap="2" align="start" wrap={false}>
                        <Text>{row.groupName}</Text>
                        {isMalicious && <Badge size="small" status="critical">Malicious</Badge>}
                        {isMisconfigured && <Badge size="small" status="attention">Misconfigured</Badge>}
                    </HorizontalStack>
                );
                return {
                    ...row,
                    riskScore,
                    maxRiskScore: riskScore,
                    isMalicious,
                    isMisconfigured,
                    // Filterable tag values (drives the "Tag" filter facet). Array so a row can
                    // carry more than one tag; empty for clean skills so no facet value is added.
                    assetTags: [
                        ...(isMalicious ? ["Malicious"] : []),
                        ...(isMisconfigured ? ["Misconfigured"] : []),
                    ],
                    groupNameDisplay,
                    riskScoreComp: riskScore
                        ? <Badge status={getRiskScoreStatus(riskScore)} size="small">{riskScore}</Badge>
                        : "-",
                };
            });
            return {
                ...prev,
                skills: updatedSkills,
                all: prev.all.map((row) => {
                    if (row.clientType !== CLIENT_TYPES.SKILL) return row;
                    return updatedSkills.find((s) => s.id === row.id) || row;
                }),
            };
        });
        // Bump the version fed to the table as callFromOutside → it re-derives from the enriched
        // data.skills with the current query, so badges show without remount or search loss.
        setSkillEnrichVersion((v) => v + 1);
    }, [getRiskScoreStatus]);

    const enrichSkillsWithApiRiskScores = useCallback(async (skillRows, isMountedRef = { current: true }) => {
        if (!skillRows.length) return;

        // Single account-wide call (no per-collection N+1) — maliciousSkillKeys is pre-aggregated
        // server-side (collection-scoped), see fetchAndCacheSkillApiData in constants.js.
        const { skillScoreMap, maliciousSkillKeys, misconfiguredSkills } = await fetchAndCacheSkillApiData([], { api, PersistStore });

        if (!isMountedRef.current) return;
        applySkillRiskScores(skillScoreMap, maliciousSkillKeys || new Set(), misconfiguredSkills || new Set(), isMountedRef);
    }, [applySkillRiskScores]);

    async function fetchData(isMountedRef = { current: true }) {
        try {
            setLoading(true);

            // getAllCollectionsBasic/traffic/risk are cached (PersistStore, 2-min TTL, in-flight-deduped)
            // and shared with AgenticAssetsPage/UsersAndDevices/DeviceEndpoints/EndpointPosture. Sensitive
            // info is cached separately since only this page + UsersAndDevices.jsx render that column.
            const [collectionsBundle, sensitiveMap] = await Promise.all([
                fetchAndCacheAgenticCollectionsBundle({ api, PersistStore }),
                fetchAndCacheAgenticSensitiveInfo({ api, PersistStore }),
            ]);

            if (!isMountedRef.current) return;

            const { collections = [], trafficMap = {}, riskScoreMap = {} } = collectionsBundle || {};
            setAllCollections(collections);

            const agentGroups = groupCollectionsByAgent(collections, trafficMap, sensitiveMap, riskScoreMap);
            const serviceGroups = groupCollectionsByService(collections, trafficMap, sensitiveMap, riskScoreMap);
            const llmGroups = groupCollectionsByLLM(collections, trafficMap, sensitiveMap, riskScoreMap);
            const skillGroups = groupCollectionsBySkill(collections, trafficMap, sensitiveMap, riskScoreMap);

            const prettifiedAgents = prettifyGroupData(agentGroups);
            const prettifiedServices = prettifyGroupData(serviceGroups);
            const prettifiedLlms = prettifyGroupData(llmGroups);
            const prettifiedSkills = prettifyGroupData(skillGroups);

            const agentGroupKeys = new Set(prettifiedAgents.map((a) => a.groupKey));
            const servicesToShow = prettifiedServices.filter((s) => !agentGroupKeys.has(s.groupKey));

            const allData = [...prettifiedAgents, ...servicesToShow, ...prettifiedLlms, ...prettifiedSkills];

            const uniqueEndpointIds = new Set();
            collections.forEach((c) => {
                if (c.deactivated) return;
                const hostName = c.hostName || c.displayName || c.name;
                const endpointId = extractEndpointId(hostName);
                if (endpointId) uniqueEndpointIds.add(endpointId);
            });

            setSummaryData({
                totalAssets: allData.length,
                totalEndpoints: uniqueEndpointIds.size
            });

            setData({
                all: allData,
                ai_agents: allData.filter(r => r.clientType === CLIENT_TYPES.AI_AGENT),
                mcp_servers: allData.filter(r => r.clientType === CLIENT_TYPES.MCP_SERVER),
                llms: allData.filter(r => r.clientType === CLIENT_TYPES.LLM),
                skills: prettifiedSkills,
            });
            setLoading(false);

            // Async enrichment — updates skill risk scores after initial render
            enrichSkillsWithApiRiskScores(prettifiedSkills, isMountedRef);
        } catch {
            setLoading(false);
        }
    }

    useEffect(() => {
        const isMountedRef = { current: true };
        fetchData(isMountedRef);
        return () => { isMountedRef.current = false; };
    }, []);

    // Re-enrich on Skills tab switch; reads latest skills via ref to avoid dep loop
    useEffect(() => {
        if (selectedTab !== "skills") return;
        const isMountedRef = { current: true };
        enrichSkillsWithApiRiskScores(dataRef.current.skills, isMountedRef);
        return () => { isMountedRef.current = false; };
    }, [selectedTab, enrichSkillsWithApiRiskScores]);

    const disambiguateLabel = useCallback((key, value) => {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }, []);

    const handleRowClick = useCallback((row) => {
        const updatedFiltersMap = { ...filtersMap };
        const filterPayload = buildAgenticInventoryFilterForRow(row);
        if (filterPayload) {
            updatedFiltersMap[INVENTORY_FILTER_KEY] = filterPayload;
        } else {
            delete updatedFiltersMap[INVENTORY_FILTER_KEY];
        }
        delete updatedFiltersMap[`${INVENTORY_FILTER_KEY}agent-tree/`];

        setFiltersMap(updatedFiltersMap);

        setTableSelectedTab({
            ...tableSelectedTab,
            [INVENTORY_PATH]: "hostname"
        });

        setTimeout(() => navigate(INVENTORY_PATH), 0);
    }, [filtersMap, setFiltersMap, navigate, tableSelectedTab, setTableSelectedTab]);

    const summaryItems = useMemo(() => [
        {
            title: "Agentic assets",
            data: transform.formatNumberWithCommas(summaryData.totalAssets),
        },
        {
            title: "Total endpoints",
            data: transform.formatNumberWithCommas(summaryData.totalEndpoints),
        },
    ], [summaryData]);

    const summaryComponent = useMemo(() => (
        <SummaryCardInfo summaryItems={summaryItems} key="summary" />
    ), [summaryItems]);

    const tableComponent = useMemo(() => {
        const commonTabProps = { tableTabs, onSelect: handleSelectedTab, selected };
        const isSkills = selectedTab === "skills";
        return (
            <GithubSimpleTable
                key="table"
                pageLimit={PAGE_LIMIT}
                data={data[selectedTab]}
                searchKeys={SEARCH_KEYS}
                // Skills tab keeps a constant table key (hardCodedKey) so it never remounts — the
                // risk-first sort set on mount stays put, and async risk-score/badge enrichment shows
                // via callFromOutside (re-derives from the now-enriched data.skills with the current
                // query) instead of a remount. Both sort and search survive enrichment.
                hardCodedKey={isSkills}
                callFromOutside={isSkills ? skillEnrichVersion : undefined}
                sortOptions={riskFirstSortOptions}
                resourceName={resourceName}
                filters={[]}
                headers={filterHeaders}
                selectable={false}
                mode={IndexFiltersMode.Default}
                headings={headers}
                useNewRow={true}
                condensedHeight={true}
                disambiguateLabel={disambiguateLabel}
                prettifyPageData={(pageData) => pageData}
                onRowClick={handleRowClick}
                {...commonTabProps}
            />
        );
    }, [data, selectedTab, skillEnrichVersion, headers, filterHeaders, riskFirstSortOptions, disambiguateLabel, handleRowClick, tableTabs, selected]);

    const pageTitle = useMemo(() => (
        <TitleWithInfo
            tooltipContent="View agentic assets"
            titleText={"Agentic assets"}
            docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
        />
    ), []);

    const layoutToggle = (
        <NewLayoutTooltip checked={false} onChange={() => { setAgenticNewLayout(true); navigate("/dashboard/observe/agentic-assets"); }} />
    );

    if (loading) {
        return (
            <PageWithMultipleCards
                title={pageTitle}
                isFirstPage={true}
                secondaryActions={layoutToggle}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <PageWithMultipleCards
            title={pageTitle}
            isFirstPage={true}
            secondaryActions={layoutToggle}
            components={[summaryComponent, tableComponent]}
        />
    );
}

export default Endpoints;

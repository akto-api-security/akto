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
import { CollectionIcon } from "../../../components/shared/CollectionIcon";
import useTable from "@/apps/dashboard/components/tables/TableContext";
import {
    getHeaders,
    sortOptions,
    resourceName,
    INVENTORY_PATH,
    INVENTORY_FILTER_KEY,
    PAGE_LIMIT,
    groupCollectionsByAgent,
    groupCollectionsByService,
    groupCollectionsBySkill,
    extractEndpointId,
    buildAgenticInventoryFilterForRow,
    fetchAndCacheSkillApiData,
} from "./constants";
import { CLIENT_TYPES, ROW_TYPES, hasPersonalAccountTag } from "./mcpClientHelper";

const definedTableTabs = ['All', 'AI Agents', 'MCP Servers', 'LLMs', 'Skills'];

function Endpoints() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState({ all: [], 'ai_agents': [], 'mcp_servers': [], llms: [], skills: [] });
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
            const groupNameDisplay = (showPersonal || showLocalMcp)
                ? (
                    <HorizontalStack gap="2" align="start" wrap={false}>
                        <Text>{group.groupName}</Text>
                        {showPersonal && <Badge size="small" status="warning">Contains personal account</Badge>}
                        {showLocalMcp && <Badge size="small" status="critical">Local MCP Server</Badge>}
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

    const applySkillRiskScores = useCallback((scoreMap, maliciousSkills, isMountedRef) => {
        if (!isMountedRef.current) return;
        setData((prev) => {
            const updatedSkills = prev.skills.map((row) => {
                const riskScore = scoreMap[row.groupName] || 0;
                const isMalicious = maliciousSkills.has(row.groupName);
                const groupNameDisplay = (
                    <HorizontalStack gap="2" align="start" wrap={false}>
                        <Text>{row.groupName}</Text>
                        {isMalicious && <Badge size="small" status="critical">Malicious</Badge>}
                    </HorizontalStack>
                );
                return {
                    ...row,
                    riskScore,
                    maxRiskScore: riskScore,
                    isMalicious,
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
        setSkillEnrichVersion((v) => v + 1);
    }, [getRiskScoreStatus]);

    const enrichSkillsWithApiRiskScores = useCallback(async (skillRows, isMountedRef = { current: true }) => {
        if (!skillRows.length) return;

        const allCollectionIds = [];
        skillRows.forEach((row) => {
            (row.collections || []).forEach((c) => {
                if (!allCollectionIds.includes(c.id)) allCollectionIds.push(c.id);
            });
        });

        const { skillScoreMap, maliciousSkills } = await fetchAndCacheSkillApiData(allCollectionIds, { api, PersistStore });

        if (!isMountedRef.current) return;
        applySkillRiskScores(skillScoreMap, maliciousSkills, isMountedRef);
    }, [applySkillRiskScores]);

    async function fetchData(isMountedRef = { current: true }) {
        try {
            setLoading(true);

            const [
                apiCollectionsResp,
                trafficInfoResp,
                riskScoreResp,
                sensitiveInfoResp,
            ] = await Promise.all([
                api.getAllCollectionsBasic(),
                api.getLastTrafficSeen(),
                api.getRiskScoreInfo(),
                api.getSensitiveInfoForCollections(),
            ]);

            if (!isMountedRef.current) return;

            const collections = apiCollectionsResp.apiCollections || [];
            setAllCollections(collections);

            const trafficMap = trafficInfoResp || {};
            const riskScoreMap = riskScoreResp?.riskScoreOfCollectionsMap || {};
            const sensitiveMap = sensitiveInfoResp?.sensitiveSubtypesInCollection || {};

            const agentGroups = groupCollectionsByAgent(collections, trafficMap, sensitiveMap, riskScoreMap);
            const serviceGroups = groupCollectionsByService(collections, trafficMap, sensitiveMap, riskScoreMap);
            const skillGroups = groupCollectionsBySkill(collections, trafficMap, sensitiveMap, riskScoreMap);

            const prettifiedAgents = prettifyGroupData(agentGroups);
            const prettifiedServices = prettifyGroupData(serviceGroups);
            const prettifiedSkills = prettifyGroupData(skillGroups);

            const agentGroupKeys = new Set(prettifiedAgents.map((a) => a.groupKey));
            const servicesToShow = prettifiedServices.filter((s) => !agentGroupKeys.has(s.groupKey));

            const allData = [...prettifiedAgents, ...servicesToShow, ...prettifiedSkills];

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
        const tableKey = selectedTab === "skills" ? `table-skills-${skillEnrichVersion}` : "table";
        return (
            <GithubSimpleTable
                key={tableKey}
                pageLimit={PAGE_LIMIT}
                data={data[selectedTab]}
                sortOptions={sortOptions}
                resourceName={resourceName}
                filters={[]}
                headers={headers}
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
    }, [data, selectedTab, skillEnrichVersion, headers, disambiguateLabel, handleRowClick, tableTabs, selected]);

    const pageTitle = useMemo(() => (
        <TitleWithInfo
            tooltipContent="View agentic assets"
            titleText={"Agentic assets"}
            docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
        />
    ), []);

    if (loading) {
        return (
            <PageWithMultipleCards
                title={pageTitle}
                isFirstPage={true}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <PageWithMultipleCards
            title={pageTitle}
            isFirstPage={true}
            components={[summaryComponent, tableComponent]}
        />
    );
}

export default Endpoints;

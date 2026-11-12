import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Badge, HorizontalGrid, VerticalStack } from "@shopify/polaris";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import AskBar from "@/apps/dashboard/components/shared/AskBar";
import useElementSize from "@/apps/dashboard/components/shared/useElementSize";
import { ASK_ATLAS_PLACEHOLDER, ASK_ATLAS_SUGGESTIONS } from "../devices_employees/constants";
import { CATEGORY_ORDER as PEOPLE_TOOL_TYPES } from "../devices_employees/peopleConstants";
import { ASSET_STATUS, ATLAS_DISCOVERY_CHAT_SCOPE, DEFAULT_ADOPTION_DAYS, DEVICES_PATH, LOAD_STATE, SUBTITLES } from "./constants";
import { agentMcpMatrix, decisionsPageSize, teamMatrix } from "./transform";
import DiscoveryHeaderActions from "./shared/DiscoveryHeaderActions";
import { openNewLayoutPage } from "./shared/openInventory";
import useAssetStatusUpdate from "./shared/useAssetStatusUpdate";
import useDiscoveryOverview from "./shared/useDiscoveryOverview";
import useDiscoveryFilters, { useToolsExport } from "./shared/useDiscoveryFilters";
import useFleetStats from "./shared/useFleetStats";
import AdoptionCard from "./cards/AdoptionCard";
import AssetFlyout from "./cards/AssetFlyout";
import DecisionsCard from "./cards/DecisionsCard";
import MatrixCard from "./cards/MatrixCard";
import NewToolsTrendCard from "./cards/NewToolsTrendCard";
import RiskReachCard from "./cards/RiskReachCard";
import RiskyConfigsCard from "./cards/RiskyConfigsCard";
import StatTiles from "./cards/StatTiles";
import StatusDonutCard from "./cards/StatusDonutCard";
import SupplyChainCard from "./cards/SupplyChainCard";
import observeFunc from "@/apps/dashboard/pages/observe/transform";

// The side-by-side heatmaps end at the same height: the one with fewer rows gets taller cells.
const AGENT_CELL_HEIGHT = 36;
const TEAM_CELL_HEIGHT = { min: 28, max: 56 };
function teamCellHeightFor(agentMatrix, teamsMatrix) {
    if (!agentMatrix?.rows.length || !teamsMatrix?.rows.length) return TEAM_CELL_HEIGHT.min + 4;
    const matched = Math.round((AGENT_CELL_HEIGHT * agentMatrix.rows.length) / teamsMatrix.rows.length);
    return Math.min(TEAM_CELL_HEIGHT.max, Math.max(TEAM_CELL_HEIGHT.min, matched));
}

function AgenticDiscoveryOverview() {
    const navigate = useNavigate();
    const filters = useDiscoveryFilters();
    const { deviceScope } = filters;
    const fleet = useFleetStats(deviceScope);
    const [refreshKey, setRefreshKey] = useState(0);
    const { data, dataScope, failed } = useDiscoveryOverview(deviceScope, refreshKey);
    const [openRow, setOpenRow] = useState(null);
    const { exportTools, exporting } = useToolsExport(filters);
    const [sideColumnRef, { height: sideColumnHeight }] = useElementSize();

    const refresh = useCallback(() => setRefreshKey((key) => key + 1), []);
    const { update, pending } = useAssetStatusUpdate(refresh);

    // Each card has its own state: one failed section must not blank the page.
    const statusOf = (name) => {
        if (failed) return LOAD_STATE.ERROR;
        if (!data) return LOAD_STATE.LOADING;
        return data.unavailableSources?.includes(name) || data[name] === undefined ? LOAD_STATE.ERROR : LOAD_STATE.READY;
    };

    // A question another card sends to the Ask bar: a new id asks again, and the bar scrolls into view.
    const [askRequest, setAskRequest] = useState(null);
    const askRef = useRef(null);
    const askAtlas = useCallback((question) => {
        setAskRequest((current) => ({ id: (current?.id || 0) + 1, question }));
        askRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    }, []);

    // A team's employees on Devices & employees, narrowed to a tool type when that page has one for the category (IDE assistants and skills do not).
    const openTeam = (team, category) => {
        const toolType = PEOPLE_TOOL_TYPES.includes(category) ? `&category=${category}` : "";
        openNewLayoutPage(navigate, `${DEVICES_PATH}?team=${encodeURIComponent(team)}${toolType}`);
    };

    const closeFlyout = useCallback(() => setOpenRow(null), []);
    // A tool may not exist in the new device scope, so its flyout would only show an error.
    useEffect(() => { setOpenRow(null); }, [deviceScope]);

    const agentMatrix = useMemo(() => (data?.agentMcpMatrix ? agentMcpMatrix(data.agentMcpMatrix) : null), [data]);
    const teamsMatrix = useMemo(() => (data?.teamCategoryMatrix ? teamMatrix(data.teamCategoryMatrix) : null), [data]);
    const teamCellHeight = teamCellHeightFor(agentMatrix, teamsMatrix);

    const components = [
        <AssetFlyout
            key="flyout"
            row={openRow}
            deviceScope={deviceScope}
            refreshKey={refreshKey}
            onClose={closeFlyout}
            onUpdated={refresh}
        />,
        <div key="ask" ref={askRef}>
            <AskBar label="Ask Atlas" placeholder={ASK_ATLAS_PLACEHOLDER} suggestions={ASK_ATLAS_SUGGESTIONS} chatScope={ATLAS_DISCOVERY_CHAT_SCOPE} request={askRequest} />
        </div>,
        <StatTiles key="tiles" tiles={data?.tiles} deviceScope={deviceScope} fleet={fleet} />,
        <HorizontalGrid key="decisions" columns={{ xs: 1, md: ["twoThirds", "oneThird"] }} gap="4">
            <DecisionsCard
                deviceScope={deviceScope}
                refreshKey={refreshKey}
                pageSize={(data && dataScope === deviceScope) || failed ? decisionsPageSize(sideColumnHeight) : 0}
                pending={pending}
                onOpen={setOpenRow}
                onApprove={(row) => update([{ key: row.key, status: row.status }], ASSET_STATUS.APPROVED, row.name)}
                onBlock={(row) => update([{ key: row.key, status: row.status }], ASSET_STATUS.BLOCKED, row.name)}
            />
            <div>
                {/* Measured: the decisions list fits as many rows as this column is tall. */}
                <div ref={sideColumnRef}>
                    <VerticalStack gap="4">
                        <AdoptionCard rows={data?.adoption} windowDays={data?.adoptionWindowDays} status={statusOf("adoption")} onOpen={setOpenRow} />
                        <RiskyConfigsCard rows={data?.riskyConfigs} status={statusOf("riskyConfigs")} onAsk={askAtlas} />
                    </VerticalStack>
                </div>
            </div>
        </HorizontalGrid>,
        <HorizontalGrid key="trend" columns={{ xs: 1, md: 2 }} gap="4">
            <NewToolsTrendCard periods={data?.newToolsTrend} status={statusOf("newToolsTrend")} />
            <StatusDonutCard counts={data?.statusCounts} status={statusOf("statusCounts")} />
        </HorizontalGrid>,
        <RiskReachCard key="reach" rows={data?.riskReach} status={statusOf("riskReach")} onOpen={setOpenRow} />,
        <SupplyChainCard key="chain" chain={data?.supplyChain} status={statusOf("supplyChain")} />,
        <HorizontalGrid key="matrices" columns={{ xs: 1, md: 2 }} gap="4">
            <MatrixCard
                title="Agent to MCP server connections"
                subtitle={SUBTITLES.agentMcp}
                showScale
                scaleLabel="Approved or under review"
                dangerScaleLabel="Unapproved"
                tipUnit="device"
                status={statusOf("agentMcpMatrix")}
                matrix={agentMatrix}
                emptyText="No agent is connected to an MCP server yet."
                labelWidth={120}
                onRowClick={(row) => setOpenRow(row.source)}
                onCellClick={(row, column) => setOpenRow(column.source || row.source)}
                cellHeight={AGENT_CELL_HEIGHT}
                minCellWidth={76}
            />
            <MatrixCard
                title="Adoption by team"
                subtitle={SUBTITLES.teams(data?.adoptionWindowDays || DEFAULT_ADOPTION_DAYS)}
                showScale
                status={statusOf("teamCategoryMatrix")}
                matrix={teamsMatrix}
                emptyText="Tag devices with a team to see adoption by team."
                format={(value) => `${value}%`}
                emptyCellText="0%"
                labelWidth={110}
                onRowClick={(row) => openTeam(row.label)}
                onCellClick={(row, column) => openTeam(row.label, column.id)}
                cellHeight={teamCellHeight}
                minCellWidth={56}
            />
        </HorizontalGrid>,
    ];

    return (
        <PageWithMultipleCards
            title="Agentic discovery"
            titleMetadata={<Badge status="info">Beta</Badge>}
            subtitle={`Every AI agent, MCP server, skill and browser AI app${fleet.managedDevices === null ? "" : ` on ${observeFunc.formatNumberWithCommas(fleet.managedDevices)} managed devices`}`}
            isFirstPage
            secondaryActions={<DiscoveryHeaderActions filters={filters} onExport={exportTools} exporting={exporting} />}
            components={components}
        />
    );
}

export default AgenticDiscoveryOverview;

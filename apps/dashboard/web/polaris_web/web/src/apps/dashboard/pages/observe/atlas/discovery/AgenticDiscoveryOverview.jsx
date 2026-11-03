import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { HorizontalGrid, VerticalStack } from "@shopify/polaris";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import AskBar from "@/apps/dashboard/components/shared/AskBar";
import useElementHeight from "@/apps/dashboard/components/shared/useElementHeight";
import func from "@/util/func";
import { ASK_ATLAS_PLACEHOLDER, ASK_ATLAS_SUGGESTIONS } from "../devices_employees/constants";
import { ATLAS_DISCOVERY_CHAT_SCOPE, SUBTITLES } from "./constants";
import discoveryApi from "./discoveryApi";
import { agentMcpMatrix, decisionsPageSize, teamMatrix } from "./transform";
import DiscoveryHeaderActions from "./shared/DiscoveryHeaderActions";
import useAssetStatusUpdate from "./shared/useAssetStatusUpdate";
import useDiscoveryFilters, { useToolsExport } from "./shared/useDiscoveryFilters";
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

// The side-by-side heatmaps end at the same height: the one with fewer rows gets taller cells.
const AGENT_CELL_HEIGHT = 36;
const TEAM_CELL_HEIGHT = { min: 28, max: 56 };
function teamCellHeightFor(agentMatrix, teamsMatrix) {
    if (!agentMatrix?.rows.length || !teamsMatrix?.rows.length) return TEAM_CELL_HEIGHT.min + 4;
    const matched = Math.round((AGENT_CELL_HEIGHT * agentMatrix.rows.length) / teamsMatrix.rows.length);
    return Math.min(TEAM_CELL_HEIGHT.max, Math.max(TEAM_CELL_HEIGHT.min, matched));
}

const STATUS_LOADING = "loading";
const STATUS_ERROR = "error";
const STATUS_READY = "ready";

function AgenticDiscoveryOverview() {
    const filters = useDiscoveryFilters();
    const { startTimestamp, endTimestamp, deviceScope } = filters;
    const filterKey = `${startTimestamp}|${endTimestamp}|${deviceScope}`;
    const [data, setData] = useState(null);
    // The filters `data` was fetched for; the decisions list waits for data of the current ones.
    const [dataFilterKey, setDataFilterKey] = useState("");
    // The filters whose overview failed to load; a new range or scope starts clean.
    const [failedFilterKey, setFailedFilterKey] = useState("");
    const failed = failedFilterKey === filterKey;
    const [refreshKey, setRefreshKey] = useState(0);
    const [openRow, setOpenRow] = useState(null);
    const { exportTools, exporting } = useToolsExport(filters);
    const [sideColumnRef, sideColumnHeight] = useElementHeight();
    const loadedFor = useRef("");
    const dataRef = useRef(null);
    dataRef.current = data;

    useEffect(() => {
        let ignore = false;
        // A new range or scope starts from spinners; a refresh after a decision keeps the cards on screen.
        if (loadedFor.current !== filterKey) {
            loadedFor.current = filterKey;
            setData(null);
        }
        discoveryApi.fetchAtlasDiscoveryOverview(startTimestamp, endTimestamp, deviceScope)
            .then((response) => {
                if (ignore) return;
                setData(response || {});
                setDataFilterKey(filterKey);
                setFailedFilterKey("");
            })
            .catch(() => {
                if (ignore) return;
                // A failed refresh keeps the cards that are already on screen.
                if (!dataRef.current) setFailedFilterKey(filterKey);
                func.setToast(true, true, "Failed to load agentic discovery");
            });
        return () => { ignore = true; };
    }, [startTimestamp, endTimestamp, deviceScope, refreshKey]);

    const refresh = useCallback(() => setRefreshKey((key) => key + 1), []);
    const { update, pending } = useAssetStatusUpdate(refresh);

    // Each card has its own state: one failed section must not blank the page.
    const statusOf = (name) => {
        if (failed) return STATUS_ERROR;
        if (!data) return STATUS_LOADING;
        return data.unavailableSources?.includes(name) || data[name] === undefined ? STATUS_ERROR : STATUS_READY;
    };

    const closeFlyout = useCallback(() => setOpenRow(null), []);

    const agentMatrix = useMemo(() => (data?.agentMcpMatrix ? agentMcpMatrix(data.agentMcpMatrix) : null), [data]);
    const teamsMatrix = useMemo(() => (data?.teamCategoryMatrix ? teamMatrix(data.teamCategoryMatrix) : null), [data]);
    const teamCellHeight = teamCellHeightFor(agentMatrix, teamsMatrix);

    const components = [
        <AssetFlyout
            key="flyout"
            row={openRow}
            startTimestamp={startTimestamp}
            endTimestamp={endTimestamp}
            deviceScope={deviceScope}
            refreshKey={refreshKey}
            onClose={closeFlyout}
            onUpdated={refresh}
        />,
        <AskBar key="ask" label="Ask Atlas" placeholder={ASK_ATLAS_PLACEHOLDER} suggestions={ASK_ATLAS_SUGGESTIONS} chatScope={ATLAS_DISCOVERY_CHAT_SCOPE} />,
        <StatTiles key="tiles" tiles={data?.tiles} />,
        <HorizontalGrid key="decisions" columns={{ xs: 1, md: ["twoThirds", "oneThird"] }} gap="4">
            <DecisionsCard
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
                deviceScope={deviceScope}
                refreshKey={refreshKey}
                pageSize={(data && dataFilterKey === filterKey) || failed ? decisionsPageSize(sideColumnHeight) : 0}
                pending={pending}
                onOpen={setOpenRow}
                onApprove={(row) => update([{ key: row.key, status: row.status }], "APPROVED", row.name)}
                onBlock={(row) => update([{ key: row.key, status: row.status }], "BLOCKED", row.name)}
            />
            <div>
                {/* Measured: the decisions list fits as many rows as this column is tall. */}
                <div ref={sideColumnRef}>
                    <VerticalStack gap="4">
                        <AdoptionCard rows={data?.adoption} status={statusOf("adoption")} onOpen={setOpenRow} />
                        <RiskyConfigsCard rows={data?.riskyConfigs} status={statusOf("riskyConfigs")} />
                    </VerticalStack>
                </div>
            </div>
        </HorizontalGrid>,
        <HorizontalGrid key="trend" columns={{ xs: 1, md: 2 }} gap="4">
            <NewToolsTrendCard weeks={data?.weeklyNew} status={statusOf("weeklyNew")} />
            <StatusDonutCard counts={data?.statusCounts} status={statusOf("statusCounts")} />
        </HorizontalGrid>,
        <RiskReachCard key="reach" rows={data?.riskReach} status={statusOf("riskReach")} onOpen={setOpenRow} />,
        <SupplyChainCard key="chain" chain={data?.supplyChain} status={statusOf("supplyChain")} />,
        <HorizontalGrid key="matrices" columns={{ xs: 1, md: 2 }} gap="4">
            <MatrixCard
                title="Agent to MCP server connections"
                subtitle={SUBTITLES.agentMcp}
                status={statusOf("agentMcpMatrix")}
                matrix={agentMatrix}
                emptyText="No agent is connected to an MCP server yet."
                labelWidth={120}
                cellHeight={AGENT_CELL_HEIGHT}
                minCellWidth={76}
            />
            <MatrixCard
                title="Adoption by team"
                subtitle={SUBTITLES.teams}
                showScale
                status={statusOf("teamCategoryMatrix")}
                matrix={teamsMatrix}
                emptyText="Tag devices with a team to see adoption by team."
                format={(value) => `${value}%`}
                emptyCellText="0%"
                labelWidth={110}
                cellHeight={teamCellHeight}
                minCellWidth={56}
            />
        </HorizontalGrid>,
    ];

    return (
        <PageWithMultipleCards
            title="Agentic discovery"
            subtitle={`Every AI agent, MCP server, skill, local model and browser AI app${data?.tiles ? ` on ${data.tiles.managedDevices.toLocaleString("en-US")} managed devices` : ""}`}
            isFirstPage
            secondaryActions={<DiscoveryHeaderActions filters={filters} onExport={exportTools} exporting={exporting} />}
            components={components}
        />
    );
}

export default AgenticDiscoveryOverview;

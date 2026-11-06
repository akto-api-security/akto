import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { HorizontalGrid, VerticalStack } from "@shopify/polaris";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import AskBar from "@/apps/dashboard/components/shared/AskBar";
import useElementSize from "@/apps/dashboard/components/shared/useElementSize";
import func from "@/util/func";
import { ASK_ATLAS_PLACEHOLDER, ASK_ATLAS_SUGGESTIONS } from "../devices_employees/constants";
import { ASSET_STATUS, ATLAS_DISCOVERY_CHAT_SCOPE, LOAD_STATE, SUBTITLES } from "./constants";
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

function AgenticDiscoveryOverview() {
    const filters = useDiscoveryFilters();
    const { deviceScope } = filters;
    const [data, setData] = useState(null);
    // The scope `data` was fetched for; the decisions list waits for data of the current one.
    const [dataScope, setDataScope] = useState("");
    // The scope whose overview failed to load; a new scope starts clean.
    const [failedScope, setFailedScope] = useState("");
    const failed = failedScope === deviceScope;
    const [refreshKey, setRefreshKey] = useState(0);
    const [openRow, setOpenRow] = useState(null);
    const { exportTools, exporting } = useToolsExport(filters);
    const [sideColumnRef, { height: sideColumnHeight }] = useElementSize();
    const loadedFor = useRef("");
    const dataRef = useRef(null);
    dataRef.current = data;

    useEffect(() => {
        let ignore = false;
        // A new scope starts from spinners; a refresh after a decision keeps the cards on screen.
        if (loadedFor.current !== deviceScope) {
            loadedFor.current = deviceScope;
            setData(null);
        }
        discoveryApi.fetchAtlasDiscoveryOverview(deviceScope)
            .then((response) => {
                if (ignore) return;
                setData(response || {});
                setDataScope(deviceScope);
                setFailedScope("");
            })
            .catch(() => {
                if (ignore) return;
                // A failed refresh keeps the cards that are already on screen.
                if (!dataRef.current) setFailedScope(deviceScope);
                func.setToast(true, true, "Failed to load agentic discovery");
            });
        return () => { ignore = true; };
    }, [deviceScope, refreshKey]);

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
        <StatTiles key="tiles" tiles={data?.tiles} />,
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
                        <AdoptionCard rows={data?.adoption} status={statusOf("adoption")} onOpen={setOpenRow} />
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
            subtitle={`Every AI agent, MCP server, skill and browser AI app${data?.tiles ? ` on ${data.tiles.managedDevices.toLocaleString("en-US")} managed devices` : ""}`}
            isFirstPage
            secondaryActions={<DiscoveryHeaderActions filters={filters} onExport={exportTools} exporting={exporting} />}
            components={components}
        />
    );
}

export default AgenticDiscoveryOverview;

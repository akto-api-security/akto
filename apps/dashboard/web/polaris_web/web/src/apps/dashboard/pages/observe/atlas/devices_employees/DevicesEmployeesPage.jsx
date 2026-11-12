import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Box, Button, Card, HorizontalGrid } from "@shopify/polaris";
import { ExportMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import settingRequests from "../../../settings/api";
import observeApi from "../../api";
import PageWithMultipleCards from "../../../../components/layouts/PageWithMultipleCards";
import AskBar from "../../../../components/shared/AskBar";
import { EndpointBrowserTrendChart } from "../../agentic/TrendCharts";
import LocalStore from "../../../../../main/LocalStorageStore";
import NewLayoutTooltip from "../../agentic/NewLayoutTooltip";
import SummaryCardInfo from "../../../../components/shared/SummaryCardInfo";
import AgentHealthCard from "./AgentHealthCard";
import AiToolsByOsCard from "./AiToolsByOsCard";
import PeopleTable from "./PeopleTable";
import RiskHistogramCard from "./RiskHistogramCard";
import ViolationsBySeverityCard from "./ViolationsBySeverityCard";
import PeopleFlyout from "./PeopleFlyout";
import { ASK_ATLAS_PLACEHOLDER, ASK_ATLAS_SUGGESTIONS, ATLAS_CHAT_SCOPE } from "./constants";
import {
    CATEGORY_ORDER, DEVICE_SCOPES, EMPTY_PEOPLE_FILTERS, EXPORT_PAGE_SIZE, EXPORT_ROW_LIMIT, HIGH_RISK_THRESHOLD, MIN_SEARCH_LENGTH,
} from "./peopleConstants";
import transform from "./transform";

function DevicesEmployeesPage() {
    const deviceScope = DEVICE_SCOPES[0].value;
    const navigate = useNavigate();
    const newLayout = LocalStore((state) => state.agenticNewLayout);
    const setAgenticNewLayout = LocalStore((state) => state.setAgenticNewLayout);
    const [stats, setStats] = useState(null);
    const [overview, setOverview] = useState(null);
    const [overviewStatus, setOverviewStatus] = useState("loading");
    const [tab, setTab] = useState("employees");
    const [filters, setFilters] = useState(EMPTY_PEOPLE_FILTERS);
    const [search, setSearch] = useState("");
    const [openTarget, setOpenTarget] = useState(null);
    const [exporting, setExporting] = useState(false);
    const [trendStats, setTrendStats] = useState(null);
    const [severityCounts, setSeverityCounts] = useState(null);
    const [severityStatus, setSeverityStatus] = useState("loading");
    const [queuedModuleIds, setQueuedModuleIds] = useState([]);
    const [clickedStat, setClickedStat] = useState(null);

    useEffect(() => {
        let ignore = false;
        settingRequests.fetchAtlasTrend({ deviceScope })
            .then((res) => {
                if (!ignore) setTrendStats(res?.atlasTrend || null);
            })
            .catch(() => {
                if (!ignore) func.setToast(true, true, "Failed to load trend data");
            });
        return () => { ignore = true; };
    }, [deviceScope]);

    useEffect(() => {
        let ignore = false;
        observeApi.fetchHostSeverityCounts(0, 0)
            .then((rows) => {
                if (ignore) return;
                const sums = { critical: 0, high: 0, medium: 0, low: 0 };
                rows.forEach((hostCounts) => Object.keys(sums).forEach((severity) => { sums[severity] += hostCounts[severity] || 0; }));
                setSeverityCounts(sums);
                setSeverityStatus("ready");
            })
            .catch(() => {
                if (ignore) return;
                setSeverityStatus("error");
                func.setToast(true, true, "Failed to load violation counts");
            });
        return () => { ignore = true; };
    }, []);

    useEffect(() => {
        let ignore = false;
        settingRequests.fetchEndpointShieldStats({ deviceScope })
            .then((res) => {
                if (!ignore) setStats(res?.deviceStats || null);
            })
            .catch(() => {
                if (!ignore) func.setToast(true, true, "Failed to load device stats");
            });
        return () => { ignore = true; };
    }, [deviceScope]);

    useEffect(() => {
        let ignore = false;
        setOverviewStatus("loading");
        settingRequests.fetchAtlasOverview({ deviceScope })
            .then((res) => {
                if (ignore) return;
                setOverview(res?.atlasOverview || {});
                setOverviewStatus("ready");
            })
            .catch(() => {
                if (!ignore) setOverviewStatus("error");
            });
        return () => { ignore = true; };
    }, [deviceScope]);

    useEffect(() => {
        if (!newLayout) navigate("/dashboard/observe/users-and-devices", { replace: true });
    }, [navigate, newLayout]);
    const handleLayoutToggle = useCallback((value) => {
        const checked = value === true;
        setAgenticNewLayout(checked);
        if (!checked) navigate("/dashboard/observe/users-and-devices");
    }, [navigate, setAgenticNewLayout]);

    const filterOptions = useMemo(() => transform.filterOptions(overview), [overview]);
    const patchFilters = useCallback((patch) => setFilters((previous) => ({ ...previous, ...patch })), []);
    const showTable = useCallback((nextTab, patch = {}) => {
        setTab(nextTab);
        setSearch("");
        setFilters({ ...EMPTY_PEOPLE_FILTERS, ...patch });
    }, []);
    const markQueued = useCallback((moduleIds) => setQueuedModuleIds((previous) => [...new Set([...previous, ...moduleIds])]), []);
    const tableRef = useRef(null);
    // Chart and stat clicks drive the table below them, so bring it into view when one applies a filter.
    const toggleView = (nextTab, patch = {}) => {
        const sameView = tab === nextTab && JSON.stringify({ ...EMPTY_PEOPLE_FILTERS, ...patch }) === JSON.stringify(filters);
        showTable(nextTab, sameView ? {} : patch);
        if (!sameView) tableRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    };
    const onDevicesTab = tab === "devices";
    const activeHealth = onDevicesTab && filters.healthStates.length === 1 ? filters.healthStates[0] : null;
    const activeMode = onDevicesTab && filters.guardrailModes.length === 1 ? filters.guardrailModes[0] : null;
    const singleCategory = onDevicesTab && filters.osList.length === 0 && filters.categories.length === 1 ? filters.categories[0] : null;
    const activeBrowser = singleCategory === "browser" && filters.browserList.length === 1 ? filters.browserList[0] : null;
    const activeCategory = filters.browserList.length === 0 || singleCategory === "browser" ? singleCategory : null;

    const openPerson = useCallback((kind, id) => {
        const nextTab = kind === "employee" ? "employees" : "devices";
        if (nextTab !== tab) showTable(nextTab);
        setOpenTarget({ kind, id });
    }, [tab, showTable]);
    const closePerson = useCallback(() => setOpenTarget(null), []);
    const deepLinkHandled = useRef(false);
    useEffect(() => {
        if (deepLinkHandled.current) return;
        deepLinkHandled.current = true;
        const params = new URLSearchParams(window.location.search);
        const device = params.get("device");
        const employee = params.get("employee") || params.get("user");
        if (device) openPerson("device", device);
        else if (employee) openPerson("employee", employee);
    }, [openPerson]);

    // ?team=<name>[&category=agents|mcp|browser] opens the Employees tab filtered to that team and tool type (the Atlas Overview links here).
    // The filters only know their values once the overview has loaded, and the grid drops a selection it does not know, so this waits for it.
    const teamLinkHandled = useRef(false);
    useEffect(() => {
        if (teamLinkHandled.current || overviewStatus === "loading") return;
        teamLinkHandled.current = true;
        const params = new URLSearchParams(window.location.search);
        const team = params.get("team");
        const category = params.get("category");
        if (team && !params.get("device") && !params.get("employee") && !params.get("user")) {
            showTable("employees", { teams: [team], categories: CATEGORY_ORDER.includes(category) ? [category] : [] });
        }
    }, [overviewStatus, showTable]);

    const exportTable = async () => {
        const requestName = tab === "employees" ? "fetchAtlasEmployees" : "fetchAtlasDevices";
        const params = transform.peopleParams(deviceScope, filters, search.length >= MIN_SEARCH_LENGTH ? search : "");
        setExporting(true);
        try {
            const rows = [];
            for (let skip = 0; skip < EXPORT_ROW_LIMIT; skip += EXPORT_PAGE_SIZE) {
                const response = await settingRequests[requestName]({ ...params, skip, limit: EXPORT_PAGE_SIZE });
                const page = response?.atlasRows || [];
                rows.push(...page);
                if (page.length === 0 || rows.length >= (response?.atlasTotal || 0)) break;
            }
            func.saveCsv(transform.toCsvLines(tab, rows), `atlas-${tab}`);
        } catch (error) {
            func.setToast(true, true, `Failed to export ${tab}`);
        } finally {
            setExporting(false);
        }
    };

    const onSelectStat = (id) => {
        const target = transform.statCardTarget(id);
        if (!target) return;
        toggleView(target.tab, target.filters);
        setClickedStat(clickedStat === id ? null : id);
    };
    const onSelectSeverity = (severity) => toggleView("devices", { violationSeverities: [severity] });
    const onSelectMode = (mode) => toggleView("devices", { guardrailModes: [mode] });
    const onSelectBucket = ({ riskMin, riskMax }) => toggleView("employees", { riskMin, riskMax });

    return (
        <PageWithMultipleCards
            title="Devices & Employees"
            subtitle="Who uses which AI tools, on which devices, and whether Atlas is protecting them"
            isFirstPage
            secondaryActions={
                <>
                    <NewLayoutTooltip checked={newLayout} onChange={handleLayoutToggle} />
                    <Button icon={ExportMinor} onClick={exportTable} loading={exporting}>Export</Button>
                </>
            }
            components={[
                <AskBar
                    key="ask-atlas"
                    label="Ask Atlas"
                    placeholder={ASK_ATLAS_PLACEHOLDER}
                    suggestions={ASK_ATLAS_SUGGESTIONS}
                    chatScope={ATLAS_CHAT_SCOPE}
                />,
                <SummaryCardInfo key="stats-row" summaryItems={transform.buildStatItems(stats, onSelectStat, transform.activeStatCard(tab, filters, clickedStat))} />,
                <HorizontalGrid key="overview-cards" columns={{ xs: 1, md: 3 }} gap="4">
                    <AgentHealthCard
                        overview={overview}
                        status={overviewStatus}
                        activeHealth={activeHealth}
                        activeMode={activeMode}
                        onSelectHealth={(state) => toggleView("devices", { healthStates: [state] })}
                        onSelectMode={onSelectMode}
                        onUpdated={markQueued}
                        queuedIds={queuedModuleIds}
                    />
                    <AiToolsByOsCard
                        overview={overview}
                        status={overviewStatus}
                        activeCategory={activeCategory}
                        activeBrowser={activeBrowser}
                        onSelectCategory={(category) => toggleView("devices", { categories: [category] })}
                        onSelect={(os, category) => toggleView("devices", { osList: [os], categories: [category] })}
                        onSelectBrowser={(browser, category) => toggleView("devices", { browserList: [browser], categories: [category] })}
                        onSelectHook={(category) => toggleView("devices", { inferenceHooksOnly: true, categories: [category] })}
                    />
                    <RiskHistogramCard
                        overview={overview}
                        status={overviewStatus}
                        onSelectBucket={onSelectBucket}
                        onSelectHighRisk={() => toggleView("employees", { riskMin: HIGH_RISK_THRESHOLD })}
                    />
                </HorizontalGrid>,
                <HorizontalGrid key="trend-cards" columns={{ xs: "1fr", md: "2fr 1fr" }} gap="4">
                    <Card padding="0">
                        <EndpointBrowserTrendChart
                            subtitle="Managed endpoints and browser extensions by month"
                            height={250}
                            tooltip={transform.tooltip("Number of managed devices over time, split by operating system for desktop agents and by browser for browser extensions.")}
                            osTrend={trendStats?.osTrend || {}}
                            browserTrend={trendStats?.browserTrend || {}}
                            monthLabels={trendStats?.monthLabels || []}
                        />
                    </Card>
                    <ViolationsBySeverityCard counts={severityCounts} status={severityStatus} onSelect={onSelectSeverity} />
                </HorizontalGrid>,
                <Box key="people-table" ref={tableRef}>
                    <PeopleTable
                        tab={tab}
                        onTabChange={(nextTab) => showTable(nextTab)}
                        filters={filters}
                        onFiltersChange={patchFilters}
                        deviceScope={deviceScope}
                        counts={{ employees: overview?.employeeCount, devices: overview?.deviceCount }}
                        search={search}
                        onSearchChange={setSearch}
                        violationsAvailable={overview?.violationsAvailable}
                        riskAvailable={overview?.riskAvailable}
                        onOpen={openPerson}
                        selected={openTarget}
                        filterOptions={filterOptions}
                    />
                </Box>,
                <PeopleFlyout
                    key="people-flyout"
                    target={openTarget}
                    deviceScope={deviceScope}
                    onClose={closePerson}
                    onOpen={openPerson}
                    onUpdated={markQueued}
                        queuedIds={queuedModuleIds}
                />,
            ]}
        />
    );
}

export default DevicesEmployeesPage;

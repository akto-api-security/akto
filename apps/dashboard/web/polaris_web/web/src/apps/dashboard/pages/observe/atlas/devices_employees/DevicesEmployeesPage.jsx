import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, HorizontalGrid } from "@shopify/polaris";
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
    DEVICE_SCOPES, EMPTY_PEOPLE_FILTERS, EXPORT_PAGE_SIZE, EXPORT_ROW_LIMIT, HIGH_RISK_THRESHOLD, MIN_SEARCH_LENGTH, RISK_BUCKET_WIDTH,
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
    const [discovery, setDiscovery] = useState(null);
    const [openTarget, setOpenTarget] = useState(null);
    const [exporting, setExporting] = useState(false);
    const [trendStats, setTrendStats] = useState(null);
    const [severityCounts, setSeverityCounts] = useState(null);
    const [severityStatus, setSeverityStatus] = useState("loading");
    const [queuedModuleIds, setQueuedModuleIds] = useState([]);

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
        observeApi.fetchUsersAndDevicesStats()
            .then((res) => {
                if (!ignore) setDiscovery(res);
            })
            .catch(() => {
                if (!ignore) func.setToast(true, true, "Failed to load AI discovery counts");
            });
        return () => { ignore = true; };
    }, []);

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
    const toggleView = (nextTab, patch = {}) => {
        const sameView = tab === nextTab && JSON.stringify({ ...EMPTY_PEOPLE_FILTERS, ...patch }) === JSON.stringify(filters);
        showTable(nextTab, sameView ? {} : patch);
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
        if (target) toggleView(target.tab, target.filters);
    };
    const onSelectSeverity = (severity) => toggleView("devices", { violationSeverities: [severity] });
    const onSelectMode = (mode) => toggleView("devices", { guardrailModes: [mode] });
    const onSelectBucket = (index) => toggleView("employees", {
        riskMin: index * RISK_BUCKET_WIDTH,
        riskMax: (index + 1) * RISK_BUCKET_WIDTH,
    });

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
                <SummaryCardInfo key="stats-row" summaryItems={transform.buildStatItems(stats, onSelectStat, discovery)} />,
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
                            osTrend={trendStats?.osTrend || {}}
                            browserTrend={trendStats?.browserTrend || {}}
                            monthLabels={trendStats?.monthLabels || []}
                        />
                    </Card>
                    <ViolationsBySeverityCard counts={severityCounts} status={severityStatus} onSelect={onSelectSeverity} />
                </HorizontalGrid>,
                <PeopleTable
                    key="people-table"
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
                />,
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

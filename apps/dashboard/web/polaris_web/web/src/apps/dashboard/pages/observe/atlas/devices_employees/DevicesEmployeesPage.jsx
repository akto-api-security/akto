import { useState, useEffect, useCallback } from "react";
import { Button, HorizontalGrid } from "@shopify/polaris";
import { ExportMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import settingRequests from "../../../settings/api";
import PageWithMultipleCards from "../../../../components/layouts/PageWithMultipleCards";
import AskBar from "../../../../components/shared/AskBar";
import AgentHealthCard from "./AgentHealthCard";
import AiToolsByOsCard from "./AiToolsByOsCard";
import PeopleTable from "./PeopleTable";
import RiskHistogramCard from "./RiskHistogramCard";
import StatsRow from "./StatsRow";
import DeviceScopeDropdown, { DEVICE_SCOPES } from "./DeviceScopeDropdown";
import { ASK_ATLAS_PLACEHOLDER, ASK_ATLAS_SUGGESTIONS, ATLAS_CHAT_SCOPE } from "./constants";
import { EMPTY_PEOPLE_FILTERS, HIGH_RISK_THRESHOLD, RISK_BUCKET_WIDTH } from "./peopleConstants";
import transform from "./transform";

function DevicesEmployeesPage() {
    const [deviceScope, setDeviceScope] = useState(DEVICE_SCOPES[0].id);
    const [stats, setStats] = useState(null);
    const [overview, setOverview] = useState(null);
    const [overviewStatus, setOverviewStatus] = useState("loading");
    const [tab, setTab] = useState("employees");
    const [filters, setFilters] = useState(EMPTY_PEOPLE_FILTERS);
    const [search, setSearch] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        let ignore = false;
        settingRequests.fetchEndpointShieldStats({ deviceScope })
            .then((res) => {
                if (!ignore) setStats(transform.buildStatCards(res?.deviceStats));
            })
            .catch(() => {
                if (!ignore) func.setToast(true, true, "Failed to load device stats");
            });
        return () => { ignore = true; };
    }, [deviceScope, refreshKey]);

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
    }, [deviceScope, refreshKey]);

    const patchFilters = useCallback((patch) => setFilters((previous) => ({ ...previous, ...patch })), []);
    const showTable = useCallback((nextTab, patch = {}) => {
        setTab(nextTab);
        setSearch("");
        setFilters({ ...EMPTY_PEOPLE_FILTERS, ...patch });
    }, []);
    const refreshAll = useCallback(() => setRefreshKey((key) => key + 1), []);
    const toggleView = (nextTab, patch = {}) => {
        const sameView = tab === nextTab && JSON.stringify({ ...EMPTY_PEOPLE_FILTERS, ...patch }) === JSON.stringify(filters);
        showTable(nextTab, sameView ? {} : patch);
    };
    const onDevicesTab = tab === "devices";
    const activeHealth = onDevicesTab && filters.healthStates.length === 1 ? filters.healthStates[0] : null;
    const activeMode = onDevicesTab && filters.monitorMode ? "monitor" : null;

    const onSelectStat = (id) => {
        const target = transform.statCardTarget(id);
        if (target) toggleView(target.tab, target.filters);
    };
    const onSelectMode = (mode) => toggleView("devices", mode === "monitor" ? { monitorMode: true } : {});
    const onSelectBucket = (index) => toggleView("employees", {
        riskMin: index * RISK_BUCKET_WIDTH,
        riskMax: (index + 1) * RISK_BUCKET_WIDTH,
    });

    return (
        <PageWithMultipleCards
            title="Devices & employees"
            subtitle="Who uses which AI tools, on which devices, and whether Atlas is protecting them"
            isFirstPage
            secondaryActions={
                <>
                    <DeviceScopeDropdown value={deviceScope} onChange={setDeviceScope} />
                    <Button icon={ExportMinor} disabled>Export</Button>
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
                <StatsRow key="stats-row" stats={stats} onSelect={onSelectStat} />,
                <HorizontalGrid key="overview-cards" columns={{ xs: 1, md: 3 }} gap="4">
                    <AgentHealthCard
                        overview={overview}
                        status={overviewStatus}
                        activeHealth={activeHealth}
                        activeMode={activeMode}
                        onSelectHealth={(state) => toggleView("devices", { healthStates: [state] })}
                        onSelectMode={onSelectMode}
                        onUpdated={refreshAll}
                    />
                    <AiToolsByOsCard
                        overview={overview}
                        status={overviewStatus}
                        onSelect={(os, category) => toggleView("devices", { osList: [os], categories: [category] })}
                    />
                    <RiskHistogramCard
                        overview={overview}
                        status={overviewStatus}
                        onSelectBucket={onSelectBucket}
                        onSelectHighRisk={() => toggleView("employees", { riskMin: HIGH_RISK_THRESHOLD })}
                    />
                </HorizontalGrid>,
                <PeopleTable
                    key="people-table"
                    tab={tab}
                    onTabChange={(nextTab) => showTable(nextTab)}
                    filters={filters}
                    onFiltersChange={patchFilters}
                    deviceScope={deviceScope}
                    counts={{ employees: overview?.employeeCount, devices: overview?.deviceCount }}
                    refreshKey={refreshKey}
                    search={search}
                    onSearchChange={setSearch}
                    violationsAvailable={overview?.violationsAvailable}
                    riskAvailable={overview?.riskAvailable}
                />,
            ]}
        />
    );
}

export default DevicesEmployeesPage;

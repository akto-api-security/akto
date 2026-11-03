import { useState, useEffect, useCallback } from "react";
import { Button, HorizontalGrid } from "@shopify/polaris";
import { ExportMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import settingRequests from "../../../settings/api";
import PageWithMultipleCards from "../../../../components/layouts/PageWithMultipleCards";
import AskBar from "../../../../components/shared/AskBar";
import SummaryCardInfo from "../../../../components/shared/SummaryCardInfo";
import OperatorDropdown from "../../../../components/layouts/OperatorDropdown";
import AgentHealthCard from "./AgentHealthCard";
import AiToolsByOsCard from "./AiToolsByOsCard";
import PeopleTable from "./PeopleTable";
import RiskHistogramCard from "./RiskHistogramCard";
import PeopleFlyout from "./PeopleFlyout";
import { ASK_ATLAS_PLACEHOLDER, ASK_ATLAS_SUGGESTIONS, ATLAS_CHAT_SCOPE } from "./constants";
import {
    DEVICE_SCOPES, EMPTY_PEOPLE_FILTERS, EXPORT_PAGE_SIZE, EXPORT_ROW_LIMIT, HIGH_RISK_THRESHOLD, MIN_SEARCH_LENGTH, RISK_BUCKET_WIDTH,
} from "./peopleConstants";
import transform from "./transform";

function DevicesEmployeesPage() {
    const [deviceScope, setDeviceScope] = useState(DEVICE_SCOPES[0].value);
    const [stats, setStats] = useState(null);
    const [overview, setOverview] = useState(null);
    const [overviewStatus, setOverviewStatus] = useState("loading");
    const [tab, setTab] = useState("employees");
    const [filters, setFilters] = useState(EMPTY_PEOPLE_FILTERS);
    const [search, setSearch] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);
    const [openTarget, setOpenTarget] = useState(null);
    const [exporting, setExporting] = useState(false);

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
    const activeMode = onDevicesTab && filters.guardrailModes.length === 1 ? filters.guardrailModes[0] : null;
    const activeCategory = onDevicesTab && filters.osList.length === 0 && filters.categories.length === 1 ? filters.categories[0] : null;

    const openPerson = useCallback((kind, id) => {
        const nextTab = kind === "employee" ? "employees" : "devices";
        if (nextTab !== tab) showTable(nextTab);
        setOpenTarget({ kind, id });
    }, [tab, showTable]);
    const closePerson = useCallback(() => setOpenTarget(null), []);

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
                if (page.length < EXPORT_PAGE_SIZE || rows.length >= (response?.atlasTotal || 0)) break;
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
    const onSelectMode = (mode) => toggleView("devices", { guardrailModes: [mode] });
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
                    <OperatorDropdown
                        label={(DEVICE_SCOPES.find((scope) => scope.value === deviceScope) || DEVICE_SCOPES[0]).label}
                        items={DEVICE_SCOPES}
                        selected={setDeviceScope}
                        designer
                    />
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
                <SummaryCardInfo key="stats-row" summaryItems={transform.buildStatItems(stats, onSelectStat)} />,
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
                        activeCategory={activeCategory}
                        onSelectCategory={(category) => toggleView("devices", { categories: [category] })}
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
                    onOpen={openPerson}
                    selected={openTarget}
                    teamOptions={overview?.teamOptions || []}
                />,
                <PeopleFlyout
                    key="people-flyout"
                    target={openTarget}
                    deviceScope={deviceScope}
                    onClose={closePerson}
                    onOpen={openPerson}
                    onUpdated={refreshAll}
                />,
            ]}
        />
    );
}

export default DevicesEmployeesPage;

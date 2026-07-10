import { useState, useMemo, useReducer, useEffect } from "react";
import { IndexFiltersMode, Badge } from "@shopify/polaris";
import { Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import { produce } from "immer";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import DonutChart from "../../components/shared/DonutChart";
import LineChart from "../../components/charts/LineChart";
import InfoCard from "../dashboard/new_components/InfoCard";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";
import values from "@/util/values";
import { violationsHeaders, violationsSortOptions, transformApiViolations } from "./nhiViolationsData";
import ViolationDetailsPanel from "./ViolationDetailsPanel";
import observeRequests from "../observe/api";
import SpinnerCentered from "../../components/progress/SpinnerCentered";
import JiraTicketCreationModal from "../../components/shared/JiraTicketCreationModal.jsx";
import issuesFunctions from "@/apps/dashboard/pages/issues/module";
import settingFunctions from "@/apps/dashboard/pages/settings/module";

const definedTableTabs = ["All", "Open", "Fixed"];
const resourceName = { singular: "violation", plural: "violations" };

function ChartLegend({ items }) {
    return (
        <VerticalStack gap="2">
            {items.map(({ label, color, count }) => (
                <HorizontalStack key={label} gap="2" blockAlign="center">
                    <Box style={{ width:10, height:10, borderRadius:"50%", background:color, flexShrink:0 }} />
                    <Text variant="bodyMd" color="subdued">{label}</Text>
                    <Text variant="bodyMd" fontWeight="semibold">{count.toLocaleString()}</Text>
                </HorizontalStack>
            ))}
        </VerticalStack>
    );
}

function DonutCard({ title, donutData }) {
    const legendItems = Object.entries(donutData).map(([label, { text, color }]) => ({ label, color, count: text }));
    return (
        <InfoCard title={title} component={
            <HorizontalStack gap="4" blockAlign="center" wrap={false}>
                <DonutChart data={donutData} title="" size={150} pieInnerSize="55%" />
                <ChartLegend items={legendItems} />
            </HorizontalStack>
        } />
    );
}

// ── Generate chart data from violations ──────────────────────────────────────────
const generateViolationsOverTimeData = (violations) => {
    if (!violations || violations.length === 0) return null;

    // Group violations by day (7-day window from now)
    const dailyCounts = {};
    const now = Math.floor(Date.now() / 1000);
    const sevenDaysAgo = now - (7 * 24 * 60 * 60);

    // Initialize 7 days with 0 violations
    for (let i = 0; i < 7; i++) {
        const date = new Date(now * 1000);
        date.setDate(date.getDate() - (6 - i)); // Go back 6 days from today
        const dayKey = date.toISOString().split('T')[0]; // YYYY-MM-DD
        dailyCounts[dayKey] = 0;
    }

    // Count violations per day
    violations.forEach((violation) => {
        if (!violation.discoveredAt) return;

        const violDate = new Date(violation.discoveredAt * 1000);
        const dayKey = violDate.toISOString().split('T')[0];

        // Only count if within last 7 days
        if (violation.discoveredAt >= sevenDaysAgo) {
            if (dailyCounts.hasOwnProperty(dayKey)) {
                dailyCounts[dayKey]++;
            }
        }
    });

    // Convert to HighCharts format
    const chartData = Object.entries(dailyCounts)
        .sort(([dateA], [dateB]) => dateA.localeCompare(dateB))
        .map(([dateStr]) => {
            const date = new Date(dateStr + 'T00:00:00Z');
            return [date.getTime(), dailyCounts[dateStr]];
        });

    return [{
        data: chartData,
        color: "#EF4444",
        name: "Violations",
    }];
};

// ── Page ───────────────────────────────────────────────────────────────────────
const violationsPageTitle = (
    <HorizontalStack gap="2" blockAlign="center">
        <TitleWithInfo
            titleText="Violations"
            tooltipContent="Policy violations detected across all non-human identities used by your AI agents."
            docsUrl="https://ai-security-docs.akto.io/nhi-governance/violations"
        />
        <Badge status="info">Beta</Badge>
    </HorizontalStack>
);

export default function ViolationsPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab    = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab  = tableSelectedTab[window.location.pathname] || "open";

    // API fetching state
    const [rawViolations, setRawViolations] = useState([]);
    const [loading, setLoading] = useState(true);

    // UI state
    const [selectedTab, setSelectedTab]             = useState(initialSelectedTab);
    const [selected, setSelected]                   = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [selectedViolation, setSelectedViolation] = useState(null);
    const [showViolationPanel, setShowViolationPanel] = useState(false);

    // Bulk action state
    const [jiraModalActive, setJiraModalActive] = useState(false);
    const [bulkViolationIds, setBulkViolationIds] = useState([]);
    const [projId, setProjId] = useState("");
    const [issueType, setIssueType] = useState("");
    const [labelsText, setLabelsText] = useState("");
    const [jiraProjectMap, setJiraProjectMap] = useState({});

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2]
    );

    const startTimestamp = parseInt(currDateRange.period.since.getTime() / 1000);
    const endTimestamp = parseInt(currDateRange.period.until.getTime() / 1000);

    const fetchViolations = async () => {
        try {
            setLoading(true);
            const response = await observeRequests.fetchAllNhiViolations(startTimestamp, endTimestamp);
            setRawViolations(Array.isArray(response) ? response : []);
            setLoading(false);
        } catch (err) {
            console.error("Error fetching violations:", err);
            setRawViolations([]);
        } finally {
            setLoading(false);
        }
    };

    // Fetch violations from API
    useEffect(() => {
        fetchViolations();
    }, [startTimestamp, endTimestamp]);

    useEffect(() => {
        const pending = sessionStorage.getItem("nhi_pending_violation");
        if (pending) {
            sessionStorage.removeItem("nhi_pending_violation");
            try {
                setSelectedViolation(JSON.parse(pending));
                setShowViolationPanel(true);
            } catch (_) {}
        }
    }, []);

    const violationsTableData = useMemo(() => transformApiViolations(rawViolations), [rawViolations]);

    // Compute donut from actual violations data so chart always matches the table
    const severityDonutData = useMemo(() => {
        const counts = violationsTableData.filter((v) => v.status !== "Fixed").reduce((acc, v) => {
            acc[v.severity] = (acc[v.severity] || 0) + 1;
            return acc;
        }, {});
        return {
            Critical: { text: counts.Critical || 0, color: "#DF2909" },
            High:     { text: counts.High || 0,     color: "#FED3D1" },
            Medium:   { text: counts.Medium || 0,   color: "#FFD79D" },
            Low:      { text: counts.Low || 0,      color: "#E4E5E7" },
        };
    }, [violationsTableData]);

    // Generate line chart data dynamically from violations
    const violationsOverTimeData = useMemo(() => {
        if (rawViolations && rawViolations.length > 0) {
            const dynamicData = generateViolationsOverTimeData(rawViolations);
            if (dynamicData) return dynamicData;
        }
        return [];
    }, [rawViolations]);

    const dataByTab = useMemo(() => ({
        all:   violationsTableData,
        open:  violationsTableData.filter((r) => r.status !== "Fixed"),
        fixed: violationsTableData.filter((r) => r.status === "Fixed"),
    }), [violationsTableData]);

    const tableCountObj = func.getTabsCount(definedTableTabs, dataByTab);
    const tableTabs = func.getTableTabsContent(
        definedTableTabs, tableCountObj,
        (tabId) => {
            setSelectedTab(tabId);
            setTableSelectedTab({ ...tableSelectedTab, [window.location.pathname]: tabId });
        },
        selectedTab, tabsInfo
    );

    const handleBulkMarkAsFixed = async (selectedResources) => {
        try {
            await Promise.all(selectedResources.map((id) => observeRequests.markViolationAsFixed(id)));
            func.setToast(true, false, `${selectedResources.length} violation${selectedResources.length > 1 ? "s" : ""} marked as fixed`);
            await fetchViolations();
        } catch (err) {
            func.setToast(true, true, "Failed to mark violations as fixed");
        }
    };

    const handleOpenBulkJiraModal = (selectedResources) => {
        setBulkViolationIds(selectedResources);
        settingFunctions.fetchJiraIntegration().then((jiraIntegration) => {
            if (jiraIntegration.projectIdsMap !== null && Object.keys(jiraIntegration.projectIdsMap).length > 0) {
                setJiraProjectMap(jiraIntegration.projectIdsMap);
                setProjId(Object.keys(jiraIntegration.projectIdsMap)[0]);
            } else {
                setProjId(jiraIntegration.projId);
                setIssueType(jiraIntegration.issueType);
            }
            setJiraModalActive(true);
        });
    };

    const handleSaveBulkJira = async (issueId, labels) => {
        let jiraMetaData;
        try {
            jiraMetaData = issuesFunctions.prepareAdditionalIssueFieldsJiraMetaData(projId, issueType);
            if (labels !== undefined && labels && labels.trim()) {
                jiraMetaData.labels = labels.trim();
            }
        } catch (error) {
            return;
        }

        setJiraModalActive(false);
        try {
            await Promise.all(bulkViolationIds.map((id) =>
                observeRequests.createJiraTicketFromViolation(id, window.location.origin, projId, issueType, jiraMetaData)
            ));
            func.setToast(true, false, `Jira ticket${bulkViolationIds.length > 1 ? "s" : ""} created successfully`);
            await fetchViolations();
        } catch (err) {
            func.setToast(true, true, "Failed to create Jira ticket");
        }
    };

    if (loading) {
        return <SpinnerCentered />;
    }

    return (
        <>
        <PageWithMultipleCards
            title={violationsPageTitle}
            isFirstPage
            primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(d) => dispatchCurrDateRange({ type: "update", period: d.period, title: d.title, alias: d.alias })} />}
            components={[
                <Box key="charts" style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "16px" }}>
                    <InfoCard
                        title="Violations over time"
                        component={
                            violationsOverTimeData.length > 0 ? (
                                <LineChart
                                    data={violationsOverTimeData}
                                    type="line"
                                    height={220}
                                    text={true}
                                    showGridLines={true}
                                    exportingDisabled={true}
                                    defaultChartOptions={{
                                        xAxis: {
                                            type: "datetime",
                                            dateTimeLabelFormats: { day: "%a" },
                                            title: { text: null },
                                            visible: true,
                                            gridLineWidth: 0,
                                        },
                                        yAxis: {
                                            title: { text: "Violations" },
                                            gridLineWidth: 1,
                                            min: 0,
                                        },
                                        legend: { enabled: true },
                                    }}
                                />
                            ) : (
                                <Box style={{ height: 220, display: "flex", alignItems: "center", justifyContent: "center" }}>
                                    <Text variant="bodyMd" color="subdued">No violations in the selected time range</Text>
                                </Box>
                            )
                        }
                    />
                    <DonutCard title="Violations by severity" donutData={severityDonutData} />
                </Box>,

                <GithubSimpleTable
                    key="violations-table"
                    data={dataByTab[selectedTab]}
                    headers={violationsHeaders}
                    resourceName={resourceName}
                    sortOptions={violationsSortOptions}
                    filters={[]}
                    selectable={true}
                    mode={IndexFiltersMode.Default}
                    headings={violationsHeaders}
                    useNewRow={true}
                    condensedHeight={true}
                    tableTabs={tableTabs}
                    onSelect={(i) => setSelected(i)}
                    selected={selected}
                    promotedBulkActions={(selectedResources) => [
                        { content: "Mark as fixed", onAction: () => handleBulkMarkAsFixed(selectedResources) },
                        { content: "Open Jira ticket", onAction: () => handleOpenBulkJiraModal(selectedResources) },
                    ]}
                    onRowClick={(r) => { setSelectedViolation(r); setShowViolationPanel(true); }}
                    rowClickable={true}
                />,
            ]}
        />
        {selectedViolation && (
            <ViolationDetailsPanel
                row={selectedViolation}
                show={showViolationPanel}
                setShow={setShowViolationPanel}
                onUpdated={fetchViolations}
            />
        )}
        <JiraTicketCreationModal
            activator={<div />}
            modalActive={jiraModalActive}
            setModalActive={setJiraModalActive}
            handleSaveAction={handleSaveBulkJira}
            jiraProjectMaps={jiraProjectMap}
            setProjId={setProjId}
            setIssueType={setIssueType}
            projId={projId}
            issueType={issueType}
            labelsText={labelsText}
            setLabelsText={setLabelsText}
        />
        </>
    );
}

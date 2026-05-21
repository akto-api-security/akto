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
import { violationsHeaders, violationsSortOptions, SEV_ORD, sevBadge, IdentityIcon, AgentIcon, PolicyCell } from "./nhiViolationsData";
import ViolationDetailsPanel from "./ViolationDetailsPanel";
import observeRequests from "../observe/api";
import { formatRelativeTime } from "./nhiUtils";
import SpinnerCentered from "../../components/progress/SpinnerCentered";
import { extractIdentityName, getFirstIdentityName } from "./identityHelper";

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

// ── Transform API violations to table format ───────────────────────────────────

const transformApiViolations = (apiViolations) => {
    return apiViolations
        .sort((a, b) => SEV_ORD[b.severity] - SEV_ORD[a.severity])
        .map((v, i) => {
            // Extract hex ID - use hexId field from API response (like updateAuditData does)
            const violationHexId = v.hexId || v.id;

            // Transform policy array to object format
            const policyObj = v.policy && Array.isArray(v.policy)
                ? {
                    primary: v.policy[0] || "N/A",
                    extra: Math.max(0, v.policy.length - 1),
                    extras: v.policy.slice(1) || [],
                  }
                : v.policy;

            // Extract identity name from new format { id: ObjectId, identityName: "name" }
            const firstIdentityName = getFirstIdentityName(v.identities);

            return {
                ...v,
                id: violationHexId,
                violation: v.violationType,
                identity: firstIdentityName,
                identities: v.identities,
                discovered: formatRelativeTime(v.discoveredAt, "Unknown"),
                severityOrder: SEV_ORD[v.severity] || 0,
                policy: policyObj,
                violationComp: <Text variant="bodyMd" fontWeight="medium">{v.violationType}</Text>,
                identityComp: (
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <IdentityIcon name={firstIdentityName} />
                        <Text variant="bodyMd">{firstIdentityName}</Text>
                    </HorizontalStack>
                ),
                agentComp: (
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <AgentIcon name={v.agentName} />
                        <Text variant="bodyMd">{v.agentName}</Text>
                    </HorizontalStack>
                ),
                severityComp: sevBadge(v.severity),
                policyComp: <PolicyCell policy={policyObj} />,
            };
        });
};

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
    <TitleWithInfo
        titleText="Violations"
        tooltipContent="Policy violations detected across all non-human identities used by your AI agents."
        docsUrl="https://ai-security-docs.akto.io/nhi-governance/violations"
    />
);

export default function ViolationsPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab    = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab  = tableSelectedTab[window.location.pathname] || "open";

    // API fetching state
    const [rawViolations, setRawViolations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // UI state
    const [selectedTab, setSelectedTab]             = useState(initialSelectedTab);
    const [selected, setSelected]                   = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [selectedViolation, setSelectedViolation] = useState(null);
    const [showViolationPanel, setShowViolationPanel] = useState(false);

    // Fetch violations from API
    useEffect(() => {
        const fetchViolations = async () => {
            try {
                setLoading(true);
                setError(null);

                // Fetch violations from API
                const response = await observeRequests.fetchAllNhiViolations();

                if (response && Array.isArray(response) && response.length > 0) {
                    setRawViolations(response);
                } else if (Array.isArray(response)) {
                    // Empty array response
                    setRawViolations([]);
                } else {
                    setRawViolations([]);
                }
            } catch (err) {
                console.error("Error fetching violations:", err);
                setError(err.message);
                setRawViolations([]);
            } finally {
                setLoading(false);
            }
        };

        fetchViolations();
    }, []);

    const violationsTableData = useMemo(() => transformApiViolations(rawViolations), [rawViolations]);

    // Compute donut from actual violations data so chart always matches the table
    const severityDonutData = useMemo(() => {
        const counts = violationsTableData.reduce((acc, v) => {
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
        // If we have API data, generate from it
        if (rawViolations && rawViolations.length > 0) {
            const dynamicData = generateViolationsOverTimeData(rawViolations);
            if (dynamicData) return dynamicData;
        }
        return [];
    }, [rawViolations]);

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

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2]
    );

    const dataByTab = useMemo(() => ({
        all:   violationsTableData,
        open:  violationsTableData.filter((r) => r.status === "Open"),
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
                    promotedBulkActions={() => [
                        { content: "Mark as fixed", onAction: () => {} },
                        { content: "Open Jira ticket", onAction: () => {} },
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
            />
        )}
        </>
    );
}

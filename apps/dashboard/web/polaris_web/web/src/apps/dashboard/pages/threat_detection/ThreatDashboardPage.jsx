import { useEffect, useReducer, useState, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards"
import { Box, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris';
import SpinnerCentered from '../../components/progress/SpinnerCentered';
import DateRangeFilter from '../../components/layouts/DateRangeFilter';
import { produce } from 'immer';
import func from '@/util/func';
import values from "@/util/values";
import observeFunc from '../observe/transform';
import DonutChart from '../../components/shared/DonutChart';
import BarGraph from '../../components/charts/BarGraph';
import InfoCard from '../dashboard/new_components/InfoCard';
import api from './api';
import { formatCategoryName, openThreatActivityPage } from './utils/threatDashboardUtils';

import ThreatDetectionOverTimeChart from './components/ThreatDetectionOverTimeChart';
import ThreatTabbedSection from './components/ThreatTabbedSection';
import TopStatisticsSidebar from './components/TopStatisticsSidebar';

const CATEGORY_COLORS = [
    "#E45858", "#5B8DEF", "#F5A623", "#7B61FF", "#2ECC71",
    "#E67E22", "#3498DB", "#9B59B6", "#1ABC9C", "#E74C3C",
];

function ThreatDashboardPage() {
    const [loading, setLoading] = useState(true);

    // Bottom charts data
    const [categoryData, setCategoryData] = useState([]);
    const [severityDistribution, setSeverityDistribution] = useState({});

    const [searchParams] = useSearchParams();
    const getInitialDateRange = () => {
        const rangeAlias = searchParams.get('range');
        if (rangeAlias) {
            const preset = values.ranges.find((r) => r.alias === rangeAlias);
            if (preset) return preset;
        }
        const sinceParam = searchParams.get('since');
        const untilParam = searchParams.get('until');
        if (sinceParam != null && untilParam != null) {
            const sinceTs = parseInt(sinceParam, 10);
            const untilTs = parseInt(untilParam, 10);
            if (!Number.isNaN(sinceTs) && !Number.isNaN(untilTs)) {
                const sinceDate = new Date(sinceTs * 1000);
                const untilDate = new Date(untilTs * 1000);
                const title = sinceDate.toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' }) + " - " + untilDate.toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' });
                return { alias: "custom", title, period: { since: sinceDate, until: untilDate } };
            }
        }
        return values.ranges[2];
    };
    const initialDateRange = getInitialDateRange();
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialDateRange);

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    const fetchData = useCallback(async () => {
        setLoading(true)
        try {
            const [categoryResp, severityResp] = await Promise.all([
                api.fetchThreatCategoryCount(startTimestamp, endTimestamp),
                api.fetchCountBySeverity(startTimestamp, endTimestamp),
            ]);

            // Top Categories bar chart data
            if (categoryResp?.categoryCounts) {
                const subcategoryMap = {};
                categoryResp.categoryCounts.forEach((item) => {
                    const sub = item.subCategory || item.category || "Unknown";
                    if (!subcategoryMap[sub]) {
                        subcategoryMap[sub] = { rawName: sub, count: 0 };
                    }
                    subcategoryMap[sub].count += (item.count || 0);
                });
                const sorted = Object.values(subcategoryMap)
                    .map((entry, idx) => ({
                        text: formatCategoryName(entry.rawName).length > 12
                            ? formatCategoryName(entry.rawName).substring(0, 12)
                            : formatCategoryName(entry.rawName),
                        value: entry.count,
                        color: CATEGORY_COLORS[idx % CATEGORY_COLORS.length],
                        filterKey: entry.rawName,
                    }))
                    .sort((a, b) => b.value - a.value)
                    .slice(0, 5);
                setCategoryData(sorted);
            }

            // Severity donut data
            if (severityResp?.categoryCounts && Array.isArray(severityResp.categoryCounts)) {
                const severityLevels = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
                const formattedSeverity = {};
                severityLevels.forEach((severity) => {
                    formattedSeverity[severity] = {
                        text: 0,
                        color: observeFunc.getColorForSensitiveData(severity),
                        filterKey: severity,
                    };
                });
                severityResp.categoryCounts.forEach((item) => {
                    const raw = item.subCategory || item.severity || '';
                    const severity = String(raw).toUpperCase();
                    if (severity && formattedSeverity[severity]) {
                        formattedSeverity[severity].text = item.count || 0;
                    }
                });
                setSeverityDistribution(formattedSeverity);
            }
        } catch (err) {
            setCategoryData([]);
            setSeverityDistribution({});
        } finally {
            setLoading(false);
        }
    }, [startTimestamp, endTimestamp]);

    useEffect(() => {
        fetchData()
    }, [fetchData])

    // Top Categories bar chart
    const topCategoriesCard = (
        <InfoCard
            title="Top Categories"
            titleToolTip="Most common threat categories by volume"
            component={
                categoryData.length > 0 ? (
                    <BarGraph
                        data={categoryData}
                        backgroundColor="transparent"
                        yAxisTitle="# of APIs"
                        showYAxis={true}
                        showGridLines={true}
                        barGap={0.1}
                        defaultChartOptions={{ legend: { enabled: false } }}
                        onBarClick={(_name, custom) => {
                            if (custom?.filterKey) openThreatActivityPage({ latestAttack: custom.filterKey });
                        }}
                    />
                ) : (
                    <Box padding="8">
                        <Text variant="bodySm" color="subdued">No data available</Text>
                    </Box>
                )
            }
        />
    );

    // Severity donut chart
    const severityCard = (
        <InfoCard
            title="Threat by severity"
            titleToolTip="Distribution of threats by severity level"
            component={
                <VerticalStack gap="3">
                    <HorizontalStack align="center">
                        <DonutChart
                            data={severityDistribution}
                            navUrl="/dashboard/protection/threat-activity"
                            size={200}
                            pieInnerSize="50%"
                        />
                    </HorizontalStack>
                    <HorizontalStack gap="4" align="center" wrap>
                        {Object.entries(severityDistribution).map(([label, item]) => (
                            <HorizontalStack gap="1" blockAlign="center" key={label}>
                                <span style={{ background: item.color, borderRadius: "50%", width: "var(--p-space-2, 0.5rem)", height: "var(--p-space-2, 0.5rem)", display: "inline-block" }} />
                                <Text variant="bodySm">{label}</Text>
                            </HorizontalStack>
                        ))}
                    </HorizontalStack>
                </VerticalStack>
            }
        />
    );

    const mainContent = (
        <VerticalStack gap="4">
            <ThreatDetectionOverTimeChart
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
            />
            <ThreatTabbedSection
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
            />
            <HorizontalGrid gap={5} columns={2}>
                {topCategoriesCard}
                {severityCard}
            </HorizontalGrid>
        </VerticalStack>
    );

    const sidebarContent = (
        <TopStatisticsSidebar
            startTimestamp={startTimestamp}
            endTimestamp={endTimestamp}
        />
    );

    const dashboardLayout = (
        <Box style={{ display: "flex", gap: "var(--p-space-5, 1.25rem)", alignItems: "flex-start" }}>
            <Box style={{ flex: "1 1 0%", minWidth: 0 }}>
                {mainContent}
            </Box>
            <Box style={{ flex: "0 0 30%", minWidth: "18rem" }}>
                {sidebarContent}
            </Box>
        </Box>
    );

    return (
        <Box>
            {loading ? <SpinnerCentered /> :
                <PageWithMultipleCards
                    title={
                        <Text variant='headingLg'>
                            Threat Detection Dashboard
                        </Text>
                    }
                    isFirstPage={true}
                    components={[dashboardLayout]}
                    primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
                />
            }
        </Box>
    )
}

export default ThreatDashboardPage

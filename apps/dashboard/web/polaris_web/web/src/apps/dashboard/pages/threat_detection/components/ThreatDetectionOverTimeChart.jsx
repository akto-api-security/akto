import { useEffect, useState } from "react";
import { Box, Card, HorizontalGrid, HorizontalStack, Icon, Text, VerticalStack } from "@shopify/polaris";
import { ArrowUpMinor } from "@shopify/polaris-icons";
import LineChart from "../../../components/charts/LineChart";
import api from "../api";
import observeFunc from "../../observe/transform";
import dayjs from "dayjs";

const SERIES_COLORS = {
    totalThreats: "#E45858",
    threatActors: "#E67E22",
};

function ThreatDetectionOverTimeChart({ startTimestamp, endTimestamp }) {
    const [loading, setLoading] = useState(true);
    const [summaryMetrics, setSummaryMetrics] = useState({
        totalThreats: 0,
        apisUnderThreat: 0,
        threatActors: 0,
    });
    const [changes] = useState({ totalThreats: 0, apisUnderThreat: 0, threatActors: 0 });
    const [chartData, setChartData] = useState([]);
    const [trafficMetric, setTrafficMetric] = useState({ attacks: 0, totalTraffic: 0 });

    useEffect(() => {
        let mounted = true;

        const fetchData = async () => {
            setLoading(true);
            try {
                const [dailyResp, timelineResp, apisResp] = await Promise.all([
                    api.getDailyThreatActorsCount(startTimestamp, endTimestamp, []),
                    api.getThreatActivityTimeline(startTimestamp, endTimestamp),
                    api.fetchThreatApis(0, {}, []),
                ]);

                if (!mounted) return;

                const totalActive = dailyResp?.totalActiveStatus || 0;
                const totalIgnored = dailyResp?.totalIgnoredStatus || 0;
                const totalUnderReview = dailyResp?.totalUnderReviewStatus || 0;
                const totalThreats = totalActive + totalIgnored + totalUnderReview;
                const totalActors = dailyResp?.totalActiveStatus || 0;
                const apisTotal = apisResp?.total || 0;

                setSummaryMetrics({ totalThreats, apisUnderThreat: apisTotal, threatActors: totalActors });

                // Fetch recent traffic metric (attacks out of total traffic in last 5 mins)
                try {
                    const dashResp = await api.fetchDashboardTopData(startTimestamp, endTimestamp);
                    if (mounted && dashResp) {
                        setTrafficMetric({
                            attacks: dashResp.recentMaliciousCount || 0,
                            totalTraffic: dashResp.totalKafkaRecords || 0,
                        });
                    }
                } catch (_) {
                    // keep default
                }

                const actorsCounts = dailyResp?.actorsCounts || [];
                const timelines = timelineResp?.threatActivityTimelines || [];

                const dailyThreatMap = new Map();
                timelines.forEach((item) => {
                    let ts = item.ts || item.timestamp || 0;
                    if (ts > 1e12) ts = Math.floor(ts / 1000);
                    const dayKey = dayjs(ts * 1000).startOf("day").valueOf();
                    const subList = item.subCategoryWiseData || [];
                    let dayTotal = 0;
                    subList.forEach((s) => { dayTotal += (s.activityCount || 0); });
                    dailyThreatMap.set(dayKey, (dailyThreatMap.get(dayKey) || 0) + dayTotal);
                });

                const dailyActorMap = new Map();
                actorsCounts.forEach((item) => {
                    let ts = item.ts || 0;
                    if (ts > 1e12) ts = Math.floor(ts / 1000);
                    const dayKey = dayjs(ts * 1000).startOf("day").valueOf();
                    dailyActorMap.set(dayKey, item.totalActors || 0);
                });

                const allDays = new Set([...dailyThreatMap.keys(), ...dailyActorMap.keys()]);
                const sortedDays = Array.from(allDays).sort((a, b) => a - b);

                setChartData([
                    {
                        name: "Total Threats",
                        data: sortedDays.map((d) => [d, dailyThreatMap.get(d) || 0]),
                        color: SERIES_COLORS.totalThreats,
                    },
                    {
                        name: "Threat Actors",
                        data: sortedDays.map((d) => [d, dailyActorMap.get(d) || 0]),
                        color: SERIES_COLORS.threatActors,
                    },
                ]);
            } catch (err) {
                // Keep empty state
            } finally {
                if (mounted) setLoading(false);
            }
        };

        fetchData();
        return () => { mounted = false; };
    }, [startTimestamp, endTimestamp]);

    const metricItems = [
        { label: "Total Threats", value: summaryMetrics.totalThreats, change: changes.totalThreats },
        { label: "APIs Under Threat", value: summaryMetrics.apisUnderThreat, change: changes.apisUnderThreat },
        { label: "Threat Actors", value: summaryMetrics.threatActors, change: changes.threatActors },
    ];

    return (
        <Card padding={5}>
            <VerticalStack gap="4">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingMd">Threat detection over time</Text>
                    {(
                        <HorizontalStack gap="1" blockAlign="center">
                            <Text variant="bodySm" color="subdued">Last 5 min:</Text>
                            <Text variant="headingSm" color="critical">
                                {observeFunc.formatNumberWithCommas(trafficMetric.attacks)}
                            </Text>
                            <Text variant="bodySm" color="subdued">
                                of {observeFunc.formatNumberWithCommas(trafficMetric.totalTraffic)} requests
                            </Text>
                        </HorizontalStack>
                    )}
                </HorizontalStack>

                <HorizontalGrid columns={3} gap="4">
                    {metricItems.map((item, idx) => (
                        <VerticalStack key={idx} gap="1">
                            <Text variant="bodySm" color="subdued">{item.label}</Text>
                            <HorizontalStack gap="2" blockAlign="center">
                                <Text variant="heading2xl">
                                    {observeFunc.formatNumberWithCommas(item.value)}
                                </Text>
                                {item.change > 0 && (
                                    <HorizontalStack gap="1" blockAlign="center">
                                        <Icon source={ArrowUpMinor} color="success" />
                                        <Text variant="bodySm" color="success">{item.change}</Text>
                                    </HorizontalStack>
                                )}
                            </HorizontalStack>
                        </VerticalStack>
                    ))}
                </HorizontalGrid>

                {!loading && chartData.length > 0 && chartData.some(s => s.data.length > 0) ? (
                    <LineChart
                        type="spline"
                        height={280}
                        backgroundColor="transparent"
                        data={chartData}
                        text={true}
                        yAxisTitle=""
                        showGridLines={true}
                        defaultChartOptions={{
                            yAxis: {
                                title: { text: undefined },
                                gridLineColor: "#F3F4F6",
                                labels: { style: { color: "#6B7280" } },
                                min: 0,
                            },
                            legend: {
                                enabled: true,
                                align: "center",
                                verticalAlign: "bottom",
                                symbolRadius: 6,
                                itemStyle: { fontWeight: "normal" },
                            },
                            tooltip: {
                                shared: true,
                                xDateFormat: "%b %e, %Y",
                            },
                            plotOptions: {
                                spline: {
                                    lineWidth: 2,
                                    marker: { enabled: false },
                                },
                                series: { point: { events: {} } },
                            },
                        }}
                    />
                ) : !loading ? (
                    <Box padding="8">
                        <Text variant="bodySm" color="subdued" alignment="center">No timeline data available for the selected period</Text>
                    </Box>
                ) : null}
            </VerticalStack>
        </Card>
    );
}

export default ThreatDetectionOverTimeChart;

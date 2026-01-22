import { Page, LegacyCard, EmptyState, Text, HorizontalStack, VerticalStack, Box } from "@shopify/polaris"
import { useEffect, useReducer, useState } from "react"
import { produce } from "immer"
import DateRangeFilter from "../../../components/layouts/DateRangeFilter"
import Dropdown from "../../../components/layouts/Dropdown"
import settingFunctions from "../module"
import settingRequests from "../api"
import GraphMetric from '../../../components/GraphMetric'
import values from '@/util/values'
import func from "@/util/func"

function TrafficCollectorsMetrics() {

    const [orderedResult, setOrderedResult] = useState([])
    const [instanceIds, setInstanceIds] = useState([])
    const [selectedInstanceId, setSelectedInstanceId] = useState(null)
    const [moduleInfoData, setModuleInfoData] = useState({})

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[2]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTime = getTimeEpoch('since')
    const endTime = getTimeEpoch('until')

    const tcMetrics = [
        'TC_CPU_USAGE',
        'TC_MEMORY_USAGE',
        'TC_TOTAL_CPU_USAGE',
        'TC_TOTAL_MEMORY_USAGE',
    ];

    const tcMetricNames = {
        'TC_CPU_USAGE': { title: 'CPU Usage', description: 'Traffic Collector CPU usage percentage' },
        'TC_MEMORY_USAGE': { title: 'Memory Usage', description: 'Traffic Collector memory usage in MB' },
        'TC_TOTAL_CPU_USAGE': { title: 'Total CPU Cores', description: 'Total CPU cores available on traffic collector instances' },
        'TC_TOTAL_MEMORY_USAGE': { title: 'Total Memory', description: 'Total memory in MB available on traffic collector instances' }
    };

    const fetchModuleInfo = async(startTime, endTime) => {
        try {
            const filter = {
                moduleType: 'TRAFFIC_COLLECTOR',
                lastHeartbeatReceived: { $gte: startTime, $lte: endTime }
            }
            const response = await settingRequests.fetchModuleInfo(filter)
            const modules = response?.moduleInfos || []

            const sortedModules = modules.sort((a, b) => {
                const aTime = a.startedTs || a.lastHeartbeatReceived || 0
                const bTime = b.startedTs || b.lastHeartbeatReceived || 0
                return bTime - aTime
            })

            const moduleData = {}
            sortedModules.forEach(module => {
                const instanceId = module.name
                const profiling = module.additionalData?.profiling
                if (profiling) {
                    const cpuCores = profiling.cpu_cores_total
                    const memoryMB = profiling.memory_cumulative_mb

                    // Only add to moduleData if at least one value exists
                    if (cpuCores || memoryMB) {
                        moduleData[instanceId] = {
                            totalMemoryMB: memoryMB ? parseFloat(memoryMB).toFixed(2) : null,
                            totalCpuCores: cpuCores || null
                        }
                    }
                }
            })
            setModuleInfoData(moduleData)
        } catch (error) {
            console.error("Error fetching module info:", error)
        }
    }

    const getTCMetricsData = async(startTime, endTime) => {
        const metricsData = {};

        // Fetch TC metrics with filters (metricIdPrefix="TC_" and optionally instanceId)
        const data = await settingFunctions.fetchAllMetricsData(startTime, endTime, "TC_", selectedInstanceId);

        if (!data) {
            return metricsData;
        }

        if (instanceIds.length === 0) {
            // Get unique instance IDs from metrics data
            const uniqueInstanceIds = new Set();
            data.forEach(item => {
                if (item.instanceId) {
                    uniqueInstanceIds.add(item.instanceId);
                }
            });

            const moduleInfoKeys = Object.keys(moduleInfoData);
            const sortedIds = Array.from(uniqueInstanceIds).sort((a, b) => {
                const aIndex = moduleInfoKeys.indexOf(a);
                const bIndex = moduleInfoKeys.indexOf(b);
                if (aIndex !== -1 && bIndex !== -1) return aIndex - bIndex;
                if (aIndex !== -1) return -1;
                if (bIndex !== -1) return 1;
                return 0;
            });

            const instanceIdsList = sortedIds.map(id => ({ label: id, value: id }));
            setInstanceIds(instanceIdsList);


            if (instanceIdsList.length > 0 && !selectedInstanceId) {
                setSelectedInstanceId(instanceIdsList[0].value);
                return metricsData;
            }
        }

        for (const metricId of tcMetrics) {
            let result = [];
            const metricData = data.filter(item => item.metricId === metricId);

            if (metricData && metricData.length > 0) {
                const groupedByInstance = {};
                metricData.forEach(item => {
                    const instanceId = item.instanceId || 'unknown';
                    if (!groupedByInstance[instanceId]) {
                        groupedByInstance[instanceId] = [];
                    }
                    groupedByInstance[instanceId].push([item.timestamp * 1000, item.value]);
                });

                Object.entries(groupedByInstance).forEach(([instanceId, dataPoints]) => {
                    result.push({
                        "data": dataPoints,
                        "color": null,
                        "name": instanceId
                    });
                });

                metricsData[metricId] = result;
            }
        }
        return metricsData;
    };

    const getGraphData = async(startTime, endTime) => {
        const metricsData = await getTCMetricsData(startTime, endTime);
        setOrderedResult([]);
        const arr = tcMetrics.map((name) => ({
            key: name,
            value: metricsData[name]
        }));
        setTimeout(() => {
            setOrderedResult(arr);
        }, 0);
    }

    useEffect(() => {
        const fetchData = async () => {
            await fetchModuleInfo(startTime, endTime);
            await getGraphData(startTime, endTime);
        };

        fetchData();
    }, [currDateRange, selectedInstanceId]);

    const defaultChartOptions = function (enableLegends) {
        const options = {
            "plotOptions": {
                series: {
                    events: {
                        legendItemClick: function () {
                            var seriesIndex = this.index;
                            var chart = this.chart;
                            var series = chart.series[seriesIndex];

                            chart.series.forEach(function (s) {
                                s.hide();
                            });
                            series.show();

                            return false;
                        }
                    }
                }
            }
        }
        if (enableLegends) {
            options["legend"] = { layout: 'vertical', align: 'right', verticalAlign: 'middle' }
        }
        return options
    }

    return (
        <Page
            title='Traffic Collectors Metrics'
            divider
            backAction={{
                content: 'Back',
                onAction: () => window.history.back()
            }}
        >
            <LegacyCard >
                <LegacyCard.Section>
                    <VerticalStack gap="4">
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="headingLg" as="h2">Traffic Collectors Metrics</Text>
                            <HorizontalStack gap="3" blockAlign="center">
                                <DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>
                                {instanceIds.length > 1 && (
                                    <Dropdown
                                        menuItems={instanceIds}
                                        initial={selectedInstanceId}
                                        selected={(val) => setSelectedInstanceId(val)}
                                    />
                                )}
                            </HorizontalStack>
                        </HorizontalStack>
                        {selectedInstanceId && moduleInfoData[selectedInstanceId] && (
                            <Box background="bg-fill-tertiary" padding="3" borderRadius="200">
                                <HorizontalStack gap="6">
                                    <HorizontalStack gap="2" blockAlign="center">
                                        <Text variant="bodySm" tone="subdued">Instance:</Text>
                                        <Text variant="bodySm" fontWeight="semibold">{selectedInstanceId}</Text>
                                    </HorizontalStack>
                                    {moduleInfoData[selectedInstanceId]?.totalCpuCores && (
                                        <HorizontalStack gap="2" blockAlign="center">
                                            <Text variant="bodySm" tone="subdued">CPU Cores:</Text>
                                            <Text variant="bodySm" fontWeight="semibold">{moduleInfoData[selectedInstanceId].totalCpuCores}</Text>
                                        </HorizontalStack>
                                    )}
                                    {moduleInfoData[selectedInstanceId]?.totalMemoryMB && (
                                        <HorizontalStack gap="2" blockAlign="center">
                                            <Text variant="bodySm" tone="subdued">Memory:</Text>
                                            <Text variant="bodySm" fontWeight="semibold">{moduleInfoData[selectedInstanceId].totalMemoryMB} MB</Text>
                                        </HorizontalStack>
                                    )}
                                </HorizontalStack>
                            </Box>
                        )}
                    </VerticalStack>
                </LegacyCard.Section>
                {orderedResult.map((element) => (
                    element.value && element.value.length > 0 ? (
                        <LegacyCard.Section key={element.key}>
                            <GraphMetric
                                data={element.value}
                                type='spline'
                                color='#6200EA'
                                areaFillHex="true"
                                height="330"
                                title={tcMetricNames[element.key]?.title || element.key}
                                subtitle={tcMetricNames[element.key]?.description || ''}
                                defaultChartOptions={defaultChartOptions(true)}
                                background-color="#000000"
                                text="true"
                                inputMetrics={[]}
                            />
                        </LegacyCard.Section>
                    ) : (
                        <LegacyCard.Section key={element.key}>
                            <EmptyState
                                heading={tcMetricNames[element.key]?.title || element.key}
                                footerContent="No Graph Data exist !"
                            >
                                <p>{tcMetricNames[element.key]?.description || ''}</p>
                            </EmptyState>
                        </LegacyCard.Section>
                    )
                ))}
            </LegacyCard>
        </Page>
    )
}

export default TrafficCollectorsMetrics

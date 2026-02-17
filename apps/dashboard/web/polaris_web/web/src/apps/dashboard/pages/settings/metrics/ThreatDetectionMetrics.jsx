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

function ThreatDetectionMetrics() {

    const [orderedResult, setOrderedResult] = useState([])
    const [instanceIds, setInstanceIds] = useState([])
    const [selectedInstanceId, setSelectedInstanceId] = useState(null)
    const [moduleInfoData, setModuleInfoData] = useState({})
    const [sortedModuleOrder, setSortedModuleOrder] = useState([])

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[2]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTime = getTimeEpoch('since')
    const endTime = getTimeEpoch('until')

    // Display only these 3 metrics (matching Traffic Collectors)
    const tdMetrics = [
        'CPU_USAGE_PERCENT',
        'HEAP_MEMORY_USED_MB',
        'NON_HEAP_MEMORY_USED_MB'
    ];

    // Metrics for fetching system info (not charted, just displayed in info box)
    const systemInfoMetrics = [
        'TOTAL_PHYSICAL_MEMORY_MB',
        'HEAP_MEMORY_MAX_MB',
        'AVAILABLE_PROCESSORS'
    ];

    const tdMetricNames = {
        'CPU_USAGE_PERCENT': { title: 'CPU Usage', description: 'CPU usage percentage' },
        'HEAP_MEMORY_USED_MB': { title: 'Heap Memory Used', description: 'Heap memory used in MB' },
        'NON_HEAP_MEMORY_USED_MB': { title: 'Non-Heap Memory Used', description: 'Non-heap memory used in MB' }
    };

    const fetchModuleInfo = async(startTime, endTime) => {
        try {
            const filter = {
                moduleType: 'THREAT_DETECTION',
                lastHeartbeatReceived: { $gte: startTime, $lte: endTime }
            }
            const response = await settingRequests.fetchModuleInfo(filter)
            const modules = response?.moduleInfos || []

            const sortedModules = modules.sort((a, b) => {
                const aTime = a.startedTs || a.lastHeartbeatReceived || 0
                const bTime = b.startedTs || b.lastHeartbeatReceived || 0
                return bTime - aTime
            })

            // Store sorted order of module names for dropdown sorting
            const sortedOrder = sortedModules.map(module => module.name)
            setSortedModuleOrder(sortedOrder)
        } catch (error) {
            console.error("Error fetching module info:", error)
        }
    }

    const getTDMetricsData = async(startTime, endTime) => {
        const metricsData = {};

        // Fetch all metrics for THREAT_DETECTION module type
        const data = await settingFunctions.fetchMetricsDataByModule(startTime, endTime, "THREAT_DETECTION", selectedInstanceId);

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

            // Sort instance IDs based on module start time (sortedModuleOrder)
            const sortedIds = Array.from(uniqueInstanceIds).sort((a, b) => {
                const aIndex = sortedModuleOrder.indexOf(a);
                const bIndex = sortedModuleOrder.indexOf(b);
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

        // Extract system info from metrics (latest values)
        const systemInfo = {};
        data.forEach(item => {
            if (systemInfoMetrics.includes(item.metricId)) {
                if (!systemInfo[item.instanceId]) {
                    systemInfo[item.instanceId] = {};
                }
                // Get the latest value (last timestamp)
                if (!systemInfo[item.instanceId][item.metricId] ||
                    item.timestamp > (systemInfo[item.instanceId].lastUpdate || 0)) {
                    systemInfo[item.instanceId][item.metricId] = item.value;
                    systemInfo[item.instanceId].lastUpdate = item.timestamp;
                }
            }
        });

        // Transform to display format
        const moduleInfo = {};
        Object.entries(systemInfo).forEach(([instanceId, infoData]) => {
            moduleInfo[instanceId] = {
                availableProcessors: infoData.AVAILABLE_PROCESSORS || null,
                heapMemoryMaxMb: infoData.HEAP_MEMORY_MAX_MB ? parseFloat(infoData.HEAP_MEMORY_MAX_MB).toFixed(2) : null,
                totalPhysicalMemoryMb: infoData.TOTAL_PHYSICAL_MEMORY_MB ? parseFloat(infoData.TOTAL_PHYSICAL_MEMORY_MB).toFixed(2) : null
            };
        });
        setModuleInfoData(moduleInfo);

        // Process metrics for charting
        for (const metricId of tdMetrics) {
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
        const metricsData = await getTDMetricsData(startTime, endTime);
        setOrderedResult([]);
        const arr = tdMetrics.map((name) => ({
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
            title='Threat Detection Metrics'
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
                            <Text variant="headingLg" as="h2">Threat Detection Metrics</Text>
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
                                    {moduleInfoData[selectedInstanceId]?.availableProcessors && (
                                        <HorizontalStack gap="2" blockAlign="center">
                                            <Text variant="bodySm" tone="subdued">CPU Cores:</Text>
                                            <Text variant="bodySm" fontWeight="semibold">{moduleInfoData[selectedInstanceId].availableProcessors}</Text>
                                        </HorizontalStack>
                                    )}
                                    {moduleInfoData[selectedInstanceId]?.heapMemoryMaxMb && (
                                        <HorizontalStack gap="2" blockAlign="center">
                                            <Text variant="bodySm" tone="subdued">Max Heap Memory:</Text>
                                            <Text variant="bodySm" fontWeight="semibold">{moduleInfoData[selectedInstanceId].heapMemoryMaxMb} MB</Text>
                                        </HorizontalStack>
                                    )}
                                    {moduleInfoData[selectedInstanceId]?.totalPhysicalMemoryMb && (
                                        <HorizontalStack gap="2" blockAlign="center">
                                            <Text variant="bodySm" tone="subdued">Total Memory:</Text>
                                            <Text variant="bodySm" fontWeight="semibold">{moduleInfoData[selectedInstanceId].totalPhysicalMemoryMb} MB</Text>
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
                                title={tdMetricNames[element.key]?.title || element.key}
                                subtitle={tdMetricNames[element.key]?.description || ''}
                                defaultChartOptions={defaultChartOptions(false)}
                                background-color="#000000"
                                text="true"
                                inputMetrics={[]}
                            />
                        </LegacyCard.Section>
                    ) : (
                        <LegacyCard.Section key={element.key}>
                            <EmptyState
                                heading={tdMetricNames[element.key]?.title || element.key}
                                footerContent="No Graph Data exist !"
                            >
                                <p>{tdMetricNames[element.key]?.description || ''}</p>
                            </EmptyState>
                        </LegacyCard.Section>
                    )
                ))}
            </LegacyCard>
        </Page>
    )
}

export default ThreatDetectionMetrics

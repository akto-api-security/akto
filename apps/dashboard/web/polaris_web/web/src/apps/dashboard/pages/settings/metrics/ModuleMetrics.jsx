import { Page, LegacyCard, Text, HorizontalStack, VerticalStack } from "@shopify/polaris"
import { useEffect, useReducer, useState } from "react"
import { produce } from "immer"
import DateRangeFilter from "../../../components/layouts/DateRangeFilter"
import settingFunctions from "../module"
import settingRequests from "../api"
import values from '@/util/values'
import func from "@/util/func"
import { useChartOptions } from './hooks/useChartOptions'
import SystemInfoBox from './components/SystemInfoBox'
import MetricChart from './components/MetricChart'
import DropdownSearch from "../../../components/shared/DropdownSearch"
import { timezonesAvailable } from '../about/About'

function getTimezoneOffsetMinutes(tzValue) {
    if (!tzValue) return new Date().getTimezoneOffset() * -1;
    try {
        const now = new Date();
        const utcMs = now.getTime() + now.getTimezoneOffset() * 60000;
        const tzDate = new Date(now.toLocaleString('en-US', { timeZone: tzValue }));
        return Math.round((tzDate.getTime() - utcMs) / 60000);
    } catch {
        return new Date().getTimezoneOffset() * -1;
    }
}

/**
 * Base component for displaying module metrics
 * @param {Object} config - Module configuration object
 */
function ModuleMetrics({ config }) {
    const [moduleInfoData, setModuleInfoData] = useState({})
    const [orderedResult, setOrderedResult] = useState([])
    const [instanceIds, setInstanceIds] = useState([])
    const [selectedInstanceId, setSelectedInstanceId] = useState(null)
    const [selectedTimezone, setSelectedTimezone] = useState(null)

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2]
    )

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTime = getTimeEpoch('since')
    const endTime = getTimeEpoch('until')

    const getChartOptions = useChartOptions(config.enableLegends)

    const fetchModuleInfo = async() => {
        try {
            const filter = {
                moduleType: config.moduleType,
                lastHeartbeatReceived: { $gte: startTime, $lte: endTime }
            }
            const response = await settingRequests.fetchModuleInfo(filter)
            const modules = response?.moduleInfos || []

            const nowSec = Math.floor(Date.now() / 1000)
            const ONLINE_THRESHOLD_SEC = 5 * 60

            const sorted = modules.sort((a, b) => {
                const aOnline = (nowSec - (a.lastHeartbeatReceived || 0)) < ONLINE_THRESHOLD_SEC
                const bOnline = (nowSec - (b.lastHeartbeatReceived || 0)) < ONLINE_THRESHOLD_SEC
                if (aOnline !== bOnline) return bOnline ? 1 : -1
                const aTime = a.lastHeartbeatReceived || a.startedTs || 0
                const bTime = b.lastHeartbeatReceived || b.startedTs || 0
                return bTime - aTime
            }).map(module => {
                const isOnline = (nowSec - (module.lastHeartbeatReceived || 0)) < ONLINE_THRESHOLD_SEC
                const name = module?.name || module?.id
                return {
                    label: (
                        <HorizontalStack gap="1" blockAlign="center">
                            <Text as="span" variant="bodyMd" color={isOnline ? 'success' : 'subdued'}>●</Text>
                            <Text as="span" variant="bodyMd">{name}</Text>
                        </HorizontalStack>
                    ),
                    value: name,
                }
            })

            setInstanceIds(sorted);
            setSelectedInstanceId(sorted[0]?.value);

            await fetchAndProcessMetrics(sorted[0]?.value);
        } catch (error) {
            console.error("Error fetching module info:", error)
        }
    }

    const fetchAndProcessMetrics = async(instanceId) => {
        try {
            let data
            if (config.fetchStrategy === 'prefix') {
                data = await settingFunctions.fetchAllMetricsData(
                    startTime, endTime, config.metricPrefix, instanceId
                )
            } else if (config.fetchStrategy === 'moduleType') {
                data = await settingFunctions.fetchMetricsDataByModule(
                    startTime, endTime, config.moduleType, instanceId
                )
            }

            if (!data) {
                setOrderedResult([])
                return
            }
            // For Threat Detection: extract system info from metrics
            if (config.fetchStrategy === 'moduleType' && config.systemInfoMetrics) {
                const systemInfo = {}
                data.forEach(item => {
                    if (config.systemInfoMetrics.includes(item.metricId)) {
                        if (!systemInfo[item.instanceId]) {
                            systemInfo[item.instanceId] = {}
                        }
                        if (!systemInfo[item.instanceId][item.metricId] ||
                            item.timestamp > (systemInfo[item.instanceId].lastUpdate || 0)) {
                            systemInfo[item.instanceId][item.metricId] = item.value
                            systemInfo[item.instanceId].lastUpdate = item.timestamp
                        }
                    }
                })

                const moduleInfo = {}
                Object.entries(systemInfo).forEach(([instanceId, infoData]) => {
                    const extracted = {
                        availableProcessors: infoData.AVAILABLE_PROCESSORS || null,
                        heapMemoryMaxMb: infoData.HEAP_MEMORY_MAX_MB ? parseFloat(infoData.HEAP_MEMORY_MAX_MB).toFixed(2) : null,
                        totalPhysicalMemoryMb: infoData.TOTAL_PHYSICAL_MEMORY_MB ? parseFloat(infoData.TOTAL_PHYSICAL_MEMORY_MB).toFixed(2) : null
                    }
                    moduleInfo[instanceId] = config.systemInfoExtractor(null, extracted)
                })
                setModuleInfoData(moduleInfo)
            }

            // Process metrics for charting
            const metricsData = {}
            for (const metricId of config.metrics) {
                const metricData = data.filter(item => item.metricId === metricId)

                if (metricData && metricData.length > 0) {
                    const groupedByInstance = {}
                    metricData.forEach(item => {
                        const instanceId = item.instanceId || 'unknown'
                        if (!groupedByInstance[instanceId]) {
                            groupedByInstance[instanceId] = []
                        }
                        groupedByInstance[instanceId].push([item.timestamp * 1000, item.value])
                    })

                    const result = []
                    Object.entries(groupedByInstance).forEach(([instanceId, dataPoints]) => {
                        result.push({
                            "data": dataPoints,
                            "color": null,
                            "name": instanceId
                        })
                    })

                    metricsData[metricId] = result
                }
            }

            const arr = config.metrics.map((name) => ({
                key: name,
                value: metricsData[name]
            }))

            setOrderedResult([])
            setTimeout(() => {
                setOrderedResult(arr)
            }, 0)
        } catch (error) {
            console.error('Error fetching metrics:', error)
            setOrderedResult([])
        }
    }

    useEffect(() => {
        const fetchData = async () => {
            setInstanceIds([])
            await fetchModuleInfo()
        }

        fetchData()
    }, [currDateRange])

    return (
        <Page
            title={config.title}
            divider
            fullWidth
            backAction={{
                content: 'Back',
                onAction: () => window.history.back()
            }}
        >
            <LegacyCard>
                <LegacyCard.Section>
                    <VerticalStack gap="4">
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="headingLg" as="h2">{config.title}</Text>
                            <HorizontalStack gap="3" blockAlign="center">
                                <DateRangeFilter
                                    initialDispatch={currDateRange}
                                    dispatch={(dateObj) => dispatchCurrDateRange({
                                        type: "update",
                                        period: dateObj.period,
                                        title: dateObj.title,
                                        alias: dateObj.alias
                                    })}
                                />
                                <DropdownSearch
                                    placeholder="Timezone"
                                    optionsList={timezonesAvailable}
                                    setSelected={setSelectedTimezone}
                                    value={selectedTimezone}
                                    sliceMaxVal={10}
                                />
                                {instanceIds.length > 0 && (
                                    <DropdownSearch
                                        placeholder="Select module"
                                        optionsList={instanceIds}
                                        setSelected={async(val) => {setSelectedInstanceId(val); await fetchAndProcessMetrics(val)}}
                                        preSelected={[selectedInstanceId]}
                                        value={selectedInstanceId}
                                        sliceMaxVal={10}
                                        dropdownSearchKey="value"
                                    />
                                )}
                            </HorizontalStack>
                        </HorizontalStack>

                        {selectedInstanceId && moduleInfoData[selectedInstanceId] && (
                            <SystemInfoBox
                                instanceId={selectedInstanceId}
                                data={moduleInfoData[selectedInstanceId]}
                                fields={config.systemInfoFields}
                            />
                        )}
                    </VerticalStack>
                </LegacyCard.Section>

                {orderedResult.map((element) => (
                    <MetricChart
                        key={element.key}
                        metricId={element.key}
                        data={element.value}
                        title={config.metricNames[element.key]?.title}
                        description={config.metricNames[element.key]?.description}
                        chartOptions={getChartOptions()}
                        timezoneOffsetMinutes={getTimezoneOffsetMinutes(selectedTimezone)}
                    />
                ))}
            </LegacyCard>
        </Page>
    )
}

export default ModuleMetrics

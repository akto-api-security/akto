import { Page, LegacyCard, Text, HorizontalStack, VerticalStack } from "@shopify/polaris"
import { useEffect, useReducer, useState } from "react"
import { produce } from "immer"
import DateRangeFilter from "../../../components/layouts/DateRangeFilter"
import Dropdown from "../../../components/layouts/Dropdown"
import settingFunctions from "../module"
import settingRequests from "../api"
import values from '@/util/values'
import func from "@/util/func"
import { useChartOptions } from './hooks/useChartOptions'
import SystemInfoBox from './components/SystemInfoBox'
import MetricChart from './components/MetricChart'

/**
 * Base component for displaying module metrics
 * @param {Object} config - Module configuration object
 */
function ModuleMetrics({ config }) {
    const [sortedModuleOrder, setSortedModuleOrder] = useState([])
    const [moduleInfoData, setModuleInfoData] = useState({})
    const [orderedResult, setOrderedResult] = useState([])
    const [instanceIds, setInstanceIds] = useState([])
    const [selectedInstanceId, setSelectedInstanceId] = useState(null)

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

            const sortedModules = modules.sort((a, b) => {
                const aTime = a.startedTs || a.lastHeartbeatReceived || 0
                const bTime = b.startedTs || b.lastHeartbeatReceived || 0
                return bTime - aTime
            })

            setSortedModuleOrder(sortedModules.map(module => module.name))

            // Extract system info based on config strategy
            if (config.fetchStrategy === 'prefix') {
                // Traffic Collector: extract from moduleInfo.additionalData
                const moduleData = {}
                sortedModules.forEach(module => {
                    const extracted = config.systemInfoExtractor(module)
                    if (extracted) {
                        moduleData[module.name] = extracted
                    }
                })
                setModuleInfoData(moduleData)
            }
            // For moduleType strategy (Threat Detection), system info extracted from metrics later
        } catch (error) {
            console.error("Error fetching module info:", error)
        }
    }

    const fetchAndProcessMetrics = async() => {
        try {
            let data
            if (config.fetchStrategy === 'prefix') {
                data = await settingFunctions.fetchAllMetricsData(
                    startTime, endTime, config.metricPrefix, selectedInstanceId
                )
            } else if (config.fetchStrategy === 'moduleType') {
                data = await settingFunctions.fetchMetricsDataByModule(
                    startTime, endTime, config.moduleType, selectedInstanceId
                )
            }

            if (!data) {
                setOrderedResult([])
                return
            }

            // Extract unique instances and set first one if not selected
            if (instanceIds.length === 0) {
                const uniqueIds = new Set()
                data.forEach(item => {
                    if (item.instanceId) uniqueIds.add(item.instanceId)
                })

                const sortedIds = Array.from(uniqueIds).sort((a, b) => {
                    const aIndex = sortedModuleOrder.indexOf(a)
                    const bIndex = sortedModuleOrder.indexOf(b)
                    if (aIndex !== -1 && bIndex !== -1) return aIndex - bIndex
                    if (aIndex !== -1) return -1
                    if (bIndex !== -1) return 1
                    return 0
                })

                const instanceIdsList = sortedIds.map(id => ({ label: id, value: id }))
                setInstanceIds(instanceIdsList)

                if (sortedIds.length > 0 && !selectedInstanceId) {
                    setSelectedInstanceId(sortedIds[0])
                    return // Will refetch with selected instance
                }
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
            await fetchModuleInfo()
            await fetchAndProcessMetrics()
        }

        fetchData()
    }, [currDateRange, selectedInstanceId])

    return (
        <Page
            title={config.title}
            divider
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
                    />
                ))}
            </LegacyCard>
        </Page>
    )
}

export default ModuleMetrics

import { Page, LegacyCard, Text, HorizontalStack, VerticalStack, Checkbox } from "@shopify/polaris"
import { useCallback, useEffect, useReducer, useState } from "react"
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

const MODULE_ONLINE_THRESHOLD_SEC = 5 * 60

function isModuleOnline(module, nowSec) {
    return (nowSec - (module.lastHeartbeatReceived || 0)) < MODULE_ONLINE_THRESHOLD_SEC
}

function moduleDisplayNameKey(module) {
    return module?.name || module?.id || ''
}

/** Value typically stored on {@code metrics_data.instanceId} (e.g. TC prod uses {@code ModuleInfo.name}; JVM paths may use id). */
function metricsSeriesInstanceKey(module) {
    if (!module) return null
    const n = module.name
    if (n != null && String(n).trim() !== '') return n
    if (module.id != null && String(module.id).trim() !== '') return module.id
    return null
}

/**
 * @param {Array} modules Raw module infos from the API
 * @param {{ nowSec: number, showLiveOnly: boolean, showLiveModulesFilter: boolean }} opts
 */
function buildModuleDropdownOptions(modules, opts) {
    const { nowSec, showLiveOnly, showLiveModulesFilter } = opts
    let list = modules
    if (showLiveModulesFilter && showLiveOnly) {
        list = modules.filter((m) => isModuleOnline(m, nowSec))
    }
    const nameCounts = list.reduce((acc, m) => {
        const k = moduleDisplayNameKey(m)
        acc[k] = (acc[k] || 0) + 1
        return acc
    }, {})
    const sorted = [...list].sort((a, b) => {
        const aOnline = isModuleOnline(a, nowSec)
        const bOnline = isModuleOnline(b, nowSec)
        if (aOnline !== bOnline) return bOnline ? 1 : -1
        const aTime = a.lastHeartbeatReceived || a.startedTs || 0
        const bTime = b.lastHeartbeatReceived || b.startedTs || 0
        return bTime - aTime
    })
    return sorted.map((module) => {
        const online = isModuleOnline(module, nowSec)
        const baseName = moduleDisplayNameKey(module)
        const moduleId = module?.id != null && String(module.id).trim() !== '' ? module.id : baseName
        const disambiguate = nameCounts[baseName] > 1 && module?.id
        const displayName = disambiguate ? `${baseName} (${String(module.id).slice(0, 8)})` : baseName
        return {
            label: (
                <HorizontalStack gap="1" blockAlign="center">
                    <Text as="span" variant="bodyMd" color={online ? 'success' : 'subdued'}>●</Text>
                    <Text as="span" variant="bodyMd">{displayName}</Text>
                </HorizontalStack>
            ),
            value: moduleId,
        }
    })
}

/**
 * Base component for displaying module metrics
 * @param {Object} config - Module configuration object
 */
function ModuleMetrics({ config }) {
    const [moduleInfoData, setModuleInfoData] = useState({})
    const [orderedResult, setOrderedResult] = useState([])
    const [allModules, setAllModules] = useState([])
    const [instanceIds, setInstanceIds] = useState([])
    const [selectedInstanceId, setSelectedInstanceId] = useState(null)
    const [selectedTimezone, setSelectedTimezone] = useState(null)
    const [showLiveModulesOnly, setShowLiveModulesOnly] = useState(true)

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

    const selectedModule = allModules.find((m) => m.id === selectedInstanceId)
        ?? allModules.find((m) => metricsSeriesInstanceKey(m) === selectedInstanceId)
        ?? null
    const metricsFilterKey = metricsSeriesInstanceKey(selectedModule) ?? selectedInstanceId

    const fetchModuleInfo = useCallback(async () => {
        try {
            const filter = {
                moduleType: config.moduleType,
                lastHeartbeatReceived: { $gte: startTime, $lte: endTime }
            }
            const response = await settingRequests.fetchModuleInfo(filter)
            setAllModules(response?.moduleInfos || [])
        } catch {
            setAllModules([])
        }
    }, [config.moduleType, startTime, endTime])

    const fetchAndProcessMetrics = useCallback(async (metricsInstanceKey) => {
        if (metricsInstanceKey == null || metricsInstanceKey === '') {
            setOrderedResult([])
            return
        }
        try {
            let data
            if (config.fetchStrategy === 'prefix') {
                data = await settingFunctions.fetchAllMetricsData(
                    startTime, endTime, config.metricPrefix, metricsInstanceKey
                )
            } else if (config.fetchStrategy === 'moduleType') {
                data = await settingFunctions.fetchMetricsDataByModule(
                    startTime, endTime, config.moduleType, metricsInstanceKey
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
        } catch {
            setOrderedResult([])
        }
    }, [startTime, endTime, config])

    useEffect(() => {
        fetchModuleInfo()
    }, [fetchModuleInfo])

    useEffect(() => {
        const nowSec = Math.floor(Date.now() / 1000)
        const options = buildModuleDropdownOptions(allModules, {
            nowSec,
            showLiveOnly: showLiveModulesOnly,
            showLiveModulesFilter: !!config.showLiveModulesFilter,
        })
        setInstanceIds(options)
        setSelectedInstanceId((prev) => {
            if (options.some((o) => o.value === prev)) return prev
            return options[0]?.value ?? null
        })
    }, [allModules, showLiveModulesOnly, config.showLiveModulesFilter])

    useEffect(() => {
        fetchAndProcessMetrics(metricsFilterKey)
    }, [metricsFilterKey, startTime, endTime, fetchAndProcessMetrics])

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
                                {config.showLiveModulesFilter && (
                                    <Checkbox
                                        label="Live modules only"
                                        checked={showLiveModulesOnly}
                                        onChange={setShowLiveModulesOnly}
                                    />
                                )}
                                {instanceIds.length > 0 && (
                                    <DropdownSearch
                                        placeholder="Select module"
                                        optionsList={instanceIds}
                                        setSelected={(val) => setSelectedInstanceId(val)}
                                        preSelected={[selectedInstanceId]}
                                        value={selectedInstanceId}
                                        sliceMaxVal={10}
                                        dropdownSearchKey="value"
                                    />
                                )}
                            </HorizontalStack>
                        </HorizontalStack>

                        {metricsFilterKey && moduleInfoData[metricsFilterKey] && (
                            <SystemInfoBox
                                instanceId={metricsFilterKey}
                                data={moduleInfoData[metricsFilterKey]}
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

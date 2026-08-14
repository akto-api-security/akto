import { useState, useEffect, useRef, useReducer, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { HorizontalStack, Box, Text, Spinner, Button, Card, VerticalStack, HorizontalGrid } from '@shopify/polaris'
import { produce } from 'immer'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import TitleWithInfo from '../../components/shared/TitleWithInfo'
import DateRangeFilter from '../../components/layouts/DateRangeFilter'
import ComponentHeader from './new_components/ComponentHeader'
import CardWithHeader from './new_components/CardWithHeader'
import ServersLayout from './atlusPosture/ServersLayout'
import AttackWorldMap from './atlusPosture/AttackWorldMap'
import ComplianceAtRisksCard from './new_components/ComplianceAtRisksCard'
import ThreatCategoryStackedChartWrapper from './atlusPosture/ThreatCategoryStackedChartWrapper'
import { formatName } from './atlusPosture/ThreatCategoryChart'
import CustomLineChart from './new_components/CustomLineChart'
import ChartypeComponent from '../testing/TestRunsPage/ChartypeComponent'
import dashboardApi from './api'
import api from '../observe/api'
import func from '@/util/func'
import values from '@/util/values'
import { extractEndpointId, groupCollectionsByAgent, groupCollectionsByService, groupCollectionsByLLM, fetchAndCacheAgenticCollectionsBundle } from '../observe/agentic/constants'
import PersistStore from '../../../main/PersistStore'
import { GridLayout, verticalCompactor } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'
import './endpoint-posture.css'

// Reuses the same tag-based detection as the Agentic Assets "AI Agents"/"MCP Servers"/"LLMs"
// tabs (groupCollectionsByAgent/Service/LLM), rather than a separate hostname-parsing heuristic -
// that used to disagree with the canonical grouping and surface things like "MCP"/"AWS Labs"/
// "Docs" as if they were agents (picked up from inside a remote MCP server's own domain name).
const toWidgetRows = (groups, topN) =>
    groups
        .map(g => ({
            name: g.groupName,
            value: g.endpointsCount ?? (g.collections || []).length,
            hostName: g.collections?.[0]?.hostName,
            url: null,
            hostNames: (g.collections || []).map(c => c.hostName).filter(Boolean),
            filterGroupName: g.groupName,
        }))
        .sort((a, b) => b.value - a.value)
        .slice(0, topN)

const processAgenticCollections = (collections, topN = 10) => {
    const uniqueEndpointIds = new Set()
    collections.forEach(c => {
        if (c.deactivated) return
        const endpointId = extractEndpointId(c.hostName || c.displayName || c.name)
        if (endpointId) uniqueEndpointIds.add(endpointId)
    })

    const agentGroups = groupCollectionsByAgent(collections)
    const serviceGroups = groupCollectionsByService(collections)
    const llmGroups = groupCollectionsByLLM(collections)

    // A service already represented as an agent (e.g. an MCP server the agent itself hosts)
    // shouldn't also double-count as a separate "MCP server" entry - same dedup the Agentic
    // Assets page applies between its Agent and Service groups.
    const agentGroupKeys = new Set(agentGroups.map(a => a.groupKey))
    const servicesToShow = serviceGroups.filter(s => !agentGroupKeys.has(s.groupKey))

    return {
        mcpServers: toWidgetRows(servicesToShow, topN),
        llms: toWidgetRows(llmGroups, topN),
        aiAgents: toWidgetRows(agentGroups, topN),
        totalEndpoints: uniqueEndpointIds.size
    }
}

// Default layout configuration - each component is independently draggable/resizable
// Attack Flow Map hidden for now (no data) - entry commented out below; uncomment to re-enable
const defaultLayout = [
    { i: 'summary', x: 0, y: 0, w: 12, h: 3, minW: 6, minH: 2, maxH: 6 },
    { i: 'mcpServers', x: 0, y: 3, w: 4, h: 4, minW: 3, minH: 3, maxH: 10 },
    { i: 'llms', x: 4, y: 3, w: 4, h: 4, minW: 3, minH: 3, maxH: 10 },
    { i: 'aiAgents', x: 8, y: 3, w: 4, h: 4, minW: 3, minH: 3, maxH: 10 },
    // { i: 'attackFlowMap', x: 0, y: 8, w: 6, h: 6, minW: 4, minH: 4, maxH: 18 },
    { i: 'complianceAtRisks', x: 0, y: 8, w: 12, h: 4, minW: 4, minH: 4, maxH: 18 },
    { i: 'threatCategory', x: 0, y: 14, w: 12, h: 8.5, minW: 6, minH: 4, maxH: 18 },
    { i: 'dataProtectionTrends', x: 0, y: 23.5, w: 6, h: 5, minW: 4, minH: 4, maxH: 18 },
    { i: 'guardrailPolicies', x: 6, y: 23.5, w: 6, h: 5, minW: 4, minH: 4, maxH: 18 }
]

function EndpointPosture() {
    const navigate = useNavigate()
    const [summaryInfoData, setSummaryInfoData] = useState([])
    const [commonMcpServers, setCommonMcpServers] = useState([])
    const [commonLlmsInBrowsers, setCommonLlmsInBrowsers] = useState([])
    const [commonAiAgents, setCommonAiAgents] = useState([])
    const [dataProtectionTrendsData, setDataProtectionTrendsData] = useState([])
    const [guardrailPoliciesData, setGuardrailPoliciesData] = useState({})
    const [complianceData, setComplianceData] = useState([])
    const [attackRequests, setAttackRequests] = useState([])
    const [loading, setLoading] = useState(true)

    // Date range filter state
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.getRange("last1month"))

    const [gridWidth, setGridWidth] = useState(1200)
    const resizeObserverRef = useRef(null)
    const containerRef = useCallback(node => {
        if (resizeObserverRef.current) {
            resizeObserverRef.current.disconnect()
            resizeObserverRef.current = null
        }
        if (!node) return
        const width = node.getBoundingClientRect().width
        if (width > 0) setGridWidth(width)
        resizeObserverRef.current = new ResizeObserver(entries => {
            const w = entries[0]?.contentRect?.width
            if (w) setGridWidth(w)
        })
        resizeObserverRef.current.observe(node)
    }, [])

    // Load layout from localStorage or use default (strip attackFlowMap if present in saved layout)
    const [layout, setLayout] = useState(() => {
        const savedLayout = localStorage.getItem('endpointDashboardLayout')
        const parsed = savedLayout ? JSON.parse(savedLayout) : defaultLayout
        return Array.isArray(parsed) ? parsed.filter(item => item.i !== 'attackFlowMap') : defaultLayout
    })

    // State to track which widgets are visible
    const [hiddenWidgets, setHiddenWidgets] = useState(() => {
        const saved = localStorage.getItem('endpointDashboardHidden')
        return saved ? JSON.parse(saved) : []
    })

    // Dynamic width calculation
    useEffect(() => {
        let rafId = null
        let resizeObserver = null

        const updateWidth = () => {
            if (rafId) cancelAnimationFrame(rafId)
            rafId = requestAnimationFrame(() => {
                if (containerRef.current) {
                    setGridWidth(containerRef.current.clientWidth)
                }
            })
        }

        updateWidth()

        if (containerRef.current) {
            resizeObserver = new ResizeObserver(updateWidth)
            resizeObserver.observe(containerRef.current)
        }

        window.addEventListener('resize', updateWidth)
        // Recompute layout when zoom changes (visualViewport resize fires on zoom)
        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', updateWidth)
            window.visualViewport.addEventListener('scroll', updateWidth)
        }

        return () => {
            if (rafId) cancelAnimationFrame(rafId)
            if (resizeObserver) resizeObserver.disconnect()
            window.removeEventListener('resize', updateWidth)
            if (window.visualViewport) {
                window.visualViewport.removeEventListener('resize', updateWidth)
                window.visualViewport.removeEventListener('scroll', updateWidth)
            }
        }
    }, [])

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    useEffect(() => {

        const fetchSummaryData = async () => {
            try {
                const startTimestamp = getTimeEpoch("since")
                const endTimestamp = getTimeEpoch("until")

                // Fetch data from existing APIs in parallel. getAllCollectionsBasic (via the cached
                // bundle) is cached (PersistStore, 2-min TTL, in-flight-deduped) and shared with
                // AgenticAssetsPage/UsersAndDevices/Endpoints/DeviceEndpoints — this page doesn't need
                // traffic/risk itself, but reuses the same shared cache entry those pages populate.
                const [
                    guardrailResponse,
                    collectionsBundle
                ] = await Promise.all([
                    dashboardApi.fetchGuardrailData(startTimestamp, endTimestamp),
                    fetchAndCacheAgenticCollectionsBundle({ api, PersistStore })
                ])

                // Process collections to get component data (like Endpoints.jsx does)
                const collections = collectionsBundle?.collections || []
                const { mcpServers, llms, aiAgents, totalEndpoints } = processAgenticCollections(collections, 10)

                // Extract values from guardrail response
                const sensitiveCount = guardrailResponse?.sensitiveCount || 0
                const successfulExploits = guardrailResponse?.successfulExploits || 0
                const avgGuardrailScore = guardrailResponse?.avgThreatScore || 0.0

                const summaryData = [
                    {

                        title: "Total Agentic Assets",
                        data: totalEndpoints.toString(),
                        variant: 'heading2xl',
                    },
                    {
                        title: 'Total Successful exploits',
                        data: successfulExploits.toString(),
                        variant: 'heading2xl',
                        color: successfulExploits > 0 ? "warning" : undefined,
                    },
                    {
                        title: 'Total Sensitive Data Events',
                        data: sensitiveCount.toString(),
                        variant: 'heading2xl',
                    },
                    {
                        title: 'AI Average Guardrail Score',
                        data: avgGuardrailScore.toFixed(1),
                        variant: 'heading2xl',
                        color: avgGuardrailScore > 3 ? "critical" : undefined,
                    }
                ]
                setSummaryInfoData(summaryData)

                setCommonMcpServers(mcpServers)
                setCommonLlmsInBrowsers(llms)
                setCommonAiAgents(aiAgents)

                // Attack Flow Map hidden for now - uncomment to re-enable
                // const attackFlows = guardrailResponse?.attackFlows || []
                // setAttackRequests(attackFlows)

                // Process Data Protection Trends - dynamic categories
                const dataProtectionTrends = guardrailResponse?.dataProtectionTrends || {}

                // Define colors for top 3 categories
                const categoryColors = ['#3b82f6', '#ef4444', '#10b981']

                // Convert dynamic category keys to chart data (support both { data, filterId } and legacy array format)
                const trendsChartData = Object.keys(dataProtectionTrends).map((category, index) => {
                    const raw = dataProtectionTrends[category]
                    const isObj = raw && typeof raw === 'object' && !Array.isArray(raw)
                    const data = isObj ? (raw.data || []) : (raw || [])
                    const filterId = isObj ? raw.filterId : null
                    return {
                        name: formatName(category),
                        color: categoryColors[index] || '#9ca3af',
                        data,
                        filterId
                    }
                })

                setDataProtectionTrendsData(trendsChartData)

                // Process Guardrail Policies Data
                const topGuardrailPolicies = guardrailResponse?.topGuardrailPolicies || []
                const guardrailPoliciesObject = {}
                const guardrailColors = ['#ef4444', '#f59e0b', '#3b82f6', '#10b981'] // red, amber, blue, green
                topGuardrailPolicies.forEach((policy, index) => {
                    guardrailPoliciesObject[formatName(policy.name)] = {
                        text: policy.count,
                        color: guardrailColors[index % guardrailColors.length],
                        filterValue: policy.filterId
                    }
                })
                setGuardrailPoliciesData(guardrailPoliciesObject)

                // Process Compliance At Risks Data (from guardrail data, not issues)
                const complianceAtRisks = guardrailResponse?.complianceAtRisks || []

                // Color palette for compliance cards
                const complianceColors = ['#dc2626', '#ea580c', '#ca8a04', '#16a34a']

                const complianceDataMapped = complianceAtRisks.map((compliance, index) => {
                    return {
                        name: compliance.name,
                        percentage: Math.round(compliance.percentage || 0),
                        color: '#dc2626', // Use red for all
                        icon: func.getComplianceIcon(compliance.name)
                    }
                })

                setComplianceData(complianceDataMapped)

                setLoading(false)
            } catch (error) {
                console.error('Error fetching summary info:', error)
                setLoading(false)
            }
        }

        fetchSummaryData()
    }, [currDateRange])

    if (loading) {
        return (
            <Box padding="8">
                <HorizontalStack align="center" blockAlign="center">
                    <Spinner size="large" />
                    <Text variant="bodyMd">Loading dashboard data...</Text>
                </HorizontalStack>
            </Box>
        )
    }

    // Handler for layout changes (drag/resize) - keep attackFlowMap out of saved layout
    const onLayoutChange = (newLayout) => {
        const filtered = newLayout.filter(item => item.i !== 'attackFlowMap')
        setLayout(filtered)
        localStorage.setItem('endpointDashboardLayout', JSON.stringify(filtered))
    }

    // Hide widget
    const hideWidget = (widgetId) => {
        const newHidden = [...hiddenWidgets, widgetId]
        setHiddenWidgets(newHidden)
        localStorage.setItem('endpointDashboardHidden', JSON.stringify(newHidden))
    }

    // Reset layout to default and show all widgets (strip attackFlowMap if present)
    const resetLayout = () => {
        const resetLayoutCopy = JSON.parse(JSON.stringify(defaultLayout)).filter(item => item.i !== 'attackFlowMap')
        setLayout(resetLayoutCopy)
        localStorage.removeItem('endpointDashboardLayout')

        setHiddenWidgets([])
        localStorage.removeItem('endpointDashboardHidden')
    }

    // Check if widget is visible
    const isWidgetVisible = (widgetId) => !hiddenWidgets.includes(widgetId)

    const resetButton = (
        <Button onClick={resetLayout}>
            Reset Layout
        </Button>
    )

    const dateRangeFilter = (
        <DateRangeFilter
            initialDispatch={currDateRange}
            dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })}
        />
    )

    const hasMcpServers = commonMcpServers && commonMcpServers.length > 0
    const hasLlms = commonLlmsInBrowsers && commonLlmsInBrowsers.length > 0
    const hasAiAgents = commonAiAgents && commonAiAgents.length > 0

    const handleSummaryCardClick = (index) => {
        if (index === 0) {
            navigate('/dashboard/observe/agentic-assets')
        } else if (index === 1) {
            const filterStr = 'successfulExploit__true'
            const targetPath = '/dashboard/protection/threat-activity'
            sessionStorage.setItem('akto_spaFilterNav', 'true')
            sessionStorage.setItem('akto_spaNavFilter', filterStr)
            sessionStorage.setItem('akto_spaNavPath', targetPath)
            sessionStorage.setItem('akto_spaNavExpiry', String(Date.now() + 15000))
            navigate(`${targetPath}?filters=${encodeURIComponent(filterStr)}#active`, {
                state: { period: { since: currDateRange.period.since, until: currDateRange.period.until } }
            })
        } else {
            sessionStorage.setItem('akto_spaFilterNav', 'true')
            navigate('/dashboard/protection/threat-activity?filters=#active', {
                state: { period: { since: currDateRange.period.since, until: currDateRange.period.until } }
            })
        }
    }

    const summaryHeader = (
        <Card>
            <VerticalStack gap={4}>
                <ComponentHeader title='Endpoint Summary' itemId='summary' onRemove={hideWidget} tooltipContent="Overview of key endpoint security metrics" />
                <HorizontalGrid columns={summaryInfoData.length} gap={4}>
                    {summaryInfoData.map((item, index) => (
                        <Box
                            borderInlineEndWidth={index < (summaryInfoData.length - 1) ? "1" : ""}
                            key={index}
                            borderColor="transparent"
                            cursor="pointer"
                            onClick={() => handleSummaryCardClick(index)}
                        >
                            <HorizontalStack>
                                <VerticalStack gap="4">
                                    <TitleWithInfo
                                        titleComp={
                                            <Text variant="headingMd">
                                                {item.title}
                                            </Text>
                                        }
                                        docsUrl={item?.docsUrl}
                                        tooltipContent={item?.tooltipContent}
                                    />
                                    <HorizontalGrid gap={1} columns={2}>
                                        <VerticalStack gap={4}>
                                            {item?.isComp ? item.data :
                                                <div className='custom-color summary-card-number'>
                                                    <Text variant={item.variant ? item.variant : 'bodyLg'} color={item.color ? item.color : ""}>
                                                        {item.data}
                                                    </Text>
                                                </div>
                                            }
                                            {item.byLineComponent ? item.byLineComponent : null}
                                        </VerticalStack>
                                        {item.smoothChartComponent ? item.smoothChartComponent : null}
                                    </HorizontalGrid>
                                </VerticalStack>
                            </HorizontalStack>
                        </Box>
                    ))}
                </HorizontalGrid>
            </VerticalStack>
        </Card>
    )


    const handleAgenticItemClick = (filterGroupName) => {
        if (!filterGroupName) {
            navigate('/dashboard/observe/agentic-assets')
            return
        }
        const filterStr = `groupName__${filterGroupName}`
        sessionStorage.setItem('akto_spaFilterNav', 'true')
        sessionStorage.setItem('akto_spaNavFilter', filterStr)
        sessionStorage.setItem('akto_spaNavPath', '/dashboard/observe/agentic-assets')
        sessionStorage.setItem('akto_spaNavExpiry', String(Date.now() + 15000))
        navigate(`/dashboard/observe/agentic-assets?filters=${encodeURIComponent(filterStr)}`)
    }

    const mcpServersComponent = (
        <ServersLayout
            title='Common MCP Servers'
            itemId='mcpServers'
            tooltipContent="The most common MCP servers detected in your environment."
            items={commonMcpServers}
            hasItems={hasMcpServers}
            emptyMessage="No MCP servers detected"
            onRemove={hideWidget}
            onItemClick={handleAgenticItemClick}
        />
    )

    const llmsComponent = (
        <ServersLayout
            title='Common LLMs in browsers'
            itemId='llms'
            tooltipContent="The most common LLMs agents detected in your environment."
            items={commonLlmsInBrowsers}
            hasItems={hasLlms}
            emptyMessage="No LLMs detected"
            onRemove={hideWidget}
            onItemClick={handleAgenticItemClick}
        />
    )

    const aiAgentsComponent = (
        <ServersLayout
            title='Common AI Agents'
            itemId='aiAgents'
            tooltipContent="The most common AI agents detected in your environment."
            items={commonAiAgents}
            hasItems={hasAiAgents}
            emptyMessage="No AI agents detected"
            onRemove={hideWidget}
            onItemClick={handleAgenticItemClick}
        />
    )

    const handleGuardrailPolicyClick = (filterValue) => {
        navigateToThreatActivityWithFilter(filterValue)
    }

    const navigateToThreatActivityWithFilter = (filterId) => {
        if (!filterId) return
        const filterStr = `latestAttack__${filterId}`
        const targetPath = '/dashboard/protection/threat-activity'
        sessionStorage.setItem('akto_spaFilterNav', 'true')
        sessionStorage.setItem('akto_spaNavFilter', filterStr)
        sessionStorage.setItem('akto_spaNavPath', targetPath)
        sessionStorage.setItem('akto_spaNavExpiry', String(Date.now() + 15000))
        navigate(
            `${targetPath}?filters=${encodeURIComponent(filterStr)}#active`,
            { state: { period: { since: currDateRange.period.since, until: currDateRange.period.until } } }
        )
    }

    const handleDataProtectionTrendPointClick = function() {
        const seriesName = this.series?.name
        const matchingTrend = dataProtectionTrendsData.find(item => item.name === seriesName)
        navigateToThreatActivityWithFilter(matchingTrend?.filterId)
    }

    // Attack Flow Map hidden for now - uncomment to re-enable
    // const hasAttackFlowData = attackRequests && attackRequests.length > 0
    const hasComplianceData = complianceData && complianceData.length > 0

    const handleThreatCategoryClick = (rawName) => {
        if (!rawName || rawName === 'Other') {
            navigate('/dashboard/protection/threat-activity', {
                state: { period: { since: currDateRange.period.since, until: currDateRange.period.until } }
            })
            return
        }
        navigateToThreatActivityWithFilter(rawName)
    }

    const threatCategoryStackedChartComponent = (
        <ThreatCategoryStackedChartWrapper
            startTimestamp={getTimeEpoch("since")}
            endTimestamp={getTimeEpoch("until")}
            itemId='threatCategory'
            onRemoveComponent={hideWidget}
            onCategoryClick={handleThreatCategoryClick}
        />
    )

    // const attackFlowMapComponent = hasAttackFlowData ? (
    //     <AttackWorldMap
    //         attackRequests={attackRequests}
    //         style={{ width: "100%", height: "100%", marginRight: "auto" }}
    //         itemId='attackFlowMap'
    //         onRemoveComponent={hideWidget}
    //     />
    // ) : (
    //     <CardWithHeader
    //         title='Attack Flow Map'
    //         itemId='attackFlowMap'
    //         onRemove={hideWidget}
    //         tooltipContent="Geographic visualization of attack sources"
    //         hasData={false}
    //         emptyMessage="No attack flow data in the selected period"
    //         minHeight="300px"
    //     />
    // )

    const complianceAtRisksComponent = hasComplianceData ? (
        <ComplianceAtRisksCard
            complianceData={complianceData}
            itemId='complianceAtRisks'
            onRemoveComponent={hideWidget}
            tooltipContent="Overview of compliance risks across different security standards"
        />
    ) : (
        <CardWithHeader
            title='Compliance at Risks'
            itemId='complianceAtRisks'
            onRemove={hideWidget}
            tooltipContent="Overview of compliance risks across different security standards"
            hasData={false}
            emptyMessage="No compliance risk data available"
            minHeight="300px"
        />
    )

    const hasGuardrailData = guardrailPoliciesData && typeof guardrailPoliciesData === 'object' && Object.keys(guardrailPoliciesData).length > 0 && Object.values(guardrailPoliciesData).some(item => item?.text > 0);

    const dataProtectionTrendsLabels = dataProtectionTrendsData.map(item => ({
        label: item.name,
        color: item.color,
        filterId: item.filterId
    }))

    const hasDataProtectionTrends = dataProtectionTrendsData && dataProtectionTrendsData.length > 0 &&
                                     dataProtectionTrendsData.some(item => item.data && item.data.length > 0)

    const dataProtectionTrendsComponent = hasDataProtectionTrends ? (
        <CustomLineChart
            title="Data Protection Trends"
            chartData={dataProtectionTrendsData}
            labels={dataProtectionTrendsLabels}
            chartHeight={290}
            itemId='dataProtectionTrends'
            onRemoveComponent={hideWidget}
            tooltipContent="Trends showing how data protection mechanisms are being triggered over time"
            graphPointClick={handleDataProtectionTrendPointClick}
            onLabelClick={navigateToThreatActivityWithFilter}
        />
    ) : (
        <CardWithHeader
            title='Data Protection Trends'
            itemId='dataProtectionTrends'
            onRemove={hideWidget}
            tooltipContent="Trends showing how data protection mechanisms are being triggered over time"
            hasData={false}
            emptyMessage="No data protection trend data in the selected period"
        />
    )

    const guardrailPoliciesComponent = (
        <CardWithHeader
            title='Top Triggered Guardrail Policies'
            itemId='guardrailPolicies'
            onRemove={hideWidget}
            tooltipContent="Most frequently triggered guardrail policies"
            hasData={hasGuardrailData}
            emptyMessage="No guardrail policy data available"
            minHeight="250px"
        >
            <Box paddingBlockStart={3}>
                <ChartypeComponent
                    data={guardrailPoliciesData}
                    title="Top Triggered Guardrail Policies"
                    isNormal={true}
                    boxHeight={'250px'}
                    chartOnLeft={true}
                    dataTableWidth="250px"
                    pieInnerSize="50%"
                    onSegmentClick={handleGuardrailPolicyClick}
                />
            </Box>
        </CardWithHeader>
    )

    return (
        <PageWithMultipleCards
            isFirstPage={true}
            title={
                <TitleWithInfo
                    titleText="AI Security Posture"
                    tooltipContent="Monitor and manage your AI security posture from this centralized dashboard. Drag cards to reposition and hover over corners to resize."
                    docsUrl="https://docs.akto.io/endpoint-security"
                />
            }
            primaryAction={resetButton}
            secondaryActions={[dateRangeFilter]}
            components={[
                <div key="grid-container" ref={containerRef} style={{ width: '100%', maxWidth: '100%', minWidth: 0, overflow: 'hidden', boxSizing: 'border-box' }}>
                    <GridLayout
                        width={gridWidth}
                        layout={layout.filter(item => isWidgetVisible(item.i))}
                        gridConfig={{
                            cols: 12,
                            rowHeight: 50,
                            margin: [16, 16],
                            containerPadding: [0, 0]
                        }}
                        dragConfig={{
                            enabled: true,
                            handle: '.drag-handle-icon'
                        }}
                        resizeConfig={{
                            enabled: true
                        }}
                        compactor={verticalCompactor}
                        onLayoutChange={onLayoutChange}
                    >
                        {isWidgetVisible('summary') && (
                            <div key="summary">
                                {summaryHeader}
                            </div>
                        )}

                        {isWidgetVisible('mcpServers') && (
                            <div key="mcpServers">
                                {mcpServersComponent}
                            </div>
                        )}

                        {isWidgetVisible('llms') && (
                            <div key="llms">
                                {llmsComponent}
                            </div>
                        )}

                        {isWidgetVisible('aiAgents') && (
                            <div key="aiAgents">
                                {aiAgentsComponent}
                            </div>
                        )}

                        {/* Attack Flow Map hidden for now - uncomment layout entry, component, and this block to re-enable */}
                        {/* {isWidgetVisible('attackFlowMap') && (
                            <div key="attackFlowMap">{attackFlowMapComponent}</div>
                        )} */}

                        {isWidgetVisible('complianceAtRisks') && (
                            <div key="complianceAtRisks">
                                {complianceAtRisksComponent}
                            </div>
                        )}

                        {isWidgetVisible('threatCategory') && (
                            <div key="threatCategory">
                                {threatCategoryStackedChartComponent}
                            </div>
                        )}

                        {isWidgetVisible('dataProtectionTrends') && (
                            <div key="dataProtectionTrends">
                                {dataProtectionTrendsComponent}
                            </div>
                        )}

                        {isWidgetVisible('guardrailPolicies') && (
                            <div key="guardrailPolicies">
                                {guardrailPoliciesComponent}
                            </div>
                        )}
                    </GridLayout>
                </div>
            ]}
        />
    )
}

export default EndpointPosture
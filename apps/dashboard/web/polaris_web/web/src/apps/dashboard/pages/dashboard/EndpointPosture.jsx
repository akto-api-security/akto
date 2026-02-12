import { useState, useEffect, useRef } from 'react'
import { HorizontalStack, Box, Text, Spinner, Button } from '@shopify/polaris'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import TitleWithInfo from '../../components/shared/TitleWithInfo'
import SummaryCard from './new_components/SummaryCard'
import InfoCard from './new_components/InfoCard'
import ServersLayout from './atlusPosture/ServersLayout'
import AttackWorldMap from './atlusPosture/AttackWorldMap'
import ComplianceAtRisksCard from './new_components/ComplianceAtRisksCard'
import ThreatCategoryStackedChartWrapper from './atlusPosture/ThreatCategoryStackedChartWrapper'
import CustomLineChart from './new_components/CustomLineChart'
import ChartypeComponent from '../testing/TestRunsPage/ChartypeComponent'
import dashboardApi from './api'
import api from '../observe/api'
import func from '@/util/func'
import { getTypeFromTags, CLIENT_TYPES, getDomainForFavicon } from '../observe/agentic/mcpClientHelper'
import { extractEndpointId } from '../observe/agentic/constants'
import { GridLayout } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'
import './endpoint-posture.css'

const cleanHostname = (hostname) => {
    if (!hostname) return hostname

    const parts = hostname.split('.')

    // Need at least 2 parts to have a prefix (id.domain)
    if (parts.length >= 2) {
        const firstPart = parts[0]

        // Check if first part looks like an ID:
        // - At least 12 characters long (UUIDs, hashes, session IDs are typically 12+)
        // - Contains only alphanumeric characters (no hyphens or special chars)
        // - Doesn't look like a common subdomain (www, api, app, etc.)
        const isLikelyId = firstPart.length >= 12 &&
                          /^[a-zA-Z0-9]+$/.test(firstPart) &&
                          !/^(www|api|app|dev|staging|prod|test)$/i.test(firstPart)

        if (isLikelyId) {
            // Remove the first part and return the rest
            return parts.slice(1).join('.')
        }
    }

    // Return original if no ID pattern found
    return hostname
}

const processAgenticCollections = (collections, topN = 4) => {
    const typeGroups = {
        [CLIENT_TYPES.MCP_SERVER]: {},
        [CLIENT_TYPES.LLM]: {},
        [CLIENT_TYPES.AI_AGENT]: {}
    }

    const uniqueEndpointIds = new Set()

    collections.forEach(c => {
        if (c.deactivated) return

        const clientType = getTypeFromTags(c.envType)
        const rawDisplayName = c.displayName || c.name
        const hostName = c.hostName || rawDisplayName
        const displayName = cleanHostname(rawDisplayName)
        const endpointId = extractEndpointId(hostName)

        // Track unique endpoints
        if (endpointId) {
            uniqueEndpointIds.add(endpointId)
        }

        // Group by collection name and type
        if (!typeGroups[clientType][displayName]) {
            typeGroups[clientType][displayName] = {
                name: displayName,
                count: 0,
                endpoints: new Set()
            }
        }

        typeGroups[clientType][displayName].count++
        if (endpointId) {
            typeGroups[clientType][displayName].endpoints.add(endpointId)
        }
    })

    // Convert to arrays and sort by count, take top N
    const processGroup = (type) =>
        Object.values(typeGroups[type])
            .map(g => {
                const domain = getDomainForFavicon(g.name)
                const icon = domain ? `https://www.google.com/s2/favicons?domain=${domain}&sz=32` : undefined
                return { name: g.name, value: g.endpoints.size || g.count, icon }
            })
            .sort((a, b) => b.value - a.value)
            .slice(0, topN)

    return {
        mcpServers: processGroup(CLIENT_TYPES.MCP_SERVER),
        llms: processGroup(CLIENT_TYPES.LLM),
        aiAgents: processGroup(CLIENT_TYPES.AI_AGENT),
        totalEndpoints: uniqueEndpointIds.size
    }
}

// Default layout configuration - each component is independently draggable/resizable
const defaultLayout = [
    { i: 'summary', x: 0, y: 0, w: 12, h: 3, minW: 6, minH: 2, maxH: 6 },
    { i: 'mcpServers', x: 0, y: 3, w: 4, h: 5, minW: 3, minH: 3, maxH: 10 },
    { i: 'llms', x: 4, y: 3, w: 4, h: 5, minW: 3, minH: 3, maxH: 10 },
    { i: 'aiAgents', x: 8, y: 3, w: 4, h: 5, minW: 3, minH: 3, maxH: 10 },
    { i: 'attackFlowMap', x: 0, y: 8, w: 6, h: 9, minW: 4, minH: 4, maxH: 18 },
    { i: 'complianceAtRisks', x: 6, y: 8, w: 6, h: 9, minW: 4, minH: 4, maxH: 18 },
    { i: 'threatCategory', x: 0, y: 17, w: 12, h: 9, minW: 6, minH: 4, maxH: 18 },
    { i: 'dataProtectionTrends', x: 0, y: 26, w: 6, h: 9, minW: 4, minH: 4, maxH: 18 },
    { i: 'guardrailPolicies', x: 6, y: 26, w: 6, h: 9, minW: 4, minH: 4, maxH: 18 }
]

function EndpointPosture() {
    const [summaryInfoData, setSummaryInfoData] = useState([])
    const [commonMcpServers, setCommonMcpServers] = useState([])
    const [commonLlmsInBrowsers, setCommonLlmsInBrowsers] = useState([])
    const [commonAiAgents, setCommonAiAgents] = useState([])
    const [dataProtectionTrendsData, setDataProtectionTrendsData] = useState([])
    const [guardrailPoliciesData, setGuardrailPoliciesData] = useState({})
    const [complianceData, setComplianceData] = useState([])
    const [attackRequests, setAttackRequests] = useState([])
    const [loading, setLoading] = useState(true)

    const containerRef = useRef(null)
    const [gridWidth, setGridWidth] = useState(1200)

    // Load layout from localStorage or use default
    const [layout, setLayout] = useState(() => {
        const savedLayout = localStorage.getItem('endpointDashboardLayout')
        return savedLayout ? JSON.parse(savedLayout) : defaultLayout
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

        return () => {
            if (rafId) cancelAnimationFrame(rafId)
            if (resizeObserver) resizeObserver.disconnect()
            window.removeEventListener('resize', updateWidth)
        }
    }, [])

    useEffect(() => {

        const fetchSummaryData = async () => {
            try {
                const endTimestamp = Math.floor(Date.now() / 1000)
                const startTimestamp = endTimestamp - (30 * 24 * 60 * 60) // Last 30 days

                // Fetch data from existing APIs in parallel
                const [
                    guardrailResponse,
                    collectionsResponse
                ] = await Promise.all([
                    dashboardApi.fetchGuardrailData(startTimestamp, endTimestamp),
                    api.getAllCollectionsBasic()
                ])

                // Process collections to get component data (like Endpoints.jsx does)
                const collections = collectionsResponse?.apiCollections || []
                const { mcpServers, llms, aiAgents, totalEndpoints } = processAgenticCollections(collections, 4)

                // Extract values from guardrail response
                const sensitiveCount = guardrailResponse?.sensitiveCount || 0
                const successfulExploits = guardrailResponse?.successfulExploits || 0
                const avgGuardrailScore = guardrailResponse?.avgThreatScore || 0.0

                const summaryData = [
                    {

                        title: "Total Endpoint Components",
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

                // Get attack flows from guardrail response (consolidated into single API call)
                const attackFlows = guardrailResponse?.attackFlows || []
                setAttackRequests(attackFlows)

                // Process Data Protection Trends - dynamic categories
                const dataProtectionTrends = guardrailResponse?.dataProtectionTrends || {}

                // Define colors for top 3 categories
                const categoryColors = ['#3b82f6', '#ef4444', '#10b981']

                // Helper function to format category names
                const formatCategoryName = (category) => {
                    if (!category) return 'Unknown'
                    return category
                        .replace(/_/g, ' ')
                        .toLowerCase()
                        .replace(/\b\w/g, l => l.toUpperCase())
                }

                // Convert dynamic category keys to chart data
                const trendsChartData = Object.keys(dataProtectionTrends).map((category, index) => ({
                    name: formatCategoryName(category),
                    color: categoryColors[index] || '#9ca3af', // Use gray as fallback
                    data: dataProtectionTrends[category] || []
                }))

                setDataProtectionTrendsData(trendsChartData)

                // Process Guardrail Policies Data
                const topGuardrailPolicies = guardrailResponse?.topGuardrailPolicies || []
                const guardrailPoliciesObject = {}
                const guardrailColors = ['#ef4444', '#f59e0b', '#3b82f6', '#10b981'] // red, amber, blue, green
                topGuardrailPolicies.forEach((policy, index) => {
                    guardrailPoliciesObject[policy.name] = {
                        text: policy.count,
                        color: guardrailColors[index % guardrailColors.length]
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
                        color: complianceColors[index] || '#6b7280', // Use gray as fallback
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
    }, [])

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

    // Handler for layout changes (drag/resize)
    const onLayoutChange = (newLayout) => {
        setLayout(newLayout)
        localStorage.setItem('endpointDashboardLayout', JSON.stringify(newLayout))
    }

    // Reset layout to default
    const resetLayout = () => {
        // Create a deep copy to force re-render
        const resetLayoutCopy = JSON.parse(JSON.stringify(defaultLayout))
        setLayout(resetLayoutCopy)
        localStorage.removeItem('endpointDashboardLayout')
    }

    const resetButton = (
        <Button onClick={resetLayout}>
            Reset Layout
        </Button>
    )

    const summaryHeader = (
        <SummaryCard 
            summaryItems={summaryInfoData}
        />
    )

    const hasMcpServers = commonMcpServers && commonMcpServers.length > 0
    const hasLlms = commonLlmsInBrowsers && commonLlmsInBrowsers.length > 0
    const hasAiAgents = commonAiAgents && commonAiAgents.length > 0

    const mcpServersComponent = (
        <InfoCard
            title="Common MCP Servers"
            component={
                <div style={{ marginTop: "20px" }}>
                    {hasMcpServers ? (
                        <ServersLayout items={commonMcpServers} boxHeight="200px" />
                    ) : (
                        <Box minHeight="200px">
                            <Text alignment='center' color='subdued'>No MCP servers detected</Text>
                        </Box>
                    )}
                </div>
            }
            tooltipContent="The most common MCP servers detected in your environment."
        />
    )

    const llmsComponent = (
        <InfoCard
            title="Common LLMs in browsers"
            component={
                <div style={{ marginTop: "20px" }}>
                    {hasLlms ? (
                        <ServersLayout items={commonLlmsInBrowsers} boxHeight="200px" />
                    ) : (
                        <Box minHeight="200px">
                            <Text alignment='center' color='subdued'>No LLMs detected</Text>
                        </Box>
                    )}
                </div>
            }
            tooltipContent="The most common LLMs agents detected in your environment."
        />
    )

    const aiAgentsComponent = (
        <InfoCard
            title="Common AI Agents"
            component={
                <div style={{ marginTop: "20px" }}>
                    {hasAiAgents ? (
                        <ServersLayout items={commonAiAgents} boxHeight="200px" />
                    ) : (
                        <Box minHeight="200px">
                            <Text alignment='center' color='subdued'>No AI agents detected</Text>
                        </Box>
                    )}
                </div>
            }
            tooltipContent="The most common AI agents detected in your environment."
        />
    )

    const threatCategoryStackedChartComponent = (
        <ThreatCategoryStackedChartWrapper/>
    )

    const hasAttackFlowData = attackRequests && attackRequests.length > 0
    const hasComplianceData = complianceData && complianceData.length > 0

    const attackFlowMapComponent = (
        <InfoCard
            title="Attack Flow Map"
            component={
                <div style={{ marginTop: "20px" }}>
                    {hasAttackFlowData ? (
                        <AttackWorldMap
                            attackRequests={attackRequests}
                            style={{
                                width: "100%",
                                height: "300px",
                                marginRight: "auto",
                            }}
                        />
                    ) : (
                        <Box minHeight="300px">
                            <Text alignment='center' color='subdued'>No attack flow data in the selected period</Text>
                        </Box>
                    )}
                </div>
            }
            titleToolTip="Visualization of attack sources and destinations"
        />
    )

    const complianceAtRisksComponent = (
        <InfoCard
            title="Compliance At Risks"
            component={
                <div style={{ marginTop: "20px" }}>
                    {hasComplianceData ? (
                        <ComplianceAtRisksCard
                            complianceData={complianceData}
                            tooltipContent="Overview of compliance risks across different security standards"
                        />
                    ) : (
                        <Box minHeight="300px">
                            <Text alignment='center' color='subdued'>No compliance risk data available</Text>
                        </Box>
                    )}
                </div>
            }
            titleToolTip="Overview of compliance risks across different security standards"
        />
    )

    const hasGuardrailData = guardrailPoliciesData && typeof guardrailPoliciesData === 'object' && Object.keys(guardrailPoliciesData).length > 0 && Object.values(guardrailPoliciesData).some(item => item?.text > 0);

    const dataProtectionTrendsLabels = dataProtectionTrendsData.map(item => ({
        label: item.name,
        color: item.color
    }))

    const hasDataProtectionTrends = dataProtectionTrendsData && dataProtectionTrendsData.length > 0 &&
                                     dataProtectionTrendsData.some(item => item.data && item.data.length > 0)

    const dataProtectionTrendsComponent = (
        <InfoCard
            title="Data Protection Trends"
            titleToolTip="Trends showing how data protection mechanisms are being triggered over time"
            component={
                <div style={{ marginTop: "20px" }}>
                    {hasDataProtectionTrends ? (
                        <CustomLineChart
                            title="Data Protection Trends"
                            chartData={dataProtectionTrendsData}
                            labels={dataProtectionTrendsLabels}
                            chartHeight={290}
                            tooltipContent="Trends showing how data protection mechanisms are being triggered over time"
                        />
                    ) : (
                        <Box minHeight="250px">
                            <Text alignment='center' color='subdued'>No data protection trend data in the selected period</Text>
                        </Box>
                    )}
                </div>
            }
        />
    )

    const guardrailPoliciesComponent = (
        <InfoCard
            component={
                <div style={{ marginTop: "20px" }}>
                    {hasGuardrailData ? (
                        <ChartypeComponent
                            data={guardrailPoliciesData}
                            title="Top Triggered Guardrail Policies"
                            isNormal={true}
                            boxHeight={'250px'}
                            chartOnLeft={true}
                            dataTableWidth="250px"
                            pieInnerSize="50%"
                        />
                    ) : (
                        <Box minHeight="250px">
                            <Text alignment='center' color='subdued'>No guardrail policy data available</Text>
                        </Box>
                    )}
                </div>
            }
            title="Top Triggered Guardrail Policies"
            titleToolTip="Distribution of the most frequently triggered guardrail policies"
        />
    )

    return (
        <PageWithMultipleCards
            isFirstPage={true}
            title={
                <TitleWithInfo
                    titleText="Endpoint Security Dashboard"
                    tooltipContent="Monitor and manage your endpoint security from this centralized dashboard. Drag cards to reposition and hover over corners to resize."
                    docsUrl="https://docs.akto.io/endpoint-security"
                />
            }
            primaryAction={resetButton}
            components={[
                <div key="grid-container" ref={containerRef} style={{ width: '100%' }}>
                    <GridLayout
                        width={gridWidth}
                        layout={layout}
                        gridConfig={{
                            cols: 12,
                            rowHeight: 50,
                            margin: [16, 16],
                            containerPadding: [0, 0]
                        }}
                        dragConfig={{
                            enabled: true
                        }}
                        resizeConfig={{
                            enabled: true
                        }}
                        compactor={null}
                        onLayoutChange={onLayoutChange}
                    >
                        <div key="summary">
                            {summaryHeader}
                        </div>

                        <div key="mcpServers">
                            {mcpServersComponent}
                        </div>

                        <div key="llms">
                            {llmsComponent}
                        </div>

                        <div key="aiAgents">
                            {aiAgentsComponent}
                        </div>

                        <div key="attackFlowMap">
                            {attackFlowMapComponent}
                        </div>

                        <div key="complianceAtRisks">
                            {complianceAtRisksComponent}
                        </div>

                        <div key="threatCategory">
                            {threatCategoryStackedChartComponent}
                        </div>

                        <div key="dataProtectionTrends">
                            {dataProtectionTrendsComponent}
                        </div>

                        <div key="guardrailPolicies">
                            {guardrailPoliciesComponent}
                        </div>
                    </GridLayout>
                </div>
            ]}
        />
    )
}

export default EndpointPosture
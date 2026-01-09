import { Box, Button, Card, DataTable, HorizontalGrid, HorizontalStack, Text, VerticalStack, Popover, ActionList, Icon } from '@shopify/polaris'
import { DeleteMinor, SettingsFilledMinor } from '@shopify/polaris-icons'
import { useEffect, useReducer, useState, useRef } from 'react'
import TitleWithInfo from '../../components/shared/TitleWithInfo'
import DateRangeFilter from '../../components/layouts/DateRangeFilter'
import { produce } from 'immer'
import values from "@/util/values";
import func from '@/util/func'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import Dropdown from '../../components/layouts/Dropdown'
import SpinnerCentered from '../../components/progress/SpinnerCentered'
import LineChart from '../../components/charts/LineChart'
import "./agentic-dashboard.css"
import "react-grid-layout/css/styles.css"
import "react-resizable/css/styles.css"
import DonutChart from '../../components/shared/DonutChart'
import SemiCircleProgress from '../../components/shared/SemiCircleProgress'
import { mapLabel, getDashboardCategory } from '../../../main/labelHelper'
import { GridLayout } from "react-grid-layout";
import api from './api';
import Store from '../../store';

const agenticDiscoveryData = {
    "AI Agents": { text: 2000, color: "#7F56D9" },
    "MCP Servers": { text: 1500, color: "#9E77ED" },
    "LLM": { text: 1500, color: "#D6BBFB" }
}

const agenticIssuesData = {
    "Critical": { text: 420, color: "#E45357" },
    "High": { text: 400, color: "#EF864C" },
    "Medium": { text: 350, color: "#F6C564" },
    "Low": { text: 250, color: "#E0E0E0" }
}

const agenticGuardrailsData = {
    "Critical": { text: 600, color: "#E45357" },
    "High": { text: 500, color: "#EF864C" },
    "Medium": { text: 450, color: "#F6C564" },
    "Low": { text: 450, color: "#E0E0E0" }
}

const issueAgeData = [
    { label: 'Critical Issues', days: 12, progress: 40, color: '#D92D20' },
    { label: 'High Issues', days: 12, progress: 40, color: '#F79009' },
    { label: 'Medium Issues', days: 17, progress: 57, color: '#8660d8ff' },
    { label: 'Low Issues', days: 42, progress: 100, color: '#714ec3ff' }
]

const complianceData = [
    { name: 'SOC 2', percentage: 70, color: '#D97706', icon: '/public/SOC%202.svg' },
    { name: 'GDPR', percentage: 10, color: '#7C3AED', icon: '/public/GDPR.svg' },
    { name: 'ISO 27001', percentage: 50, color: '#DC6803', icon: '/public/ISO%2027001.svg' },
    { name: 'HIPAA', percentage: 90, color: '#DC2626', icon: '/public/HIPAA.svg' }
]

const testedVsNonTestedData = [
    { name: 'Non-Tested',
        data: [
            [1704067200000, 2000],
            [1706745600000, 2200],
            [1709251200000, 1600],
            [1711929600000, 2300],
            [1714521600000, 2000],
            [1717200000000, 1900],
            [1719792000000, 2000],
            [1722470400000, 1400],
            [1725148800000, 1600],
            [1727740800000, 1800],
            [1730419200000, 1700],
            [1733011200000, 1600]
        ],
        color: '#D72C0D'
    },
    { name: 'Tested',
        data: [
            [1704067200000, 3000],
            [1706745600000, 4000],
            [1709251200000, 2600],
            [1711929600000, 2300],
            [1714521600000, 1600],
            [1717200000000, 1400],
            [1719792000000, 2200],
            [1722470400000, 1600],
            [1725148800000, 2100],
            [1727740800000, 2200],
            [1730419200000, 2400],
            [1733011200000, 2700]
        ],
        color: '#9E77ED'
    }
]

const openResolvedIssuesData = [
    { name: 'Open Issues',
        data: [
            [1704067200000, 2000],
            [1706745600000, 2200],
            [1709251200000, 1600],
            [1711929600000, 2400],
            [1714521600000, 2000],
            [1717200000000, 1900],
            [1719792000000, 2000],
            [1722470400000, 1300],
            [1725148800000, 1600],
            [1727740800000, 1800],
            [1730419200000, 1700],
            [1733011200000, 1600]
        ],
        color: '#D72C0D'
    },
    { name: 'Resolved Issues',
        data: [
            [1704067200000, 3000],
            [1706745600000, 3900],
            [1709251200000, 2700],
            [1711929600000, 2300],
            [1714521600000, 1700],
            [1717200000000, 1400],
            [1719792000000, 2300],
            [1722470400000, 1700],
            [1725148800000, 2100],
            [1727740800000, 2200],
            [1730419200000, 2300],
            [1733011200000, 2700]
        ],
        color: '#9E77ED'
    }
]

const guardrailRequestsData = [
    { name: 'Flagged Requests',
        data: [
            [1704067200000, 12000],
            [1706745600000, 12200],
            [1709251200000, 11600],
            [1711929600000, 12200],
            [1714521600000, 12000],
            [1717200000000, 11900],
            [1719792000000, 13200],
            [1722470400000, 11200],
            [1725148800000, 12000],
            [1727740800000, 13500],
            [1730419200000, 11600],
            [1733011200000, 11600]
        ],
        color: '#D72C0D'
    },
    { name: 'Safe Requests',
        data: [
            [1704067200000, 13000],
            [1706745600000, 13900],
            [1709251200000, 12700],
            [1711929600000, 12200],
            [1714521600000, 14000],
            [1717200000000, 11100],
            [1719792000000, 13200],
            [1722470400000, 11900],
            [1725148800000, 12000],
            [1727740800000, 15400],
            [1730419200000, 12400],
            [1733011200000, 12700]
        ],
        color: '#47B881'
    }
]

const openResolvedGuardrailsData = [
    { name: 'Open Issues',
        data: [
            [1704067200000, 2000],
            [1706745600000, 2100],
            [1709251200000, 1600],
            [1711929600000, 2400],
            [1714521600000, 2000],
            [1717200000000, 1900],
            [1719792000000, 2000],
            [1722470400000, 1300],
            [1725148800000, 1500],
            [1727740800000, 1700],
            [1730419200000, 1600],
            [1733011200000, 1600]
        ],
        color: '#D72C0D'
    },
    { name: 'Resolved Issues',
        data: [
            [1704067200000, 3100],
            [1706745600000, 3900],
            [1709251200000, 2700],
            [1711929600000, 2300],
            [1714521600000, 2000],
            [1717200000000, 1300],
            [1719792000000, 2400],
            [1722470400000, 1900],
            [1725148800000, 2100],
            [1727740800000, 2200],
            [1730419200000, 2500],
            [1733011200000, 2700]
        ],
        color: '#9E77ED'
    }
]

const weakestAreasData = [
    { name: 'Prompt Injection', value: '64%', color: '#E45357' },
    { name: 'Memory Poisoning', value: '54.3%', color: '#E45357' },
    { name: 'Agentic AI Tool Misuse', value: '51%', color: '#E45357' },
    { name: 'Manipulation', value: '48.5%', color: '#EF864C' },
    { name: 'System Prompt Leakage', value: '32.3%', color: '#EF864C' }
]

const topAgenticComponentsData = [
    { name: 'mcp.chargebee.com', value: '1,160' },
    { name: '2a27c88357b55e31a56bee74adc33d0f.akto_mcp_server.com', value: '156' },
    { name: 'playground.chargebee.com', value: '153' },
    { name: 'docs.chargebee.com', value: '140' },
    { name: 'api.chargebee.com', value: '92' }
]

const topRequestsByTypeData = [
    { name: 'Prompt Injection', value: '64%' },
    { name: 'Toxic content', value: '54.3%' },
    { name: 'PII Data Leak', value: '51%' },
    { name: 'Harmful Content', value: '48.5%' },
    { name: 'Tool Abuse', value: '32.3%' }
]

const topAttackedComponentsData = [
    { name: 'mcp.chargebee.com', value: '24.3k' },
    { name: '2a27c88357b55e31a56bee74adc33d0f.akto_mcp_server.com', value: '12.3k' },
    { name: 'playground.chargebee.com', value: '9.2k' },
    { name: 'docs.chargebee.com', value: '8.2k' },
    { name: 'apiv2.chargebee.com', value: '800' }
]

const topBadActorsData = [
    { name: '107.85.149.128', value: '24.3k' },
    { name: '136.226.250.200', value: '12.3k' },
    { name: '45.133.2.112', value: '9.2k' },
    { name: '165.22.42.240', value: '8.2k' },
    { name: '192.168.1.254', value: '800' }
]

const AgenticDashboard = () => {
    const dashboardCategory = getDashboardCategory();
    const [loading, setLoading] = useState(true);
    const [viewMode, setViewMode] = useState('ciso')
    const [overallStats, setOverallStats] = useState({})
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5])
    const containerRef = useRef(null);
    const [popoverActive, setPopoverActive] = useState(false);
    const setToastConfig = Store(state => state.setToastConfig);

    const defaultVisibleComponents = [
        'security-posture-chart', 'api-discovery-pie', 'issues-pie', 'threat-detection-pie',
        'average-issue-age', 'compliance-at-risks', 'tested-vs-non-tested', 'open-resolved-issues',
        'threat-requests-chart', 'open-resolved-threats', 'weakest-areas', 'top-apis-issues',
        'top-requests-by-type', 'top-attacked-apis', 'top-bad-actors'
    ]

    const [visibleComponents, setVisibleComponents] = useState(defaultVisibleComponents);

    const defaultLayout = [
        { i: 'security-posture-chart', x: 0, y: 0, w: 12, h: 4, minW: 4, minH: 4, maxH: 4 },
        { i: 'api-discovery-pie', x: 0, y: 4, w: 4, h: 3, minW: 4, maxW: 4, minH: 3, maxH: 3 },
        { i: 'issues-pie', x: 4, y: 4, w: 4, h: 3, minW: 4, maxW: 4, minH: 3, maxH: 3 },
        { i: 'threat-detection-pie', x: 8, y: 4, w: 4, h: 3, minW: 4, maxW: 4, minH: 3, maxH: 3 },
        { i: 'average-issue-age', x: 0, y: 7, w: 4, h: 3, minW: 4, maxW: 4, minH: 3, maxH: 3 },
        { i: 'compliance-at-risks', x: 4, y: 7, w: 8, h: 2, minW: 6, minH: 2, maxH: 2 },
        { i: 'tested-vs-non-tested', x: 0, y: 10, w: 6, h: 4, minW: 4, minH: 4, maxH: 4 },
        { i: 'open-resolved-issues', x: 6, y: 10, w: 6, h: 4, minW: 4, minH: 4, maxH: 4 },
        { i: 'threat-requests-chart', x: 0, y: 14, w: 6, h: 4, minW: 4, minH: 4, maxH: 4 },
        { i: 'open-resolved-threats', x: 6, y: 14, w: 6, h: 4, minW: 4, minH: 4, maxH: 4 },
        { i: 'weakest-areas', x: 0, y: 18, w: 6, h: 4, minW: 4, minH: 2 },
        { i: 'top-apis-issues', x: 6, y: 18, w: 6, h: 4, minW: 4, minH: 2 },
        { i: 'top-requests-by-type', x: 0, y: 22, w: 4, h: 4, minW: 4, minH: 2 },
        { i: 'top-attacked-apis', x: 4, y: 22, w: 4, h: 4, minW: 4, minH: 2 },
        { i: 'top-bad-actors', x: 8, y: 22, w: 4, h: 4, minW: 4, minH: 2 }
    ];

    const [layout, setLayout] = useState(defaultLayout)
    const [savedLayout, setSavedLayout] = useState(null)
    const [savedVisibleComponents, setSavedVisibleComponents] = useState(null)
    const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)
    const [isSaving, setIsSaving] = useState(false)
    const [layoutLoading, setLayoutLoading] = useState(true)

    useEffect(() => {
        const loadSavedLayout = async () => {
            try {
                const resp = await api.fetchDashboardLayout()

                const layoutString = typeof resp === 'string' ? resp : resp?.dashboardLayout

                if (layoutString && layoutString !== 'null') {
                    const parsedLayout = JSON.parse(layoutString)

                    if (parsedLayout.layout && parsedLayout.visibleComponents) {
                        const loadedLayout = parsedLayout.layout
                        const loadedVisibleComponents = parsedLayout.visibleComponents

                        const defaultLayoutMap = new Map(defaultLayout.map(item => [item.i, item]))
                        const mergedLayout = loadedLayout.map(item => {
                            const defaultItem = defaultLayoutMap.get(item.i)
                            if (defaultItem) {
                                return {
                                    ...item,
                                    minW: defaultItem.minW,
                                    maxW: defaultItem.maxW,
                                    minH: defaultItem.minH,
                                    maxH: defaultItem.maxH
                                }
                            }
                            return item
                        })


                        setLayout(mergedLayout)
                        setVisibleComponents(loadedVisibleComponents)
                        setSavedLayout(mergedLayout)
                        setSavedVisibleComponents(loadedVisibleComponents)
                    } else {
                        setSavedLayout(defaultLayout)
                        setSavedVisibleComponents(defaultVisibleComponents)
                    }
                } else {
                    setSavedLayout(defaultLayout)
                    setSavedVisibleComponents(defaultVisibleComponents)
                }
            } catch (error) {
                setSavedLayout(defaultLayout)
                setSavedVisibleComponents(defaultVisibleComponents)
            } finally {
                setLayoutLoading(false)
            }
        }
        loadSavedLayout()
    }, [])

    useEffect(() => {
        if (savedLayout === null || savedVisibleComponents === null) return

        const layoutChanged = JSON.stringify(layout) !== JSON.stringify(savedLayout)
        const visibilityChanged = JSON.stringify(visibleComponents) !== JSON.stringify(savedVisibleComponents)
        setHasUnsavedChanges(layoutChanged || visibilityChanged)
    }, [layout, visibleComponents, savedLayout, savedVisibleComponents])

    const saveDashboardLayout = async () => {
        setIsSaving(true)
        try {
            const layoutData = {
                layout,
                visibleComponents
            }
            await api.saveDashboardLayout(JSON.stringify(layoutData))
            setSavedLayout(layout)
            setSavedVisibleComponents(visibleComponents)
            setHasUnsavedChanges(false)
            setToastConfig({
                isActive: true,
                isError: false,
                message: 'Dashboard layout saved successfully!'
            })
        } catch (error) {
            setToastConfig({
                isActive: true,
                isError: true,
                message: 'Failed to save dashboard layout'
            })
        } finally {
            setIsSaving(false)
        }
    }

    const componentHeader = (title, itemId) => (
        <Box width='100%'>
            <HorizontalStack blockAlign="center" align='space-between'>
                <Text variant='headingMd'>{title}</Text>
                <HorizontalStack gap={2}>
                    <Button monochrome plain icon={DeleteMinor} onClick={() => removeComponent(itemId)} />
                    <div className='graph-menu'>
                        <img src={"/public/MenuVerticalIcon.svg"} alt='graph-menu' />
                    </div>
                </HorizontalStack>
            </HorizontalStack>
        </Box>
    )

    useEffect(() => {
        setLoading(true);
        // fetch data
        setOverallStats([
            { name: mapLabel('API Endpoints Discovered', dashboardCategory),
                data: [
                    [1704067200000, 36000],
                    [1706745600000, 46000],
                    [1709251200000, 30000],
                    [1711929600000, 20000],
                    [1714521600000, 16000],
                    [1717200000000, 15000],
                    [1719792000000, 27000],
                    [1722470400000, 19000],
                    [1725148800000, 24000],
                    [1727740800000, 25000],
                    [1730419200000, 28000],
                    [1733011200000, 32000]
                ],
                color: '#B692F6'
            },
            { name: `${mapLabel('API', dashboardCategory)} Issues`,
                data: [
                    [1704067200000, 24000],
                    [1706745600000, 26000],
                    [1709251200000, 18000],
                    [1711929600000, 27000],
                    [1714521600000, 23000],
                    [1717200000000, 22000],
                    [1719792000000, 21000],
                    [1722470400000, 15000],
                    [1725148800000, 21000],
                    [1727740800000, 20000],
                    [1730419200000, 19000],
                    [1733011200000, 19000]
                ],
                color: '#D72C0D'
            },
            { name: mapLabel('Threat', dashboardCategory) + ' Requests flagged',
                data: [
                    [1704067200000, 52000],
                    [1706745600000, 47000],
                    [1709251200000, 41000],
                    [1711929600000, 32000],
                    [1714521600000, 26000],
                    [1717200000000, 22000],
                    [1719792000000, 32000],
                    [1722470400000, 33000],
                    [1725148800000, 38000],
                    [1727740800000, 36000],
                    [1730419200000, 37000],
                    [1733011200000, 33000]
                ],
                color: '#F3B283'
            }
        ])

        setLoading(false);
    }, [dashboardCategory])

    const averageIssueAgeComp = (itemId="") => {
        return (
            <Card>
                <VerticalStack gap={4} align='space-between'>
                    {componentHeader('Average Issue Age', itemId)}

                    <Box width='100%'>
                        <HorizontalGrid columns={2} gap={4} alignItems='center' blockAlign='center'>
                            {issueAgeData.map((issue, idx) => (
                                <Box key={idx}>
                                    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                        <SemiCircleProgress
                                            progress={issue.progress}
                                            size={140}
                                            height={110}
                                            width={180}
                                            color={issue.color}
                                            backgroundColor='#F2F4F7'
                                            centerText={`${issue.days} days`}
                                            subtitle={issue.label}
                                        />
                                    </div>
                                </Box>
                            ))}
                        </HorizontalGrid>
                    </Box>
                </VerticalStack>
            </Card>
        )
    }

    const complianceAtRisksComp = (itemId="") => {
        return (
            <Card>
                <VerticalStack gap={4}>
                    {componentHeader('Compliance at Risks', itemId)}

                    <Box width='100%'>
                        <HorizontalGrid columns={4} gap={5}>
                            {complianceData.map((compliance, idx) => (
                                <VerticalStack gap={5} inlineAlign='center' align='start'>
                                    <div style={{
                                        width: '139px',
                                        height: '126px',
                                        backgroundImage: `url(${compliance.icon})`,
                                        backgroundRepeat: 'no-repeat',
                                        backgroundPosition: 'center',
                                        backgroundSize: 'contain'
                                    }} />

                                    <Box width='100%'>
                                        <VerticalStack gap={2} align='end' inlineAlign='center'>
                                            <Text variant='headingXl' alignment='center' fontWeight='semibold'>
                                                {compliance.name}
                                            </Text>
                                            <Box width='100%'>
                                                <HorizontalStack gap={2} align='space-between' blockAlign='center'>
                                                    <div style={{
                                                        flex: 1,
                                                        height: '5px',
                                                        backgroundColor: '#E5E7EB',
                                                        borderRadius: '3px',
                                                        overflow: 'hidden'
                                                    }}>
                                                        <div style={{
                                                            width: `${compliance.percentage}%`,
                                                            height: '100%',
                                                            backgroundColor: compliance.color,
                                                            borderRadius: '3px'
                                                        }} />
                                                    </div>
                                                    <Text variant='bodySm' as='span'>
                                                        {compliance.percentage}%
                                                    </Text>
                                                </HorizontalStack>
                                            </Box>
                                        </VerticalStack>
                                    </Box>
                                </VerticalStack>
                            ))}
                        </HorizontalGrid>
                    </Box>
                </VerticalStack>
            </Card>
        )
    }

    const customPieChart = (title="", subtitle="", graphData={}, itemId="") => {
        const total = Object.values(graphData).reduce((sum, item) => sum + item.text, 0)
        const formattedTotal = total.toLocaleString()

        const labels = Object.keys(graphData).map(key => ({
            label: key,
            color: graphData[key].color
        }))

        return (
            <Card>
                <VerticalStack gap="4" inlineAlign='start' blockAlign="center">
                    {componentHeader(title, itemId)}
                    <Box width='100%' minHeight='210px'>
                        <VerticalStack gap="2" inlineAlign='center' blockAlign="center">
                            <DonutChart
                                title={subtitle}
                                subtitle={formattedTotal}
                                data={graphData}
                                size={200}
                                pieInnerSize="60%"
                                invertTextSizes={true}
                            />
                            {graphCustomLabels(labels)}
                        </VerticalStack>
                    </Box>
                </VerticalStack>
            </Card>
        )
    }

    const graphCustomLabels = (labelsWithColors=[]) => (
        <HorizontalStack gap={4} align='center' blockAlign='center' wrap>
            {
                labelsWithColors.map((labelObj, idx) => (
                    <HorizontalStack key={`${idx}-${labelObj.label}`} gap={2} blockAlign='center' align='center'>
                        <div style={{ width: '8px', height: '8px', backgroundColor: labelObj.color || '#3d3d3d', borderRadius: '50%' }} />
                        <Text variant='bodyMd'>{labelObj.label}</Text>
                    </HorizontalStack>
                ))
            }
        </HorizontalStack>
    )

    const customLineChart = (title="", chartData=[], labels=[], itemId="") => {
        return (
            <Card>
                <VerticalStack gap="6" inlineAlign='start' blockAlign="center">
                    {componentHeader(title, itemId)}

                    <Box width='100%'>
                        <LineChart
                            data={chartData}
                            height={290}
                            type="line"
                            text={true}
                            showGridLines={true}
                            exportingDisabled={true}
                            defaultChartOptions={{
                                xAxis: {
                                    type: 'datetime',
                                    dateTimeLabelFormats: {
                                        day: '%b %e',
                                        month: '%b',
                                    },
                                    title: { text: '' },
                                    visible: true,
                                    gridLineWidth: 0
                                },
                                yAxis: {
                                    title: { text: '' },
                                    gridLineWidth: 1,
                                    min: 0,
                                    labels: {
                                        formatter: function() {
                                            return this.value.toLocaleString();
                                        }
                                    }
                                },
                                legend: {
                                    enabled: false
                                }
                            }}
                        />
                    </Box>

                    {graphCustomLabels(labels)}
                </VerticalStack>
            </Card>
        )
    }

    const customDataTable = (title="", data=[], showSignalIcon=true, itemId="") => {
        const rows = data.map(item => [
            <HorizontalStack gap={3} blockAlign='center'>
                {showSignalIcon && <img src='/public/menu-graph.svg' alt='growth-icon' />}
                <div style={{ maxWidth: '300px', wordBreak: 'break-word', overflowWrap: 'break-word' }}>
                    <Text variant='bodyMd' fontWeight='medium'>{item.name}</Text>
                </div>
            </HorizontalStack>,
            <div style={{color: '#D72C0D'}}>
                <Text variant='bodyMd' fontWeight='medium'>{item.value}</Text>
            </div>
        ])

        return (
            <Card>
                <VerticalStack gap="4">
                    {componentHeader(title, itemId)}

                    <Box width='100%'>
                        <DataTable
                            columnContentTypes={['text', 'numeric']}
                            headings={[]}
                            rows={rows}
                            hideScrollIndicator
                        />
                    </Box>
                </VerticalStack>
            </Card>
        )
    }

    const onLayoutChange = (newLayout) => {
        setLayout(prevLayout => {
            const layoutMap = new Map(newLayout.map(item => [item.i, item]));
            return defaultLayout.map(defaultItem => {
                if (layoutMap.has(defaultItem.i)) {
                    return layoutMap.get(defaultItem.i);
                }
                return prevLayout.find(item => item.i === defaultItem.i) || defaultItem;
            });
        });
    };

    const removeComponent = (itemId) => {
        setVisibleComponents(prev => prev.filter(id => id !== itemId));
    };

    const toggleComponent = (itemId) => {
        setVisibleComponents(prev => {
            if (prev.includes(itemId)) {
                return prev.filter(id => id !== itemId);
            } else {
                setLayout(prevLayout => {
                    const existingItem = prevLayout.find(item => item.i === itemId);
                    if (!existingItem) {
                        const defaultItem = defaultLayout.find(item => item.i === itemId);
                        return [...prevLayout, defaultItem];
                    }
                    const defaultItem = defaultLayout.find(item => item.i === itemId);
                    const updatedLayout = prevLayout.map(item =>
                        item.i === itemId ? { ...defaultItem } : item
                    );
                    return updatedLayout;
                });
                return [...prev, itemId];
            }
        });
    };

    const componentNames = {
        'security-posture-chart': `${mapLabel('API Security Posture', dashboardCategory)} over time`,
        'api-discovery-pie': mapLabel('API Discovery', dashboardCategory),
        'issues-pie': 'Issues',
        'threat-detection-pie': mapLabel('Threat Detection', dashboardCategory),
        'average-issue-age': 'Average Issue Age',
        'compliance-at-risks': 'Compliance at Risks',
        'tested-vs-non-tested': `Tested vs Non-Tested ${mapLabel('APIs', dashboardCategory)}`,
        'open-resolved-issues': 'Open & Resolved Issues',
        'threat-requests-chart': `${mapLabel('Threat', dashboardCategory)} Requests over time`,
        'open-resolved-threats': `Open & Resolved ${mapLabel('Threat', dashboardCategory)}s`,
        'weakest-areas': 'Weakest Areas by Failing Percentage',
        'top-apis-issues': `Top ${mapLabel('APIs', dashboardCategory)} with Critical & High Issues`,
        'top-requests-by-type': 'Top Requests by Type',
        'top-attacked-apis': `Top Attacked ${mapLabel('APIs', dashboardCategory)}`,
        'top-bad-actors': 'Top Bad Actors'
    };

    const allComponentsMap = {
        'security-posture-chart': customLineChart(
            `${func.toSentenceCase(window.ACCOUNT_NAME)} ${mapLabel('API Security Posture', dashboardCategory)} over time`,
            overallStats,
            [
                { label: mapLabel('API Endpoints Discovered', dashboardCategory), color: '#B692F6' },
                { label: `${mapLabel('API', dashboardCategory)} Issues`, color: '#D72C0D' },
                { label: `${mapLabel('Threat', dashboardCategory)} Requests flagged`, color: '#F3B283' }
            ],
            'security-posture-chart'
        ),
        'api-discovery-pie': customPieChart(mapLabel('API Discovery', dashboardCategory), `Total ${mapLabel('APIs', dashboardCategory)}`, agenticDiscoveryData, 'api-discovery-pie'),
        'issues-pie': customPieChart("Issues", "Total Issues", agenticIssuesData, 'issues-pie'),
        'threat-detection-pie': customPieChart(mapLabel('Threat Detection', dashboardCategory), "Requests Flagged", agenticGuardrailsData, 'threat-detection-pie'),
        'average-issue-age': averageIssueAgeComp('average-issue-age'),
        'compliance-at-risks': complianceAtRisksComp('compliance-at-risks'),
        'tested-vs-non-tested': customLineChart(
            `Tested vs Non-Tested ${mapLabel('APIs', dashboardCategory)}`,
            testedVsNonTestedData,
            [
                { label: 'Non-Tested', color: '#D72C0D' },
                { label: 'Tested', color: '#9E77ED' }
            ],
            'tested-vs-non-tested'
        ),
        'open-resolved-issues': customLineChart(
            "Open & Resolved Issues",
            openResolvedIssuesData,
            [
                { label: 'Open Issues', color: '#D72C0D' },
                { label: 'Resolved Issues', color: '#9E77ED' }
            ],
            'open-resolved-issues'
        ),
        'threat-requests-chart': customLineChart(
            `${mapLabel('Threat', dashboardCategory)} Requests over time`,
            guardrailRequestsData,
            [
                { label: 'Flagged Requests', color: '#D72C0D' },
                { label: 'Safe Requests', color: '#47B881' }
            ],
            'threat-requests-chart'
        ),
        'open-resolved-threats': customLineChart(
            `Open & Resolved ${mapLabel('Threat', dashboardCategory)}s`,
            openResolvedGuardrailsData,
            [
                { label: 'Open Issues', color: '#D72C0D' },
                { label: 'Resolved Issues', color: '#9E77ED' }
            ],
            'open-resolved-threats'
        ),
        'weakest-areas': customDataTable("Weakest Areas by Failing Percentage", weakestAreasData, true, 'weakest-areas'),
        'top-apis-issues': customDataTable(`Top ${mapLabel('APIs', dashboardCategory)} with Critical & High Issues`, topAgenticComponentsData, true, 'top-apis-issues'),
        'top-requests-by-type': customDataTable("Top Requests by Type", topRequestsByTypeData, true, 'top-requests-by-type'),
        'top-attacked-apis': customDataTable(`Top Attacked ${mapLabel('APIs', dashboardCategory)}`, topAttackedComponentsData, false, 'top-attacked-apis'),
        'top-bad-actors': customDataTable("Top Bad Actors", topBadActorsData, false, 'top-bad-actors')
    }

    const componentsMenuActivator = (
        <Button onClick={() => setPopoverActive(!popoverActive)}>
            Manage Widgets
        </Button>
    );

    const componentsMenu = (
        <Popover
            active={popoverActive}
            activator={componentsMenuActivator}
            onClose={() => setPopoverActive(false)}
        >
            <Box padding={4}>
                <VerticalStack gap={4}>
                    <ActionList
                        items={defaultVisibleComponents.map(itemId => ({
                            content: (
                                <HorizontalStack gap={2} blockAlign='center'>
                                    <input
                                        type="checkbox"
                                        checked={visibleComponents.includes(itemId)}
                                        onChange={() => toggleComponent(itemId)}
                                    />
                                    <Text>{componentNames[itemId]}</Text>
                                </HorizontalStack>
                            ),
                            onAction: () => toggleComponent(itemId)
                        }))}
                    />
                    <Button
                        onClick={saveDashboardLayout}
                        disabled={!hasUnsavedChanges}
                        loading={isSaving}
                        fullWidth
                    >
                        Save Layout
                    </Button>
                </VerticalStack>
            </Box>
        </Popover>
    );

    return (
            loading ? <SpinnerCentered /> : (
                <PageWithMultipleCards
                    isFirstPage={true}
                    title={
                        <HorizontalStack gap={3}>
                            <TitleWithInfo
                                titleText="Dashboard"
                                tooltipContent="Monitor and manage your agentic processes from this centralized dashboard. View real-time status, logs, and performance metrics to ensure optimal operation."
                                docsUrl="https://docs.akto.io/agentic-ai/agentic-dashboard"
                            />
                            <Dropdown
                                menuItems={[
                                    {label: 'CISO', value: 'ciso'}
                                ]}
                                selected={setViewMode}
                                initial={viewMode}
                            />
                        </HorizontalStack>
                    }
                    primaryAction={<HorizontalStack gap={2}>
                        {componentsMenu}
                        <Button icon={SettingsFilledMinor} onClick={() => {}}>Owner setting</Button>
                    </HorizontalStack>}
                    secondaryActions={[<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />]}
                    components={[
                        <div key="grid-container" ref={containerRef} style={{ width: '100%' }}>
                            {layoutLoading ? (
                                <SpinnerCentered />
                            ) : (
                                <GridLayout
                                    // TODO: make width responsive
                                    width={1200}
                                    layout={layout.filter(item => visibleComponents.includes(item.i))}
                                    gridConfig={{
                                        cols: 12,
                                        rowHeight: 100,
                                        margin: [16, 16],
                                        containerPadding: [0, 0]
                                    }}
                                    dragConfig={{
                                        enabled: true,
                                        handle: '.graph-menu'
                                    }}
                                    resizeConfig={{
                                        enabled: true
                                    }}
                                    compactor={null}
                                    onLayoutChange={onLayoutChange}
                                >
                                    {visibleComponents.map((itemId) => (
                                        <div key={itemId}>
                                            {allComponentsMap[itemId]}
                                        </div>
                                    ))}
                                </GridLayout>
                            )}
                        </div>
                    ]}
                />
            )
    )
}

export default AgenticDashboard
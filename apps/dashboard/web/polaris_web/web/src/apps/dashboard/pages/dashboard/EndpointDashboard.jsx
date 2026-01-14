import { Box, Button, HorizontalStack, Popover, ActionList, Text, VerticalStack, Card } from '@shopify/polaris'
import { SettingsFilledMinor } from '@shopify/polaris-icons'
import { useEffect, useReducer, useState, useRef } from 'react'
import TitleWithInfo from '../../components/shared/TitleWithInfo'
import DateRangeFilter from '../../components/layouts/DateRangeFilter'
import { produce } from 'immer'
import values from "@/util/values";
import func from '@/util/func'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import Dropdown from '../../components/layouts/Dropdown'
import SpinnerCentered from '../../components/progress/SpinnerCentered'
import "./agentic-dashboard.css"
import "react-grid-layout/css/styles.css"
import "react-resizable/css/styles.css"
import { mapLabel, getDashboardCategory } from '../../../main/labelHelper'
import { GridLayout } from "react-grid-layout";
import api from './api';
import Store from '../../store';

// Import reusable components
import CustomLineChart from './new_components/CustomLineChart'
import AverageIssueAgeCard from './new_components/AverageIssueAgeCard'
import ComplianceAtRisksCard from './new_components/ComplianceAtRisksCard'
import ChartypeComponent from '../testing/TestRunsPage/ChartypeComponent'
import ComponentHeader from './new_components/ComponentHeader'

// Import new components
import EndpointCoverageCard from './new_components/EndpointCoverageCard'
import AIToolsGridCard from './new_components/AIToolsGridCard'
import GuardrailCoverageCard from './new_components/GuardrailCoverageCard'
import HighRiskToolsCard from './new_components/HighRiskToolsCard'

// Endpoint coverage data
const endpointCoverageData = [
    { os: 'Windows', count: 12000, icon: '/public/windows_logo.png' },
    { os: 'Apple', count: 6000, icon: '/public/apple_logo.png' },
    { os: 'Linux', count: 3000, icon: '/public/linux-logo.png' }
]

// AI Tools data
const aiToolsData = [
    { name: 'ChatGPT', icon: '/public/chatgpt.png', value: 120 },
    { name: 'Grok', icon: '/public/grok.svg', value: 85 },
    { name: 'Claude', icon: '/public/claude.png', value: 95 },
    { name: 'GitHub Copilot', icon: '/public/github_copilot.png', value: 110 },
    { name: 'Perplexity', icon: '/public/perplexity.png', value: 65 }
]

// MCP Servers data (for pie chart)
const mcpServersData = {
    "GitHub MCP": { text: 120, color: "#7F56D9" },
    "Slack MCP": { text: 99, color: "#9E77ED" },
    "Confluence MCP": { text: 50, color: "#D6BBFB" },
    "Notion MCP": { text: 32, color: "#B692F6" },
    "Jira MCP": { text: 24, color: "#E9D5FF" }
}

// AI Agents data (for pie chart)
const aiAgentsData = {
    "Code Assistant Agent": { text: 88, color: "#F97316" },
    "DevOps Agent": { text: 76, color: "#3B82F6" },
    "Support Agent": { text: 52, color: "#10B981" },
    "Data Analysis Agent": { text: 42, color: "#8B5CF6" },
    "Security Triage Agent": { text: 14, color: "#EAB308" }
}

// Internal vs External usage data (for line chart)
const usageComparisonData = [
    {
        name: 'Internal',
        data: [
            [1704067200000, 30000],
            [1706745600000, 32000],
            [1709251200000, 28000],
            [1711929600000, 35000],
            [1714521600000, 33000],
            [1717200000000, 31000],
            [1719792000000, 34000],
            [1722470400000, 30000],
            [1725148800000, 32000],
            [1727740800000, 36000],
            [1730419200000, 34000],
            [1733011200000, 35000]
        ],
        color: '#7C3AED'
    },
    {
        name: 'External',
        data: [
            [1704067200000, 20000],
            [1706745600000, 22000],
            [1709251200000, 19000],
            [1711929600000, 23000],
            [1714521600000, 21000],
            [1717200000000, 20000],
            [1719792000000, 22000],
            [1722470400000, 18000],
            [1725148800000, 21000],
            [1727740800000, 24000],
            [1730419200000, 22000],
            [1733011200000, 23000]
        ],
        color: '#DC2626'
    }
]

// Issue age data (reuse structure from AgenticDashboard)
const issueAgeData = [
    { label: 'Critical Issues', days: 12, progress: 40, color: '#D92D20' },
    { label: 'High Issues', days: 12, progress: 40, color: '#F79009' },
    { label: 'Medium Issues', days: 17, progress: 57, color: '#8660d8ff' },
    { label: 'Low Issues', days: 42, progress: 100, color: '#714ec3ff' }
]

// Compliance data (reuse structure from AgenticDashboard)
const complianceData = [
    { name: 'SOC 2', percentage: 70, color: '#3B82F6', icon: '/public/SOC%202.svg' },
    { name: 'GDPR', percentage: 10, color: '#7C3AED', icon: '/public/GDPR.svg' },
    { name: 'ISO 27001', percentage: 50, color: '#F97316', icon: '/public/ISO%2027001.svg' },
    { name: 'HIPAA', percentage: 90, color: '#06B6D4', icon: '/public/HIPAA.svg' }
]

// Guardrail coverage data
const guardrailCoverageData = {
    enabledCount: 120,
    totalCount: 600,
    blocklistMode: true,
    toolIcons: [
        '/public/anydesk-logo.png',
        '/public/notion-logo.png',
        '/public/gmail-logo.png',
        '/public/ms-word-logo.png',
        '/public/zoom-logo.png',
        '/public/postman-logo.png',
        '/public/arc-logo.png',
    ]
}

// Policy violations by severity (for line chart)
const policyViolationsData = [
    {
        name: 'Critical',
        data: [
            [1704067200000, 24000],
            [1706745600000, 28000],
            [1709251200000, 22000],
            [1711929600000, 26000],
            [1714521600000, 23000],
            [1717200000000, 21000],
            [1719792000000, 25000],
            [1722470400000, 20000],
            [1725148800000, 24000],
            [1727740800000, 27000],
            [1730419200000, 25000],
            [1733011200000, 26000]
        ],
        color: '#DC2626'
    },
    {
        name: 'High',
        data: [
            [1704067200000, 32000],
            [1706745600000, 38000],
            [1709251200000, 30000],
            [1711929600000, 35000],
            [1714521600000, 33000],
            [1717200000000, 31000],
            [1719792000000, 34000],
            [1722470400000, 28000],
            [1725148800000, 32000],
            [1727740800000, 36000],
            [1730419200000, 34000],
            [1733011200000, 35000]
        ],
        color: '#F97316'
    },
    {
        name: 'Medium',
        data: [
            [1704067200000, 28000],
            [1706745600000, 32000],
            [1709251200000, 26000],
            [1711929600000, 30000],
            [1714521600000, 28000],
            [1717200000000, 27000],
            [1719792000000, 29000],
            [1722470400000, 24000],
            [1725148800000, 28000],
            [1727740800000, 31000],
            [1730419200000, 29000],
            [1733011200000, 30000]
        ],
        color: '#EAB308'
    },
    {
        name: 'Low',
        data: [
            [1704067200000, 36000],
            [1706745600000, 40000],
            [1709251200000, 34000],
            [1711929600000, 38000],
            [1714521600000, 36000],
            [1717200000000, 35000],
            [1719792000000, 37000],
            [1722470400000, 32000],
            [1725148800000, 36000],
            [1727740800000, 39000],
            [1730419200000, 37000],
            [1733011200000, 38000]
        ],
        color: '#10B981'
    }
]

// AI Usage vs Violations (for line chart)
const usageVsViolationsData = [
    {
        name: 'Violations',
        data: [
            [1704067200000, 20000],
            [1706745600000, 22000],
            [1709251200000, 19000],
            [1711929600000, 23000],
            [1714521600000, 21000],
            [1717200000000, 20000],
            [1719792000000, 22000],
            [1722470400000, 18000],
            [1725148800000, 21000],
            [1727740800000, 24000],
            [1730419200000, 22000],
            [1733011200000, 23000]
        ],
        color: '#DC2626'
    },
    {
        name: 'Prompts',
        data: [
            [1704067200000, 35000],
            [1706745600000, 40000],
            [1709251200000, 33000],
            [1711929600000, 38000],
            [1714521600000, 36000],
            [1717200000000, 34000],
            [1719792000000, 37000],
            [1722470400000, 32000],
            [1725148800000, 36000],
            [1727740800000, 39000],
            [1730419200000, 37000],
            [1733011200000, 38000]
        ],
        color: '#7C3AED'
    }
]

// High-risk tools data
const highRiskToolsData = [
    { name: 'ChatGPT', icon: '/public/chatgpt.png', percentage: 70 },
    { name: 'Grok', icon: '/public/grok.svg', percentage: 60 },
    { name: 'Microsoft Copilot', icon: '/public/ms-copilot.png', percentage: 50 },
    { name: 'GitHub Copilot', icon: '/public/github_copilot.png', percentage: 20 },
    { name: 'Perplexity', icon: '/public/perplexity.png', percentage: 10 }
]

// Data protection triggers (for pie chart)
const dataProtectionTriggersData = {
    "Source Code Exfiltration": { text: 68, color: "#DC2626" },
    "Credential Leakage": { text: 54, color: "#F97316" },
    "Financial Data Exposure": { text: 41, color: "#EAB308" },
    "Encrypted Archive Transfer": { text: 36, color: "#10B981" },
    "DLL Data Detection": { text: 24, color: "#3B82F6" }
}

// Sensitive data exposure (for pie chart)
const sensitiveDataExposureData = {
    "Source Code and Repositories": { text: 762, color: "#DC2626" },
    "API Keys, Tokens, Passwords": { text: 618, color: "#F97316" },
    "Financial Documents and Records": { text: 396, color: "#EAB308" },
    "Internal Strategy and Roadmaps": { text: 312, color: "#10B981" },
    "Customer PII Files": { text: 164, color: "#3B82F6" }
}

const EndpointDashboard = () => {
    const SCREEN_NAME = 'endpoint-dashboard';
    const dashboardCategory = getDashboardCategory();
    const setToastConfig = Store(state => state.setToastConfig);
    const [loading, setLoading] = useState(false);
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5])
    const [viewMode, setViewMode] = useState('ciso')
    const containerRef = useRef(null);
    const [popoverActive, setPopoverActive] = useState(false);

    const defaultVisibleComponents = [
        'endpoint-coverage',
        'ai-tools-grid',
        'mcp-servers-pie',
        'ai-agents-pie',
        'usage-comparison',
        'average-issue-age',
        'compliance-risks',
        'guardrail-coverage',
        'policy-violations',
        'usage-vs-violations',
        'high-risk-tools',
        'protection-triggers',
        'data-exposure'
    ]

    const [visibleComponents, setVisibleComponents] = useState(defaultVisibleComponents);

    const defaultLayout = [
        { i: 'endpoint-coverage', x: 0, y: 0, w: 12, h: 1.5, minW: 12, minH: 1.5, maxH: 1.5 },
        { i: 'ai-tools-grid', x: 0, y: 1.5, w: 6, h: 3, minW: 4, minH: 3, maxH: 4 },
        { i: 'mcp-servers-pie', x: 6, y: 1.5, w: 6, h: 3.2, minW: 6, minH: 3.2, maxH: 4.2 },
        { i: 'ai-agents-pie', x: 0, y: 4.7, w: 6, h: 3.2, minW: 6, minH: 3.2, maxH: 4.2 },
        { i: 'usage-comparison', x: 6, y: 4.7, w: 6, h: 3, minW: 4, minH: 3, maxH: 4 },
        { i: 'average-issue-age', x: 0, y: 7.9, w: 4, h: 3, minW: 4, maxW: 4, minH: 3, maxH: 4 },
        { i: 'compliance-risks', x: 4, y: 7.9, w: 8, h: 2.3, minW: 6, minH: 2.3, maxH: 3.3 },
        { i: 'guardrail-coverage', x: 0, y: 10.2, w: 6, h: 3, minW: 4, minH: 3, maxH: 4 },
        { i: 'policy-violations', x: 6, y: 10.2, w: 6, h: 3, minW: 4, minH: 3, maxH: 4 },
        { i: 'usage-vs-violations', x: 0, y: 13.2, w: 6, h: 3, minW: 4, minH: 3, maxH: 4 },
        { i: 'high-risk-tools', x: 6, y: 13.2, w: 6, h: 3, minW: 4, minH: 3, maxH: 4 },
        { i: 'protection-triggers', x: 0, y: 16.2, w: 6, h: 3.2, minW: 4, minH: 3.2, maxH: 4.2 },
        { i: 'data-exposure', x: 6, y: 16.2, w: 6, h: 3.2, minW: 4, minH: 3.2, maxH: 4.2 }
    ];

    const [layout, setLayout] = useState(defaultLayout)
    const [savedLayout, setSavedLayout] = useState(null)
    const [savedVisibleComponents, setSavedVisibleComponents] = useState(null)
    const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)
    const [isSaving, setIsSaving] = useState(false)
    const [layoutLoading, setLayoutLoading] = useState(true)

    // Load saved layout on mount
    useEffect(() => {
        const loadSavedLayout = async () => {
            try {
                const resp = await api.fetchDashboardLayout(SCREEN_NAME)

                const layoutString = typeof resp === 'string' ? resp : resp?.layout

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

    // Track unsaved changes
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
            await api.saveDashboardLayout(SCREEN_NAME, JSON.stringify(layoutData))
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
        'endpoint-coverage': 'Endpoint Coverage',
        'ai-tools-grid': 'AI Tools in Use',
        'mcp-servers-pie': 'Top Connected MCP Servers',
        'ai-agents-pie': 'Most Active AI Agents',
        'usage-comparison': 'Internal vs External AI Usage',
        'average-issue-age': 'Average Issue Age',
        'compliance-risks': 'Compliance Risk Exposure',
        'guardrail-coverage': 'AI Guardrail Coverage',
        'policy-violations': 'Policy Violations by Severity',
        'usage-vs-violations': 'AI Usage vs Policy Violations',
        'high-risk-tools': 'High-Risk AI Tools Without Guardrails',
        'protection-triggers': 'Top Data Protection Policy Triggers',
        'data-exposure': 'Top Sensitive Data Exposure Events'
    }

    const allComponentsMap = {
        'endpoint-coverage': <EndpointCoverageCard
            coverageData={endpointCoverageData}
            itemId='endpoint-coverage'
            onRemoveComponent={removeComponent}
        />,
        'ai-tools-grid': <AIToolsGridCard
            toolsData={aiToolsData}
            itemId='ai-tools-grid'
            onRemoveComponent={removeComponent}
        />,
        'mcp-servers-pie': (
            <Card>
                <Box padding={4}>
                    <VerticalStack gap={4}>
                        <ComponentHeader title="Top Connected MCP Servers" itemId='mcp-servers-pie' onRemove={removeComponent} />
                        <ChartypeComponent
                            data={mcpServersData}
                            navUrl={"#"}
                            title={""}
                            isNormal={true}
                            boxHeight={'250px'}
                            chartOnLeft={true}
                            dataTableWidth="250px"
                            boxPadding={0}
                            pieInnerSize="50%"
                        />
                    </VerticalStack>
                </Box>
            </Card>
        ),
        'ai-agents-pie': (
            <Card>
                <Box padding={4}>
                    <VerticalStack gap={4}>
                        <ComponentHeader title="Most Active AI Agents" itemId='ai-agents-pie' onRemove={removeComponent} />
                        <ChartypeComponent
                            data={aiAgentsData}
                            navUrl={"#"}
                            title={""}
                            isNormal={true}
                            boxHeight={'250px'}
                            chartOnLeft={true}
                            dataTableWidth="250px"
                            boxPadding={0}
                            pieInnerSize="50%"
                        />
                    </VerticalStack>
                </Box>
            </Card>
        ),
        'usage-comparison': <CustomLineChart
            title="Internal vs External AI Usage"
            chartData={usageComparisonData}
            labels={[
                { label: 'Internal', color: '#7C3AED' },
                { label: 'External', color: '#DC2626' }
            ]}
            itemId='usage-comparison'
            onRemoveComponent={removeComponent}
            chartHeight={200}
        />,
        'average-issue-age': <AverageIssueAgeCard
            issueAgeData={issueAgeData}
            itemId='average-issue-age'
            onRemoveComponent={removeComponent}
        />,
        'compliance-risks': <ComplianceAtRisksCard
            complianceData={complianceData}
            itemId='compliance-risks'
            onRemoveComponent={removeComponent}
        />,
        'guardrail-coverage': <GuardrailCoverageCard
            enabledCount={guardrailCoverageData.enabledCount}
            totalCount={guardrailCoverageData.totalCount}
            blocklistMode={guardrailCoverageData.blocklistMode}
            toolIcons={guardrailCoverageData.toolIcons}
            itemId='guardrail-coverage'
            onRemoveComponent={removeComponent}
        />,
        'policy-violations': <CustomLineChart
            title="Policy Violations by Severity"
            chartData={policyViolationsData}
            labels={[
                { label: 'Critical', color: '#DC2626' },
                { label: 'High', color: '#F97316' },
                { label: 'Medium', color: '#EAB308' },
                { label: 'Low', color: '#10B981' }
            ]}
            itemId='policy-violations'
            onRemoveComponent={removeComponent}
            chartHeight={200}
        />,
        'usage-vs-violations': <CustomLineChart
            title="AI Usage vs Policy Violations"
            chartData={usageVsViolationsData}
            labels={[
                { label: 'Violations', color: '#DC2626' },
                { label: 'Prompts', color: '#7C3AED' }
            ]}
            itemId='usage-vs-violations'
            onRemoveComponent={removeComponent}
            chartHeight={200}
        />,
        'high-risk-tools': <HighRiskToolsCard
            toolsData={highRiskToolsData}
            itemId='high-risk-tools'
            onRemoveComponent={removeComponent}
        />,
        'protection-triggers': (
            <Card>
                <Box padding={4}>
                    <VerticalStack gap={4} align="space-between">
                        <VerticalStack gap={4}>
                            <ComponentHeader title="Top Data Protection Policy Triggers" itemId='protection-triggers' onRemove={removeComponent} />
                            <ChartypeComponent
                                data={dataProtectionTriggersData}
                                navUrl={"#"}
                                title={""}
                                isNormal={true}
                                boxHeight={'250px'}
                                chartOnLeft={true}
                                dataTableWidth="250px"
                                boxPadding={0}
                                pieInnerSize="50%"
                            />
                        </VerticalStack>
                        <Box>
                            <Button plain>View All</Button>
                        </Box>
                    </VerticalStack>
                </Box>
            </Card>
        ),
        'data-exposure': (
            <Card>
                <Box padding={4}>
                    <VerticalStack gap={4} align="space-between">
                        <VerticalStack gap={4}>
                            <ComponentHeader title="Top Sensitive Data Exposure Events" itemId='data-exposure' onRemove={removeComponent} />
                            <ChartypeComponent
                                data={sensitiveDataExposureData}
                                navUrl={"#"}
                                title={""}
                                isNormal={true}
                                boxHeight={'250px'}
                                chartOnLeft={true}
                                dataTableWidth="250px"
                                boxPadding={0}
                                pieInnerSize="50%"
                            />
                        </VerticalStack>
                        <Box>
                            <Button plain>View All</Button>
                        </Box>
                    </VerticalStack>
                </Box>
            </Card>
        )
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
                    <Button
                        onClick={saveDashboardLayout}
                        disabled={!hasUnsavedChanges}
                        loading={isSaving}
                        fullWidth
                    >
                        Save Layout
                    </Button>
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
                            titleText="Endpoint Security Posture"
                            tooltipContent="Monitor and manage your endpoint security from this centralized dashboard."
                            docsUrl="https://docs.akto.io/endpoint-security"
                        />
                        <Dropdown
                            menuItems={[
                                { label: 'CISO', value: 'ciso' }
                            ]}
                            selected={setViewMode}
                            initial={viewMode}
                        />
                    </HorizontalStack>
                }
                primaryAction={
                    <HorizontalStack gap={2}>
                        {componentsMenu}
                        <Button icon={SettingsFilledMinor} onClick={() => { }}>
                            Owner setting
                        </Button>
                    </HorizontalStack>
                }
                secondaryActions={[
                    <DateRangeFilter
                        initialDispatch={currDateRange}
                        dispatch={(dateObj) => dispatchCurrDateRange({
                            type: "update",
                            period: dateObj.period,
                            title: dateObj.title,
                            alias: dateObj.alias
                        })}
                    />
                ]}
                components={[
                    <div key="grid-container" ref={containerRef} style={{ width: '100%' }}>
                        {layoutLoading ? (
                            <SpinnerCentered />
                        ) : (
                            <GridLayout
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

export default EndpointDashboard

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
import observeApi from '../observe/api';
import transform from '../observe/transform';
import threatApi from '../threat_detection/api';
import Store from '../../store';
import ComponentHeader from './new_components/ComponentHeader'
import AverageIssueAgeCard from './new_components/AverageIssueAgeCard'
import ComplianceAtRisksCard from './new_components/ComplianceAtRisksCard'
import CustomPieChart from './new_components/CustomPieChart'
import CustomLineChart from './new_components/CustomLineChart'
import CustomDataTable from './new_components/CustomDataTable'
import EmptyCard from './new_components/EmptyCard'

// Helper function to get compliance color based on risk level (percentage)
// Higher percentage = more issues = higher risk = red color
// Lower percentage = fewer issues = lower risk = green/blue color
const getComplianceColor = (percentage) => {
    if (percentage >= 70) {
        return '#EF4444'; // Red for high risk (70%+)
    } else if (percentage >= 30) {
        return '#F59E0B'; // Orange/Yellow for medium risk (30-70%)
    } else {
        return '#10B981'; // Green for low risk (<30%)
    }
};

// Helper function to normalize compliance name for icon lookup
// Maps compliance names to their exact file names in /public/ folder
const normalizeComplianceNameForIcon = (complianceName) => {
    if (!complianceName) return '';
    
    // Map common compliance names to their exact file names
    const complianceIconMap = {
        "SOC 2": "SOC 2",
        "GDPR": "GDPR",
        "ISO 27001": "ISO 27001",
        "HIPAA": "HIPAA",
        "PCI DSS": "PCI DSS",
        "PCI-DSS": "PCI DSS",
        "NIST 800-53": "NIST 800-53",
        "NIST 800-171": "NIST 800-171",
        "NIST": "NIST 800-53", // Default to NIST 800-53 if just "NIST"
        "OWASP": "OWASP",
        "FEDRAMP": "FEDRAMP",
        "CIS CONTROLS": "CIS CONTROLS",
        "CMMC": "CYBERSECURITY MATURITY MODEL CERTIFICATION (CMMC)",
        "CYBERSECURITY MATURITY MODEL CERTIFICATION (CMMC)": "CYBERSECURITY MATURITY MODEL CERTIFICATION (CMMC)",
        "FISMA": "FISMA",
        "CSA CCM": "CSA CCM",
        "OWASP LLM": "OWASP LLM",
        "OWASP Agentic": "OWASP Agentic",
        "NIST AI Risk Management Framework": "NIST AI Risk Management Framework",
        "MITRE ATLAS": "MITRE ATLAS"
    };
    
    // Use mapped name if available, otherwise use the provided name as-is
    return complianceIconMap[complianceName] || complianceName;
};

const AgenticDashboard = () => {
    const SCREEN_NAME = 'home-main-dashboard';
    const dashboardCategory = getDashboardCategory();
    const [loading, setLoading] = useState(true);
    const [viewMode, setViewMode] = useState('ciso')
    const [overallStats, setOverallStats] = useState([])
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[3])
    const containerRef = useRef(null);
    const [popoverActive, setPopoverActive] = useState(false);
    const [gridWidth, setGridWidth] = useState(1200);
    const setToastConfig = Store(state => state.setToastConfig);

    // State for all dashboard data - initialized with empty/default values
    const [apiDiscoveryData, setApiDiscoveryData] = useState({});
    const [issuesData, setIssuesData] = useState({});
    const [threatData, setThreatData] = useState({});
    const [averageIssueAgeData, setAverageIssueAgeData] = useState([]);
    const [testedVsNonTestedChartData, setTestedVsNonTestedChartData] = useState([]);
    const [openResolvedChartData, setOpenResolvedChartData] = useState([]);
    const [topIssuesByCategory, setTopIssuesByCategory] = useState([]);
    const [topHostnamesByIssues, setTopHostnamesByIssues] = useState([]);
    const [topThreatsByCategory, setTopThreatsByCategory] = useState([]);
    const [topAttackHosts, setTopAttackHosts] = useState([]);
    const [topBadActors, setTopBadActors] = useState([]);
    const [complianceData, setComplianceData] = useState([]);
    const [threatRequestsChartData, setThreatRequestsChartData] = useState([]);
    const [openResolvedThreatsData, setOpenResolvedThreatsData] = useState([]);

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
        { i: 'compliance-at-risks', x: 4, y: 7, w: 8, h: 2, minW: 6, minH: 2, maxH: 3 },
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

    useEffect(() => {
        let rafId = null;
        let resizeObserver = null;

        const updateWidth = () => {
            if (rafId) cancelAnimationFrame(rafId);
            rafId = requestAnimationFrame(() => {
                if (containerRef.current) {
                    setGridWidth(containerRef.current.clientWidth);
                }
            });
        };

        // Initial measurement
        updateWidth();

        // Set up observers if container is available
        if (containerRef.current) {
            resizeObserver = new ResizeObserver(updateWidth);
            resizeObserver.observe(containerRef.current);
        }

        // Window resize listener - always active
        window.addEventListener('resize', updateWidth);

        return () => {
            if (rafId) cancelAnimationFrame(rafId);
            if (resizeObserver) resizeObserver.disconnect();
            window.removeEventListener('resize', updateWidth);
        };
    }, [loading, layoutLoading]);

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

    useEffect(() => {
        const fetchAllDashboardData = async () => {
        setLoading(true);
            try {
                // Extract timestamps from date range period (same pattern as other components)
                const getTimeEpoch = (key) => {
                    if (!currDateRange.period || !currDateRange.period[key]) {
                        return 0;
                    }
                    return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
                }
                
                const startTs = getTimeEpoch("since");
                const endTs = getTimeEpoch("until");

                // Fetch all consolidated APIs in parallel
                // Note: Endpoints over time is fetched via existing InventoryAction APIs
                // Threats over time is fetched via getDailyThreatActorsCount API
                // Check if threat detection feature is available before calling the API
                const threatDetectionAvailable = func.checkForFeatureSaas && func.checkForFeatureSaas('THREAT_DETECTION');
                
                const [
                    endpointDiscoveryResponse,
                    issuesResponse,
                    testingResponse,
                    threatResponse,
                    hostTrendResponse,
                    nonHostTrendResponse,
                    threatActorsCountResponse
                ] = await Promise.allSettled([
                    api.fetchEndpointDiscoveryData(startTs, endTs),
                    api.fetchIssuesData(startTs, endTs),
                    api.fetchTestingData(startTs, endTs),
                    api.fetchThreatData(startTs, endTs),
                    observeApi.fetchNewEndpointsTrendForHostCollections(startTs, endTs),
                    observeApi.fetchNewEndpointsTrendForNonHostCollections(startTs, endTs),
                    // Only call threat API if feature is available, otherwise resolve with null
                    threatDetectionAvailable 
                        ? threatApi.getDailyThreatActorsCount(startTs, endTs, []).catch(err => {
                            // Handle 403 and other errors gracefully - return null instead of throwing
                            // This prevents infinite loops and allows the dashboard to continue loading
                            if (err?.response?.status === 403 || err?.status === 403) {
                                console.warn('Threat detection API returned 403 - feature not available or permission denied');
                                return null;
                            }
                            // For other errors, also return null to prevent breaking the dashboard
                            console.warn('Threat detection API error:', err);
                            return null;
                        })
                        : Promise.resolve(null)
                ]);

                // Process Endpoint Discovery Data
                if (endpointDiscoveryResponse.status === 'fulfilled' && endpointDiscoveryResponse.value) {
                    const data = endpointDiscoveryResponse.value;
                    const discoveryStats = data.discoveryStats || {};

                    // Check if contextSource is AGENTIC - show agentic discovery stats
                    if (dashboardCategory === 'AGENTIC') {
                        setApiDiscoveryData({
                            "AI Agents": { text: discoveryStats.aiAgents || 0, color: "#7F56D9" }, // Dark purple
                            "MCP Servers": { text: discoveryStats.mcpServers || 0, color: "#9E77ED" }, // Medium purple
                            "LLM": { text: discoveryStats.llm || 0, color: "#D6BBFB" } // Light purple
                        });
                    } else {
                        setApiDiscoveryData({
                            "Shadow": { text: discoveryStats.shadow || 0, color: "#E45357" },
                            "Sensitive": { text: discoveryStats.sensitive || 0, color: "#EF864C" },
                            "No Auth": { text: discoveryStats.noAuth || 0, color: "#F6C564" },
                            "Normal": { text: discoveryStats.normal || 0, color: "#E0E0E0" }
                        });
                    }
                } else {
                    setApiDiscoveryData({});
                }

                // Process Issues Data
                if (issuesResponse.status === 'fulfilled' && issuesResponse.value) {
                    const data = issuesResponse.value;

                    // Issues by Severity
                    const issuesBySeverity = data.issuesBySeverity || {};
                    setIssuesData({
                        "Critical": { text: issuesBySeverity.critical || 0, color: "#E45357" },
                        "High": { text: issuesBySeverity.high || 0, color: "#EF864C" },
                        "Medium": { text: issuesBySeverity.medium || 0, color: "#F6C564" },
                        "Low": { text: issuesBySeverity.low || 0, color: "#E0E0E0" }
                    });

                    // Average Issue Age
                    const averageIssueAge = data.averageIssueAge || {};
                    const maxAge = Math.max(
                        averageIssueAge.critical || 0,
                        averageIssueAge.high || 0,
                        averageIssueAge.medium || 0,
                        averageIssueAge.low || 0,
                        42
                    );

                    setAverageIssueAgeData([
                        {
                            label: 'Critical Issues',
                            days: Math.round(averageIssueAge.critical || 0),
                            progress: maxAge > 0 ? Math.round(((averageIssueAge.critical || 0) / maxAge) * 100) : 0,
                            color: '#D92D20'
                        },
                        {
                            label: 'High Issues',
                            days: Math.round(averageIssueAge.high || 0),
                            progress: maxAge > 0 ? Math.round(((averageIssueAge.high || 0) / maxAge) * 100) : 0,
                            color: '#F79009'
                        },
                        {
                            label: 'Medium Issues',
                            days: Math.round(averageIssueAge.medium || 0),
                            progress: maxAge > 0 ? Math.round(((averageIssueAge.medium || 0) / maxAge) * 100) : 0,
                            color: '#8660d8ff'
                        },
                        {
                            label: 'Low Issues',
                            days: Math.round(averageIssueAge.low || 0),
                            progress: maxAge > 0 ? Math.round(((averageIssueAge.low || 0) / maxAge) * 100) : 0,
                            color: '#714ec3ff'
                        }
                    ]);

                    // Open & Resolved Issues
                    const openResolved = data.openResolvedIssues || {};
                    const openData = transformTimeSeriesData(openResolved.open || [], startTs, endTs);
                    const resolvedData = transformTimeSeriesData(openResolved.resolved || [], startTs, endTs);

                    setOpenResolvedChartData([
                        {
                            name: 'Open Issues',
                            data: openData,
                color: '#D72C0D'
            },
                        {
                            name: 'Resolved Issues',
                            data: resolvedData,
                            color: '#9E77ED'
                        }
                    ]);

                    // Top Issues by Category
                    const topIssues = data.topIssuesByCategory || [];
                    const totalTopIssues = topIssues.reduce((sum, item) => sum + (item.count || 0), 0);
                    setTopIssuesByCategory(
                        topIssues.map((item, idx) => ({
                            name: item._id || `Issue ${idx + 1}`,
                            value: totalTopIssues > 0 ? `${Math.round(((item.count || 0) / totalTopIssues) * 100)}%` : '0%',
                            color: idx < 3 ? '#E45357' : '#EF864C'
                        }))
                    );

                    // Top Hostnames by Issues
                    const topHostnames = data.topHostnamesByIssues || [];
                    setTopHostnamesByIssues(
                        topHostnames.map((item) => ({
                            name: item.hostname || 'Unknown',
                            value: (item.count || 0).toLocaleString()
                        }))
                    );
                    
                    // Process Compliance at Risks data - show top 4 only
                    const complianceAtRisks = data.complianceAtRisks || [];
                    setComplianceData(
                        complianceAtRisks.slice(0, 4).map((item) => {
                            const complianceName = item.name || 'Unknown';
                            const percentage = item.percentage || 0;
                            // Normalize the compliance name for icon lookup (handles special cases like CMMC)
                            const normalizedName = normalizeComplianceNameForIcon(complianceName);
                            // Use func.getComplianceIcon which converts to uppercase and constructs the path
                            // URL encode the path to handle spaces and special characters in filenames
                            let iconPath = normalizedName ? func.getComplianceIcon(normalizedName) : '';
                            if (iconPath) {
                                // Split path and encode only the filename part to preserve path structure
                                const pathParts = iconPath.split('/');
                                const filename = pathParts.pop();
                                // encodeURIComponent handles spaces, but we need to ensure parentheses are encoded
                                // for CSS url() to work correctly
                                let encodedFilename = encodeURIComponent(filename);
                                // Manually encode parentheses if they weren't encoded (some contexts don't encode them)
                                encodedFilename = encodedFilename.replace(/\(/g, '%28').replace(/\)/g, '%29');
                                iconPath = pathParts.join('/') + '/' + encodedFilename;
                            }
                            return {
                                name: complianceName,
                                percentage: percentage,
                                count: item.count || 0,
                                icon: iconPath,
                                color: getComplianceColor(percentage)
                            };
                        })
                    );
                } else {
                    // Set empty defaults if API fails
                    setIssuesData({});
                    setAverageIssueAgeData([]);
                    setOpenResolvedChartData([]);
                    setTopIssuesByCategory([]);
                    setTopHostnamesByIssues([]);
                    setComplianceData([]);
                }

                // Process Testing Data
                if (testingResponse.status === 'fulfilled' && testingResponse.value) {
                    const data = testingResponse.value;
                    const testedVsNonTested = data.testedVsNonTested || {};

                    const testedData = transformTimeSeriesData(testedVsNonTested.tested || [], startTs, endTs);
                    const nonTestedData = transformTimeSeriesData(testedVsNonTested.nonTested || [], startTs, endTs);

                    setTestedVsNonTestedChartData([
                        {
                            name: 'Non-Tested',
                            data: nonTestedData,
                            color: '#D72C0D'
                        },
                        {
                            name: 'Tested',
                            data: testedData,
                            color: '#9E77ED'
                        }
                    ]);
                } else {
                    setTestedVsNonTestedChartData([]);
                }

                // Process Threat Data
                if (threatResponse.status === 'fulfilled' && threatResponse.value) {
                    const data = threatResponse.value;

                    // Threats by Severity
                    const threatsBySeverity = data.threatsBySeverity || {};
                    setThreatData({
                        "Critical": { text: threatsBySeverity.critical || 0, color: "#E45357" },
                        "High": { text: threatsBySeverity.high || 0, color: "#EF864C" },
                        "Medium": { text: threatsBySeverity.medium || 0, color: "#F6C564" },
                        "Low": { text: threatsBySeverity.low || 0, color: "#E0E0E0" }
                    });

                    // Threats over time - will be processed from threatActorsCountResponse below

                    // Top Threats by Category
                    const topThreats = data.topThreatsByCategory || [];
                    const totalTopThreats = topThreats.reduce((sum, item) => sum + (item.count || 0), 0);
                    setTopThreatsByCategory(
                        topThreats.map((item, idx) => ({
                            name: item._id || `Threat ${idx + 1}`,
                            value: totalTopThreats > 0 ? `${Math.round(((item.count || 0) / totalTopThreats) * 100)}%` : '0%'
                        }))
                    );

                    // Top Attack Hosts
                    const topHosts = data.topAttackHosts || [];
                    setTopAttackHosts(
                        topHosts.map((item) => ({
                            name: item.hostname || item._id || 'Unknown',
                            value: (item.count || 0).toLocaleString()
                        }))
                    );

                    // Top Bad Actors
                    const topActors = data.topBadActors || [];
                    setTopBadActors(
                        topActors.map((item) => ({
                            name: item.actor || item._id || 'Unknown',
                            value: (item.count || 0).toLocaleString()
                        }))
                    );

                    // Open & Resolved Threats
                    const openResolvedThreats = data.openResolvedThreats || {};
                    const openThreatsData = transformTimeSeriesData(openResolvedThreats.open || [], startTs, endTs);
                    const resolvedThreatsData = transformTimeSeriesData(openResolvedThreats.resolved || [], startTs, endTs);

                    setOpenResolvedThreatsData([
                        {
                            name: 'Open Threats',
                            data: openThreatsData,
                            color: '#D72C0D'
                        },
                        {
                            name: 'Resolved Threats',
                            data: resolvedThreatsData,
                            color: '#9E77ED'
                        }
                    ]);
                } else {
                    // Set empty defaults if API fails
                    setThreatData({});
                    setThreatRequestsChartData([]);
                    setOpenResolvedThreatsData([]);
                    setTopThreatsByCategory([]);
                    setTopAttackHosts([]);
                    setTopBadActors([]);
                }

                // Process Threats Over Time from getDailyThreatActorsCount API
                // Handle both fulfilled with null (feature not available) and rejected cases
                if (threatActorsCountResponse.status === 'fulfilled' && 
                    threatActorsCountResponse.value && 
                    threatActorsCountResponse.value.actorsCounts) {
                    const actorsCounts = threatActorsCountResponse.value.actorsCounts || [];
                    // Transform actorsCounts to [timestamp, count] format (same as HomeDashboard.jsx)
                    // actorsCounts has {ts (seconds), totalActors, criticalActors}
                    const threatRequestsDataRaw = actorsCounts
                        .sort((a, b) => a.ts - b.ts)
                        .map(item => [
                            item.ts * 1000, // Convert seconds to milliseconds
                            item.totalActors || 0
                        ]);
                    // Fill missing time periods with 0 for continuous line
                    const threatRequestsData = fillMissingTimePeriods(threatRequestsDataRaw, startTs, endTs);
                    setThreatRequestsChartData(
                        threatRequestsData.length > 0 ? [
                            {
                                name: 'Flagged Requests',
                                data: threatRequestsData,
                                color: '#D72C0D'
                            }
                        ] : []
                    );
                } else {
                    // Set empty data if API failed, returned null, or feature not available
                    setThreatRequestsChartData([]);
                }
                
                // Build Security Posture Chart data from all three APIs (after all processing)
                // This ensures the chart is built even if individual APIs fail
                let endpointsData = [];
                // Merge host and non-host trend data (same pattern as ApiChanges.jsx)
                if (hostTrendResponse.status === 'fulfilled' && nonHostTrendResponse.status === 'fulfilled') {
                    const hostTrend = hostTrendResponse.value?.data?.endpoints || [];
                    const nonHostTrend = nonHostTrendResponse.value?.data?.endpoints || [];
                    
                    // Merge both responses by combining endpoints with same _id (day number)
                    const mergedArrObj = Object.values([...hostTrend, ...nonHostTrend].reduce((acc, item) => {
                        acc[item._id] = acc[item._id] || { _id: item._id, count: 0 };
                        acc[item._id].count += item.count;
                        return acc;
                    }, {}));
                    
                    // Use the same transform function as ApiChanges.jsx to ensure consistent format
                    // This fills in missing days and returns [timestamp, count] pairs
                    const endpointsTrendObj = transform.findNewParametersCountTrend(mergedArrObj, startTs, endTs);
                    endpointsData = endpointsTrendObj.trend;
                }
                
                let issuesDataForChart = [];
                if (issuesResponse.status === 'fulfilled' && issuesResponse.value) {
                    const issuesOverTimeRaw = issuesResponse.value.issuesOverTime;
                    if (issuesOverTimeRaw && Array.isArray(issuesOverTimeRaw)) {
                        if (issuesOverTimeRaw.length > 0) {
                            issuesDataForChart = transformTimeSeriesData(issuesOverTimeRaw, startTs, endTs);
                        }
                        // If empty array, keep as empty array (chart will handle empty state)
                    } else {
                        // If issuesOverTime is missing or not an array, set to empty array
                        issuesDataForChart = [];
                    }
                } else if (issuesResponse.status === 'rejected') {
                    // If API call failed, set to empty array
                    issuesDataForChart = [];
                }
                
                let threatRequestsFlaggedData = [];
                // Only process if response is fulfilled and has valid data (not null from 403/error)
                if (threatActorsCountResponse.status === 'fulfilled' && 
                    threatActorsCountResponse.value && 
                    threatActorsCountResponse.value.actorsCounts) {
                    const actorsCounts = threatActorsCountResponse.value.actorsCounts || [];
                    // Transform actorsCounts to [timestamp, count] format (same as HomeDashboard.jsx)
                    const threatRequestsFlaggedDataRaw = actorsCounts
                        .sort((a, b) => a.ts - b.ts)
                        .map(item => [
                            item.ts * 1000, // Convert seconds to milliseconds
                            item.totalActors || 0
                        ]);
                    // Fill missing time periods with 0 for continuous line
                    threatRequestsFlaggedData = fillMissingTimePeriods(threatRequestsFlaggedDataRaw, startTs, endTs);
                }
                // If API failed or feature not available, threatRequestsFlaggedData remains empty array
                
                const overallStatsData = [
                    {
                        name: mapLabel('API Endpoints Discovered', dashboardCategory),
                        data: endpointsData,
                        color: '#B692F6'
                    },
                    {
                        name: `${mapLabel('API', dashboardCategory)} Issues`,
                        data: issuesDataForChart,
                        color: '#D72C0D'
                    },
                    {
                        name: mapLabel('Threat', dashboardCategory) + ' Requests flagged',
                        data: threatRequestsFlaggedData,
                        color: '#F3B283'
                    }
                ];
                setOverallStats(overallStatsData);
            } catch (error) {
                console.error('Error fetching dashboard data:', error);
            } finally {
                setLoading(false);
            }
        };

        fetchAllDashboardData();
    }, [dashboardCategory, currDateRange])

    // Helper function to fill missing time periods with 0 values for continuous line charts
    const fillMissingTimePeriods = (data, startTs, endTs) => {
        if (!data || data.length === 0) {
            // If no data, return empty array (will be handled by empty state)
            return [];
        }

        const startMs = startTs * 1000;
        const endMs = endTs * 1000;
        const timeDiff = endMs - startMs;

        // Determine interval based on time range
        let intervalMs;
        let isDayBased = false;
        
        if (timeDiff <= 7 * 24 * 60 * 60 * 1000) {
            // <= 7 days: daily intervals
            intervalMs = 24 * 60 * 60 * 1000;
            isDayBased = true;
        } else if (timeDiff <= 90 * 24 * 60 * 60 * 1000) {
            // <= 90 days: daily intervals
            intervalMs = 24 * 60 * 60 * 1000;
            isDayBased = true;
        } else if (timeDiff <= 365 * 24 * 60 * 60 * 1000) {
            // <= 1 year: weekly intervals
            intervalMs = 7 * 24 * 60 * 60 * 1000;
        } else {
            // > 1 year: monthly intervals
            intervalMs = 30 * 24 * 60 * 60 * 1000; // Approximate month
        }

        // Create a map of existing data points
        const dataMap = new Map();
        data.forEach(([timestamp, value]) => {
            // Round timestamp to nearest interval for matching
            const roundedTs = Math.floor(timestamp / intervalMs) * intervalMs;
            if (!dataMap.has(roundedTs) || dataMap.get(roundedTs) < value) {
                dataMap.set(roundedTs, value);
            }
        });

        // Generate complete time series with gaps filled as zeros
        const completeData = [];
        let currentTs = Math.floor(startMs / intervalMs) * intervalMs;
        
        while (currentTs <= endMs) {
            const value = dataMap.get(currentTs) || 0;
            completeData.push([currentTs, value]);
            
            if (isDayBased) {
                // For day-based intervals, increment by exactly 1 day
                currentTs += 24 * 60 * 60 * 1000;
            } else {
                currentTs += intervalMs;
            }
        }

        return completeData;
    };

    const transformTimeSeriesData = (backendData, startTs = null, endTs = null) => {
        if (!backendData || !Array.isArray(backendData)) {
            return [];
        }

        const transformedData = backendData.map(item => {
            const id = item._id;
            let timestamp;

            // Handle different time key formats from backend
            if (typeof id === 'string') {
                // New format with explicit type indicators: "D_YYYY-MM-DD" (day), "M_YYYY_M" (month), or "W_YYYY_W" (week)
                if (id.startsWith('D_')) {
                    // Day format: "D_YYYY-MM-DD"
                    const dateStr = id.substring(2); // Remove "D_" prefix
                    const date = new Date(dateStr);
                    timestamp = isNaN(date.getTime()) ? Date.now() : date.getTime();
                } else if (id.startsWith('M_')) {
                    // Month format: "M_YYYY_M" (e.g., "M_2025_7" = July 2025)
                    const parts = id.substring(2).split('_'); // Remove "M_" prefix and split
                    if (parts.length === 2) {
                        const year = parseInt(parts[0], 10);
                        const month = parseInt(parts[1], 10);
                        if (!isNaN(year) && !isNaN(month) && month >= 1 && month <= 12) {
                            const date = new Date(Date.UTC(year, month - 1, 1));
                            timestamp = date.getTime();
                        } else {
                            timestamp = Date.now();
                        }
                    } else {
                        timestamp = Date.now();
                    }
                } else if (id.startsWith('W_')) {
                    // Week format: "W_YYYY_W" (e.g., "W_2024_12" = week 12 of 2024)
                    const parts = id.substring(2).split('_'); // Remove "W_" prefix and split
                    if (parts.length === 2) {
                        const year = parseInt(parts[0], 10);
                        const week = parseInt(parts[1], 10);
                        if (!isNaN(year) && !isNaN(week) && week >= 1 && week <= 53) {
                            const date = new Date(Date.UTC(year, 0, 1));
                            const firstDay = date.getUTCDay();
                            const offset = firstDay === 0 ? 0 : 7 - firstDay;
                            date.setUTCDate(date.getUTCDate() + offset + (week - 1) * 7);
                            timestamp = date.getTime();
                        } else {
                            timestamp = Date.now();
                        }
                    } else {
                        timestamp = Date.now();
                    }
                } else if (id.includes('-') && !id.includes('_')) {
                    // Legacy day format without prefix: "YYYY-MM-DD" (backward compatibility)
                    const date = new Date(id);
                    timestamp = isNaN(date.getTime()) ? Date.now() : date.getTime();
                } else if (id.includes('_') && !id.startsWith('D_') && !id.startsWith('M_') && !id.startsWith('W_')) {
                    // Legacy format without prefix: "YYYY_X" - try to infer type (backward compatibility)
                    const parts = id.split('_');
                    if (parts.length === 2) {
                        const year = parseInt(parts[0], 10);
                        const period = parseInt(parts[1], 10);
                        
                        if (!isNaN(year) && !isNaN(period)) {
                            if (period <= 12) {
                                // Assume month format: "YYYY_M" (e.g., "2025_7" = July 2025)
                                const date = new Date(Date.UTC(year, period - 1, 1));
                                timestamp = date.getTime();
                            } else if (period <= 53) {
                                // Assume week format: "YYYY_W" (e.g., "2024_12" = week 12 of 2024)
                                const date = new Date(Date.UTC(year, 0, 1));
                                const firstDay = date.getUTCDay();
                                const offset = firstDay === 0 ? 0 : 7 - firstDay;
                                date.setUTCDate(date.getUTCDate() + offset + (period - 1) * 7);
                                timestamp = date.getTime();
                            } else {
                                // Fallback: treat as day of year
                                const date = new Date(Date.UTC(year, 0, period));
                                timestamp = date.getTime();
                            }
                        } else {
                            timestamp = Date.now();
                        }
                    } else {
                        timestamp = Date.now();
                    }
                } else {
                    // Try parsing as date string
                    const date = new Date(id);
                    timestamp = isNaN(date.getTime()) ? Date.now() : date.getTime();
                }
            } else if (id && typeof id === 'object') {
                // Legacy format: { year: 2024, timePeriod: 1 } for month/week/day grouping
                const year = id.year || new Date().getFullYear();
                const timePeriod = id.timePeriod;

                if (typeof timePeriod === 'string' && timePeriod.includes('-')) {
                    // Day format: "YYYY-MM-DD"
                    const date = new Date(timePeriod);
                    timestamp = isNaN(date.getTime()) ? Date.now() : date.getTime();
                } else if (typeof timePeriod === 'number') {
                    // Month/week/day number - calculate timestamp
                    // Determine if it's month, week, or day based on the value
                    if (timePeriod <= 12) {
                        // Likely a month (1-12)
                        const date = new Date(Date.UTC(year, timePeriod - 1, 1));
                        timestamp = date.getTime();
                    } else if (timePeriod <= 53) {
                        // Likely a week (1-53)
                        const date = new Date(Date.UTC(year, 0, 1));
                        const firstDay = date.getUTCDay();
                        const offset = firstDay === 0 ? 0 : 7 - firstDay;
                        date.setUTCDate(date.getUTCDate() + offset + (timePeriod - 1) * 7);
                        timestamp = date.getTime();
                    } else {
                        // Likely a day of year (1-365)
                        const date = new Date(Date.UTC(year, 0, timePeriod));
                        timestamp = date.getTime();
                    }
                } else {
                    timestamp = Date.now();
                }
            } else {
                // Fallback
                timestamp = Date.now();
            }

            const count = item.count || 0;
            return [timestamp, count];
        }).sort((a, b) => a[0] - b[0]); // Sort by timestamp

        // Fill missing time periods with 0 if startTs and endTs are provided
        if (startTs && endTs && transformedData.length > 0) {
            return fillMissingTimePeriods(transformedData, startTs, endTs);
        }

        return transformedData;
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

    // Helper functions to check if data is empty
    const isPieChartDataEmpty = (graphData) => {
        if (!graphData || Object.keys(graphData).length === 0) return true;
        const total = Object.values(graphData).reduce((sum, item) => sum + (item?.text || 0), 0);
        return total === 0;
    };

    const isLineChartDataEmpty = (chartData) => {
        if (!chartData || !Array.isArray(chartData) || chartData.length === 0) return true;
        const hasData = chartData.some(series => 
            series.data && Array.isArray(series.data) && series.data.length > 0 && 
            series.data.some(point => point && Array.isArray(point) && point.length >= 2 && point[1] > 0)
        );
        return !hasData;
    };

    const isArrayDataEmpty = (data) => {
        return !data || !Array.isArray(data) || data.length === 0;
    };

    const componentNames = {
        'security-posture-chart': `${mapLabel('API Security Posture', dashboardCategory)} over time`,
        'api-discovery-pie': dashboardCategory === 'AGENTIC' ? 'Agentic AI Discovery' : mapLabel('API Discovery', dashboardCategory),
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
        'security-posture-chart': isLineChartDataEmpty(overallStats) ? (
            <EmptyCard 
                title={componentNames['security-posture-chart']}
                subTitleComponent={<Text alignment='center' color='subdued'>No security posture data available for the selected time period</Text>}
                itemId='security-posture-chart'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomLineChart
                title={`${func.toSentenceCase(window.ACCOUNT_NAME)} ${mapLabel('API Security Posture', dashboardCategory)} over time`}
                chartData={overallStats}
                labels={[
                    { label: mapLabel('API Endpoints Discovered', dashboardCategory), color: '#B692F6' },
                    { label: `${mapLabel('API', dashboardCategory)} Issues`, color: '#D72C0D' },
                    { label: `${mapLabel('Threat', dashboardCategory)} Requests flagged`, color: '#F3B283' }
                ]}
                itemId='security-posture-chart'
                onRemoveComponent={removeComponent}
            />
        ),
        'api-discovery-pie': isPieChartDataEmpty(apiDiscoveryData) ? (
            <EmptyCard 
                title={componentNames['api-discovery-pie']}
                subTitleComponent={<Text alignment='center' color='subdued'>No API discovery data available for the selected time period</Text>}
                itemId='api-discovery-pie'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomPieChart
                title={dashboardCategory === 'AGENTIC' ? 'Agentic AI Discovery' : mapLabel('API Discovery', dashboardCategory)}
                subtitle={dashboardCategory === 'AGENTIC' ? 'Total Agentic Components' : `Total ${mapLabel('APIs', dashboardCategory)}`}
                graphData={apiDiscoveryData}
                itemId='api-discovery-pie'
                onRemoveComponent={removeComponent}
            />
        ),
        'issues-pie': isPieChartDataEmpty(issuesData) ? (
            <EmptyCard 
                title={componentNames['issues-pie']}
                subTitleComponent={<Text alignment='center' color='subdued'>No issues found for the selected time period</Text>}
                itemId='issues-pie'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomPieChart
                title="Issues"
                subtitle="Total Issues"
                graphData={issuesData}
                itemId='issues-pie'
                onRemoveComponent={removeComponent}
            />
        ),
        'threat-detection-pie': isPieChartDataEmpty(threatData) ? (
            <EmptyCard 
                title={componentNames['threat-detection-pie']}
                subTitleComponent={<Text alignment='center' color='subdued'>No threat detection data available for the selected time period</Text>}
                itemId='threat-detection-pie'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomPieChart
                title={mapLabel('Threat Detection', dashboardCategory)}
                subtitle="Requests Flagged"
                graphData={threatData}
                itemId='threat-detection-pie'
                onRemoveComponent={removeComponent}
            />
        ),
        'average-issue-age': isArrayDataEmpty(averageIssueAgeData) ? (
            <EmptyCard 
                title={componentNames['average-issue-age']}
                subTitleComponent={<Text alignment='center' color='subdued'>No issue age data available for the selected time period</Text>}
                itemId='average-issue-age'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <AverageIssueAgeCard
                issueAgeData={averageIssueAgeData}
                itemId='average-issue-age'
                onRemoveComponent={removeComponent}
            />
        ),
        'compliance-at-risks': isArrayDataEmpty(complianceData) ? (
            <EmptyCard 
                title={componentNames['compliance-at-risks']}
                subTitleComponent={<Text alignment='center' color='subdued'>No compliance data available for the selected time period</Text>}
                itemId='compliance-at-risks'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <ComplianceAtRisksCard
                complianceData={complianceData}
                itemId='compliance-at-risks'
                onRemoveComponent={removeComponent}
            />
        ),
        'tested-vs-non-tested': isLineChartDataEmpty(testedVsNonTestedChartData) ? (
            <EmptyCard 
                title={componentNames['tested-vs-non-tested']}
                subTitleComponent={<Text alignment='center' color='subdued'>No testing data available for the selected time period</Text>}
                itemId='tested-vs-non-tested'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomLineChart
                title={`Tested vs Non-Tested ${mapLabel('APIs', dashboardCategory)}`}
                chartData={testedVsNonTestedChartData}
                labels={[
                    { label: 'Non-Tested', color: '#D72C0D' },
                    { label: 'Tested', color: '#9E77ED' }
                ]}
                itemId='tested-vs-non-tested'
                onRemoveComponent={removeComponent}
            />
        ),
        'open-resolved-issues': isLineChartDataEmpty(openResolvedChartData) ? (
            <EmptyCard 
                title={componentNames['open-resolved-issues']}
                subTitleComponent={<Text alignment='center' color='subdued'>No issues data available for the selected time period</Text>}
                itemId='open-resolved-issues'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomLineChart
                title="Open & Resolved Issues"
                chartData={openResolvedChartData}
                labels={[
                    { label: 'Open Issues', color: '#D72C0D' },
                    { label: 'Resolved Issues', color: '#9E77ED' }
                ]}
                itemId='open-resolved-issues'
                onRemoveComponent={removeComponent}
            />
        ),
        'threat-requests-chart': isLineChartDataEmpty(threatRequestsChartData) ? (
            <EmptyCard 
                title={componentNames['threat-requests-chart']}
                subTitleComponent={<Text alignment='center' color='subdued'>No threat requests data available for the selected time period</Text>}
                itemId='threat-requests-chart'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomLineChart
                title={`${mapLabel('Threat', dashboardCategory)} Requests over time`}
                chartData={threatRequestsChartData}
                labels={[
                { label: 'Flagged Requests', color: '#D72C0D' },
                { label: 'Safe Requests', color: '#47B881' }
                ]}
                itemId='threat-requests-chart'
                onRemoveComponent={removeComponent}
            />
        ),
        'open-resolved-threats': isLineChartDataEmpty(openResolvedThreatsData) ? (
            <EmptyCard 
                title={componentNames['open-resolved-threats']}
                subTitleComponent={<Text alignment='center' color='subdued'>No threat data available for the selected time period</Text>}
                itemId='open-resolved-threats'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomLineChart
                title={`Open & Resolved ${mapLabel('Threat', dashboardCategory)}s`}
                chartData={openResolvedThreatsData}
                labels={[
                    { label: 'Open Threats', color: '#D72C0D' },
                    { label: 'Resolved Threats', color: '#9E77ED' }
                ]}
                itemId='open-resolved-threats'
                onRemoveComponent={removeComponent}
            />
        ),
        'weakest-areas': isArrayDataEmpty(topIssuesByCategory) ? (
            <EmptyCard 
                title={componentNames['weakest-areas']}
                subTitleComponent={<Text alignment='center' color='subdued'>No issues by category data available for the selected time period</Text>}
                itemId='weakest-areas'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomDataTable
                title="Top Issues by Category"
                data={topIssuesByCategory}
                showSignalIcon={true}
                itemId='weakest-areas'
                onRemoveComponent={removeComponent}
            />
        ),
        'top-apis-issues': isArrayDataEmpty(topHostnamesByIssues) ? (
            <EmptyCard 
                title={componentNames['top-apis-issues']}
                subTitleComponent={<Text alignment='center' color='subdued'>No APIs with issues data available for the selected time period</Text>}
                itemId='top-apis-issues'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomDataTable
                title={`Top ${mapLabel('APIs', dashboardCategory)} with Critical & High Issues`}
                data={topHostnamesByIssues}
                showSignalIcon={true}
                itemId='top-apis-issues'
                onRemoveComponent={removeComponent}
            />
        ),
        'top-requests-by-type': isArrayDataEmpty(topThreatsByCategory) ? (
            <EmptyCard 
                title={componentNames['top-requests-by-type']}
                subTitleComponent={<Text alignment='center' color='subdued'>No threats by category data available for the selected time period</Text>}
                itemId='top-requests-by-type'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomDataTable
                title="Top Threats by Category"
                data={topThreatsByCategory}
                showSignalIcon={true}
                itemId='top-requests-by-type'
                onRemoveComponent={removeComponent}
            />
        ),
        'top-attacked-apis': isArrayDataEmpty(topAttackHosts) ? (
            <EmptyCard 
                title={componentNames['top-attacked-apis']}
                subTitleComponent={<Text alignment='center' color='subdued'>No attacked APIs data available for the selected time period</Text>}
                itemId='top-attacked-apis'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomDataTable
                title={`Top Attacked ${mapLabel('APIs', dashboardCategory)}`}
                data={topAttackHosts}
                showSignalIcon={false}
                itemId='top-attacked-apis'
                onRemoveComponent={removeComponent}
            />
        ),
        'top-bad-actors': isArrayDataEmpty(topBadActors) ? (
            <EmptyCard 
                title={componentNames['top-bad-actors']}
                subTitleComponent={<Text alignment='center' color='subdued'>No bad actors data available for the selected time period</Text>}
                itemId='top-bad-actors'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <CustomDataTable
                title="Top Bad Actors"
                data={topBadActors}
                showSignalIcon={false}
                itemId='top-bad-actors'
                onRemoveComponent={removeComponent}
            />
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
                                titleText="Dashboard"
                                tooltipContent="Monitor and manage your agentic processes from this centralized dashboard. View real-time status, logs, and performance metrics to ensure optimal operation."
                                docsUrl="https://docs.akto.io/agentic-ai/agentic-dashboard"
                            />
                            <Box style={{ display: 'none' }}>
                                <Dropdown
                                    menuItems={[
                                        {label: 'CISO', value: 'ciso'}
                                    ]}
                                    selected={setViewMode}
                                    initial={viewMode}
                                />
                            </Box>
                        </HorizontalStack>
                    }
                    primaryAction={<HorizontalStack gap={2}>
                        {componentsMenu}
                        {/* <Button icon={SettingsFilledMinor} onClick={() => {}} style={{ display: 'none' }}>Owner setting</Button> */}
                    </HorizontalStack>}
                    secondaryActions={[<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />]}
                    components={[
                        <div key="grid-container" ref={containerRef} style={{ width: '100%' }}>
                            {layoutLoading ? (
                                <SpinnerCentered />
                            ) : (
                                <GridLayout
                                    width={gridWidth}
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
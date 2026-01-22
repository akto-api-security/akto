import { Box, Button, HorizontalStack, Popover, ActionList, Text, VerticalStack } from '@shopify/polaris'
import { useEffect, useReducer, useState, useRef } from 'react'
import TitleWithInfo from '../../components/shared/TitleWithInfo'
import DateRangeFilter from '../../components/layouts/DateRangeFilter'
import { produce } from 'immer'
import values from "@/util/values";
import func from '@/util/func'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
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
    
    // Prevent infinite loops: track if we're currently fetching and if there was a critical error
    const isFetchingRef = useRef(false);
    const lastFetchParamsRef = useRef(null);

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

    // Shared function to parse timestamp from backend _id format
    const parseTimestampFromId = (id) => {
        if (typeof id === 'string') {
            if (id.startsWith('D_')) {
                const date = new Date(id.substring(2));
                return isNaN(date.getTime()) ? null : date.getTime();
            } else if (id.startsWith('M_')) {
                const parts = id.substring(2).split('_');
                if (parts.length === 2) {
                    const year = parseInt(parts[0], 10);
                    const month = parseInt(parts[1], 10);
                    if (!isNaN(year) && !isNaN(month) && month >= 1 && month <= 12) {
                        return new Date(Date.UTC(year, month - 1, 1)).getTime();
                    }
                }
            } else if (id.startsWith('W_')) {
                const parts = id.substring(2).split('_');
                if (parts.length === 2) {
                    const year = parseInt(parts[0], 10);
                    const week = parseInt(parts[1], 10);
                    if (!isNaN(year) && !isNaN(week) && week >= 1 && week <= 53) {
                        const date = new Date(Date.UTC(year, 0, 1));
                        const firstDay = date.getUTCDay();
                        const offset = firstDay === 0 ? 0 : 7 - firstDay;
                        date.setUTCDate(date.getUTCDate() + offset + (week - 1) * 7);
                        return date.getTime();
                    }
                }
            } else if (id.includes('-') && !id.includes('_')) {
                const date = new Date(id);
                return isNaN(date.getTime()) ? null : date.getTime();
            } else if (id.includes('_') && !id.startsWith('D_') && !id.startsWith('M_') && !id.startsWith('W_')) {
                const parts = id.split('_');
                if (parts.length === 2) {
                    const year = parseInt(parts[0], 10);
                    const period = parseInt(parts[1], 10);
                    if (!isNaN(year) && !isNaN(period)) {
                        if (period <= 12) {
                            return new Date(Date.UTC(year, period - 1, 1)).getTime();
                        } else if (period <= 53) {
                            const date = new Date(Date.UTC(year, 0, 1));
                            const firstDay = date.getUTCDay();
                            const offset = firstDay === 0 ? 0 : 7 - firstDay;
                            date.setUTCDate(date.getUTCDate() + offset + (period - 1) * 7);
                            return date.getTime();
                        }
                    }
                }
            } else {
                const date = new Date(id);
                return isNaN(date.getTime()) ? null : date.getTime();
            }
        } else if (id && typeof id === 'object') {
            const year = id.year || new Date().getFullYear();
            const timePeriod = id.timePeriod;
            if (typeof timePeriod === 'string' && timePeriod.includes('-')) {
                const date = new Date(timePeriod);
                return isNaN(date.getTime()) ? null : date.getTime();
            } else if (typeof timePeriod === 'number') {
                if (timePeriod <= 12) {
                    return new Date(Date.UTC(year, timePeriod - 1, 1)).getTime();
                } else if (timePeriod <= 53) {
                    const date = new Date(Date.UTC(year, 0, 1));
                    const firstDay = date.getUTCDay();
                    const offset = firstDay === 0 ? 0 : 7 - firstDay;
                    date.setUTCDate(date.getUTCDate() + offset + (timePeriod - 1) * 7);
                    return date.getTime();
                }
            }
        }
        return null;
    }

    // Helper function to extract timestamps from raw backend data (before transformation)
    const findActualDataTimeRangeFromRaw = (rawDataArrays) => {
        let minTs = null;
        let maxTs = null;

        rawDataArrays.forEach(rawData => {
            if (!rawData || !Array.isArray(rawData)) return;
            rawData.forEach(item => {
                if (!item || !item.count || item.count <= 0) return;
                const timestamp = parseTimestampFromId(item._id);
                if (timestamp) {
                    if (minTs === null || timestamp < minTs) minTs = timestamp;
                    if (maxTs === null || timestamp > maxTs) maxTs = timestamp;
                }
            });
        });

        return (minTs !== null && maxTs !== null) ? {
            startTs: Math.floor(minTs / 1000),
            endTs: Math.floor(maxTs / 1000)
        } : null;
    }

    useEffect(() => {
        const fetchAllDashboardData = async () => {
            // Prevent infinite loops: check if we're already fetching or if params haven't changed
            const fetchParams = JSON.stringify({ dashboardCategory, currDateRange });
            if (isFetchingRef.current) {
                console.log('Already fetching, skipping duplicate call');
                return;
            }
            if (lastFetchParamsRef.current === fetchParams) {
                console.log('Params unchanged, skipping duplicate call');
                return;
            }
            
            isFetchingRef.current = true;
            lastFetchParamsRef.current = fetchParams;
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
                const isAllTime = startTs <= 0 || startTs < 86400000; // Less than 1 day = likely "All time"

                // Fetch all consolidated APIs in parallel
                // Note: Endpoints over time is fetched via existing InventoryAction APIs
                // Threats over time is fetched via getDailyThreatActorsCount API
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
                    threatApi.getDailyThreatActorsCount(startTs, endTs, [])
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

                // Collect all raw time series data to determine actual time range for "All time"
                const allRawTimeSeriesData = [];
                let threatActorsCountsRaw = [];

                // Process Issues Data
                if (issuesResponse.status === 'fulfilled' && issuesResponse.value) {
                    const data = issuesResponse.value;

                    // Collect raw time series data
                    const openResolved = data.openResolvedIssues || {};
                    if (openResolved.open) allRawTimeSeriesData.push(openResolved.open);
                    if (openResolved.resolved) allRawTimeSeriesData.push(openResolved.resolved);
                    if (data.issuesOverTime) allRawTimeSeriesData.push(data.issuesOverTime);

                    // Issues by Severity
                    const issuesBySeverity = data.issuesBySeverity || {};
                    setIssuesData({
                        "Critical": { text: issuesBySeverity.critical || 0, color: "#DF2909" },
                        "High": { text: issuesBySeverity.high || 0, color: "#FED3D1" },
                        "Medium": { text: issuesBySeverity.medium || 0, color: "#FFD79D" },
                        "Low": { text: issuesBySeverity.low || 0, color: "#E4E5E7" }
                    });

                    // Average Issue Age
                    const averageIssueAge = data.averageIssueAge || {};
                    const severityData = [
                        { key: 'critical', label: 'Critical Issues', color: '#D92D20' },
                        { key: 'high', label: 'High Issues', color: '#F79009' },
                        { key: 'medium', label: 'Medium Issues', color: '#8660d8ff' },
                        { key: 'low', label: 'Low Issues', color: '#714ec3ff' }
                    ];
                    const maxAge = Math.max(...severityData.map(s => averageIssueAge[s.key] || 0), 42);
                    setAverageIssueAgeData(severityData.map(s => ({
                        label: s.label,
                        days: Math.round(averageIssueAge[s.key] || 0),
                        progress: maxAge > 0 ? Math.round(((averageIssueAge[s.key] || 0) / maxAge) * 100) : 0,
                        color: s.color
                    })));
                } else {
                    // Set empty defaults if API fails
                    setIssuesData({});
                    setAverageIssueAgeData([]);
                }

                // Calculate actual time range from data if "All time" is selected
                let chartStartTs = startTs;
                let chartEndTs = endTs;
                
                if (isAllTime) {
                    // Find actual data range from all collected raw time series data
                    let actualRange = findActualDataTimeRangeFromRaw(allRawTimeSeriesData);
                    
                    // Also check threat actors count data
                    if (threatActorsCountsRaw.length > 0) {
                        const threatTimestamps = threatActorsCountsRaw
                            .filter(item => item.ts && item.totalActors > 0)
                            .map(item => item.ts);
                        if (threatTimestamps.length > 0) {
                            const threatRange = {
                                startTs: Math.min(...threatTimestamps),
                                endTs: Math.max(...threatTimestamps)
                            };
                            actualRange = actualRange ? {
                                startTs: Math.min(actualRange.startTs, threatRange.startTs),
                                endTs: Math.max(actualRange.endTs, threatRange.endTs)
                            } : threatRange;
                        }
                    }
                    
                    // Also check endpoints trend data
                    if (hostTrendResponse.status === 'fulfilled' && nonHostTrendResponse.status === 'fulfilled') {
                        const hostTrend = hostTrendResponse.value?.data?.endpoints || [];
                        const nonHostTrend = nonHostTrendResponse.value?.data?.endpoints || [];
                        const mergedTrend = [...hostTrend, ...nonHostTrend];
                        
                        if (mergedTrend.length > 0) {
                            const trendRange = findActualDataTimeRangeFromRaw([mergedTrend]);
                            if (trendRange) {
                                if (!actualRange) {
                                    actualRange = trendRange;
                                } else {
                                    actualRange.startTs = Math.min(actualRange.startTs, trendRange.startTs);
                                    actualRange.endTs = Math.max(actualRange.endTs, trendRange.endTs);
                                }
                            }
                        }
                    }
                    
                    // Use the calculated range if we found one
                    if (actualRange) {
                        chartStartTs = actualRange.startTs;
                        chartEndTs = actualRange.endTs;
                    } else {
                        // If we still don't have a valid range, use current time as fallback
                        chartStartTs = Math.floor(Date.now() / 1000) - (365 * 24 * 60 * 60); // Default to 1 year ago
                        chartEndTs = Math.floor(Date.now() / 1000);
                    }
                }

                // Now process all time series data with the calculated time range
                // Process Issues Data - Open & Resolved Issues
                if (issuesResponse.status === 'fulfilled' && issuesResponse.value) {
                    const data = issuesResponse.value;
                    const openResolved = data.openResolvedIssues || {};
                    const openData = transformTimeSeriesData(openResolved.open || [], chartStartTs, chartEndTs);
                    const resolvedData = transformTimeSeriesData(openResolved.resolved || [], chartStartTs, chartEndTs);

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
                    setTopIssuesByCategory(topIssues.map((item, idx) => ({
                        name: item._id || `Issue ${idx + 1}`,
                        value: totalTopIssues > 0 ? `${Math.round(((item.count || 0) / totalTopIssues) * 100)}%` : '0%',
                        color: idx < 3 ? '#E45357' : '#EF864C'
                    })));

                    // Top Hostnames by Issues
                    setTopHostnamesByIssues((data.topHostnamesByIssues || []).map(item => ({
                        name: item.hostname || 'Unknown',
                        value: (item.count || 0).toLocaleString()
                    })));
                    
                    // Process Compliance at Risks data - show top 4 only
                    const complianceAtRisks = data.complianceAtRisks || [];
                    setComplianceData(
                        complianceAtRisks.slice(0, 4).map((item) => {
                            const complianceName = item.name || 'Unknown';
                            const normalizedName = normalizeComplianceNameForIcon(complianceName);
                            const iconPath = encodeComplianceIconPath(
                                normalizedName ? func.getComplianceIcon(normalizedName) : ''
                            );
                            return {
                                name: complianceName,
                                percentage: item.percentage || 0,
                                count: item.count || 0,
                                icon: iconPath,
                                color: getComplianceColor(item.percentage || 0)
                            };
                        })
                    );
                } else {
                    // Set empty defaults if API fails
                    setOpenResolvedChartData([]);
                    setTopIssuesByCategory([]);
                    setTopHostnamesByIssues([]);
                    setComplianceData([]);
                }

                // Process Testing Data
                if (testingResponse.status === 'fulfilled' && testingResponse.value) {
                    const data = testingResponse.value;
                    const testedVsNonTested = data.testedVsNonTested || {};

                    const testedData = transformTimeSeriesData(testedVsNonTested.tested || [], chartStartTs, chartEndTs);
                    const nonTestedData = transformTimeSeriesData(testedVsNonTested.nonTested || [], chartStartTs, chartEndTs);

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
                        "Critical": { text: threatsBySeverity.critical || 0, color: "#DF2909" },
                        "High": { text: threatsBySeverity.high || 0, color: "#FED3D1" },
                        "Medium": { text: threatsBySeverity.medium || 0, color: "#FFD79D" },
                        "Low": { text: threatsBySeverity.low || 0, color: "#E4E5E7" }
                    });

                    // Top Threats by Category
                    const topThreats = data.topThreatsByCategory || [];
                    const totalTopThreats = topThreats.reduce((sum, item) => sum + (item.count || 0), 0);
                    setTopThreatsByCategory(topThreats.map((item, idx) => ({
                        name: item._id || `Threat ${idx + 1}`,
                        value: totalTopThreats > 0 ? `${Math.round(((item.count || 0) / totalTopThreats) * 100)}%` : '0%'
                    })));

                    // Top Attack Hosts
                    setTopAttackHosts((data.topAttackHosts || []).map(item => ({
                        name: item.hostname || item._id || 'Unknown',
                        value: (item.count || 0).toLocaleString()
                    })));

                    // Top Bad Actors
                    setTopBadActors((data.topBadActors || []).map(item => ({
                        name: item.actor || item._id || 'Unknown',
                        value: (item.count || 0).toLocaleString(),
                        country: item.country || null
                    })));

                    // Open & Resolved Threats
                    const openResolvedThreats = data.openResolvedThreats || {};
                    const openThreatsData = transformTimeSeriesData(openResolvedThreats.open || [], chartStartTs, chartEndTs);
                    const resolvedThreatsData = transformTimeSeriesData(openResolvedThreats.resolved || [], chartStartTs, chartEndTs);

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
                    setTopThreatsByCategory([]);
                    setTopAttackHosts([]);
                    setTopBadActors([]);
                    setOpenResolvedThreatsData([]);
                }

                // Process Threats Over Time from getDailyThreatActorsCount API
                if (threatActorsCountResponse.status === 'fulfilled' && threatActorsCountResponse.value) {
                    const actorsCounts = threatActorsCountResponse.value.actorsCounts || [];
                    const threatRequestsData = transformThreatActorsCount(actorsCounts, chartStartTs, chartEndTs);
                    setThreatRequestsChartData(
                        threatRequestsData.length > 0 ? [{
                            name: 'Flagged Requests',
                            data: threatRequestsData,
                            color: '#D72C0D'
                        }] : []
                    );
                } else {
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
                    const endpointsTrendObj = transform.findNewParametersCountTrend(mergedArrObj, chartStartTs, chartEndTs);
                    endpointsData = endpointsTrendObj.trend;
                }
                
                let issuesDataForChart = [];
                if (issuesResponse.status === 'fulfilled' && issuesResponse.value) {
                    const issuesOverTimeRaw = issuesResponse.value.issuesOverTime;
                    if (issuesOverTimeRaw && Array.isArray(issuesOverTimeRaw)) {
                        if (issuesOverTimeRaw.length > 0) {
                            issuesDataForChart = transformTimeSeriesData(issuesOverTimeRaw, chartStartTs, chartEndTs);
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
                
                const threatRequestsFlaggedData = (threatActorsCountResponse.status === 'fulfilled' && threatActorsCountResponse.value)
                    ? transformThreatActorsCount(threatActorsCountResponse.value.actorsCounts || [], chartStartTs, chartEndTs)
                    : [];
                
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
                // Don't retry on error - prevent infinite loops
            } finally {
                setLoading(false);
                isFetchingRef.current = false;
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
        if (!backendData || !Array.isArray(backendData)) return [];

        const transformedData = backendData.map(item => {
            const timestamp = parseTimestampFromId(item._id) || Date.now();
            return [timestamp, item.count || 0];
        }).sort((a, b) => a[0] - b[0]);

        return (startTs && endTs && transformedData.length > 0) 
            ? fillMissingTimePeriods(transformedData, startTs, endTs)
            : transformedData;
    }

    // Helper to transform threat actors count data
    const transformThreatActorsCount = (actorsCounts, chartStartTs, chartEndTs) => {
        if (!actorsCounts || !Array.isArray(actorsCounts)) return [];
        const threatRequestsDataRaw = actorsCounts
            .sort((a, b) => a.ts - b.ts)
            .map(item => [item.ts * 1000, item.totalActors || 0]);
        return fillMissingTimePeriods(threatRequestsDataRaw, chartStartTs, chartEndTs);
    }

    // Helper to encode compliance icon path
    const encodeComplianceIconPath = (iconPath) => {
        if (!iconPath) return '';
        const pathParts = iconPath.split('/');
        const filename = pathParts.pop();
        let encodedFilename = encodeURIComponent(filename);
        encodedFilename = encodedFilename.replace(/\(/g, '%28').replace(/\)/g, '%29');
        return pathParts.join('/') + '/' + encodedFilename;
    }

    // Handler for clicking on lines in the security posture chart
    const handleSecurityPostureLineClick = (event) => {
        if (!event || !event.point || !event.point.series) return;
        
        const clickedSeriesName = event.point.series.name;
        const apiIssuesSeriesName = `${mapLabel('API', dashboardCategory)} Issues`;
        const apiEndpointsSeriesName = mapLabel('API Endpoints Discovered', dashboardCategory);
        const threatRequestsSeriesName = `${mapLabel('Threat', dashboardCategory)} Requests flagged`;
        
        let url = null;
        
        if (clickedSeriesName === apiIssuesSeriesName) {
            // API Issues line -> redirect to issues page
            url = `${window.location.origin}/dashboard/reports/issues#open`;
        } else if (clickedSeriesName === apiEndpointsSeriesName) {
            // API Endpoints Discovered line -> redirect to changes page
            url = `${window.location.origin}/dashboard/observe/changes?filters=#new_endpoints`;
        } else if (clickedSeriesName === threatRequestsSeriesName) {
            // Threat Requests flagged line -> redirect to threat activity page
            url = `${window.location.origin}/dashboard/protection/threat-activity?filters=#active`;
        }
        
        if (url) {
            window.open(url, '_blank');
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
        'top-attacked-apis': 'Top Attacked Hosts by Threats',
        'top-bad-actors': 'Top Bad Actors'
    };

    const allComponentsMap = {
        'security-posture-chart': isLineChartDataEmpty(overallStats) ? (
            <EmptyCard
                title={componentNames['security-posture-chart']}
                subTitleComponent={<Text alignment='center' color='subdued'>No security posture data available for the selected time period</Text>}
                itemId='security-posture-chart'
                onRemoveComponent={removeComponent}
                tooltipContent="Track the overall security posture over time, including API discovery, issues, and threat detection trends"
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
                tooltipContent="Track the overall security posture over time, including API discovery, issues, and threat detection trends"
                graphPointClick={handleSecurityPostureLineClick}
            />
        ),
        'api-discovery-pie': isPieChartDataEmpty(apiDiscoveryData) ? (
            <EmptyCard
                title={componentNames['api-discovery-pie']}
                subTitleComponent={<Text alignment='center' color='subdued'>No API discovery data available for the selected time period</Text>}
                itemId='api-discovery-pie'
                onRemoveComponent={removeComponent}
                tooltipContent={dashboardCategory === 'AGENTIC' ? 'Distribution of discovered agentic components including AI agents, MCP servers, and LLMs' : 'Distribution of discovered APIs categorized by shadow, sensitive, no auth, and normal'}
            />
        ) : (
            <CustomPieChart
                title={dashboardCategory === 'AGENTIC' ? 'Agentic AI Discovery' : mapLabel('API Discovery', dashboardCategory)}
                subtitle={dashboardCategory === 'AGENTIC' ? 'Total Agentic Components' : `Total ${mapLabel('APIs', dashboardCategory)}`}
                graphData={apiDiscoveryData}
                itemId='api-discovery-pie'
                onRemoveComponent={removeComponent}
                tooltipContent={dashboardCategory === 'AGENTIC' ? 'Distribution of discovered agentic components including AI agents, MCP servers, and LLMs' : 'Distribution of discovered APIs categorized by shadow, sensitive, no auth, and normal'}
            />
        ),
        'issues-pie': isPieChartDataEmpty(issuesData) ? (
            <EmptyCard
                title={componentNames['issues-pie']}
                subTitleComponent={<Text alignment='center' color='subdued'>No issues found for the selected time period</Text>}
                itemId='issues-pie'
                onRemoveComponent={removeComponent}
                tooltipContent="Distribution of security issues by severity level (Critical, High, Medium, Low)"
            />
        ) : (
            <CustomPieChart
                title="Issues"
                subtitle="Total Issues"
                graphData={issuesData}
                itemId='issues-pie'
                onRemoveComponent={removeComponent}
                tooltipContent="Distribution of security issues by severity level (Critical, High, Medium, Low)"
            />
        ),
        'threat-detection-pie': isPieChartDataEmpty(threatData) ? (
            <EmptyCard
                title={componentNames['threat-detection-pie']}
                subTitleComponent={<Text alignment='center' color='subdued'>No threat detection data available for the selected time period</Text>}
                itemId='threat-detection-pie'
                onRemoveComponent={removeComponent}
                tooltipContent="Distribution of flagged threat requests by severity level"
            />
        ) : (
            <CustomPieChart
                title={mapLabel('Threat Detection', dashboardCategory)}
                subtitle="Requests Flagged"
                graphData={threatData}
                itemId='threat-detection-pie'
                onRemoveComponent={removeComponent}
                tooltipContent="Distribution of flagged threat requests by severity level"
            />
        ),
        'average-issue-age': isArrayDataEmpty(averageIssueAgeData) ? (
            <EmptyCard
                title={componentNames['average-issue-age']}
                subTitleComponent={<Text alignment='center' color='subdued'>No issue age data available for the selected time period</Text>}
                itemId='average-issue-age'
                onRemoveComponent={removeComponent}
                tooltipContent="Average number of days security issues have been open, categorized by severity"
            />
        ) : (
            <AverageIssueAgeCard
                issueAgeData={averageIssueAgeData}
                itemId='average-issue-age'
                onRemoveComponent={removeComponent}
                tooltipContent="Average number of days security issues have been open, categorized by severity"
            />
        ),
        'compliance-at-risks': isArrayDataEmpty(complianceData) ? (
            <EmptyCard
                title={componentNames['compliance-at-risks']}
                subTitleComponent={<Text alignment='center' color='subdued'>No compliance data available for the selected time period</Text>}
                itemId='compliance-at-risks'
                onRemoveComponent={removeComponent}
                tooltipContent="Top compliance frameworks at risk based on failing test percentages"
            />
        ) : (
            <ComplianceAtRisksCard
                complianceData={complianceData}
                itemId='compliance-at-risks'
                onRemoveComponent={removeComponent}
                tooltipContent="Top compliance frameworks at risk based on failing test percentages"
            />
        ),
        'tested-vs-non-tested': isLineChartDataEmpty(testedVsNonTestedChartData) ? (
            <EmptyCard
                title={componentNames['tested-vs-non-tested']}
                subTitleComponent={<Text alignment='center' color='subdued'>No testing data available for the selected time period</Text>}
                itemId='tested-vs-non-tested'
                onRemoveComponent={removeComponent}
                tooltipContent="Track the number of APIs that have been tested vs those that haven't been tested over time"
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
                tooltipContent="Track the number of APIs that have been tested vs those that haven't been tested over time"
            />
        ),
        'open-resolved-issues': isLineChartDataEmpty(openResolvedChartData) ? (
            <EmptyCard
                title={componentNames['open-resolved-issues']}
                subTitleComponent={<Text alignment='center' color='subdued'>No issues data available for the selected time period</Text>}
                itemId='open-resolved-issues'
                onRemoveComponent={removeComponent}
                tooltipContent="Trend of open vs resolved security issues over time"
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
                tooltipContent="Trend of open vs resolved security issues over time"
            />
        ),
        'threat-requests-chart': isLineChartDataEmpty(threatRequestsChartData) ? (
            <EmptyCard
                title={componentNames['threat-requests-chart']}
                subTitleComponent={<Text alignment='center' color='subdued'>No threat requests data available for the selected time period</Text>}
                itemId='threat-requests-chart'
                onRemoveComponent={removeComponent}
                tooltipContent="Number of threat requests flagged by the system over time"
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
                tooltipContent="Number of threat requests flagged by the system over time"
            />
        ),
        'open-resolved-threats': isLineChartDataEmpty(openResolvedThreatsData) ? (
            <EmptyCard
                title={componentNames['open-resolved-threats']}
                subTitleComponent={<Text alignment='center' color='subdued'>No threat data available for the selected time period</Text>}
                itemId='open-resolved-threats'
                onRemoveComponent={removeComponent}
                tooltipContent="Trend of open vs resolved threats over time"
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
                tooltipContent="Trend of open vs resolved threats over time"
            />
        ),
        'weakest-areas': isArrayDataEmpty(topIssuesByCategory) ? (
            <EmptyCard
                title={componentNames['weakest-areas']}
                subTitleComponent={<Text alignment='center' color='subdued'>No issues by category data available for the selected time period</Text>}
                itemId='weakest-areas'
                onRemoveComponent={removeComponent}
                tooltipContent="Top vulnerability categories by percentage of total issues"
            />
        ) : (
            <CustomDataTable
                title="Top Issues by Category"
                data={topIssuesByCategory}
                showSignalIcon={true}
                itemId='weakest-areas'
                onRemoveComponent={removeComponent}
                tooltipContent="Top vulnerability categories by percentage of total issues"
                columnHeaders={['Issues', 'By %']}
            />
        ),
        'top-apis-issues': isArrayDataEmpty(topHostnamesByIssues) ? (
            <EmptyCard
                title={componentNames['top-apis-issues']}
                subTitleComponent={<Text alignment='center' color='subdued'>No APIs with issues data available for the selected time period</Text>}
                itemId='top-apis-issues'
                onRemoveComponent={removeComponent}
                tooltipContent="APIs with the highest number of critical and high severity security issues"
            />
        ) : (
            <CustomDataTable
                title={`Top ${mapLabel('APIs', dashboardCategory)} with Critical & High Issues`}
                data={topHostnamesByIssues}
                showSignalIcon={true}
                iconType='globe'
                itemId='top-apis-issues'
                onRemoveComponent={removeComponent}
                tooltipContent="APIs with the highest number of critical and high severity security issues"
                columnHeaders={[mapLabel('APIs', dashboardCategory), 'Issues']}
            />
        ),
        'top-requests-by-type': isArrayDataEmpty(topThreatsByCategory) ? (
            <EmptyCard
                title={componentNames['top-requests-by-type']}
                subTitleComponent={<Text alignment='center' color='subdued'>No threats by category data available for the selected time period</Text>}
                itemId='top-requests-by-type'
                onRemoveComponent={removeComponent}
                tooltipContent="Most common threat types detected by percentage"
            />
        ) : (
            <CustomDataTable
                title="Top Threats by Category"
                data={topThreatsByCategory}
                showSignalIcon={true}
                itemId='top-requests-by-type'
                onRemoveComponent={removeComponent}
                tooltipContent="Most common threat types detected by percentage"
                columnHeaders={['Threat Category', 'By %']}
            />
        ),
        'top-attacked-apis': isArrayDataEmpty(topAttackHosts) ? (
            <EmptyCard
                title={componentNames['top-attacked-apis']}
                subTitleComponent={<Text alignment='center' color='subdued'>No attacked hostnames data available for the selected time period</Text>}
                itemId='top-attacked-apis'
                onRemoveComponent={removeComponent}
                tooltipContent="Hostnames that have received the most threat requests"
            />
        ) : (
            <CustomDataTable
                title="Top Attacked Hosts by Threats"
                data={topAttackHosts}
                showSignalIcon={true}
                iconType='globe'
                itemId='top-attacked-apis'
                onRemoveComponent={removeComponent}
                tooltipContent="Hostnames that have received the most threat requests"
                columnHeaders={['Hostnames', 'Threats']}
            />
        ),
        'top-bad-actors': isArrayDataEmpty(topBadActors) ? (
            <EmptyCard
                title={componentNames['top-bad-actors']}
                subTitleComponent={<Text alignment='center' color='subdued'>No bad actors data available for the selected time period</Text>}
                itemId='top-bad-actors'
                onRemoveComponent={removeComponent}
                tooltipContent="IP addresses or actors that have triggered the most threats"
            />
        ) : (
            <CustomDataTable
                title="Top Bad Actors"
                data={topBadActors}
                showSignalIcon={false}
                itemId='top-bad-actors'
                onRemoveComponent={removeComponent}
                tooltipContent="IP addresses or actors that have triggered the most threats"
                columnHeaders={['Bad Actors', 'Threats']}
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
                        <TitleWithInfo
                            titleText="Dashboard"
                            tooltipContent="Monitor and manage your agentic processes from this centralized dashboard. View real-time status, logs, and performance metrics to ensure optimal operation."
                            docsUrl="https://docs.akto.io/agentic-ai/agentic-dashboard"
                        />
                    }
                    primaryAction={componentsMenu}
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
                                        handle: '.drag-handle-icon'
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
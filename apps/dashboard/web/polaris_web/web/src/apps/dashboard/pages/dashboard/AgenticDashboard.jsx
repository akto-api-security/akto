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
import ComponentHeader from './new_components/ComponentHeader'
import AverageIssueAgeCard from './new_components/AverageIssueAgeCard'
import ComplianceAtRisksCard from './new_components/ComplianceAtRisksCard'
import CustomPieChart from './new_components/CustomPieChart'
import CustomLineChart from './new_components/CustomLineChart'
import CustomDataTable from './new_components/CustomDataTable'

const AgenticDashboard = () => {
    const SCREEN_NAME = 'home-main-dashboard';
    const dashboardCategory = getDashboardCategory();
    const [loading, setLoading] = useState(true);
    const [viewMode, setViewMode] = useState('ciso')
    const [overallStats, setOverallStats] = useState([])
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5])
    const containerRef = useRef(null);
    const [popoverActive, setPopoverActive] = useState(false);
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
                const [
                    endpointDiscoveryResponse,
                    issuesResponse,
                    testingResponse,
                    threatResponse
                ] = await Promise.allSettled([
                    api.fetchEndpointDiscoveryData(startTs, endTs),
                    api.fetchIssuesData(startTs, endTs),
                    api.fetchTestingData(startTs, endTs),
                    api.fetchThreatData(startTs, endTs)
                ]);

                // Process Endpoint Discovery Data
                if (endpointDiscoveryResponse.status === 'fulfilled' && endpointDiscoveryResponse.value) {
                    const data = endpointDiscoveryResponse.value;
                    const discoveryStats = data.discoveryStats || {};

                    setApiDiscoveryData({
                        "Shadow": { text: discoveryStats.shadow || 0, color: "#E45357" },
                        "Sensitive": { text: discoveryStats.sensitive || 0, color: "#EF864C" },
                        "No Auth": { text: discoveryStats.noAuth || 0, color: "#F6C564" },
                        "Normal": { text: discoveryStats.normal || 0, color: "#E0E0E0" }
                    });
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
                    const openData = transformTimeSeriesData(openResolved.open || []);
                    const resolvedData = transformTimeSeriesData(openResolved.resolved || []);

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
                } else {
                    // Set empty defaults if API fails
                    setIssuesData({});
                    setAverageIssueAgeData([]);
                    setOpenResolvedChartData([]);
                    setTopIssuesByCategory([]);
                    setTopHostnamesByIssues([]);
                }

                // Process Testing Data
                if (testingResponse.status === 'fulfilled' && testingResponse.value) {
                    const data = testingResponse.value;
                    const testedVsNonTested = data.testedVsNonTested || {};

                    const testedData = transformTimeSeriesData(testedVsNonTested.tested || []);
                    const nonTestedData = transformTimeSeriesData(testedVsNonTested.nonTested || []);

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

                    // Threats over time
                    const threatsOverTime = data.threatsOverTime || [];
                    const threatRequestsData = transformTimeSeriesData(threatsOverTime);
                    // For now, threat requests chart shows only flagged requests
                    // TODO: Add safe requests data when available from API
                    setThreatRequestsChartData(
                        threatRequestsData.length > 0 ? [
                            {
                                name: 'Flagged Requests',
                                data: threatRequestsData,
                                color: '#D72C0D'
                            }
                        ] : []
                    );

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

                    // Open & Resolved Threats - not available from backend yet
                    // Set empty array as backend doesn't provide this data
                    setOpenResolvedThreatsData([]);
                } else {
                    // Set empty defaults if API fails
                    setThreatData({});
                    setThreatRequestsChartData([]);
                    setOpenResolvedThreatsData([]);
                    setTopThreatsByCategory([]);
                    setTopAttackHosts([]);
                    setTopBadActors([]);
                }
                
                // Build Security Posture Chart data from all three APIs (after all processing)
                // This ensures the chart is built even if individual APIs fail
                let endpointsData = [];
                if (endpointDiscoveryResponse.status === 'fulfilled' && endpointDiscoveryResponse.value) {
                    endpointsData = transformTimeSeriesData(endpointDiscoveryResponse.value.endpointsDiscovered || []);
                }
                
                let issuesData = [];
                if (issuesResponse.status === 'fulfilled' && issuesResponse.value) {
                    issuesData = transformTimeSeriesData(issuesResponse.value.issuesOverTime || []);
                }
                
                let threatRequestsFlaggedData = [];
                if (threatResponse.status === 'fulfilled' && threatResponse.value) {
                    const threatsOverTime = threatResponse.value.threatsOverTime || [];
                    threatRequestsFlaggedData = transformTimeSeriesData(threatsOverTime);
                }
                
                const overallStatsData = [
                    {
                        name: mapLabel('API Endpoints Discovered', dashboardCategory),
                        data: endpointsData,
                        color: '#B692F6'
                    },
                    {
                        name: `${mapLabel('API', dashboardCategory)} Issues`,
                        data: issuesData,
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

    const transformTimeSeriesData = (backendData) => {
        if (!backendData || !Array.isArray(backendData)) {
            return [];
        }

        return backendData.map(item => {
            const id = item._id;
            let timestamp;

            // Handle different time key formats from backend
            if (typeof id === 'string') {
                // New format from Java-based aggregation: "YYYY-MM-DD" (day), "YYYY_M" (month), or "YYYY_W" (week)
                if (id.includes('-')) {
                    // Day format: "YYYY-MM-DD"
                    const date = new Date(id);
                    timestamp = date.getTime();
                } else if (id.includes('_')) {
                    // Month or week format: "YYYY_M" or "YYYY_W"
                    const parts = id.split('_');
                    if (parts.length === 2) {
                        const year = parseInt(parts[0], 10);
                        const period = parseInt(parts[1], 10);
                        
                        if (period <= 12) {
                            // Month format: "YYYY_M" (e.g., "2025_7" = July 2025)
                            const date = new Date(Date.UTC(year, period - 1, 1));
                            timestamp = date.getTime();
                        } else if (period <= 53) {
                            // Week format: "YYYY_W" (e.g., "2024_12" = week 12 of 2024)
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
                    timestamp = date.getTime();
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
        'security-posture-chart': <CustomLineChart
            title={`${func.toSentenceCase(window.ACCOUNT_NAME)} ${mapLabel('API Security Posture', dashboardCategory)} over time`}
            chartData={overallStats}
            labels={[
                { label: mapLabel('API Endpoints Discovered', dashboardCategory), color: '#B692F6' },
                { label: `${mapLabel('API', dashboardCategory)} Issues`, color: '#D72C0D' },
                { label: `${mapLabel('Threat', dashboardCategory)} Requests flagged`, color: '#F3B283' }
            ]}
            itemId='security-posture-chart'
            onRemoveComponent={removeComponent}
        />,
        'api-discovery-pie': <CustomPieChart
            title={mapLabel('API Discovery', dashboardCategory)}
            subtitle={`Total ${mapLabel('APIs', dashboardCategory)}`}
            graphData={apiDiscoveryData}
            itemId='api-discovery-pie'
            onRemoveComponent={removeComponent}
        />,
        'issues-pie': <CustomPieChart
            title="Issues"
            subtitle="Total Issues"
            graphData={issuesData}
            itemId='issues-pie'
            onRemoveComponent={removeComponent}
        />,
        'threat-detection-pie': <CustomPieChart
            title={mapLabel('Threat Detection', dashboardCategory)}
            subtitle="Requests Flagged"
            graphData={threatData}
            itemId='threat-detection-pie'
            onRemoveComponent={removeComponent}
        />,
        'average-issue-age': <AverageIssueAgeCard
            issueAgeData={averageIssueAgeData}
            itemId='average-issue-age'
            onRemoveComponent={removeComponent}
        />,
        'compliance-at-risks': <ComplianceAtRisksCard
            complianceData={complianceData}
            itemId='compliance-at-risks'
            onRemoveComponent={removeComponent}
        />,
        'tested-vs-non-tested': <CustomLineChart
            title={`Tested vs Non-Tested ${mapLabel('APIs', dashboardCategory)}`}
            chartData={testedVsNonTestedChartData}
            labels={[
                { label: 'Non-Tested', color: '#D72C0D' },
                { label: 'Tested', color: '#9E77ED' }
            ]}
            itemId='tested-vs-non-tested'
            onRemoveComponent={removeComponent}
        />,
        'open-resolved-issues': <CustomLineChart
            title="Open & Resolved Issues"
            chartData={openResolvedChartData}
            labels={[
                { label: 'Open Issues', color: '#D72C0D' },
                { label: 'Resolved Issues', color: '#9E77ED' }
            ]}
            itemId='open-resolved-issues'
            onRemoveComponent={removeComponent}
        />,
        'threat-requests-chart': (threatRequestsChartData && Array.isArray(threatRequestsChartData) && threatRequestsChartData.length > 0) ? (
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
        ) : (
            <Card>
                <VerticalStack gap="4">
                    <ComponentHeader 
                        title={`${mapLabel('Threat', dashboardCategory)} Requests over time`} 
                        itemId='threat-requests-chart' 
                        onRemove={removeComponent} 
                    />
                    <Box width='100%' minHeight='290px' display='flex' alignItems='center' justifyContent='center'>
                        <Text alignment='center' color='subdued'>No threat data available</Text>
                    </Box>
                </VerticalStack>
            </Card>
        ),
        'open-resolved-threats': (openResolvedThreatsData && Array.isArray(openResolvedThreatsData) && openResolvedThreatsData.length > 0) ? (
            <CustomLineChart
                title={`Open & Resolved ${mapLabel('Threat', dashboardCategory)}s`}
                chartData={openResolvedThreatsData}
                labels={[
                    { label: 'Open Issues', color: '#D72C0D' },
                    { label: 'Resolved Issues', color: '#9E77ED' }
                ]}
                itemId='open-resolved-threats'
                onRemoveComponent={removeComponent}
            />
        ) : (
            <Card>
                <VerticalStack gap="4">
                    <ComponentHeader 
                        title={`Open & Resolved ${mapLabel('Threat', dashboardCategory)}s`} 
                        itemId='open-resolved-threats' 
                        onRemove={removeComponent} 
                    />
                    <Box width='100%' minHeight='290px' display='flex' alignItems='center' justifyContent='center'>
                        <Text alignment='center' color='subdued'>No threat data available</Text>
                    </Box>
                </VerticalStack>
            </Card>
        ),
        'weakest-areas': <CustomDataTable
            title="Top Issues by Category"
            data={topIssuesByCategory}
            showSignalIcon={true}
            itemId='weakest-areas'
            onRemoveComponent={removeComponent}
        />,
        'top-apis-issues': <CustomDataTable
            title={`Top ${mapLabel('APIs', dashboardCategory)} with Critical & High Issues`}
            data={topHostnamesByIssues}
            showSignalIcon={true}
            itemId='top-apis-issues'
            onRemoveComponent={removeComponent}
        />,
        'top-requests-by-type': <CustomDataTable
            title="Top Threats by Category"
            data={topThreatsByCategory}
            showSignalIcon={true}
            itemId='top-requests-by-type'
            onRemoveComponent={removeComponent}
        />,
        'top-attacked-apis': <CustomDataTable
            title={`Top Attacked ${mapLabel('APIs', dashboardCategory)}`}
            data={topAttackHosts}
            showSignalIcon={false}
            itemId='top-attacked-apis'
            onRemoveComponent={removeComponent}
        />,
        'top-bad-actors': <CustomDataTable
            title="Top Bad Actors"
            data={topBadActors}
            showSignalIcon={false}
            itemId='top-bad-actors'
            onRemoveComponent={removeComponent}
        />
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
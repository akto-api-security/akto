import React, { useEffect, useReducer, useState, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import api from './api';
import threatApi from "../threat_detection/api"
import func from '@/util/func';
import observeFunc from "../observe/transform"
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards"
import { Box, DataTable, HorizontalGrid, HorizontalStack, Icon, Link, Scrollable, Text, VerticalStack, LegacyTabs, Badge, Banner, ProgressBar, Spinner } from '@shopify/polaris';
import observeApi from "../observe/api"
import testingTransform from "../testing/transform"
import StackedChart from '../../components/charts/StackedChart';
import ChartypeComponent from '../testing/TestRunsPage/ChartypeComponent';
import testingApi from "../testing/api"
import PersistStore from '../../../main/PersistStore';
import { DashboardBanner } from './components/DashboardBanner';
import SummaryCard from './new_components/SummaryCard';
import { ArrowUpMinor, ArrowDownMinor, AlertMinor } from '@shopify/polaris-icons';
import TestSummaryCardsList from './new_components/TestSummaryCardsList';
import InfoCard from './new_components/InfoCard';
import ProgressBarChart from './new_components/ProgressBarChart';
import SpinnerCentered from '../../components/progress/SpinnerCentered';
import SmoothAreaChart from './new_components/SmoothChart'
import DateRangeFilter from '../../components/layouts/DateRangeFilter';
import { produce } from 'immer';
import EmptyCard from './new_components/EmptyCard';
import TooltipText from '../../components/shared/TooltipText';
import transform from '../observe/transform';
import CriticalUnsecuredAPIsOverTimeGraph from '../issues/IssuesPage/CriticalUnsecuredAPIsOverTimeGraph';
import CriticalFindingsGraph from '../issues/IssuesPage/CriticalFindingsGraph';
import values from "@/util/values";
import { ActionItemsContent } from './components/ActionItemsContent';
import { buildActionItemsData } from './components/actionItemsTransform';
import settingsModule from '../settings/module';
import { getDashboardCategory, isMCPSecurityCategory, isApiSecurityCategory, mapLabel } from '../../../main/labelHelper';
import GraphMetric from '../../components/GraphMetric';
import Dropdown from '../../components/layouts/Dropdown';
import { usePermissions } from '@/util/permissions';

// The api numbers of a first fill come a run at a time, and a run starts when the dashboard asks: look again
// this often, this many times (the backend is due another run every 30s while collections are uncounted)
const PENDING_POLL_MS = 3000
const MAX_PENDING_POLLS = 10

function HomeDashboard() {

    const { canCall } = usePermissions();
    // data the role can't load isn't requested; its widget falls back to empty or is left out
    const callIfAllowed = (action, call) => canCall(action) ? call() : Promise.reject(new Error(`${action} not allowed`));
    // widgets that load their own data, by the action they load it with
    const WIDGET_REQUIRES = {
        'critical-apis': 'api/fetchCriticalIssuesTrend',
        'vulnerable-apis': 'api/fetchSeverityInfoForIssues',
        'critical-findings': 'api/getIssueSummaryInfo',
        'threat-timeline': 'api/getDailyThreatActorsCount',
        'threat-severity': 'api/fetchCountBySeverity',
        'threat-categories': 'api/fetchThreatCategoryCount',
        'risk-score': 'api/fetchPostureSnapshot',
        'access-type': 'api/fetchPostureSnapshot',
        'auth-type': 'api/fetchPostureSnapshot',
        'api-type': 'api/fetchPostureSnapshot',
        'mcp-open-alerts': 'api/fetchMcpPostureData',
        'mcp-types-table': 'api/fetchMcpPostureCounts',
    };
    const canLoadApiCallStats = canCall('api/fetchTaggedApiCallSeries');

    const [loading, setLoading] = useState(true);
    const [showBannerComponent, setShowBannerComponent] = useState(false)
    const [testSummaryInfo, setTestSummaryInfo] = useState([])
    const [selectedTab, setSelectedTab] = useState(0);
    const [actionItemsCount, setActionItemsCount] = useState(0);
    const [actionItemsData, setActionItemsData] = useState(null);
    // what the two snapshot calls returned: the action items and the critical findings chart are made from them
    const [snapshots, setSnapshots] = useState(null);
    // what the api numbers say about themselves: still being counted, or approximate for a range that ended in the past
    const [postureNotice, setPostureNotice] = useState({ pending: false, endApproximate: false, counted: 0, collections: 0 });
    const [pendingPolls, setPendingPolls] = useState(0);
    // the endpoints count is a slow call of its own that nothing else waits for: null until it has answered, {} if it failed
    const [endpointsCount, setEndpointsCount] = useState(null);
    const endpointsCountRequest = useRef(0);
    const [jiraTicketUrlMap, setJiraTicketUrlMap] = useState({});

    const handleTabChange = useCallback(
        (selectedTabIndex) => setSelectedTab(selectedTabIndex),
        [],
    );

    const tabs = [
        {
            id: 'home',
            content: 'Home',
            panelID: 'home-content',
        },
        {
            id: 'analytics',
            content: (
                <HorizontalStack gap={"1"}>
                    <Text>Analysis</Text>
                    {actionItemsCount > 0 && (
                        <Badge status="new">{actionItemsCount > 10 ? '10+' : actionItemsCount}</Badge>
                    )}
                </HorizontalStack>
            ),
            panelID: 'analytics-content',
        },
    ];

    const ALL_COLLECTION_INVENTORY_ID = 111111121
    const EXPLORE_MODE_QUERY_PATH = '/dashboard/observe/query_mode'

    const [authMap, setAuthMap] = useState({})
    const [apiTypesData, setApiTypesData] = useState([{ "data": [], "color": "#D6BBFB" }])
    const [riskScoreData, setRiskScoreData] = useState([])
    const [newDomains, setNewDomains] = useState([])
    const [severityMap, setSeverityMap] = useState({})
    const [severityMapEmpty, setSeverityMapEmpty] = useState(true)
    const [totalIssuesCount, setTotalIssuesCount] = useState(0)
    const [oldIssueCount, setOldIssueCount] = useState(0)
    const [apiRiskScore, setApiRiskScore] = useState(0)
    const [testCoverage, setTestCoverage] = useState(0)
    const [totalAPIs, setTotalAPIs] = useState(0)
    const [oldTotalApis, setOldTotalApis] = useState(0)
    const [oldTestCoverage, setOldTestCoverage] = useState(0)
    const [oldRiskScore, setOldRiskScore] = useState(0)
    const [showTestingComponents, setShowTestingComponents] = useState(false)
    const [customRiskScoreAvg, setCustomRiskScoreAvg] = useState(0)
    const [mcpTotals, setMcpTotals] = useState({ mcpTotalApis: null, thirdPartyApis: null, newApis7Days: null, openAlerts: null, criticalApis: null, policyGuardrailApis: null })
    const [mcpOpenAlertDetails, setMcpOpenAlertDetails] = useState([])
    const [mcpApiCallStats, setMcpApiCallStats] = useState([])
    const [policyGuardrailStats, setPolicyGuardrailStats] = useState([])
    const [mcpTopApplications, setMcpTopApplications] = useState([])
    const [threatActorsTimeline, setThreatActorsTimeline] = useState([])
    const [threatSeverityData, setThreatSeverityData] = useState({})
    const [threatCategoryData, setThreatCategoryData] = useState([{ data: [], color: observeFunc.getColorForSensitiveData('CRITICAL') }])

    // MCP API Requests time selector state
    const statsOptions = [
        {label: "15 minutes", value: 15*60},
        {label: "30 minutes", value: 30*60},
        {label: "1 hour", value: 60*60},
        {label: "3 hours", value: 3*60*60},
        {label: "6 hours", value: 6*60*60},
        {label: "12 hours", value: 12*60*60},
        {label: "1 day", value: 24*60*60},
        {label: "7 days", value: 7*24*60*60},
        {label: "1 month", value: 30*24*60*60},
        {label: "2 months", value: 60*24*60*60},
        {label: "1 year", value: 365*24*60*60},
        {label: "All time", value: 10*365*24*60*60} // 10 years as a proxy for all time
    ]
    const [mcpStatsTimeRange, setMcpStatsTimeRange] = useState(func.timeNow() - statsOptions[8].value)
    const [policyGuardrailStatsTimeRange, setPolicyGuardrailStatsTimeRange] = useState(func.timeNow() - statsOptions[8].value)

    // Function to handle navigation to audit page
    const navigate = useNavigate();
    const handleMcpAuditNavigation = useCallback(() => {
        navigate('/dashboard/observe/audit');
    }, [navigate])

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[4]);

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    const [accessTypeMap, setAccessTypeMap] = useState({
        "Partner": {
            "text": 0,
            "color": "#147CF6",
            "filterKey": "Partner",
            "filterValue": "Partner"
        },
        "Internal": {
            "text": 0,
            "color": "#FDB33D",
            "filterKey": "Internal",
            "filterValue": "Private"
        },
        "External": {
            "text": 0,
            "color": "#658EE2",
            "filterKey": "External",
            "filterValue": "Public"
        },
        "Third Party": {
            "text": 0,
            "color": "#68B3D0",
            "filterKey": "Third Party",
            "filterValue": "Third Party"
        },
        "Need more data": {
            "text": 0,
            "color": "#FFB3B3",
            "filterKey": "Need more data"
        }
    });


    const initialHistoricalData = []
    const finalHistoricalData = []

    const defaultChartOptions = {
        "legend": {
            enabled: false
        },
    }

    const convertSeverityArrToMap = (arr) => {
        return Object.fromEntries(arr.map(item => [item.split(' ')[1].toLowerCase(), +item.split(' ')[0]]));
    }

    function transformThreatActorsTimeline(actorsCounts) {
        if (!actorsCounts || !Array.isArray(actorsCounts)) {
            return [];
        }
        return actorsCounts
            .sort((a, b) => a.ts - b.ts)
            .map(item => [
                item.ts * 1000,
                item.totalActors || 0
            ]);
    }

    function transformSeverityData(categoryCounts) {
        const severityLevels = func.getAktoSeverities();
        const severityMap = {};

        severityLevels.forEach(severity => {
            severityMap[severity] = {
                text: 0,
                color: func.getHexColorForSeverity(severity),
                filterKey: severity
            };
        });

        if (categoryCounts && Array.isArray(categoryCounts)) {
            categoryCounts.forEach(item => {
                const severity = String(item.subCategory || item.severity || '').toUpperCase();
                if (severityMap[severity]) {
                    severityMap[severity].text = item.count || 0;
                }
            });
        }

        return severityMap;
    }

    function transformThreatCategoryData(threatCategoryCount) {
        if (!threatCategoryCount || !Array.isArray(threatCategoryCount)) {
            return [{ data: [], color: observeFunc.getColorForSensitiveData('CRITICAL') }];
        }

        const categoryMap = {};
        threatCategoryCount.forEach(item => {
            const category = item.category || item.subCategory || "Unknown";
            const displayName = category
                .replace(/_/g, ' ')
                .toLowerCase()
                .replace(/\b\w/g, l => l.toUpperCase());

            if (!categoryMap[displayName]) {
                categoryMap[displayName] = 0;
            }
            categoryMap[displayName] += item.count || 0;
        });

        const sortedCategories = Object.entries(categoryMap)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 10);

        const data = sortedCategories.map(([name, count]) => [name, count]);

        return [{ data, color: observeFunc.getColorForSensitiveData('CRITICAL'), name: "Threats" }];
    }

    const testSummaryData = async () => {
        if (!canCall('api/retrieveAllCollectionTests')) return
        const endTimestamp = func.timeNow()
        await testingApi.fetchTestingDetails(
            0, endTimestamp, "endTimestamp", "-1", 0, 10, null, null, ""
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
            const result = testingTransform.prepareTestRuns(testingRuns, latestTestingRunResultSummaries);
            const finalResult = []
            result.forEach((x) => {
                const severity = x["severity"]
                const severityMap = convertSeverityArrToMap(severity)
                finalResult.push({
                    "testName": x["name"],
                    "time": func.prettifyEpoch(x["run_time_epoch"]),
                    "criticalCount": severityMap["critical"] ? severityMap["critical"] : "0",
                    "highCount": severityMap["high"] ? severityMap["high"] : "0",
                    "mediumCount": severityMap["medium"] ? severityMap["medium"] : "0",
                    "lowCount": severityMap["low"] ? severityMap["low"] : "0",
                    "totalApis": x["total_apis"],
                    "link": x["nextUrl"]
                })
            })

            setTestSummaryInfo(finalResult)
            setShowTestingComponents(finalResult && finalResult.length > 0)
        });
    }

    // Api calls of the collections with a tag, summed per time bucket by the backend, as a chart series
    const fetchTaggedApiCallStats = async (tag, startTs, endTs, setSeries) => {
        if (!canLoadApiCallStats) return;
        if (!func.checkForFeatureSaas('THREAT_DETECTION')) {
            setSeries(generateTimeSeriesWithGaps(startTs, endTs, {}));
            return;
        }
        try {
            const resp = await api.fetchTaggedApiCallSeries(tag, startTs, endTs);
            const aggregatedStats = {};
            (resp?.points || []).forEach(point => {
                aggregatedStats[point.ts * 60 * 1000] = point.count; // minutes to milliseconds
            });
            setSeries(generateTimeSeriesWithGaps(startTs, endTs, aggregatedStats));
        } catch (error) {
            setSeries([]);
        }
    };

    const fetchPolicyGuardrailStats = (startTs, endTs) => fetchTaggedApiCallStats('GUARDRAIL', startTs, endTs, setPolicyGuardrailStats);
    const fetchMcpApiCallStats = (startTs, endTs) => fetchTaggedApiCallStats('MCP', startTs, endTs, setMcpApiCallStats);

    // Helper function to generate time series data with gaps filled as zeros
    const generateTimeSeriesWithGaps = (startTs, endTs, dataMap) => {
        const startMs = startTs * 1000;
        const endMs = endTs * 1000;
        const timeDiff = endMs - startMs;
        
        // Determine appropriate interval based on time range
        let intervalMs;
        if (timeDiff <= 60 * 60 * 1000) { // <= 1 hour: 1-minute intervals
            intervalMs = 60 * 1000;
        } else if (timeDiff <= 24 * 60 * 60 * 1000) { // <= 1 day: 5-minute intervals
            intervalMs = 5 * 60 * 1000;
        } else if (timeDiff <= 7 * 24 * 60 * 60 * 1000) { // <= 7 days: 1-hour intervals
            intervalMs = 60 * 60 * 1000;
        } else if (timeDiff <= 30 * 24 * 60 * 60 * 1000) { // <= 30 days: 6-hour intervals
            intervalMs = 6 * 60 * 60 * 1000;
        } else if (timeDiff <= 90 * 24 * 60 * 60 * 1000) { // <= 90 days: 1-day intervals
            intervalMs = 24 * 60 * 60 * 1000;
        } else { // > 90 days: 1-week intervals
            intervalMs = 7 * 24 * 60 * 60 * 1000;
        }
        
        // One point per interval from the start of the range, then each data point added to the interval it falls in
        // (the backend counts its buckets from the start of the range too, with these same sizes)
        const completeData = [];
        for (let ts = startMs; ts <= endMs; ts += intervalMs) {
            completeData.push([ts, 0]);
        }
        Object.entries(dataMap).forEach(([ts, count]) => {
            const timestamp = parseInt(ts);
            if (timestamp > endMs || completeData.length === 0) return;
            // a bucket named by a minute just before the start of the range still belongs to the first interval
            const intervalIndex = Math.min(Math.max(Math.floor((timestamp - startMs) / intervalMs), 0), completeData.length - 1);
            completeData[intervalIndex][1] += count;
        });

        return completeData.sort((a, b) => a[0] - b[0]);
    };

    const safely = (what, build) => {
        try {
            build()
        } catch (error) {
            console.error('home dashboard: ' + what + ' failed', error)
        }
    }

    // the charts, cards and tiles made from the api numbers snapshot; a snapshot that never came leaves them as they are
    const applyPosture = (posture) => {
        // the missing apis figure is already the net of apis missing from and redundant in api_info
        const missing = posture.missing || {}
        const netMissing = missing.netMissing || 0
        const stats = posture.apiStatsEnd ? posture : null
        safely('access types', () => mapAccessTypes(stats, netMissing, 0, missing.accessTypeNotCalculated || 0))
        safely('auth types', () => mapAuthTypes(stats, netMissing, 0, missing.authNotCalculated || 0))
        if (posture.apiStatsEnd) {
            safely('api types', () => buildAPITypesData(posture.apiStatsEnd, netMissing, 0, missing.apiTypeMissing || 0))
            safely('risk score', () => buildSetRiskScoreData(posture.apiStatsEnd, Math.max(0, netMissing)))
        }
        setNewDomains((posture.newDomains || []).map(domain => ({ name: domain.name, apisTested: domain.apisTested, totalApis: domain.totalApis })))
        setPostureNotice({
            pending: !!posture.statsPending, endApproximate: !!posture.endApproximate,
            counted: posture.statsProgress?.countedCollections || 0, collections: posture.statsProgress?.collections || 0
        })
    }

    // Not awaited by the page: the count is slow, and only what is made from it waits. An answer to an earlier range is dropped.
    const fetchEndpointsCountInBackground = () => {
        const request = ++endpointsCountRequest.current
        setEndpointsCount(null)
        callIfAllowed('api/fetchEndpointsCount', () => api.fetchEndpointsCount(startTimestamp, endTimestamp))
            .then(response => response || {}, () => ({}))
            .then(response => {
                if (request === endpointsCountRequest.current) setEndpointsCount(response)
            })
    }

    const fetchData = async () => {
        setLoading(true)
        // the api numbers, the issue numbers and the MCP counts are one call each; the lists and charts load after
        let apiPromises = [
            callIfAllowed('api/getCustomerEndpoints', () => observeApi.getUserEndpoints()),
            callIfAllowed('api/fetchIssuesSnapshot', () => api.fetchIssuesSnapshot(startTimestamp, endTimestamp)),
            callIfAllowed('api/fetchPostureSnapshot', () => api.fetchPostureSnapshot(startTimestamp, endTimestamp)),
        ];
        fetchEndpointsCountInBackground()

        // Only fetch MCP data if not in API Security category
        const loadMcp = !isApiSecurityCategory() && canCall('api/fetchMcpPostureCounts')
        if (loadMcp) {
            apiPromises.push(
                api.fetchMcpPostureCounts(),
                canCall('api/fetchMcpPostureData') ? api.fetchMcpPostureData('RECENT_ALERTS', 0, 2) : Promise.reject(new Error('not allowed')),
                canCall('api/fetchMcpPostureData') ? api.fetchMcpPostureData('TOP_APPLICATIONS', 0, 3) : Promise.reject(new Error('not allowed'))
            );
        }

        let results = await Promise.allSettled(apiPromises);
        const valueOf = (index, fallback) => results[index]?.status === 'fulfilled' ? results[index].value : fallback

        let userEndpoints = valueOf(0, true)
        let issuesSnapshot = valueOf(1, {})
        let posture = valueOf(2, {})

        setShowBannerComponent(!userEndpoints)
        setPendingPolls(0)

        // one widget failing to build must not leave the others, and the page, waiting
        try {
            applyPosture(posture)
            safely('recent tests', () => testSummaryData())
            setSnapshots({ posture, issues: issuesSnapshot })
            safely('severity map', () => buildSeverityMap(issuesSnapshot.severityCounts))
            safely('issues summary', () => buildIssuesSummary(issuesSnapshot))

            const fetchHistoricalDataResp = { "finalHistoricalData": finalHistoricalData, "initialHistoricalData": initialHistoricalData }
            buildDeltaInfo(fetchHistoricalDataResp)

            if (loadMcp) {
                const mcpCounts = valueOf(3, {})
                setMcpTotals({ mcpTotalApis: mcpCounts.totalApis ?? null, thirdPartyApis: mcpCounts.thirdPartyApis ?? null, newApis7Days: mcpCounts.newApis7Days ?? null,
                    criticalApis: mcpCounts.criticalApis ?? null, tools: mcpCounts.tools ?? null, prompts: mcpCounts.prompts ?? null,
                    resources: mcpCounts.resources ?? null, server: mcpCounts.servers ?? null, policyGuardrailApis: mcpCounts.policyGuardrailApis ?? null })
                setMcpOpenAlertDetails(valueOf(4, {})?.alerts || [])
                setMcpTopApplications(valueOf(5, {})?.topApplications || [])
            }
        } catch (error) {
            console.error('home dashboard: building the page failed', error)
        } finally {
            setLoading(false)
        }
    }

    const fetchThreatData = async () => {
        try {
            const threatPromises = [
                callIfAllowed('api/getDailyThreatActorsCount', () => threatApi.getDailyThreatActorsCount(startTimestamp, endTimestamp, [])),
                callIfAllowed('api/fetchCountBySeverity', () => threatApi.fetchCountBySeverity(startTimestamp, endTimestamp)),
                callIfAllowed('api/fetchThreatCategoryCount', () => threatApi.fetchThreatCategoryCount(startTimestamp, endTimestamp))
            ];

            const results = await Promise.allSettled(threatPromises);

            // Process timeline data
            if (results[0].status === 'fulfilled' && results[0].value?.actorsCounts) {
                const timelineData = transformThreatActorsTimeline(results[0].value.actorsCounts);
                setThreatActorsTimeline(timelineData);
            } else {
                setThreatActorsTimeline([]);
            }

            // Process severity data
            if (results[1].status === 'fulfilled' && results[1].value?.categoryCounts) {
                const severityData = transformSeverityData(results[1].value.categoryCounts);
                setThreatSeverityData(severityData);
            } else {
                setThreatSeverityData({});
            }

            // Process category data
            if (results[2].status === 'fulfilled' && results[2].value?.categoryCounts) {
                const categoryData = transformThreatCategoryData(results[2].value.categoryCounts);
                setThreatCategoryData(categoryData);
            } else {
                setThreatCategoryData([{ data: [], color: observeFunc.getColorForSensitiveData('CRITICAL') }]);
            }
        } catch (error) {
            setThreatActorsTimeline([]);
            setThreatSeverityData({});
            setThreatCategoryData([{ data: [], color: observeFunc.getColorForSensitiveData('CRITICAL') }]);
        }
    };

    useEffect(() => {
        fetchData()
        if (func.checkForFeatureSaas('THREAT_DETECTION') && func.hasThreatAccess()) {
            fetchThreatData()
        }
    }, [startTimestamp, endTimestamp])
    
    // the total apis tile, and the risk score and test coverage, which divide by the count when there is one
    // (for now we are actually using the apis from stis instead of the total apis in the response)
    // TODO: Fix apiStats API to return the correct total apis
    useEffect(() => {
        if (endpointsCount !== null) safely('total apis', () => buildEndpointsCount(endpointsCount))
        safely('metrics', () => buildMetrics(snapshots?.posture?.apiStatsEnd, endpointsCount))
    }, [snapshots, endpointsCount])

    // collections not counted yet make the numbers grow as they are: look again, with the banner showing how far it has got
    useEffect(() => {
        if (!postureNotice.pending || pendingPolls >= MAX_PENDING_POLLS) return
        const timer = setTimeout(async () => {
            try {
                const posture = await api.fetchPostureSnapshot(startTimestamp, endTimestamp)
                applyPosture(posture)
                setSnapshots(previous => ({ ...(previous || {}), posture }))
            } catch (error) {
                // the next look will tell
            }
            setPendingPolls(count => count + 1)
        }, PENDING_POLL_MS)
        return () => clearTimeout(timer)
    }, [postureNotice.pending, pendingPolls, startTimestamp, endTimestamp])

    // Fetch MCP API call stats when time range changes
    useEffect(() => {
        fetchMcpApiCallStats(mcpStatsTimeRange, func.timeNow());
    }, [mcpStatsTimeRange])

    // Fetch Policy Guardrail stats when time range changes
    useEffect(() => {
        fetchPolicyGuardrailStats(policyGuardrailStatsTimeRange, func.timeNow());
    }, [policyGuardrailStatsTimeRange])

    useEffect(() => {
        settingsModule.fetchAccountConfig().then(config => setJiraTicketUrlMap(config?.jiraTicketUrlMap || {})).catch(() => {})
    }, [])

    useEffect(() => {
        if (!snapshots) return
        const data = buildActionItemsData(snapshots.posture, snapshots.issues, jiraTicketUrlMap);
        setActionItemsData(data);
        const excludeKeys = new Set([
            'numBatches',
            'highRiskNumBatches',
            'shadowNumBatches',
            'notTestedNumBatches'
        ]);
        let count = 0;
        Object.entries(data).forEach(([key, val]) => {
            if (excludeKeys.has(key)) return;
            if (typeof val === 'number' && val > 0) count++;
        });
        setActionItemsCount(count);
    }, [snapshots, jiraTicketUrlMap]);

    function buildIssuesSummary(issuesSnapshot) {
        setTotalIssuesCount(issuesSnapshot?.totalIssuesCount || 0)
        setOldIssueCount(issuesSnapshot?.oldOpenCount || 0)
    }

    function buildEndpointsCount(fetchEndpointsCountResp) {
        let newCount = fetchEndpointsCountResp.newCount
        let oldCount = fetchEndpointsCountResp.oldCount

        if (newCount) {
            setTotalAPIs(newCount)
        } else {
            setTotalAPIs(0)
        }

        if (oldCount) {
            setOldTotalApis(oldCount)
        } else {
            setOldTotalApis(0)
        }
    }

    function buildMetrics(apiStats, fetchEndpointsCount) {
        if (!apiStats) return;

        const totalRiskScore = apiStats.totalRiskScore

        // For now we are taking the new count from the fetchEndpointsCount API
        let totalAPIs = fetchEndpointsCount?.newCount
        if(totalAPIs === undefined || totalAPIs === null){
            totalAPIs = apiStats.totalAPIs
        }
        const apisTestedInLookBackPeriod = apiStats.apisTestedInLookBackPeriod
        const apisInScopeForTesting = apiStats?.totalInScopeForTestingApis || apisTestedInLookBackPeriod

        if (totalAPIs && totalAPIs > 0 && totalRiskScore) {
            const tempRiskScore = totalRiskScore / totalAPIs
            setApiRiskScore(parseFloat(tempRiskScore.toFixed(2)))
        } else {
            setApiRiskScore(0)
        }

        if (totalAPIs && totalAPIs> 0 && apisTestedInLookBackPeriod) {
            const testCoverage = Math.min(100 * apisTestedInLookBackPeriod / totalAPIs, 100);
            setTestCoverage(parseFloat(testCoverage.toFixed(2)))
        } else {
            setTestCoverage(0)
        }
    }

    function buildDeltaInfo(deltaInfo) {
        const initialHistoricalData = deltaInfo.initialHistoricalData

        let totalApis = 0
        let totalRiskScore = 0
        let totalTestedApis = 0
        initialHistoricalData.forEach((x) => {
            totalApis += x.totalApis
            totalRiskScore += x.riskScore
            totalTestedApis += x.apisTested
        })

        const tempRiskScore = totalApis ? (totalRiskScore / totalApis).toFixed(2) : 0
        setOldRiskScore(parseFloat(tempRiskScore))
        const tempTestCoverate = totalApis ? (100 * totalTestedApis / totalApis).toFixed(2) : 0
        setOldTestCoverage(parseFloat(tempTestCoverate))
    }

    function generateChangeComponent(val, invertColor) {
        if (!val) return null
        const source = val > 0 ? ArrowUpMinor : ArrowDownMinor
        if (val === 0) return null
        const color = !invertColor && val > 0 ? "success" : "critical"
        return (
            <HorizontalStack wrap={false}>
                <Icon source={source} color={color} />
                <div className='custom-color'>
                    <Text color={color}>{Math.abs(val)}</Text>
                </div>
            </HorizontalStack>
        )
    }

    const runTestEmptyCardComponent = <Text alignment='center' color='subdued'>There's no data to show. <Link url="/dashboard/testing" target='_blank'>{mapLabel('Run test', getDashboardCategory())}</Link> to get data populated. </Text>

    function mapAccessTypes(apiStats, missingCount, redundantCount, apiTypeMissing) {
        if (!apiStats) return
        const apiStatsEnd = apiStats.apiStatsEnd
        const apiStatsStart = apiStats.apiStatsStart

        const countMissing = apiTypeMissing  + missingCount - redundantCount

        const accessTypeMapping = {
            "PUBLIC": "External",
            "PRIVATE": "Internal",
            "PARTNER": "Partner",
            "THIRD_PARTY": "Third Party",
            "NEED_MORE_DATA": "Need more data"
        };

        for (const [key, value] of Object.entries(apiStatsEnd.accessTypeMap)) {
            const mappedKey = accessTypeMapping[key];
            if (mappedKey && accessTypeMap[mappedKey]) {
                accessTypeMap[mappedKey].text = value;
                accessTypeMap[mappedKey].dataTableComponent = generateChangeComponent((value - apiStatsStart.accessTypeMap[key]), false);
            }
        }
        // Handle missing access types
        if(countMissing > 0) {
            accessTypeMap["Need more data"].text = countMissing;
            accessTypeMap["Need more data"].dataTableComponent = generateChangeComponent(0, false);
        }

        setAccessTypeMap(accessTypeMap)
    }


    function mapAuthTypes(apiStats, missingCount, redundantCount, authTypeMissing) {
        if (!apiStats) return
        const apiStatsEnd = apiStats.apiStatsEnd
        const apiStatsStart = apiStats.apiStatsStart
        const convertKey = (key) => {
            return key
                .toLowerCase()
                .replace(/(^|\s)\S/g, (letter) => letter.toUpperCase()) // Capitalize the first character after any space
                .replace(/_/g, ' '); // Replace underscores with spaces
        };

        // Initialize colors list
        const colors = ["#7F56D9", "#8C66E1", "#9E77ED", "#AB88F1", "#B692F6", "#D6BBFB", "#E9D7FE", "#F4EBFF"];

        const formatFilterValue = (key) => {
            if (key === undefined || key === null) return undefined
            return String(key).toLowerCase()
        }

        // Convert and sort the authTypeMap entries by value (count) in descending order
        const sortedAuthTypes = Object.entries(apiStatsEnd.authTypeMap)
            .map(([key, value]) => ({ key: key, text: value }))
            .filter(item => item.text > 0) // Filter out entries with a count of 0
            .sort((a, b) => b.text - a.text); // Sort by count descending

        // Initialize the output authMap
        const authMap = {};


        // Fill in the authMap with sorted entries and corresponding colors
        sortedAuthTypes.forEach((item, index) => {
            const displayKey = convertKey(item.key)
            authMap[displayKey] = {
                "text": item.text,
                "color": colors[index] || "#F4EBFF", // Assign color; default to last color if out of range
                "filterKey": displayKey,
                "filterValue": formatFilterValue(item.key),
                "dataTableComponent": apiStatsStart && apiStatsStart.authTypeMap && apiStatsStart.authTypeMap[item.key] ? generateChangeComponent((item.text - apiStatsStart.authTypeMap[item.key]), item.key === "UNAUTHENTICATED") : null
            };
        });

        const countMissing = authTypeMissing + missingCount - redundantCount

        if(countMissing > 0) {
            authMap["Need more data"] = {
                "text": countMissing,
                "color": "#EFE3FF",
                "filterKey": "Need more data",
                "filterValue": undefined,
                "dataTableComponent": generateChangeComponent(0, false) // No change component for missing auth types
            };
        }


        setAuthMap(authMap)
    }


    const buildAuthFiltersUrl = useCallback((baseUrl, filterValue, baseFilter) => {
        if(!func.checkForFeatureSaas("AKTO_API_GROUP_CRONS")){
            return undefined;
        }
        if (!baseUrl) return undefined
        if (!filterValue) {
            return baseUrl
        }
        const separator = baseUrl.includes('?') ? '&' : '?'
        return `${baseUrl}${separator}filters=${baseFilter}__${encodeURIComponent(filterValue)}`
    }, [])

    const buildExploreModeAuthFiltersUrl = useCallback((baseUrl, filterValue, baseFilter) => {
        if (!baseUrl) return undefined
        if (!filterValue) {
            return baseUrl
        }
        const separator = baseUrl.includes('?') ? '&' : '?'
        return `${baseUrl}${separator}filters=${baseFilter}__${encodeURIComponent(filterValue)}`
    }, [])

    function buildAPITypesData(apiStats, missingCount, redundantCount, apiTypeMissing) {
        // Initialize the data with default values for all API types
        const data = [
            ["REST", apiStats.apiTypeMap.REST || 0], // Use the value from apiTypeMap or 0 if not available
            ["GraphQL", apiStats.apiTypeMap.GRAPHQL || 0],
            ["gRPC", apiStats.apiTypeMap.GRPC || 0],
            ["SOAP", apiStats.apiTypeMap.SOAP || 0],
        ];
        const countMissing = apiTypeMissing;
        if(missingCount > 0) {
            data.push(["Need more data", countMissing]);
        }

        setApiTypesData([{ data: data, color: "#D6BBFB" }])
    }

    function buildSetRiskScoreData(apiStats, missingCount) {
        const totalApisCount = apiStats.totalAPIs + (missingCount || 0);

        let tempScore = 0, tempTotal = 0
        Object.keys(apiStats.riskScoreMap).forEach((x) => {
            if(x > 1){
                const apisVal = apiStats.riskScoreMap[x]
                tempScore += (x * apisVal)
                tempTotal += apisVal
            }
        })
        if(tempScore > 0 && tempTotal > 0){
            let val = (tempScore * 1.0)/tempTotal
            if(val >= 2){
                setCustomRiskScoreAvg(val)
            }
        }

        const sumOfRiskScores = Object.values(apiStats.riskScoreMap).reduce((acc, value) => acc + value, 0);

        // Calculate the additional APIs that should be added to risk score "0"
        const additionalAPIsForZero = totalApisCount - sumOfRiskScores - missingCount;
        apiStats.riskScoreMap["0"] = apiStats.riskScoreMap["0"] ? apiStats.riskScoreMap["0"] : 0
        if (additionalAPIsForZero > 0) apiStats.riskScoreMap["0"] += additionalAPIsForZero;

        // Prepare the result array with values from 5 to 0
        const result = [
            { "badgeValue": 5, "progressValue": "0%", "text": 0, "topColor": "#E45357", "backgroundColor": "#FFDCDD", "badgeColor": "critical" },
            { "badgeValue": 4, "progressValue": "0%", "text": 0, "topColor": "#EF864C", "backgroundColor": "#FFD9C4", "badgeColor": "attention" },
            { "badgeValue": 3, "progressValue": "0%", "text": 0, "topColor": "#F6C564", "backgroundColor": "#FFF1D4", "badgeColor": "warning" },
            { "badgeValue": 2, "progressValue": "0%", "text": 0, "topColor": "#F5D8A1", "backgroundColor": "#FFF6E6", "badgeColor": "info" },
            { "badgeValue": 1, "progressValue": "0%", "text": 0, "topColor": "#A4E8F2", "backgroundColor": "#EBFCFF", "badgeColor": "new" },
            { "badgeValue": 0, "progressValue": "0%", "text": 0, "topColor": "#6FD1A6", "backgroundColor": "#E0FFF1", "badgeColor": "success" }
        ];

        // Update progress and text based on riskScoreMap values
        Object.keys(apiStats.riskScoreMap).forEach((key) => {
            const badgeIndex = 5 - parseInt(key, 10);
            const value = apiStats.riskScoreMap[key];
            result[badgeIndex].text = value ? value : 0;
            if (!totalApisCount || totalApisCount === 0) {
                result[badgeIndex].progressValue = `0%`;
            } else {
                result[badgeIndex].progressValue = `${((value / totalApisCount) * 100).toFixed(2)}%`;
            }
        });

        if(missingCount > 0){
            result.push({
                "badgeValue": "N/A",
                "progressValue": (missingCount / totalApisCount) * 100 + "%",
                "text": missingCount,
                "topColor": "#DDE0E4",
                "backgroundColor": "#F6F7F8",
                "badgeColor": "subdued"
            })
        }
        setRiskScoreData(result)
    }

    let summaryInfo = [
        {
            title: 'Issues',
            requires: 'api/fetchIssuesSnapshot',
            data: observeFunc.formatNumberWithCommas(totalIssuesCount),
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: observeFunc.generateByLineComponent((totalIssuesCount - oldIssueCount), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={[oldIssueCount, totalIssuesCount]} />),
        },
        {
            title: mapLabel("API Risk Score", getDashboardCategory()),
            requires: 'api/fetchPostureSnapshot',
            data: customRiskScoreAvg !== 0 ? parseFloat(customRiskScoreAvg.toFixed(2))  : apiRiskScore,
            variant: 'heading2xl',
            color: (customRiskScoreAvg > 2.5 || apiRiskScore > 2.5) ? 'critical' : 'warning',
            byLineComponent: observeFunc.generateByLineComponent((apiRiskScore - oldRiskScore).toFixed(2), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={[oldRiskScore, apiRiskScore]} />),
            tooltipContent: 'This represents a cumulative risk score for the whole dashboard',
            docsUrl: 'https://docs.akto.io/api-discovery/concepts/risk-score'
        },
        {
            title: 'Test Coverage',
            requires: 'api/fetchPostureSnapshot',
            data: testCoverage + "%",
            variant: 'heading2xl',
            color: testCoverage > 80 ? 'success' : 'warning',
            byLineComponent: observeFunc.generateByLineComponent((testCoverage - oldTestCoverage).toFixed(2), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={[oldTestCoverage, testCoverage]} />)
        }
    ]
    // only show total apis for non-MCP security category
    if (!isMCPSecurityCategory()) {
        summaryInfo.unshift({
            title: mapLabel("Total APIs", getDashboardCategory()),
            requires: 'api/fetchEndpointsCount',
            // still counting: no number yet rather than a 0 that is not one
            data: endpointsCount === null ? '-' : transform.formatNumberWithCommas(totalAPIs),
            variant: 'heading2xl',
            ...(endpointsCount === null ? {} : {
                byLineComponent: observeFunc.generateByLineComponent((totalAPIs - oldTotalApis), func.timeDifference(startTimestamp, endTimestamp)),
                smoothChartComponent: (<SmoothAreaChart tickPositions={[oldTotalApis, totalAPIs]} />)
            })
        })
    }

    // MCP-only summary items

    if (isMCPSecurityCategory()) {
        const totalRequestsItem = {
            title: 'Total MCP Components',
            data: mcpTotals.mcpTotalApis ?? '-',
            variant: 'heading2xl'
        }


        summaryInfo.unshift(totalRequestsItem)
    }

    const summaryComp = (
        <SummaryCard summaryItems={summaryInfo.filter(item => !item.requires || canCall(item.requires))} />
    )


    const testSummaryCardsList = showTestingComponents ? (
        <InfoCard
            component={<TestSummaryCardsList summaryItems={testSummaryInfo} />}
            title={"Recent " + mapLabel("Tests", getDashboardCategory())}
            titleToolTip={"View details of recent" + mapLabel("API security tests", getDashboardCategory()) + ", APIs tested and number of issues found of last 7 days."}
            linkText={"Increase " + mapLabel("test coverage", getDashboardCategory())}
            linkUrl="/dashboard/testing"
        />
    ) : null

    // severityCounts: open issues by severity, already summed by the backend
    function buildSeverityMap(severityCounts) {
        const countMap = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }

        if (severityCounts instanceof Object) {
            for (const key in severityCounts) {
                if (key in countMap) countMap[key] += severityCounts[key]
            }
        }

        const result = {
            "Critical": {
                "text": countMap.CRITICAL || 0,
                "color": func.getHexColorForSeverity("CRITICAL"),
                "filterKey": "CRITICAL"
            },
            "High": {
                "text": countMap.HIGH || 0,
                "color": func.getHexColorForSeverity("HIGH"),
                "filterKey": "HIGH"
            },
            "Medium": {
                "text": countMap.MEDIUM || 0,
                "color": func.getHexColorForSeverity("MEDIUM"),
                "filterKey": "MEDIUM"
            },
            "Low": {
                "text": countMap.LOW || 0,
                "color": func.getHexColorForSeverity("LOW"),
                "filterKey": "LOW"
            }
        };

        setSeverityMap(result)

        const allZero = Object.values(result).every(item => item.text === 0);
        setSeverityMapEmpty(allZero)
    }

    const genreateDataTableRows = (collections) => {
        return collections.map((collection, index) => ([
            <HorizontalStack align='space-between'>
                <HorizontalStack gap={2}>
                    <Box maxWidth='287px'>
                        <TooltipText tooltip={collection.name} text={collection.name}/>
                    </Box>
                    <Text variant='bodySm' color='subdued'>{(collection.totalApis === 0 ? 0 : Math.floor(100.0 * collection.apisTested / collection.totalApis))}% test coverage</Text>
                </HorizontalStack>
                <Text>{collection.totalApis}</Text>
            </HorizontalStack>
        ]
        ));
    }

    function extractCategoryNames(data) {
        if (!data || !Array.isArray(data) || data.length === 0) {
            return [];
        }

        const findings = data[0]?.data || [];
        return findings.map(([category]) => category);
    }

    const vulnerableApisBySeverityComponent = !severityMapEmpty ? <InfoCard
        component={
            <div style={{ marginTop: "20px" }}>
                <ChartypeComponent
                    data={severityMap}
                    navUrl={"/dashboard/issues"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="250px" boxPadding={0}
                    pieInnerSize="50%"
                />
            </div>
        }
        title="Issues by Severity"
        titleToolTip="Breakdown of issues categorized by severity level (High, Medium, Low). Click to see details for each category."
        linkText="Fix critical issues"
        linkUrl="/dashboard/issues"
    /> : <EmptyCard title="Issues by Severity" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No issues found for this time-frame</Text>: runTestEmptyCardComponent}/>

    const criticalUnsecuredAPIsOverTime = <CriticalUnsecuredAPIsOverTimeGraph startTimestamp={startTimestamp} endTimestamp={endTimestamp} linkText={"Fix critical issues"} linkUrl={"/dashboard/issues"} />

    const criticalFindings = <CriticalFindingsGraph startTimestamp={startTimestamp} endTimestamp={endTimestamp} subCategoryCounts={snapshots?.issues?.subCategoryCounts ?? null} linkText={"Fix critical issues"} linkUrl={"/dashboard/issues"} />

    const apisByRiskscoreComponent = <InfoCard
        component={
            <div style={{ marginTop: "20px" }}>
                <ProgressBarChart data={riskScoreData} />
            </div>
        }
        title={`${mapLabel("APIs", getDashboardCategory())} by Risk score`}
        titleToolTip={`Distribution of ${mapLabel("APIs", getDashboardCategory())} based on their calculated risk scores. Higher scores indicate greater potential security risks.`}
        linkText="Check out"
        linkUrl="/dashboard/observe/inventory"
    />

    const inventoryAllCollectionBaseUrl = `/dashboard/observe/inventory/${ALL_COLLECTION_INVENTORY_ID}`

    const apisByAccessTypeComponent = <InfoCard
        component={
            <ChartypeComponent data={accessTypeMap} navUrl={inventoryAllCollectionBaseUrl}  navUrlBuilder={(baseUrl, filterValue) => buildAuthFiltersUrl(baseUrl, filterValue, "access_type")} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="250px" boxPadding={0} pieInnerSize="50%" />
        }
        title={`${mapLabel("APIs", getDashboardCategory())} by Access type`}
        titleToolTip={`Categorization of ${mapLabel("APIs", getDashboardCategory())} based on their access permissions and intended usage (Partner, Internal, External, etc.).`}
        linkText="Check out"
        linkUrl={inventoryAllCollectionBaseUrl}
    />

    

    const apisByAuthTypeComponent =
        <InfoCard
            component={
                <div style={{ marginTop: showTestingComponents ? '0px' : '20px' }}>
                    <ChartypeComponent data={authMap} navUrl={EXPLORE_MODE_QUERY_PATH} navUrlBuilder={(baseUrl, filterValue) => buildExploreModeAuthFiltersUrl(baseUrl, filterValue, "auth_type")} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="250px" boxPadding={0} pieInnerSize="50%"/>
                </div>
            }
            title={`${mapLabel("APIs", getDashboardCategory())} by Authentication`}
            titleToolTip={`Breakdown of ${mapLabel("APIs", getDashboardCategory())} by the authentication methods they use, including unauthenticated APIs which may pose security risks.`}
            linkText="Check out"
            linkUrl={EXPLORE_MODE_QUERY_PATH}
        />

    const apisByTypeComponent = (!isMCPSecurityCategory()) ? <InfoCard
        component={
            <StackedChart
                type='column'
                color='#6200EA'
                areaFillHex="true"
                height="280"
                background-color="#ffffff"
                data={apiTypesData}
                defaultChartOptions={defaultChartOptions}
                text="true"
                yAxisTitle={`Number of ${mapLabel("APIs", getDashboardCategory())}`}
                width={Object.keys(apiTypesData).length > 4 ? "25" : "40"}
                gap={10}
                showGridLines={true}
                customXaxis={
                    {
                        categories: extractCategoryNames(apiTypesData),
                        crosshair: true
                    }
                }
                exportingDisabled={true}
            />
        }
        title={`${mapLabel("API", getDashboardCategory())} Type`}
        titleToolTip={`Distribution of ${mapLabel("APIs", getDashboardCategory())} by their architectural style or protocol (e.g., REST, GraphQL, gRPC, SOAP).`}
        linkText="Check out"
        linkUrl="/dashboard/observe/inventory"
    /> : null

    // MCP-only 
    const mcpDiscoveryMiniCard = (
        <InfoCard
            component={
                <HorizontalGrid columns={4} gap={6}>
                    <VerticalStack gap={1}>
                        <Box style={{ minHeight: '36px' }}><Text color="subdued">Components</Text></Box>
                        <Text variant='headingMd'>{mcpTotals.mcpTotalApis ?? '-'}</Text>
                    </VerticalStack>
                    <VerticalStack gap={1}>
                        <Box style={{ minHeight: '36px' }}><Text color="subdued">Services</Text></Box>
                        <Text variant='headingMd'>-</Text>
                    </VerticalStack>
                    <VerticalStack gap={1}>
                        <Box style={{ minHeight: '36px' }}><Text color="subdued">Third party Components</Text></Box>
                        <Text variant='headingMd'>{mcpTotals.thirdPartyApis ?? '-'}</Text>
                    </VerticalStack>
                     <VerticalStack gap={1}>
                        <Box style={{ minHeight: '36px' }}><Text color="subdued">Policy Guardrail APIs</Text></Box>
                        <Text variant='headingMd'>{mcpTotals.policyGuardrailApis ?? '-'}</Text>
                    </VerticalStack>
                </HorizontalGrid>
            }
            title={'Discovery'}
            titleToolTip={'High-level MCP discovery snapshot'}
            linkText={''}
            linkUrl={''}
        />
    )

    const mcpRiskDetectionsMiniCard = (
        <InfoCard
            component={
                <HorizontalGrid columns={4} gap={6}>
                    <VerticalStack gap={1}>
                        <Box style={{ minHeight: '36px' }}><Text color="subdued">Critical MCP Components</Text></Box>
                        <Text variant='headingMd'>{mcpTotals.criticalApis ?? '-'}</Text>
                    </VerticalStack>
                    <VerticalStack gap={1}>
                        <Box style={{ minHeight: '36px' }}><Text color="subdued">AI security</Text></Box>
                        <Text variant='headingMd'>-</Text>
                    </VerticalStack>
                    <VerticalStack gap={1}>
                        <Box style={{ minHeight: '36px' }}><Text color="subdued">MCP security</Text></Box>
                        <Text variant='headingMd'>-</Text>
                    </VerticalStack>
                    <VerticalStack gap={1}>
                        <Box style={{ minHeight: '36px' }}><Text color="subdued">CVE's</Text></Box>
                        <Text variant='headingMd'>-</Text>
                    </VerticalStack>
                </HorizontalGrid>
            }
            title={'Risk Detections'}
            titleToolTip={'Key MCP risk detection metrics'}
            linkText={''}
            linkUrl={''}
        />
    )

    const hasTypesData = 
        mcpTotals.tools != null || 
        mcpTotals.prompts != null || 
        mcpTotals.resources != null || 
        mcpTotals.server != null

    const mcpTypesTableCard = (
        hasTypesData ?
        <InfoCard
            component={
                <Box>
                    <DataTable
                        columnContentTypes={['text','numeric']}
                        headings={[<Text color="subdued">Type</Text>, <Text color="subdued">Count</Text>]}
                        rows={[
                            ['Tools', mcpTotals.tools ?? '-'],
                            ['Prompts', mcpTotals.prompts ?? '-'],
                            ['Resources', mcpTotals.resources ?? '-'],
                            ['MCP Server', mcpTotals.server ?? '-']
                        ]}
                        increasedTableDensity
                        hoverable={false}
                    />
                </Box>
            }
            title={'Types'}
            titleToolTip={'Sample distribution of MCP entity types'}
            linkText={''}
            linkUrl={''}
        /> : <EmptyCard title="Types" subTitleComponent={<Text alignment='center' color='subdued'>No MCP entity types to display</Text>} />
    )
 
    const mcpApiRequestsSeries = mcpApiCallStats
    const hasRequestsData = Array.isArray(mcpApiRequestsSeries) && mcpApiRequestsSeries.length > 0 && mcpApiRequestsSeries.some(p => p && p[1] > 0)
    const defaultMcpChartOptions = (enableLegends) => {
        const options = {
          plotOptions: {
            series: {
              events: {
                legendItemClick: function () {
                  return false;
                },
              },
              marker: {
                radius: 2,  // Smaller marker radius (default is usually 4)
                states: {
                  hover: {
                    radius: 3  // Slightly larger on hover for better interaction
                  }
                }
              }
            },
          },
        };
        if (enableLegends) {
          options['legend'] = { layout: 'vertical', align: 'right', verticalAlign: 'middle' };
        }
        return options;
    };
    
    const mcpApiRequestsCard = (
        <InfoCard
            component={
                <Box paddingBlockStart={'2'}>
                    <HorizontalStack align="end">
                        <Dropdown
                            menuItems={statsOptions}
                            initial={statsOptions[8].label}
                            selected={(timeInSeconds) => {
                                setMcpStatsTimeRange(func.timeNow() - timeInSeconds);
                            }}
                        />
                    </HorizontalStack>
                    {hasRequestsData ? (
                        <GraphMetric
                            key={`mcp-stats-${mcpStatsTimeRange}`}
                            data={[{
                                data: mcpApiRequestsSeries,
                                color: '',
                                name: 'MCP Components Calls'
                            }]}
                            type='spline'
                            color='#6200EA'
                            areaFillHex='true'
                            height='250'
                            title=''
                            subtitle=''
                            defaultChartOptions={defaultMcpChartOptions(false)}
                            backgroundColor='#ffffff'
                            text='true'
                            inputMetrics={[]}
                        />
                    ) : (
                        <Box minHeight="250px" paddingBlockStart="8">
                            <Text alignment='center' color='subdued'>No MCP components requests in the selected period</Text>
                        </Box>
                    )}
                </Box>
            }
            title={'MCP Components Requests'}
            titleToolTip={'Components request volume trend for MCP collections over time'}
            linkText={''}
            linkUrl={''}
        />
    )

    // Policy Guardrails graph component
    const policyGuardrailSeries = policyGuardrailStats
    const hasPolicyGuardrailData = Array.isArray(policyGuardrailSeries) && policyGuardrailSeries.length > 0 && policyGuardrailSeries.some(p => p && p[1] > 0)
    const policyGuardrailsCard = (
        <InfoCard
            component={
                <Box paddingBlockStart={'2'}>
                    <HorizontalStack align="end">
                        <Dropdown
                            menuItems={statsOptions}
                            initial={statsOptions[8].label}
                            selected={(timeInSeconds) => {
                                setPolicyGuardrailStatsTimeRange(func.timeNow() - timeInSeconds);
                            }}
                        />
                    </HorizontalStack>
                    {hasPolicyGuardrailData ? (
                        <GraphMetric
                            key={`policy-guardrail-stats-${policyGuardrailStatsTimeRange}`}
                            data={[{
                                data: policyGuardrailSeries,
                                color: '',
                                name: 'Policy Guardrails'
                            }]}
                            type='spline'
                            color='#00AA5B'
                            areaFillHex='true'
                            height='250'
                            title=''
                            subtitle=''
                            defaultChartOptions={defaultMcpChartOptions(false)}
                            backgroundColor='#ffffff'
                            text='true'
                            inputMetrics={[]}
                        />
                    ) : (
                        <Box minHeight="250px" paddingBlockStart="8">
                            <Text alignment='center' color='subdued'>No policy guardrail requests in the selected period</Text>
                        </Box>
                    )}
                </Box>
            }
            title={'Policy Guardrails'}
            titleToolTip={'Policy guardrail request volume trend for collections with guard-rail tag over time'}
            linkText={''}
            linkUrl={''}
        />
    )

    // MCP-only Open Alerts card
    const mcpOpenAlertsCard = (
        <InfoCard
            component={
                <VerticalStack gap={3}>
                    {mcpOpenAlertDetails && mcpOpenAlertDetails.length > 0 ? (
                        mcpOpenAlertDetails.map((alert, idx) => (
                            <Box key={`open-alert-${idx}`} onClick={handleMcpAuditNavigation} style={{cursor: 'pointer'}}>
                                <Box padding="4" background="bg-surface" borderRadius="2" borderColor="border" borderWidth="2">
                                    <HorizontalStack align="center" gap={3}>
                                        <Box style={{ flex: 1 }}>
                                            <VerticalStack gap={1}>
                                                <Text variant='bodyMd' fontWeight='semibold'>{alert?.resourceName || '-'}</Text>
                                                <Text variant='bodyMd' color='text'>{alert?.type || '-'}</Text>
                                                <Text color='subdued' variant='bodySm'>{alert?.lastDetected ? func.prettifyEpoch(alert.lastDetected) : '-'}</Text>
                                            </VerticalStack>
                                        </Box>
                                        <Box paddingInlineEnd="2">
                                            <div style={{ fontSize: '32px' }}>
                                                <Icon source={AlertMinor} color="warning" />
                                            </div>
                                        </Box>
                                    </HorizontalStack>
                                </Box>
                            </Box>
                        ))
                    ) : (
                        <Box paddingBlockStart="1">
                            <Text alignment='center' color='subdued'>No recent open audit alerts</Text>
                        </Box>
                    )}
                </VerticalStack>
            }
            title={'Open Audit Alerts'}
            titleToolTip={'MCP open audit alerts detected in your workspace'}
            linkText={mcpOpenAlertDetails && mcpOpenAlertDetails.length > 0 ? 'View more' : undefined}
            linkUrl={undefined}
            onLinkClick={mcpOpenAlertDetails && mcpOpenAlertDetails.length > 0 ? handleMcpAuditNavigation : undefined}
        />
    )

     // MCP-only Top Applications by Traffic card
        const hasTopApps = Array.isArray(mcpTopApplications) && mcpTopApplications.length > 0
        const mcpTopApplicationsCard = (
            hasTopApps ? (
                <InfoCard
                    component={
                        <Box>
                            <VerticalStack gap={3}>
                                {mcpTopApplications.map((app, idx) => (
                                    <HorizontalStack key={`top-app-${idx}`} align="space-between">
                                        <Text variant='bodyMd' color='text'>{app?.name ?? '-'}</Text>
                                        <Box paddingInlineStart="2" paddingInlineEnd="2" background="bg-fill-info" borderRadius="5">
                                            <Text variant='bodySm' color='text-on-primary'>
                                                {app?.hitCount != null ? app.hitCount.toLocaleString() + ' requests' : '-'}
                                            </Text>
                                        </Box>
                                    </HorizontalStack>
                                ))}
                            </VerticalStack>
                        </Box>
                    }
                    title={'Top MCP Collections by Traffic'}
                    titleToolTip={'Top 3 MCP collections ranked by total request volume'}
                    linkText={''}
                    linkUrl={''}
                />
            ) : (
                <EmptyCard title="Top Applications by Traffic" subTitleComponent={<Text alignment='center' color='subdued'>No application traffic data</Text>} />
            )
        )

    const newDomainsComponent = <InfoCard
        component={
            <Scrollable style={{ height: "320px" }} shadow>
                <DataTable
                    columnContentTypes={[
                        'text',
                    ]}
                    headings={[]}
                    rows={genreateDataTableRows(newDomains)}
                    hoverable={false}
                    increasedTableDensity
                />
            </Scrollable>
        }
        title="New Domains"
        titleToolTip="Recently discovered or added domains containing APIs. Shows test coverage for each new domain."
        linkText="Increase test coverage"
        linkUrl="/dashboard/observe/inventory"
        minHeight="344px"
    />

    const hasTimelineData = Array.isArray(threatActorsTimeline) && threatActorsTimeline.length > 0;

    const threatActorsTimelineComponent = (
        <InfoCard
            component={
                <Box>
                    {hasTimelineData ? (
                        <GraphMetric
                            data={[{
                                data: threatActorsTimeline,
                                color: observeFunc.getColorForSensitiveData('CRITICAL'),
                                name: 'Active Threat Actors'
                            }]}
                            type='spline'
                            color={observeFunc.getColorForSensitiveData('CRITICAL')}
                            areaFillHex='true'
                            height='250'
                            title=''
                            subtitle=''
                            defaultChartOptions={defaultChartOptions}
                            backgroundColor='#ffffff'
                            text='true'
                            inputMetrics={[]}
                        />
                    ) : (
                        <Box minHeight="250px" paddingBlockStart="8">
                            <Text alignment='center' color='subdued'>No threat actor data in the selected period</Text>
                        </Box>
                    )}
                </Box>
            }
            title="Threat Actor Activity"
            titleToolTip="Timeline showing the number of active threat actors detected over time"
            linkText="View threat details"
            linkUrl="/dashboard/protection/threat-dashboard"
        />
    );

    const hasSeverityData = threatSeverityData && typeof threatSeverityData === 'object' && Object.keys(threatSeverityData).length > 0 && Object.values(threatSeverityData).some(item => item?.text > 0);

    const threatSeverityComponent = (
        <InfoCard
            component={
                <div style={{ marginTop: "20px" }}>
                    {hasSeverityData ? (
                        <ChartypeComponent
                            data={threatSeverityData}
                            navUrl="/dashboard/protection/threat-dashboard"
                            isNormal={true}
                            boxHeight={'250px'}
                            chartOnLeft={true}
                            dataTableWidth="250px"
                            pieInnerSize="50%"
                        />
                    ) : (
                        <Box minHeight="250px">
                            <Text alignment='center' color='subdued'>No threat severity data available</Text>
                        </Box>
                    )}
                </div>
            }
            title="Threats by Severity"
            titleToolTip="Distribution of detected threats categorized by severity level"
            linkText="View all threats"
            linkUrl="/dashboard/protection/threat-dashboard"
        />
    );

    const hasCategoryData = threatCategoryData[0] && Array.isArray(threatCategoryData[0].data) && threatCategoryData[0].data.length > 0;

    const threatCategoryComponent = (
        <InfoCard
            component={
                <Box>
                    {hasCategoryData ? (
                        <StackedChart
                            type='column'
                            color={observeFunc.getColorForSensitiveData('CRITICAL')}
                            height="280"
                            data={threatCategoryData}
                            yAxisTitle="Number of Threats"
                            width={40}
                            showGridLines={true}
                            customXaxis={{
                                categories: threatCategoryData[0].data.map(item => item[0])
                            }}
                        />
                    ) : (
                        <Box minHeight="280px" paddingBlockStart="8">
                            <Text alignment='center' color='subdued'>No threat category data available</Text>
                        </Box>
                    )}
                </Box>
            }
            title="Top Threat Categories"
            titleToolTip="Most common types of security threats detected across your APIs"
            linkText="View all categories"
            linkUrl="/dashboard/protection/threat-dashboard"
        />
    );

    const showForAccount = func.isDemoAccount();
    const threatComponents = func.checkForFeatureSaas('THREAT_DETECTION') && func.hasThreatAccess() ? [
        ...(showForAccount ? [{id: 'threat-timeline', component: threatActorsTimelineComponent}] : []),
        {id: 'threat-severity', component: threatSeverityComponent},
        {id: 'threat-categories', component: threatCategoryComponent},
    ] : [];
    let gridComponents = showTestingComponents ?
        [
            ...(showForAccount ? [{id: 'critical-apis', component: criticalUnsecuredAPIsOverTime}] : []),
            {id: 'vulnerable-apis', component: vulnerableApisBySeverityComponent},
            {id: 'critical-findings', component: criticalFindings},
            ...threatComponents,
            {id: 'risk-score', component: apisByRiskscoreComponent},
            {id: 'access-type', component: apisByAccessTypeComponent},
            {id: 'auth-type', component: apisByAuthTypeComponent},
            {id: 'new-domains', component: newDomainsComponent},
            {id: 'api-type', component: apisByTypeComponent}
        ] :
        [
            {id: 'risk-score', component: apisByRiskscoreComponent},
            {id: 'access-type', component: apisByAccessTypeComponent},
            {id: 'auth-type', component: apisByAuthTypeComponent},
            {id: 'new-domains', component: newDomainsComponent},
            ...(showForAccount ? [{id: 'critical-apis', component: criticalUnsecuredAPIsOverTime}] : []),
            {id: 'vulnerable-apis', component: vulnerableApisBySeverityComponent},
            {id: 'critical-findings', component: criticalFindings},
            ...threatComponents,
            {id: 'api-type', component: apisByTypeComponent},
        ]

    if (isMCPSecurityCategory()) {
        gridComponents = [
            {id: 'mcp-api-requests', component: mcpApiRequestsCard},
            {id: 'policy-guardrails', component: policyGuardrailsCard},
          //  {id: 'mcp-discovery', component: mcpDiscoveryMiniCard},
          //  {id: 'mcp-top-applications', component: mcpTopApplicationsCard},
          //  {id: 'mcp-risk', component: mcpRiskDetectionsMiniCard},
            {id: 'mcp-open-alerts', component: mcpOpenAlertsCard},
            {id: 'mcp-types-table', component: mcpTypesTableCard},
            ...gridComponents
        ]
    }
    gridComponents = gridComponents.filter(({id}) => !WIDGET_REQUIRES[id] || canCall(WIDGET_REQUIRES[id]))

    const gridComponent = (
        (isMCPSecurityCategory()) ? (
            <VerticalStack gap={5}>
                {/* First row with MCP Components Requests and Policy Guardrails side by side */}
                {canLoadApiCallStats && (
                <HorizontalGrid gap={5} columns={2}>
                    {mcpApiRequestsCard}
                    {policyGuardrailsCard}
                </HorizontalGrid>
                )}
                {/* Second row with equal columns for remaining components */}
                <HorizontalGrid gap={5} columns={2}>
                    {gridComponents.slice(2).map(({id, component}) => (
                        <div key={id}>{component}</div>
                    ))}
                </HorizontalGrid>
            </VerticalStack>
        ) : (
            <HorizontalGrid gap={5} columns={2}>
                {gridComponents.map(({id, component}) => (
                    <div key={id}>{component}</div>
                ))}
            </HorizontalGrid>
         )
    )

    const components = [
        {id: 'summary', component: summaryComp},
        {id: 'test-summary', component: testSummaryCardsList},
        {id: 'grid', component: gridComponent}
    ]

    const dashboardComp = (
        <VerticalStack gap={4}>
            {postureNotice.pending && (
                <Banner status="info">
                    <VerticalStack gap="2">
                        {pendingPolls < MAX_PENDING_POLLS ? (
                            <HorizontalStack gap="2" blockAlign="center">
                                <Spinner size="small" accessibilityLabel="Counting" />
                                <Text>
                                    Counting the APIs of your collections{postureNotice.collections > 0 ? ` (${postureNotice.counted} of ${postureNotice.collections})` : ''}.
                                    The numbers below are partial and fill in as it goes.
                                </Text>
                            </HorizontalStack>
                        ) : (
                            <Text>Still counting the APIs of your collections: the numbers below are partial. Reload in a while to see them complete.</Text>
                        )}
                        {postureNotice.collections > 0 && (
                            <ProgressBar size="small" progress={Math.round(100 * postureNotice.counted / postureNotice.collections)} />
                        )}
                    </VerticalStack>
                </Banner>
            )}
            {postureNotice.endApproximate && (
                <Banner status="warning">
                    Too many APIs were discovered after the end of this range to take them out of the numbers, so they are today's.
                </Banner>
            )}
            {components.map(({id, component}) => (
                <div key={id}>{component}</div>
            ))}
        </VerticalStack>
    )

    const tabsComponent = (
        <VerticalStack gap="4" key="tabs-stack">
            <LegacyTabs tabs={tabs} selected={selectedTab} onSelect={handleTabChange} />
            {selectedTab === 0 ? dashboardComp : <ActionItemsContent actionItemsData={actionItemsData} />}
        </VerticalStack>
    )

    const pageComponents = [showBannerComponent ? <DashboardBanner key="dashboardBanner" /> : tabsComponent]

    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security"

    return (
        <Box>
            {loading ? <SpinnerCentered /> :
                <PageWithMultipleCards
                    title={
                        <Text variant='headingLg'>
                            {mapLabel("API Security Posture", dashboardCategory)}
                        </Text>
                    }
                    isFirstPage={true}
                    components={pageComponents}
                    primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} disabled={selectedTab === 1} />}
                />
            }

        </Box>

    )
}

export default HomeDashboard
import React, { useEffect, useReducer, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import api from './api';
import func from '@/util/func';
import observeFunc from "../observe/transform"
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards"
import { Box, DataTable, HorizontalGrid, HorizontalStack, Icon, Link, Scrollable, Text, VerticalStack, LegacyTabs, Badge } from '@shopify/polaris';
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
import { fetchActionItemsData } from './components/actionItemsTransform';
import { getDashboardCategory, isMCPSecurityCategory, mapLabel } from '../../../main/labelHelper';
import GraphMetric from '../../components/GraphMetric';
import Dropdown from '../../components/layouts/Dropdown';

function HomeDashboard() {

    const [loading, setLoading] = useState(true);
    const [showBannerComponent, setShowBannerComponent] = useState(false)
    const [testSummaryInfo, setTestSummaryInfo] = useState([])
    const [selectedTab, setSelectedTab] = useState(0);
    const [actionItemsCount, setActionItemsCount] = useState(0);
    const [actionItemsData, setActionItemsData] = useState(null);

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


    const allCollections = PersistStore(state => state.allCollections)
    const hostNameMap = PersistStore(state => state.hostNameMap)
    const coverageMap = PersistStore(state => state.coverageMap)
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

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[2]);

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

    const testSummaryData = async () => {
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

    const fetchPolicyGuardrailStats = async (startTs, endTs) => {
        try {
            // Get collections with guard-rail tag
            const guardRailCollections = allCollections.filter(collection => {
                return collection.envType && collection.envType.some(envType =>
                    envType.keyName === 'guard-rail' && envType.value === 'Guard Rail'
                );
            });

            if (guardRailCollections.length === 0) {
                // Generate empty data points for the time range
                const emptyData = generateTimeSeriesWithGaps(startTs, endTs, {});
                setPolicyGuardrailStats(emptyData);
                return;
            }

            // Fetch API call stats for each guard-rail collection and their URLs
            const promises = [];
            guardRailCollections.forEach(collection => {
                // Check if the collection has urls array and it's not empty
                if (collection.urls && Array.isArray(collection.urls) && collection.urls.length > 0) {
                    collection.urls.forEach(urlString => {
                        // Split the URL string by space to get url and method
                        const parts = urlString.split(' ');
                        const url = parts[0];
                        const method = parts[1];

                        // Create a promise for each URL/method combination
                        promises.push(
                            observeApi.fetchApiCallStats(collection.id, url, method, startTs, endTs)
                                .catch(err => {
                                    return null;
                                })
                        );
                    });
                } else {
                    // Skip collections without urls array or with empty urls array
                }
            });

            const results = await Promise.all(promises);

            // Aggregate the results
            const aggregatedStats = {};
            results.forEach(result => {
                if (result && result.result && result.result.apiCallStats) {
                    result.result.apiCallStats.forEach(stat => {
                        const ts = stat.ts * 60 * 1000; // Convert to milliseconds
                        if (!aggregatedStats[ts]) {
                            aggregatedStats[ts] = 0;
                        }
                        aggregatedStats[ts] += stat.count;
                    });
                }
            });

            // Generate complete time series with gaps filled as zeros
            const chartData = generateTimeSeriesWithGaps(startTs, endTs, aggregatedStats);

            setPolicyGuardrailStats(chartData);
        } catch (error) {
            setPolicyGuardrailStats([]);
        }
    };

    const fetchMcpApiCallStats = async (startTs, endTs) => {
        try {
            // Get MCP collections by checking for MCP tag
            const mcpCollections = allCollections.filter(collection => {
                return collection.envType && collection.envType.some(envType =>
                    envType.keyName === 'mcp-server' && envType.value === 'MCP Server'
                );
            });


            if (mcpCollections.length === 0) {
                // Generate empty data points for the time range
                const emptyData = generateTimeSeriesWithGaps(startTs, endTs, {});
                setMcpApiCallStats(emptyData);
                return;
            }

            // Fetch API call stats for each MCP collection and their URLs
            const promises = [];
            mcpCollections.forEach(collection => {
                // Check if the collection has urls array and it's not empty
                if (collection.urls && Array.isArray(collection.urls) && collection.urls.length > 0) {
                    collection.urls.forEach(urlString => {
                        // Split the URL string by space to get url and method
                        const parts = urlString.split(' ');
                        const url = parts[0];
                        const method = parts[1];

                        // Create a promise for each URL/method combination
                        promises.push(
                            observeApi.fetchApiCallStats(collection.id, url, method, startTs, endTs)
                                .catch(err => {
                                    return null;
                                })
                        );
                    });
                } else {
                    // Skip collections without urls array or with empty urls array
                }
            });

            const results = await Promise.all(promises);

            // Aggregate the results
            const aggregatedStats = {};
            results.forEach(result => {
                if (result && result.result && result.result.apiCallStats) {
                    result.result.apiCallStats.forEach(stat => {
                        const ts = stat.ts * 60 * 1000; // Convert to milliseconds
                        if (!aggregatedStats[ts]) {
                            aggregatedStats[ts] = 0;
                        }
                        aggregatedStats[ts] += stat.count;
                    });
                }
            });

            // Generate complete time series with gaps filled as zeros
            const chartData = generateTimeSeriesWithGaps(startTs, endTs, aggregatedStats);

            setMcpApiCallStats(chartData);
        } catch (error) {
            setMcpApiCallStats([]);
        }
    };
    
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
        
        // Generate complete time series
        const completeData = [];
        for (let ts = startMs; ts <= endMs; ts += intervalMs) {
            // Find the closest data point within the interval
            let value = 0;
            for (let checkTs = ts; checkTs < ts + intervalMs && checkTs <= endMs; checkTs += 60000) {
                if (dataMap[checkTs]) {
                    value += dataMap[checkTs];
                }
            }
            completeData.push([ts, value]);
        }
        
        // If we have actual data points, merge them in for accuracy
        Object.entries(dataMap).forEach(([ts, count]) => {
            const timestamp = parseInt(ts);
            if (timestamp >= startMs && timestamp <= endMs) {
                // Find the nearest interval point
                const intervalIndex = Math.floor((timestamp - startMs) / intervalMs);
                if (intervalIndex >= 0 && intervalIndex < completeData.length) {
                    // Update the value at this interval
                    completeData[intervalIndex][1] = Math.max(completeData[intervalIndex][1], count);
                }
            }
        });
        
        return completeData.sort((a, b) => a[0] - b[0]);
    };

    const fetchData = async () => {
        setLoading(true)
        // all apis
        let apiPromises = [
            observeApi.getUserEndpoints(),
            api.findTotalIssues(startTimestamp, endTimestamp),
            api.fetchApiStats(startTimestamp, endTimestamp),
            api.fetchEndpointsCount(startTimestamp, endTimestamp),
            testingApi.fetchSeverityInfoForIssues({}, [], 0),
            api.getApiInfoForMissingData(0, endTimestamp),
            api.fetchMcpdata('TOTAL_APIS'),
            api.fetchMcpdata('THIRD_PARTY_APIS'),
            api.fetchMcpdata('RECENT_OPEN_ALERTS'),
            api.fetchMcpdata('CRITICAL_APIS'),
            api.fetchMcpdata('TOOLS'),
            api.fetchMcpdata('PROMPTS'),
            api.fetchMcpdata('RESOURCES'),
            api.fetchMcpdata('MCP_SERVER'),
            api.fetchMcpdata('POLICY_GUARDRAIL_APIS'),
            api.fetchMcpdata('TOP_3_APPLICATIONS_BY_TRAFFIC')
        ];

        let results = await Promise.allSettled(apiPromises);

        let userEndpoints = results[0].status === 'fulfilled' ? results[0].value : true;
        let findTotalIssuesResp = results[1].status === 'fulfilled' ? results[1].value : {}
        let apisStatsResp = results[2].status === 'fulfilled' ? results[2].value : {}
        let fetchEndpointsCountResp = results[3].status === 'fulfilled' ? results[3].value : {}
        let issueSeverityMap = results[4].status === 'fulfilled' ? results[4].value : {}
        let missingApiInfoData = results[5].status === 'fulfilled' ? results[5].value : {}
        let mcpTotalApis = results[6]?.status === 'fulfilled' ? (results[6].value?.mcpDataCount ?? null) : null
        let mcpThirdParty = results[7]?.status === 'fulfilled' ? (results[7].value?.mcpDataCount ?? null) : null
        let mcpOpenAlertsDetails = results[8]?.status === 'fulfilled' ? (results[8].value?.response?.alertDetails ?? []) : []
        let mcpCriticalApis = results[9]?.status === 'fulfilled' ? (results[9].value?.mcpDataCount ?? null) : null
        let mcpTools = results[10]?.status === 'fulfilled' ? (results[10].value?.mcpDataCount ?? null) : null
        let mcpPrompts = results[11]?.status === 'fulfilled' ? (results[11].value?.mcpDataCount ?? null) : null
        let mcpResources = results[12]?.status === 'fulfilled' ? (results[12].value?.mcpDataCount ?? null) : null
        let mcpServer = results[13]?.status === 'fulfilled' ? (results[13].value?.mcpDataCount ?? null) : null
        let mcpPolicyGuardrailApis = results[14]?.status === 'fulfilled' ? (results[14].value?.mcpDataCount ?? null) : null
        let mcpTopApps = results[15]?.status === 'fulfilled' ? (results[15].value?.response?.topApplications ?? []) : []
        const totalRedundantApis = missingApiInfoData?.redundantApiInfoKeys || 0
        const totalMissingApis = missingApiInfoData?.totalMissing|| 0

        setShowBannerComponent(!userEndpoints)

        // for now we are actually using the apis from stis instead of the total apis in the response
        // TODO: Fix apiStats API to return the correct total apis
        buildMetrics(apisStatsResp.apiStatsEnd, fetchEndpointsCountResp)
        testSummaryData()
        mapAccessTypes(apisStatsResp, totalMissingApis, totalRedundantApis, missingApiInfoData?.accessTypeNotCalculated || 0)
        mapAuthTypes(apisStatsResp, totalMissingApis, totalRedundantApis, (missingApiInfoData?.authNotCalculated || 0))
        buildAPITypesData(apisStatsResp.apiStatsEnd, totalMissingApis, totalRedundantApis, (missingApiInfoData?.apiTypeMissing || 0))
        buildSetRiskScoreData(apisStatsResp.apiStatsEnd, Math.max(0, totalMissingApis - totalRedundantApis)) //todo
        getCollectionsWithCoverage()
        buildSeverityMap(issueSeverityMap.severityInfo)
        buildIssuesSummary(findTotalIssuesResp)

        const fetchHistoricalDataResp = { "finalHistoricalData": finalHistoricalData, "initialHistoricalData": initialHistoricalData }
        buildDeltaInfo(fetchHistoricalDataResp)

        buildEndpointsCount(fetchEndpointsCountResp)

        setMcpTotals({ mcpTotalApis: mcpTotalApis, thirdPartyApis: mcpThirdParty, newApis7Days: null, criticalApis: mcpCriticalApis, tools: mcpTools, prompts: mcpPrompts, resources: mcpResources, server: mcpServer,policyGuardrailApis: mcpPolicyGuardrailApis })
        setMcpOpenAlertDetails(Array.isArray(mcpOpenAlertsDetails) ? mcpOpenAlertsDetails.slice(0, 2) : [])
        setMcpTopApplications(Array.isArray(mcpTopApps) ? mcpTopApps : [])
        fetchMcpApiCallStats(mcpStatsTimeRange, func.timeNow());
        setLoading(false)
    }

    useEffect(() => {
        fetchData()
    }, [startTimestamp, endTimestamp])
    
    // Fetch MCP API call stats when time range changes
    useEffect(() => {
        if (allCollections && allCollections.length > 0) {
            fetchMcpApiCallStats(mcpStatsTimeRange, func.timeNow());
        }
    }, [mcpStatsTimeRange, allCollections])

    // Fetch Policy Guardrail stats when time range changes
    useEffect(() => {
        if (allCollections && allCollections.length > 0) {
            fetchPolicyGuardrailStats(policyGuardrailStatsTimeRange, func.timeNow());
        }
    }, [policyGuardrailStatsTimeRange, allCollections])

    async function getActionItemsDataAndCount() {
        const data = await fetchActionItemsData();
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
    }

    useEffect(() => {
        getActionItemsDataAndCount();
    }, []);

    function buildIssuesSummary(findTotalIssuesResp) {
        if (findTotalIssuesResp && findTotalIssuesResp.totalIssuesCount) {
            setTotalIssuesCount(findTotalIssuesResp.totalIssuesCount)
        } else {
            setTotalIssuesCount(0)
        }

        if (findTotalIssuesResp && findTotalIssuesResp.oldOpenCount){
            setOldIssueCount(findTotalIssuesResp.oldOpenCount)
        } else {
            setOldIssueCount(0)
        }
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
            const testCoverage = 100 * apisTestedInLookBackPeriod / totalAPIs
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

    function getCollectionsWithCoverage() {
        const validCollections = allCollections.filter(collection => (hostNameMap[collection.id] && hostNameMap[collection.id] !== undefined)  && !collection.deactivated);

        const sortedCollections = validCollections.sort((a, b) => b?.startTs - a?.startTs);

        const result = sortedCollections.slice(0, 10).map(collection => {
            const apisTested = coverageMap[collection.id] || 0;
            return {
                name: collection.hostName,
                apisTested: apisTested,
                totalApis: collection.urlsCount
            };
        });

        setNewDomains(result)
    }

    let summaryInfo = [
        {
            title: 'Issues',
            data: observeFunc.formatNumberWithCommas(totalIssuesCount),
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: observeFunc.generateByLineComponent((totalIssuesCount - oldIssueCount), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={[oldIssueCount, totalIssuesCount]} />),
        },
        {
            title: mapLabel("API Risk Score", getDashboardCategory()),
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
            data: transform.formatNumberWithCommas(totalAPIs),
            variant: 'heading2xl',
            byLineComponent: observeFunc.generateByLineComponent((totalAPIs - oldTotalApis), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={[oldTotalApis, totalAPIs]} />)
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
        <SummaryCard summaryItems={summaryInfo} />
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

    function buildSeverityMap(severityInfo) {
        const countMap = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }

        if (severityInfo && severityInfo != undefined && severityInfo != null && severityInfo instanceof Object) {
            for (const apiCollectionId in severityInfo) {
                let temp = severityInfo[apiCollectionId]
                for (const key in temp) {
                    countMap[key] += temp[key]
                }
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

    const criticalFindings = <CriticalFindingsGraph startTimestamp={startTimestamp} endTimestamp={endTimestamp} linkText={"Fix critical issues"} linkUrl={"/dashboard/issues"} />

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
                    <ChartypeComponent data={authMap} navUrl={inventoryAllCollectionBaseUrl} navUrlBuilder={(baseUrl, filterValue) => buildAuthFiltersUrl(baseUrl, filterValue, "auth_type")} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="250px" boxPadding={0} pieInnerSize="50%"/>
                </div>
            }
            title={`${mapLabel("APIs", getDashboardCategory())} by Authentication`}
            titleToolTip={`Breakdown of ${mapLabel("APIs", getDashboardCategory())} by the authentication methods they use, including unauthenticated APIs which may pose security risks.`}
            linkText="Check out"
            linkUrl={inventoryAllCollectionBaseUrl}
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

    let gridComponents = showTestingComponents ?
        [
            {id: 'critical-apis', component: criticalUnsecuredAPIsOverTime},
            {id: 'vulnerable-apis', component: vulnerableApisBySeverityComponent},
            {id: 'critical-findings', component: criticalFindings},
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
            {id: 'critical-apis', component: criticalUnsecuredAPIsOverTime},
            {id: 'vulnerable-apis', component: vulnerableApisBySeverityComponent},
            {id: 'critical-findings', component: criticalFindings},
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

    const gridComponent = (
        (isMCPSecurityCategory()) ? (
            <VerticalStack gap={5}>
                {/* First row with MCP Components Requests and Policy Guardrails side by side */}
                <HorizontalGrid gap={5} columns={2}>
                    {mcpApiRequestsCard}
                    {policyGuardrailsCard}
                </HorizontalGrid>
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
import React, { useEffect, useReducer, useState } from 'react'
import api from './api';
import func from '@/util/func';
import observeFunc from "../observe/transform"
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards"
import { Box, DataTable, HorizontalGrid, HorizontalStack, Icon, Link, Scrollable, Text, VerticalStack } from '@shopify/polaris';
import observeApi from "../observe/api"
import testingTransform from "../testing/transform"
import StackedChart from '../../components/charts/StackedChart';
import ChartypeComponent from '../testing/TestRunsPage/ChartypeComponent';
import testingApi from "../testing/api"
import testingFunc from "../testing/transform"
import PersistStore from '../../../main/PersistStore';
import { DashboardBanner } from './components/DashboardBanner';
import SummaryCard from './new_components/SummaryCard';
import { ArrowUpMinor, ArrowDownMinor } from '@shopify/polaris-icons';
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

function HomeDashboard() {

    const [loading, setLoading] = useState(true);
    const [skip, setSkip] = useState(0)
    const [showBannerComponent, setShowBannerComponent] = useState(false)
    const [testSummaryInfo, setTestSummaryInfo] = useState([])

    const allCollections = PersistStore(state => state.allCollections)
    const coverageMap = PersistStore(state => state.coverageMap)
    const [authMap, setAuthMap] = useState({})
    const [apiTypesData, setApiTypesData] = useState([{ "data": [], "color": "#D6BBFB" }])
    const [riskScoreData, setRiskScoreData] = useState([])
    const [newDomains, setNewDomains] = useState([])
    const [criticalFindingsData, setCriticalFindingsData] = useState([])
    const [severityMap, setSeverityMap] = useState({})
    const [severityMapEmpty, setSeverityMapEmpty] = useState(true)
    const [unsecuredAPIs, setUnsecuredAPIs] = useState([])
    const [totalIssuesCount, setTotalIssuesCount] = useState(0)
    const [oldIssueCount, setOldIssueCount] = useState(0)
    const [apiRiskScore, setApiRiskScore] = useState(0)
    const [testCoverage, setTestCoverage] = useState(0)
    const [totalAPIs, setTotalAPIs] = useState(0)
    const [oldTotalApis, setOldTotalApis] = useState(0)
    const [oldTestCoverage, setOldTestCoverage] = useState(0)
    const [oldRiskScore, setOldRiskScore] = useState(0)
    const initialStartTimestamp = func.timeNow() - 60 * 60 * 24
    const initialEndTimestamp = func.timeNow()
    const [showTestingComponents, setShowTestingComponents] = useState(false)
    const [riskScoreHistoricalData, setRiskScoreHistoricalData] = useState([])
    const [testCoverageHistoricalData, setTestCoverageHistoricalData] = useState([])
    const [dayTotalIssues, setDayTotalIssues] = useState([])
    const [endpointsCountDayWise, setEndpointsCountDayWise] = useState([])

    const tempVal = { alias: "custom", title: "Custom", period: { since: new Date(initialStartTimestamp * 1000), until: new Date(initialEndTimestamp * 1000) } }

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), tempVal);

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
        },
        "Internal": {
            "text": 0,
            "color": "#FDB33D",
            "filterKey": "Internal"
        },
        "External": {
            "text": 0,
            "color": "#658EE2",
            "filterKey": "External"
        },
        "Third Party": {
            "text": 0,
            "color": "#68B3D0",
            "filterKey": "Third Party"
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

    const fetchData = async () => {
        setLoading(true)
        // all apis 
        let apiPromises = [
            testingApi.getSummaryInfo(0, func.timeNow()),
            observeApi.getUserEndpoints(),
            api.fetchCriticalIssuesTrend(), // todo:
            api.findTotalIssues(startTimestamp, endTimestamp),
            api.fetchApiStats(startTimestamp, endTimestamp),
            api.fetchEndpointsCount(startTimestamp, endTimestamp),
            api.fetchAllHistoricalData(startTimestamp, endTimestamp)
        ];

        let results = await Promise.allSettled(apiPromises);

        let subcategoryDataResp = results[0].status === 'fulfilled' ? results[0].value : {};
        let userEndpoints = results[1].status === 'fulfilled' ? results[1].value : true;
        let criticalIssuesTrendResp = results[2].status === 'fulfilled' ? results[2].value : {}
        let findTotalIssuesResp = results[3].status === 'fulfilled' ? results[3].value : {}
        let apisStatsResp = results[4].status === 'fulfilled' ? results[4].value : {}
        let fetchEndpointsCountResp = results[5].status === 'fulfilled' ? results[5].value : {}
        let fetchAllHistoricalDataResp = results[6].status === 'fulfilled' ? results[6].value : {}

        setShowBannerComponent(!userEndpoints)

        buildUnsecuredAPIs(criticalIssuesTrendResp)

        const tempResult = testingFunc.convertSubIntoSubcategory(subcategoryDataResp)

        // buildMetrics(apisStatsResp.apiStatsEnd)
        testSummaryData()
        mapAccessTypes(apisStatsResp)
        mapAuthTypes(apisStatsResp)
        buildAuthTypesData(apisStatsResp.apiStatsEnd)
        buildSetRiskScoreData(apisStatsResp.apiStatsEnd) //todo
        getCollectionsWithCoverage()
        convertSubCategoryInfo(tempResult.subCategoryMap)
        buildSeverityMap(apisStatsResp.apiStatsEnd)
        buildIssuesSummary(findTotalIssuesResp)

        const fetchHistoricalDataResp = { "finalHistoricalData": finalHistoricalData, "initialHistoricalData": initialHistoricalData }
        // buildDeltaInfo(fetchHistoricalDataResp)

        buildEndpointsCount(fetchEndpointsCountResp)

        buildHistoricalData(fetchAllHistoricalDataResp)

        setLoading(false)
    }

    useEffect(() => {
        fetchData()
    }, [startTimestamp, endTimestamp])

    function buildIssuesSummary(findTotalIssuesResp) {
        const totalIssuesValues = findTotalIssuesResp.totalIssuesCountDayWise

        if(!totalIssuesValues || totalIssuesValues.length === 0) {
            setDayTotalIssues([0, 0])
            setTotalIssuesCount(0)
            setOldIssueCount(0)
            return
        }

        setTotalIssuesCount(totalIssuesValues.reduce((acc, value) => acc + value, 0))
        
        if (totalIssuesValues.length === 1) {
            setDayTotalIssues([0, totalIssuesValues.at(0)])
        } else if(totalIssuesValues.length > 1) {
            setOldIssueCount(totalIssuesValues.at(0))
            setDayTotalIssues(totalIssuesValues)
        }
        
    }

    function buildEndpointsCount(fetchEndpointsCountResp) {
        const endpointsCountDayWise = fetchEndpointsCountResp.endpointsCountDayWise

        if(endpointsCountDayWise) {
            const res = endpointsCountDayWise
            if(!res || res.length === 0) {
                setTotalAPIs(0)
                setOldTotalApis(0)
                setEndpointsCountDayWise([0, 0])
                return
            }

            setTotalAPIs(res.reduce((acc, value) => acc + value, 0))
            setOldTotalApis(res[0])

            if(res.length === 1) {
                setEndpointsCountDayWise([0, res[0]])
                setOldTotalApis(0)
            } else {
                setEndpointsCountDayWise(res)
            }
        } else {
            setTotalAPIs(0)
            setOldTotalApis(0)
            setEndpointsCountDayWise([0, 0])
        }
    }

    function buildMetrics(apiStats) {
        if (!apiStats) return;

        const totalRiskScore = apiStats.totalRiskScore
        const totalAPIs = apiStats.totalAPIs

        const apisTestedInLookBackPeriod = apiStats.apisTestedInLookBackPeriod

        if (totalAPIs && totalAPIs > 0 && totalRiskScore) {
            const tempRiskScore = totalRiskScore / totalAPIs
            setApiRiskScore(parseFloat(tempRiskScore.toFixed(2)))
        } else {
            setApiRiskScore(0)
        }

        if (totalAPIs && totalAPIs > 0 && apisTestedInLookBackPeriod) {
            const testCoverage = 100 * apisTestedInLookBackPeriod / totalAPIs
            setTestCoverage(parseFloat(testCoverage.toFixed(2)))
        } else {
            setTestCoverage(0)
        }
    }

    function buildHistoricalData(historicalDataResp) {
        if(!historicalDataResp || historicalDataResp.length === 0) {
            setApiRiskScore(0)
            setOldRiskScore(0)
            setTestCoverage(0)
            setOldTestCoverage(0)
            setRiskScoreHistoricalData([0, 0])
            setTestCoverageHistoricalData([0,0])
            return
        }

        const riskScoreData = historicalDataResp.map(item => item.riskScore)
        
        if(riskScoreData && riskScoreData.length > 1) {
            setOldRiskScore(riskScoreData[0])
        } else {
            setOldRiskScore(0)
        }

        if(riskScoreData.length === 0) {
            setRiskScoreHistoricalData([0,0])
            setApiRiskScore(0)
        } else if(riskScoreData.length === 1) {
            setRiskScoreHistoricalData([0, riskScoreData[0]])
            setApiRiskScore(parseFloat(riskScoreData[0].toFixed(2)))
        } else {
            setRiskScoreHistoricalData(riskScoreData)
            setApiRiskScore(parseFloat(riskScoreData[riskScoreData.length-1].toFixed(2)))
        }

        const testCoverageData = historicalDataResp.map(item => (100 * item.apisTested/item.totalApis))

        if(testCoverageData && testCoverageData.length > 1) {
            setOldTestCoverage(testCoverageData[0])
        } else {
            setOldTestCoverage(0)
        }

        if(!testCoverageData || testCoverageData.length === 0) {
            setTestCoverageHistoricalData([0,0])
            setTestCoverage(0)
        } else if(testCoverageData.length === 1) {
            setTestCoverageHistoricalData([0, testCoverageData[0]])
            setTestCoverage(parseFloat(testCoverageData[0].toFixed(2)))
        } else {
            setTestCoverageHistoricalData(testCoverageData)
            setTestCoverage(parseFloat(testCoverageData[testCoverageData.length-1].toFixed(2)))
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

        const tempRiskScore = totalAPIs ? (totalRiskScore / totalApis).toFixed(2) : 0
        setOldRiskScore(parseFloat(tempRiskScore))
        const tempTestCoverate = totalAPIs ? (100 * totalTestedApis / totalApis).toFixed(2) : 0
        setOldTestCoverage(parseFloat(tempTestCoverate))
    }

    const generateByLineComponent = (val, time) => {
        if (!val || isNaN(val)) return null
        if (val === 0 ) {
            return <Text>No change in {time}</Text>
        }
        const source = val > 0 ? ArrowUpMinor : ArrowDownMinor
        return (
            <HorizontalStack gap={1}>
                <Box>
                    <Icon source={source} color='subdued' />
                </Box>
                <Text color='subdued' fontWeight='medium'>{Math.abs(val)}</Text>
                <Text color='subdued' fontWeight='semibold'>{time}</Text>
            </HorizontalStack>
        )
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

    const runTestEmptyCardComponent = <Text alignment='center' color='subdued'>There’s no data to show. <Link url="/dashboard/testing" target='_blank'>Run test</Link> to get data populated. </Text>

    function mapAccessTypes(apiStats) {
        if (!apiStats) return
        const apiStatsEnd = apiStats.apiStatsEnd
        const apiStatsStart = apiStats.apiStatsStart

        const accessTypeMapping = {
            "PUBLIC": "External",
            "PRIVATE": "Internal",
            "PARTNER": "Partner",
            "THIRD_PARTY": "Third Party"
        };

        for (const [key, value] of Object.entries(apiStatsEnd.accessTypeMap)) {
            const mappedKey = accessTypeMapping[key];
            if (mappedKey && accessTypeMap[mappedKey]) {
                accessTypeMap[mappedKey].text = value;
                accessTypeMap[mappedKey].dataTableComponent = generateChangeComponent((value - apiStatsStart.accessTypeMap[key]), false);
            }
        }
        setAccessTypeMap(accessTypeMap)
    }


    function mapAuthTypes(apiStats) {
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

        // Convert and sort the authTypeMap entries by value (count) in descending order
        const sortedAuthTypes = Object.entries(apiStatsEnd.authTypeMap)
            .map(([key, value]) => ({ key: key, text: value }))
            .filter(item => item.text > 0) // Filter out entries with a count of 0
            .sort((a, b) => b.text - a.text); // Sort by count descending

        // Initialize the output authMap
        const authMap = {};


        // Fill in the authMap with sorted entries and corresponding colors
        sortedAuthTypes.forEach((item, index) => {
            authMap[convertKey(item.key)] = {
                "text": item.text,
                "color": colors[index] || "#F4EBFF", // Assign color; default to last color if out of range
                "filterKey": convertKey(item.key),
                "dataTableComponent": apiStatsStart && apiStatsStart.authTypeMap && apiStatsStart.authTypeMap[item.key] ? generateChangeComponent((item.text - apiStatsStart.authTypeMap[item.key]), item.key === "UNAUTHENTICATED") : null
            };
        });

        setAuthMap(authMap)
    }


    function buildAuthTypesData(apiStats) {
        // Initialize the data with default values for all API types
        const data = [
            ["REST", apiStats.apiTypeMap.REST || 0], // Use the value from apiTypeMap or 0 if not available
            ["GraphQL", apiStats.apiTypeMap.GRAPHQL || 0],
            ["gRPC", apiStats.apiTypeMap.GRPC || 0],
            ["SOAP", apiStats.apiTypeMap.SOAP || 0]
        ];

        setApiTypesData([{ data: data, color: "#D6BBFB" }])
    }

    function buildSetRiskScoreData(apiStats) {
        const totalApisCount = apiStats.totalAPIs

        const sumOfRiskScores = Object.values(apiStats.riskScoreMap).reduce((acc, value) => acc + value, 0);

        // Calculate the additional APIs that should be added to risk score "0"
        const additionalAPIsForZero = totalApisCount - sumOfRiskScores;
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

        setRiskScoreData(result)
    }

    function getCollectionsWithCoverage() {
        const validCollections = allCollections.filter(collection => collection.hostName !== null && collection.hostName !== undefined && !collection.deactivated);

        const sortedCollections = validCollections.sort((a, b) => b.startTs - a.startTs);

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
    const getCumulativeData = (data) => {
        let cumulative = [];
        data.reduce((acc, val, i) => cumulative[i] = acc + val, 0);
        return cumulative;
    };

    const summaryInfo = [
        {
            title: 'Total APIs',
            data: transform.formatNumberWithCommas(totalAPIs),
            variant: 'heading2xl',
            byLineComponent: generateByLineComponent((totalAPIs - oldTotalApis), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={getCumulativeData(endpointsCountDayWise)} />)
        },
        {
            title: 'Issues',
            data: observeFunc.formatNumberWithCommas(totalIssuesCount),
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: generateByLineComponent((totalIssuesCount - oldIssueCount), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={getCumulativeData(dayTotalIssues)} />)
        },
        {
            title: 'API Risk Score',
            data: apiRiskScore,
            variant: 'heading2xl',
            color: apiRiskScore > 2.5 ? 'critical' : 'warning',
            byLineComponent: generateByLineComponent((apiRiskScore - oldRiskScore).toFixed(2), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={riskScoreHistoricalData} />)
        },
        {
            title: 'Test Coverage',
            data: testCoverage + "%",
            variant: 'heading2xl',
            color: testCoverage > 80 ? 'success' : 'warning',
            byLineComponent: generateByLineComponent((testCoverage - oldTestCoverage).toFixed(2), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={testCoverageHistoricalData} />)
        }
    ]

    const summaryComp = (
        <SummaryCard summaryItems={summaryInfo} />
    )


    const testSummaryCardsList = showTestingComponents ? (
        <InfoCard
            component={<TestSummaryCardsList summaryItems={testSummaryInfo} />}
            title="Recent Tests"
            titleToolTip="View details of recent API security tests, APIs tested and number of issues found of last 7 days."
            linkText="Increase test coverage"
            linkUrl="/dashboard/testing"
        />
    ) : null

    function buildSeverityMap(apiStats) {
        const countMap = apiStats ? apiStats.criticalMap : {};

        const result = {
            "Critical": {
                "text": countMap.CRITICAL || 0,
                "color": "#E45357",
                "filterKey": "Critical",
            },
            "High": {
                "text": countMap.HIGH || 0,
                "color": "#EF864C",
                "filterKey": "High"
            },
            "Medium": {
                "text": countMap.MEDIUM || 0,
                "color": "#F6C564",
                "filterKey": "Medium"
            },
            "Low": {
                "text": countMap.LOW || 0,
                "color": "#6FD1A6",
                "filterKey": "Low"
            }
        };

        setSeverityMap(result)

        const allZero = Object.values(result).every(item => item.text === 0);
        setSeverityMapEmpty(allZero)
    }

    function buildUnsecuredAPIs(input) {
        const CRITICAL_COLOR = "#E45357";
        const transformed = [];

        // Initialize objects for CRITICAL and HIGH data
        const criticalData = { data: [], color: CRITICAL_COLOR };

        // Iterate through the input to populate criticalData and highData
        for (const epoch in input) {
            const epochMillis = Number(epoch) * 86400000; // Convert days to milliseconds
            criticalData.data.push([epochMillis, input[epoch]]);
        }

        // Push the results to the transformed array
        transformed.push(criticalData);

        setUnsecuredAPIs(transformed)
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

    function convertSubCategoryInfo(tempSubCategoryMap) {
        const entries = Object.values(tempSubCategoryMap);
        entries.sort((a, b) => b.text - a.text);
        const topEntries = entries.slice(0, 5);
        const data = topEntries.map(entry => [entry.filterKey, entry.text]);
        setCriticalFindingsData([{ "data": data, "color": "#E45357" }])
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
                    navUrl={"/dashboard/issues/"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="250px" boxPadding={0}
                    pieInnerSize="50%"
                />
            </div>
        }
        title="Vulnerable APIs by Severity"
        titleToolTip="Breakdown of vulnerable APIs categorized by severity level (Critical, High, Medium, Low). Click to see details for each category."
        linkText="Fix critical issues"
        linkUrl="/dashboard/issues"
    /> : <EmptyCard title="Vulnerable APIs by Severity" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No vulnerable APIs found</Text>: runTestEmptyCardComponent}/>

    const criticalUnsecuredAPIsOverTime = (unsecuredAPIs && unsecuredAPIs.length > 0 && unsecuredAPIs[0].data && unsecuredAPIs[0].data.length > 0) ? <InfoCard
        component={
            <StackedChart
                type='column'
                color='#6200EA'
                areaFillHex="true"
                height="280"
                background-color="#ffffff"
                data={unsecuredAPIs}
                defaultChartOptions={defaultChartOptions}
                text="true"
                yAxisTitle="Number of issues"
                width={40}
                gap={10}
                showGridLines={true}
                exportingDisabled={true}
            />
        }
        title="Critical Unsecured APIs Over Time"
        titleToolTip="Chart showing the number of APIs detected(risk score >= 4) each month over the past year. Helps track security trends over time."
        linkText="Fix critical issues"
        linkUrl="/dashboard/issues"
    /> : <EmptyCard title="Critical Unsecured APIs Over Time" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No Unsecured APIs found</Text>: runTestEmptyCardComponent} />

    const criticalFindings = (criticalFindingsData && criticalFindingsData.length > 0 && criticalFindingsData[0].data && criticalFindingsData[0].data.length > 0) ?
        <InfoCard
            component={
                <StackedChart
                    type='column'
                    color='#6200EA'
                    areaFillHex="true"
                    height="280"
                    background-color="#ffffff"
                    data={criticalFindingsData}
                    defaultChartOptions={defaultChartOptions}
                    text="true"
                    yAxisTitle="Number of issues"
                    width={40}
                    gap={10}
                    showGridLines={true}
                    customXaxis={
                        {
                            categories: extractCategoryNames(criticalFindingsData),
                            crosshair: true
                        }
                    }
                    exportingDisabled={true}
                />
            }
            title="Vulnerabilities findings"
            titleToolTip="Overview of the most critical security issues detected, including the number of issues and APIs affected for each type of vulnerability."
            linkText="Fix critical issues"
            linkUrl="/dashboard/issues"
        /> : <EmptyCard title="Vulnerabilities findings" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No Vulnerabilities found</Text>: runTestEmptyCardComponent} />

    const apisByRiskscoreComponent = <InfoCard
        component={
            <div style={{ marginTop: "20px" }}>
                <ProgressBarChart data={riskScoreData} />
            </div>
        }
        title="APIs by Risk score"
        titleToolTip="Distribution of APIs based on their calculated risk scores. Higher scores indicate greater potential security risks."
        linkText="Check out"
        linkUrl="/dashboard/observe/inventory"
    />

    const apisByAccessTypeComponent = <InfoCard
        component={
            <ChartypeComponent data={accessTypeMap} navUrl={"/dashboard/observe/inventory"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="250px" boxPadding={0} pieInnerSize="50%" />
        }
        title="APIs by Access type"
        titleToolTip="Categorization of APIs based on their access permissions and intended usage (Partner, Internal, External, etc.)."
        linkText="Check out"
        linkUrl="/dashboard/observe/inventory"
    />

    const apisByAuthTypeComponent =
        <InfoCard
            component={
                <div style={{ marginTop: showTestingComponents ? '0px' : '20px' }}>
                    <ChartypeComponent data={authMap} navUrl={"/dashboard/observe/inventory"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="250px" boxPadding={0} pieInnerSize="50%"/>
                </div>
            }
            title="APIs by Authentication"
            titleToolTip="Breakdown of APIs by the authentication methods they use, including unauthenticated APIs which may pose security risks."
            linkText="Check out"
            linkUrl="/dashboard/observe/inventory"
        />

    const apisByTypeComponent = <InfoCard
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
                yAxisTitle="Number of APIs"
                width={40}
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
        title="API Type"
        titleToolTip="Distribution of APIs by their architectural style or protocol (e.g., REST, GraphQL, gRPC, SOAP)."
        linkText="Check out"
        linkUrl="/dashboard/observe/inventory"
    />

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

    const gridComponents = showTestingComponents ?
        [criticalUnsecuredAPIsOverTime, vulnerableApisBySeverityComponent, criticalFindings, apisByRiskscoreComponent, apisByAccessTypeComponent, apisByAuthTypeComponent, apisByTypeComponent, newDomainsComponent] :
        [apisByRiskscoreComponent, apisByAccessTypeComponent, apisByAuthTypeComponent, apisByTypeComponent, newDomainsComponent, criticalUnsecuredAPIsOverTime, vulnerableApisBySeverityComponent, criticalFindings]

    const gridComponent = (
        <HorizontalGrid gap={5} columns={2}>
            {gridComponents}
        </HorizontalGrid>
    )

    const components = [summaryComp, testSummaryCardsList, gridComponent]

    const dashboardComp = (
        <VerticalStack gap={4}>
            {components.map((component) => {
                return component
            })}
        </VerticalStack>
    )

    const pageComponents = [showBannerComponent ? <DashboardBanner key="dashboardBanner" /> : dashboardComp]

    return (
        <Box>
            {loading ? <SpinnerCentered /> :
                <PageWithMultipleCards
                    title={
                        <Text variant='headingLg'>
                            API Security Posture
                        </Text>
                    }
                    isFirstPage={true}
                    components={pageComponents}
                    primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
                />
            }

        </Box>

    )
}

export default HomeDashboard
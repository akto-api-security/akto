import React, { useEffect, useState } from 'react'
import api from './api';
import func from '@/util/func';
import observeFunc from "../observe/transform"
import SummaryCardInfo from '../../components/shared/SummaryCardInfo';
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards"
import { Box, Card, Checkbox, DataTable, HorizontalGrid, HorizontalStack, Icon, Scrollable, Text, VerticalStack } from '@shopify/polaris';
import observeApi from "../observe/api"
import transform from './transform';
import testingTransform from "../testing/transform"
import StackedChart from '../../components/charts/StackedChart';
import ChartypeComponent from '../testing/TestRunsPage/ChartypeComponent';
import testingApi from "../testing/api"
import testingFunc from "../testing/transform"
import InitialSteps from './components/InitialSteps';
import CoverageCard from './components/CoverageCard';
import PersistStore from '../../../main/PersistStore';
import Pipeline from './components/Pipeline';
import ActivityTracker from './components/ActivityTracker';
import NullData from './components/NullData';
import {DashboardBanner} from './components/DashboardBanner';
import RiskScoreTrend from './components/RiskScoreTrend';
import TitleWithInfo from '@/apps/dashboard/components/shared/TitleWithInfo';
import SummaryCard from './new_components/SummaryCard';
import { ArrowUpMinor, ArrowDownMinor} from '@shopify/polaris-icons';
import TestSummaryCardsList from './new_components/TestSummaryCardsList';
import InfoCard from './new_components/InfoCard';
import DonutChart from '../../components/shared/DonutChart';
import ProgressBarChart from './new_components/ProgressBarChart';
import SpinnerCentered from '../../components/progress/SpinnerCentered';

function HomeDashboard() {

    const [riskScoreRangeMap, setRiskScoreRangeMap] = useState({});
    const [issuesTrendMap, setIssuesTrendMap] = useState({trend: [], allSubCategories: []});
    const [loading, setLoading] = useState(false) ;
    const [countInfo, setCountInfo] = useState({totalUrls: 0, coverage: '0%'})
    const [riskScoreObj, setRiskScoreObj]= useState({}) ;
    const [sensitiveCount, setSensitiveCount]= useState([]) ;
    const [sensitiveData, setSensitiveData] = useState({request: {}, response: {}})
    const [subCategoryInfo, setSubCategoryInfo] = useState({});
    const [coverageObj, setCoverageObj] = useState({})
    const [recentActivities, setRecentActivities] = useState([])
    const [totalActivities, setTotalActivities] = useState(0)
    const [skip, setSkip] = useState(0)
    const [criticalUrls, setCriticalUrls] = useState(0);
    const [initialSteps, setInitialSteps] = useState({}) ;
    const [showBannerComponent, setShowBannerComponent] = useState(false)
    const [testSummaryInfo, setTestSummaryInfo] = useState([])

    const allCollections = PersistStore(state => state.allCollections)
    const coverageMap = PersistStore(state => state.coverageMap)
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const [authMap, setAuthMap] = useState({})
    const [apiTypesData, setApiTypesData] = useState([{"data": [], "color": "#D6BBFB"}])
    const [riskScoreData, setRiskScoreData] = useState([])
    const [newDomains, setNewDomains] = useState([])
    const [criticalFindingsData, setCriticalFindingsData] = useState([])
    const [severityMap, setSeverityMap] = useState({})
    const [unsecuredAPIs, setUnsecuredAPIs] = useState([])

    const [accessTypeMap, setAccessTypeMap] = useState({
        "Partner": {
          "text": 0,
          "color": "#147CF6",
          "filterKey": "Partner"
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




    
    //todo: remove
    const apiStats = {
        "accessTypeMap": {
            "PUBLIC": 10,
            "PRIVATE": 3,
            "PARTNER": 0,
            "THIRD_PARTY": 2
        },
        "apiTypeMap": {
            "REST": 208
        },
        "authTypeMap": {
            "UNAUTHENTICATED": 2,
            "BASIC": 1,
            "AUTHORIZATION_HEADER": 3,
            "JWT": 1,
            "API_TOKEN": 3,
            "BEARER": 3,
            "CUSTOM": 2
        },
        "riskScoreMap": {
            "1": 232
        }
    }

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
                    "highCount": severityMap["high"] ? severityMap["high"] : "0" ,
                    "mediumCount": severityMap["medium"] ? severityMap["medium"] : "0" ,
                    "lowCount": severityMap["low"] ? severityMap["low"] : "0",
                    "totalApis": x["total_apis"],
                })
            })

            setTestSummaryInfo(finalResult)
          });
    }

    const fetchData = async() =>{
        setLoading(true)
        // all apis 
        let apiPromises = [
            observeApi.getCoverageInfoForCollections(),
            api.getRiskScoreRangeMap(),
            api.getIssuesTrend((func.timeNow() - func.recencyPeriod), func.timeNow()),
            api.fetchSubTypeCountMap(0 , func.timeNow()),
            testingApi.getSummaryInfo(0 , func.timeNow()),
            api.fetchRecentFeed(skip),
            api.getIntegratedConnections(),
            observeApi.getUserEndpoints(),
            api.fetchCriticalIssuesTrend() // todo:
        ];
        
        let results = await Promise.allSettled(apiPromises);

        let coverageInfo = results[0].status === 'fulfilled' ? results[0].value : {};
        let riskScoreRangeMap = results[1].status === 'fulfilled' ? results[1].value : {};
        let issuesTrendResp = results[2].status === 'fulfilled' ? results[2].value : {};
        let sensitiveDataResp = results[3].status === 'fulfilled' ? results[3].value : {} ;
        let subcategoryDataResp = results[4].status === 'fulfilled' ? results[4].value : {} ;
        let recentActivitiesResp = results[5].status === 'fulfilled' ? results[5].value : {} ;
        let connectionsInfo = results[6].status === 'fulfilled' ? results[6].value : {} ;
        let userEndpoints = results[7].status === 'fulfilled' ? results[7].value : true ;
        let criticalIssuesTrendResp = results[8].status === 'fulfilled' ? results[8].value : {}
        setShowBannerComponent(!userEndpoints)

        buildUnsecuredAPIs(criticalIssuesTrendResp)

        setCountInfo(transform.getCountInfo((allCollections || []), coverageInfo))
        setCoverageObj(coverageInfo)
        setRiskScoreRangeMap(riskScoreRangeMap);
        setIssuesTrendMap(transform.formatTrendData(issuesTrendResp));
        setSensitiveData(transform.getFormattedSensitiveData(sensitiveDataResp.response))
        const tempResult =  testingFunc.convertSubIntoSubcategory(subcategoryDataResp)
        setSubCategoryInfo(tempResult.subCategoryMap)
        setRecentActivities(recentActivitiesResp.recentActivities)
        setTotalActivities(recentActivitiesResp.totalActivities)
        setInitialSteps(connectionsInfo)

        const riskScoreObj = (await observeFunc.fetchRiskScoreInfo()).riskScoreObj
        const riskScoreMap = riskScoreObj.riskScoreMap || {};
        const endpoints = riskScoreObj.criticalUrls;
        setCriticalUrls(endpoints)

        const sensitiveInfo = await observeFunc.fetchSensitiveInfo() ;
        setRiskScoreObj(riskScoreMap) ;
        setSensitiveCount(sensitiveInfo.sensitiveUrls) ;

        testSummaryData()
        mapAccessTypes(apiStats)
        mapAuthTypes(apiStats)
        buildAuthTypesData(apiStats)
        buildSetRiskScoreData(apiStats) //todo
        getCollectionsWithCoverage()
        convertSubCategoryInfo(tempResult.subCategoryMap)
        buildSeverityMap(tempResult.countMap)

        setLoading(false)
        
    }

    useEffect(()=>{
        fetchData()
    },[])

    const generateByLineComponent = (up, val, time) => {
        const source = up ? ArrowUpMinor : ArrowDownMinor
        return (
            <HorizontalStack gap={1}>
                <Box>
                    <Icon source={source} color='subdued'/>
                </Box>
                <Text color='subdued' fontWeight='medium'>{val}</Text>
                <Text color='subdued' fontWeight='semibold'>{time}</Text>
            </HorizontalStack>
        )
    }

    function mapAccessTypes(apiStats) {
        const accessTypeMapping = {
            "PUBLIC": "External",
            "PRIVATE": "Internal",
            "PARTNER": "Partner",
            "THIRD_PARTY": "Third Party"
        };

        for (const [key, value] of Object.entries(apiStats.accessTypeMap)) {
            const mappedKey = accessTypeMapping[key];
            if (mappedKey && accessTypeMap[mappedKey]) {
                accessTypeMap[mappedKey].text = value;
            }
        }
        setAccessTypeMap(accessTypeMap)
    }


    function mapAuthTypes(apiStats) {
        const convertKey = (key) => {
            return key
                .toLowerCase()
                .replace(/(^|\s)\S/g, (letter) => letter.toUpperCase()) // Capitalize the first character after any space
                .replace(/_/g, ' '); // Replace underscores with spaces
        };

        // Initialize colors list
        const colors = ["#7F56D9", "#8C66E1", "#9E77ED", "#AB88F1", "#B692F6", "#D6BBFB", "#E9D7FE", "#F4EBFF"];

        // Convert and sort the authTypeMap entries by value (count) in descending order
        const sortedAuthTypes = Object.entries(apiStats.authTypeMap)
            .map(([key, value]) => ({ key: convertKey(key), text: value }))
            .filter(item => item.text > 0) // Filter out entries with a count of 0
            .sort((a, b) => b.text - a.text); // Sort by count descending

        // Initialize the output authMap
        const authMap = {};

        // Fill in the authMap with sorted entries and corresponding colors
        sortedAuthTypes.forEach((item, index) => {
            authMap[item.key] = {
                "text": item.text,
                "color": colors[index] || "#F4EBFF", // Assign color; default to last color if out of range
                "filterKey": item.key
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
    
        setApiTypesData([{data: data,color: "#D6BBFB"}])
    }

    function buildSetRiskScoreData(apiStats) {
        const totalApisCount = transform.getCountInfo((allCollections || []), coverageMap).totalUrls

        const sumOfRiskScores = Object.values(apiStats.riskScoreMap).reduce((acc, value) => acc + value, 0);

        // Calculate the additional APIs that should be added to risk score "0"
        const additionalAPIsForZero = totalApisCount - sumOfRiskScores;
        apiStats.riskScoreMap["0"] = apiStats.riskScoreMap["0"] ? apiStats.riskScoreMap["0"] : 0
        if (additionalAPIsForZero > 0) apiStats.riskScoreMap["0"] += additionalAPIsForZero;

        // Prepare the result array with values from 5 to 0
        const result = [
            { "badgeValue": 5, "progressValue": "0%", "text": 0, "topColor": "#E45357", "backgroundColor": "#FFDCDD" },
            { "badgeValue": 4, "progressValue": "0%", "text": 0, "topColor": "#EF864C", "backgroundColor": "#FFD9C4" },
            { "badgeValue": 3, "progressValue": "0%", "text": 0, "topColor": "#F6C564", "backgroundColor": "#FFF1D4" },
            { "badgeValue": 2, "progressValue": "0%", "text": 0, "topColor": "#F5D8A1", "backgroundColor": "#FFF6E6" },
            { "badgeValue": 1, "progressValue": "0%", "text": 0, "topColor": "#A4E8F2", "backgroundColor": "#EBFCFF" },
            { "badgeValue": 0, "progressValue": "0%", "text": 0, "topColor": "#6FD1A6", "backgroundColor": "#E0FFF1" }
        ];

        // Update progress and text based on riskScoreMap values
        Object.keys(apiStats.riskScoreMap).forEach((key) => {
            const badgeIndex = 5 - parseInt(key, 10);
            const value = apiStats.riskScoreMap[key];
            result[badgeIndex].text = value;
            result[badgeIndex].progressValue = `${((value / totalApisCount) * 100).toFixed(2)}%`;
        });

        setRiskScoreData(result)
    }

    function getCollectionsWithCoverage() {
        const validCollections = allCollections.filter(collection => collection.hostName !== null && collection.hostName !== undefined);
    
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

    const summaryInfo = [
        {
            title: 'Total APIs',
            data: observeFunc.formatNumberWithCommas(countInfo.totalUrls),
            variant: 'heading2xl',
            byLineComponent: generateByLineComponent(true, 200, "Yesterday"),
        },
        {
            title: 'Issues',
            data: observeFunc.formatNumberWithCommas(criticalUrls),
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: generateByLineComponent(false, 200, "Yesterday")
            
        },
        {
            title: 'API Risk Score',
            data: countInfo.totalUrls === 0 ? "0%" : countInfo.coverage,
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: generateByLineComponent(true, 200, "Yesterday")
        },
        {
            title: 'Test Coverage',
            data: observeFunc.formatNumberWithCommas(sensitiveCount),
            variant: 'heading2xl',
            color: 'warning',
            byLineComponent: generateByLineComponent(true, 200, "Yesterday")
        }
    ]

    const summaryComp = (
        <SummaryCard summaryItems={summaryInfo}/>
    )


    const testSummaryCardsList = (
        <InfoCard 
            component={<TestSummaryCardsList summaryItems={ testSummaryInfo}/>}
            title="Recent Tests"
            titleToolTip="Tests runs since last 7 days"
            linkText="Check results"
            linkUrl="/dashboard/testing"
        />
    )

    function buildSeverityMap(countMap) {
        const result = {
            "Critical": {
                "text": countMap.CRITICAL || 0,
                "color": "#E45357",
                "filterKey": "Critical"
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
    }

    function buildUnsecuredAPIs(input) {
        const CRITICAL_COLOR = "#E45357";
        const HIGH_COLOR = "#EF864C";
        const transformed = [];

        // Initialize objects for CRITICAL and HIGH data
        const criticalData = { data: [], color: CRITICAL_COLOR };
        const highData = { data: [], color: HIGH_COLOR };

        // Iterate through the input to populate criticalData and highData
        for (const epoch in input) {
            const epochMillis = Number(epoch) * 86400000; // Convert days to milliseconds

            if (input[epoch].CRITICAL) {
                criticalData.data.push([epochMillis, input[epoch].CRITICAL]);
            }
            if (input[epoch].HIGH) {
                highData.data.push([epochMillis, input[epoch].HIGH]);
            }
        }

        // Push the results to the transformed array
        transformed.push(criticalData, highData);

        setUnsecuredAPIs(transformed)
    }


    const genreateDataTableRows = (collections) => {
        return collections.map((collection, index) => ([
            <HorizontalStack align='space-between'>
                <HorizontalStack gap={2}>
                    <Checkbox
                        key={index}
                        label={collection.name}
                        checked={false}
                        ariaDescribedBy={`checkbox-${index}`}
                        onChange={() => {}}
                    />
                    <Text color='subdued'>{Math.floor(100.0 * collection.apisTested / collection.totalApis)}% test coverage</Text>
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
        setCriticalFindingsData([{"data": data, "color": "#E45357"}])
    }


    function extractCategoryNames(data) {
        if (!data || !Array.isArray(data) || data.length === 0) {
            return [];
        }
        
        const findings = data[0]?.data || [];
        return findings.map(([category]) => category);
    }

    const gridComponent = (
        <HorizontalGrid gap={5} columns={2}>
            <InfoCard 
                component={
                    <ChartypeComponent data={severityMap} navUrl={"/dashboard/issues/"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="230px"/>
                }
                title="Vulnerable APIs by Severity"
                titleToolTip="Vulnerable APIs by Severity"
                linkText="Fix critical issues"
                linkUrl="/dashboard/issues"
            />
            <InfoCard 
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
                    />
                }
                title="Critical Unsecured APIs Over Time"
                titleToolTip="Critical Unsecured APIs Over Time"
                linkText="Fix critical issues"
                linkUrl="/dashboard/issues"
            />
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
                    />
                }
                title="Critical Findings"
                titleToolTip="Critical Findings"
                linkText="Fix critical issues"
                linkUrl="/dashboard/issues"
            />
            <InfoCard 
                component={
                    <ProgressBarChart data={riskScoreData}/>
                }
                title="APIs by Risk score"
                titleToolTip="APIs by Risk score"
                linkText="Check out"
                linkUrl="/dashboard/observe/inventory"
            />
            <InfoCard 
                component={
                    <ChartypeComponent data={accessTypeMap} navUrl={"/dashboard/observe/inventory"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="230px"/>
                }
                title="APIs by Access type"
                titleToolTip="APIs by Access type"
                linkText="Check out"
                linkUrl="/dashboard/observe/inventory"
            />
            <InfoCard 
                component={
                    <ChartypeComponent data={authMap} navUrl={"/dashboard/observe/inventory"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true} dataTableWidth="230px"/>
                }
                title="APIs by Authentication"
                titleToolTip="APIs by Authentication"
                linkText="Check out"
                linkUrl="/dashboard/observe/inventory"
            />
            <InfoCard 
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
                    />
                }
                title="API Type"
                titleToolTip="API Type"
                linkText="Check out"
                linkUrl="/dashboard/observe/inventory"
            />
            <InfoCard 
                component={
                    <DataTable
                        columnContentTypes={[
                            'text',
                        ]}
                        headings={[]}
                        rows={genreateDataTableRows(newDomains)}
                        hoverable={false}
                        increasedTableDensity
                    />
                }
                title="New Domains"
                titleToolTip="New Domains"
                linkText="Increase test coverage"
                linkUrl="/dashboard/observe/inventory"
            />
        </HorizontalGrid>
    )

    const subcategoryInfoComp = (
        Object.keys(subCategoryInfo).length > 0 ? 
            <Card key="subcategoryTrend">
                <VerticalStack gap={5}>
                    <TitleWithInfo
                        titleText={"Issues by category"}
                        tooltipContent={"Testing run issues present in dashboard categorised by subcategory of tests."}
                        textProps={{variant: "headingMd"}}
                    />
                    <ChartypeComponent navUrl={"/dashboard/issues/"} data={subCategoryInfo} title={"Categories"} isNormal={true} boxHeight={'200px'}/>
                </VerticalStack>
            </Card>

        : <NullData text={"Issues by category"} url={"/dashboard/observe/inventory"} urlText={"to run a test on a collection."} description={"No test categories found."} key={"subcategoryTrend"}/>
     )

    const riskScoreRanges = [
        {
            text: "High risk",
            range: '4-5',
            status: "critical",
            apiCollectionId: 111_111_150
        },
        {
            text: 'Medium risk',
            range: '3-4',
            status: 'warning',
            apiCollectionId: 111_111_149
        },
        {
            text: "Low risk",
            range: '0-3',
            status: 'new',
            apiCollectionId: 111_111_148
        }
    ]
    const riskScoreTrendComp = (
        <RiskScoreTrend  key={"risk-score-trend"} riskScoreRangeMap={riskScoreRangeMap} riskScoreRanges={riskScoreRanges} />
    )

    const sensitiveDataTrendComp = (
        (!sensitiveData || (!(sensitiveData.request) && !(sensitiveData.response))) ? 
        <NullData text={"Sensitive Data"} url={"/dashboard/observe/inventory"} urlText={"to create a collection and upload traffic in it."} description={"No sensitive data found."} key={"sensitiveNullTrend"}/>
        :
        <Card key="sensitiveTrend">
            <VerticalStack gap={5}>
                <TitleWithInfo
                    titleText={"Sensitive data"}
                    tooltipContent={"Count of endpoints per data type."}
                    textProps={{variant: "headingMd"}}
                    docsUrl={"https://docs.akto.io/api-inventory/concepts/sensitive-data"}
                />
                <HorizontalGrid gap={5} columns={2}>
                    <ChartypeComponent navUrl={"/dashboard/observe/sensitive/"} isRequest={true} data={sensitiveData.request} title={"Request"} isNormal={true} boxHeight={'100px'}/>
                    <ChartypeComponent navUrl={"/dashboard/observe/sensitive/"} data={sensitiveData.response} title={"Response"} isNormal={true} boxHeight={'100px'}/>
                </HorizontalGrid>
            </VerticalStack>
        </Card>
    )

    const issuesTrendComp = (
        (issuesTrendMap.allSubCategories.length > 0 && issuesTrendMap.trend.length > 0) ? 
        <Card key="issuesTrend">
            <VerticalStack gap={5}>
                <TitleWithInfo
                    titleText={"Issues timeline"}
                    tooltipContent={"Count of issues per category against the time they were last seen"}
                    textProps={{variant: "headingMd"}}
                />
                <VerticalStack gap={3}>
                    <HorizontalStack align="end">
                        <Scrollable style={{ width: '350px' }} shadow>
                            <Box maxWidth="50%">
                                    <div style={{display: 'flex', gap: '12px', cursor:'pointer'}}>
                                        {issuesTrendMap.allSubCategories.map((x,index)=>{
                                            return(
                                                <div style={{display: 'flex', gap: '8px', alignItems: 'center'}} key={index}>
                                                    <div style={{background: func.getColorForCharts(x), borderRadius: '50%', height:'8px', width: '8px'}}/>
                                                    <Text variant="bodySm">{x}</Text>
                                                </div>
                                                )
                                            })}
                                    </div>
                                
                            </Box>
                        </Scrollable>
                    </HorizontalStack>
                    <StackedChart 
                        type='column'
                        color='#6200EA'
                        areaFillHex="true"
                        height="280"
                        background-color="#ffffff"
                        data={issuesTrendMap.trend}
                        defaultChartOptions={defaultChartOptions}
                        text="true"
                        yAxisTitle="Number of issues"
                        width={30}
                        gap={10}
                        showGridLines={true}
                    />
                </VerticalStack>
            </VerticalStack>
        </Card>
        :  <NullData text={"Issues timeline."} url={"/dashboard/observe/inventory"} urlText={"to run a test on a collection."} description={"No issues found."} key={"issuesTrend"}/>
    )

    const checkLoadMore = () => {
        const calledActivitiesYet = recentActivities.length;
        return calledActivitiesYet < totalActivities;
    }

    const handleLoadMore = async() => {
        if(checkLoadMore()){
            await api.fetchRecentFeed(skip + 1).then((resp) => {
                setRecentActivities([...recentActivities,...resp.recentActivities])
            })
            setSkip(skip + 1);
        }
    }

    const components = [summaryComp, testSummaryCardsList, gridComponent]

    const dashboardComp = (
        <div style={{display: 'flex', gap: '32px'}} key={"dashboardComp"}>
            <div style={{flex: 7}}>
                <VerticalStack gap={4}>
                    {components.map((component) => {
                        return component
                    })}
                </VerticalStack>
            </div>
        </div>
    )

    const pageComponents = [showBannerComponent ? <DashboardBanner key="dashboardBanner" />: null, dashboardComp]

    return (
        <Box>
            {loading ? <SpinnerCentered /> :
                <PageWithMultipleCards
                    title={
                        <Text variant='headingLg'>
                            Home
                        </Text>
                    }
                    isFirstPage={true}
                    components={pageComponents}
                />
            }

        </Box>

    )
}

export default HomeDashboard
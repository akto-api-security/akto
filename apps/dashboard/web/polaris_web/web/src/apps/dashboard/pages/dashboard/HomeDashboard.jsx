import React, { useEffect, useState } from 'react'
import api from './api';
import func from '@/util/func';
import observeFunc from "../observe/transform"
import SummaryCardInfo from '../../components/shared/SummaryCardInfo';
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards"
import { Box, Card, Checkbox, DataTable, HorizontalGrid, HorizontalStack, Icon, Scrollable, Text, VerticalStack } from '@shopify/polaris';
import observeApi from "../observe/api"
import transform from './transform';
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

    const allCollections = PersistStore(state => state.allCollections)
    const collectionsMap = PersistStore(state => state.collectionsMap)

    const defaultChartOptions = {
        "legend": {
            enabled: false
        },
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
        setShowBannerComponent(!userEndpoints)

        setCountInfo(transform.getCountInfo((allCollections || []), coverageInfo))
        setCoverageObj(coverageInfo)
        setRiskScoreRangeMap(riskScoreRangeMap);
        setIssuesTrendMap(transform.formatTrendData(issuesTrendResp));
        setSensitiveData(transform.getFormattedSensitiveData(sensitiveDataResp.response))
        setSubCategoryInfo(testingFunc.convertSubIntoSubcategory(subcategoryDataResp).subCategoryMap)
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

    const testSummaryInfo = [
        {
            testName: 'Test run on Juice shop collection with nothing',
            time: '1 day ago',
            highCount: 3,
            mediumCount: 10,
            lowCount: 22,
            totalApis: 202,
        }, 
        {
            testName: 'Test run on Juice shop collection with nothing',
            time: '1 day ago',
            highCount: 3,
            mediumCount: 10,
            lowCount: 22,
            totalApis: 202,
        }, 
        {
            testName: 'Test run on Juice shop collection with nothing',
            time: '1 day ago',
            highCount: 3,
            mediumCount: 10,
            lowCount: 22,
            totalApis: 202,
        }, 
        {
            testName: 'Test run on Juice shop collection with nothing',
            time: '1 day ago',
            highCount: 3,
            mediumCount: 10,
            lowCount: 22,
            totalApis: 202,
        }, 
        {
            testName: 'Test run on Juice shop collection with nothing',
            time: '1 day ago',
            highCount: 3,
            mediumCount: 10,
            lowCount: 22,
            totalApis: 202,
        }, 
    ]

    const testSummaryCardsList = (
        <InfoCard 
            component={<TestSummaryCardsList summaryItems={ testSummaryInfo}/>}
            title="Recent Tests"
            titleToolTip="Tests runs since last 7 days"
            linkText="Check results"
            linkUrl="/dashboard/testing"
        />
    )

    const severityMap = {
        "Critical": {
            "text": 3,
            "color": "#E45357",
            "filterKey": "Critical"
        },
        "High": {
            "text": 2,
            "color": "#EF864C",
            "filterKey": "High"
        },
        "Medium": {
            "text": 1,
            "color": "#F6C564",
            "filterKey": "Medium"
        },
        "Low": {
            "text": 1,
            "color": "#6FD1A6",
            "filterKey": "Low"
        }
    }

    const accessTypeMap = {
        "Partner": {
            "text": 3,
            "color": "#147CF6",
            "filterKey": "Partner"
        },
        "Zombie": {
            "text": 2,
            "color": "#22BB97",
            "filterKey": "Zombie"
        },
        "Shadow": {
            "text": 1,
            "color": "#A278FF",
            "filterKey": "Shadow"
        },
        "Internal": {
            "text": 1,
            "color": "#FDB33D",
            "filterKey": "Internal"
        },
        "External": {
            "text": 1,
            "color": "#658EE2",
            "filterKey": "External"
        },
        "Third Party": {
            "text": 1,
            "color": "#68B3D0",
            "filterKey": "Third Party"
        }
    }

    const authMap = {
        "Unauthenticated": {
            "text": 3,
            "color": "#7F56D9",
            "filterKey": "Unauthenticated"
        },
        "JWT": {
            "text": 2,
            "color": "#9E77ED",
            "filterKey": "JWT"
        },
        "Bearer": {
            "text": 1,
            "color": "#B692F6",
            "filterKey": "Bearer"
        },
        "Basic": {
            "text": 1,
            "color": "#D6BBFB",
            "filterKey": "D6BBFB"
        },
        "Custom": {
            "text": 1,
            "color": "#E9D7FE",
            "filterKey": "Custom"
        }
    }

    const newDomains = ["ironman.demo.akto.io", "flash.akto.io", "app.akto.io", "cyborg.akto.io", "hulk.akto.io", "docs.akto.io"]

    const unsecuredAPIs = [
        {
            "data": [[1704067200000,200], [1706745600000,100] ,[1709251200000,120], [1711929600000,110], [1714521600000,90], [1717200000000,110], [1719792000000, 120], [1722470400000, 110], [1725148800000, 100], [1727740800000, 110], [1730419200000, 120], [1733011200000, 110]],
            "color": "#E45357",
        },
    ]

    const genreateDataTableRows = (collections) => {
        return collections.map((collection, index) => ([
            <HorizontalStack align='space-between'>
                <HorizontalStack gap={2}>
                    <Checkbox
                        key={index}
                        label={collection}
                        checked={false}
                        ariaDescribedBy={`checkbox-${index}`}
                        onChange={() => {}}
                    />
                    <Text color='subdued'>32% test coverage</Text>
                </HorizontalStack>
                <Text>22</Text>
            </HorizontalStack>
            ]
          ));
    }

    const criticalFindingsData = [
        {
            "data": [["BOLA",200], ["BUA",100] ,["MA",120], ["Misconfig",110], ["SSRF",90]],
            "color": "#E45357",
        },
    ]

    const apiTypesData = [
        {
            "data": [["REST",200], ["GraphQL", 0] ,["gRPC",10], ["SOAP", 0]],
            "color": "#D6BBFB",
        },
    ]

    const riskScoreData = [
        {"badgeValue": 5, "progressValue": "20%", "text" : 20, "topColor": "#E45357", "backgroundColor": "#FFDCDD"},
        {"badgeValue": 4, "progressValue": "40%", "text" : 40, "topColor": "#EF864C", "backgroundColor": "#FFD9C4"},
        {"badgeValue": 3, "progressValue": "0%", "text" : 0, "topColor": "#F6C564", "backgroundColor": "#FFF1D4"},
        {"badgeValue": 2, "progressValue": "40%", "text" : 40, "topColor": "#F5D8A1", "backgroundColor": "#FFF6E6"},
        {"badgeValue": 1, "progressValue": "0%", "text" : 0, "topColor": "#A4E8F2", "backgroundColor": "#EBFCFF"},
        {"badgeValue": 0, "progressValue": "0%", "text" : 0, "topColor": "#6FD1A6", "backgroundColor": "#E0FFF1"}
    ]

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
                    <ChartypeComponent data={severityMap} navUrl={"/dashboard/issues/"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true}/>
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
                    <ChartypeComponent data={accessTypeMap} navUrl={"/dashboard/observe/inventory"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true}/>
                }
                title="APIs by Access type"
                titleToolTip="APIs by Access type"
                linkText="Check out"
                linkUrl="/dashboard/observe/inventory"
            />
            <InfoCard 
                component={
                    <ChartypeComponent data={authMap} navUrl={"/dashboard/observe/inventory"} title={""} isNormal={true} boxHeight={'250px'} chartOnLeft={true}/>
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
            <PageWithMultipleCards
                title={
                    <Text variant='headingLg'>
                        Home
                    </Text>
                }
                isFirstPage={true}
                components={pageComponents}
            />
                
    )
}

export default HomeDashboard
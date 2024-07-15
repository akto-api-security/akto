import React, { useEffect, useState } from 'react'
import api from './api';
import func from '@/util/func';
import observeFunc from "../observe/transform"
import SummaryCardInfo from '../../components/shared/SummaryCardInfo';
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards"
import { Box, Card, HorizontalGrid, HorizontalStack, Scrollable, Text, VerticalStack } from '@shopify/polaris';
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

    const summaryInfo = [
        {
            title: 'Total APIs',
            data: observeFunc.formatNumberWithCommas(countInfo.totalUrls),
            variant: 'headingLg'
        },
        {
            title: 'Critical endpoints',
            data: observeFunc.formatNumberWithCommas(criticalUrls),
            variant: 'headingLg'
        },
        {
            title: 'Test coverage',
            data: countInfo.totalUrls === 0 ? "0%" : countInfo.coverage,
            variant: 'headingLg'
        },
        {
            title: 'Sensitive data',
            data: observeFunc.formatNumberWithCommas(sensitiveCount),
            variant: 'headingLg'
        }
    ]

    const summaryComp = (
        <SummaryCardInfo summaryItems={summaryInfo} key="summary"/>
    )

    const defaultChartOptions = {
        "legend": {
            enabled: false
        },
    }

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

    const components = [summaryComp, subcategoryInfoComp, riskScoreTrendComp, sensitiveDataTrendComp,  issuesTrendComp]

    const dashboardComp = (
        <div style={{display: 'flex', gap: '32px'}} key={"dashboardComp"}>
            <div style={{flex: 7}}>
                <VerticalStack gap={4}>
                    {components.map((component) => {
                        return component
                    })}
                </VerticalStack>
            </div>
            <div style={{flex: 3}}>
                <VerticalStack gap={5}>
                    <InitialSteps initialSteps={initialSteps}/>
                    <ActivityTracker collections={collectionsMap} latestActivity={recentActivities} onLoadMore={handleLoadMore} showLoadMore={checkLoadMore}/>
                    <CoverageCard coverageObj={coverageObj} collections={allCollections} collectionsMap={collectionsMap}/>
                    <Pipeline riskScoreMap={riskScoreObj} collections={allCollections} collectionsMap={collectionsMap}/> 
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
import React, { useEffect, useRef, useState } from 'react'
import api from './api';
import func from '@/util/func';
import observeFunc from "../observe/transform"
import SummaryCardInfo from '../../components/shared/SummaryCardInfo';
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards"
import { Badge, Box, Card, Divider, HorizontalGrid, HorizontalStack, Scrollable, Text, VerticalStack } from '@shopify/polaris';
import observeApi from "../observe/api"
import transform from './transform';
import StackedChart from '../../components/charts/StackedChart';
import HighchartsReact from 'highcharts-react-official';
import Highcharts from "highcharts"
import SpinnerCentered from '../../components/progress/SpinnerCentered';
import ChartypeComponent from '../testing/TestRunsPage/ChartypeComponent';
import testingApi from "../testing/api"
import testingFunc from "../testing/transform"
import InitialSteps from './components/InitialSteps';

function HomeDashboard() {

    const [riskScoreRangeMap, setRiskScoreRangeMap] = useState({});
    const [issuesTrendMap, setIssuesTrendMap] = useState({trend: [], allSubCategories: []});
    const [loading, setLoading] = useState(false) ;
    const [countInfo, setCountInfo] = useState({totalUrls: 0, coverage: '0%'})
    const [riskScoreObj, setRiskScoreObj]= useState({}) ;
    const [sensitiveArr, setSensitiveArr]= useState([]) ;
    const [sensitiveData, setSensitiveData] = useState({request: {}, response: {}})
    const [subCategoryInfo, setSubCategoryInfo] = useState({})

    const riskScoreTrendRef = useRef(null)

    const fetchData = async() =>{
        setLoading(true)
        // all apis 
        let apiPromises = [
            observeApi.getAllCollections(),
            observeApi.getCoverageInfoForCollections(),
            api.getRiskScoreRangeMap(),
            api.getIssuesTrend((func.timeNow() - func.recencyPeriod), func.timeNow()),
            api.fetchSubTypeCountMap(0 , func.timeNow()),
            testingApi.getSummaryInfo(0 , func.timeNow())
        ];
        
        let results = await Promise.allSettled(apiPromises);

        let apiCollectionsResp = results[0].status === 'fulfilled' ? results[0].value : {};
        let coverageInfo = results[1].status === 'fulfilled' ? results[1].value : {};
        let riskScoreRangeMap = results[2].status === 'fulfilled' ? results[2].value : {};
        let issuesTrendResp = results[3].status === 'fulfilled' ? results[3].value : {};
        let sensitiveDataResp = results[4].status === 'fulfilled' ? results[4].value : {} ;
        let subcategoryDataResp = results[4].status === 'fulfilled' ? results[5].value : {} ;

        setCountInfo(transform.getCountInfo((apiCollectionsResp?.apiCollections || []), coverageInfo))
        setRiskScoreRangeMap(riskScoreRangeMap);
        setIssuesTrendMap(transform.formatTrendData(issuesTrendResp));
        setSensitiveData(transform.getFormattedSensitiveData(sensitiveDataResp.response))
        setSubCategoryInfo(testingFunc.convertSubIntoSubcategory(subcategoryDataResp.subcategoryInfo))

        const riskScoreObj = (await observeFunc.fetchRiskScoreInfo()).riskScoreObj ;
        const sensitiveInfo = await observeFunc.fetchSensitiveInfo() ;
        setRiskScoreObj(riskScoreObj) ;
        setSensitiveArr(sensitiveInfo) ;

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
            data: observeFunc.formatNumberWithCommas(riskScoreObj.criticalUrls || 0),
            variant: 'headingLg'
        },
        {
            title: 'Test coverage',
            data: countInfo.coverage,
            variant: 'headingLg'
        },
        {
            title: 'Sensitive data',
            data: observeFunc.formatNumberWithCommas(transform.getSensitiveCount(sensitiveArr)),
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
        <Card key="subcategoryTrend">
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">Issues by category</Text>
                <ChartypeComponent data={subCategoryInfo} title={"Categories"} isNormal={true} boxHeight={'200px'}/>
            </VerticalStack>
        </Card>
    )

    const riskScoreRanges = [
        {
            text: "High risk",
            range: '4-5',
            status: "critical"
        },
        {
            text: 'Medium risk',
            range: '3-4',
            status: 'warning',
        },
        {
            text: "Low risk",
            range: '0-3',
            status: 'new',
        }
    ]

    const riskScoreTrendComp = (
        <Card key="scoreTrend">
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">APIS by risk score</Text>
                <HorizontalGrid columns={2} gap={5}>
                <HighchartsReact
                    highcharts={Highcharts}
                    options={transform.getRiskScoreTrendOptions(riskScoreRangeMap)}
                    ref={riskScoreTrendRef}
                />
                <Box paddingInlineEnd={4} paddingInlineStart={4} paddingBlockEnd={2} paddingBlockStart={2}>
                    <VerticalStack gap={3}>
                        {riskScoreRanges.map((range)=>{
                            return(
                                <VerticalStack gap={1} key={range.text}>
                                    <HorizontalStack align="space-between">
                                        <Text variant="bodyMd">{range.text}</Text>
                                        <Badge status={range.status}>{range.range}</Badge>
                                    </HorizontalStack>
                                    <Divider />
                                </VerticalStack>
                            )
                        })}
                    </VerticalStack>
                </Box>
                </HorizontalGrid>
            </VerticalStack>
        </Card>
    )

    const sensitiveDataTrendComp = (
        <Card key="sensitiveTrend">
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">Sensitive Data</Text>
                <HorizontalGrid gap={5} columns={2}>
                    <ChartypeComponent data={sensitiveData.request} title={"Request"} isNormal={true} boxHeight={'100px'}/>
                    <ChartypeComponent data={sensitiveData.response} title={"Response"} isNormal={true} boxHeight={'100px'}/>
                </HorizontalGrid>
            </VerticalStack>
        </Card>
    )

    const issuesTrendComp = (
        <Card key="issuesTrend">
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">Issues timeline</Text>
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

    )

    const components = [summaryComp, subcategoryInfoComp, riskScoreTrendComp, sensitiveDataTrendComp,  issuesTrendComp]

    return (
        <div style={{display: 'flex'}}>
            <div style={{flex: 7}}>
                <PageWithMultipleCards
                        title={
                            <Text variant='headingLg'>
                                Home
                            </Text>
                        }
                        isFirstPage={true}
                        components={components}
                />
            </div>
            <div style={{flex: 3}}>
                <InitialSteps />
            </div>
        </div>
    )
}

export default HomeDashboard
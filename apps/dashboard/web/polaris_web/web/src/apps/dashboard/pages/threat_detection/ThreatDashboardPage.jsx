import React, { useEffect, useReducer, useState, useCallback } from 'react'
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards"
import { Box, DataTable, HorizontalGrid, HorizontalStack, Icon, Text, VerticalStack, Badge } from '@shopify/polaris';
import SummaryCard from '../dashboard/new_components/SummaryCard';
import { ArrowUpMinor, ArrowDownMinor } from '@shopify/polaris-icons';
import InfoCard from '../dashboard/new_components/InfoCard';
import SpinnerCentered from '../../components/progress/SpinnerCentered';
import SmoothAreaChart from '../dashboard/new_components/SmoothChart'
import DateRangeFilter from '../../components/layouts/DateRangeFilter';
import { produce } from 'immer';
import func from '@/util/func';
import values from "@/util/values";
import ChartypeComponent from '../testing/TestRunsPage/ChartypeComponent';
import dummyData from './dummyData';
import observeFunc from '../observe/transform';
import ThreatWorldMap from './components/ThreatWorldMap';
import ThreatSankeyChart from './components/ThreatSankeyChart';
import ThreatCategoryStackedChart from './components/ThreatCategoryStackedChart';
import GetPrettifyEndpoint from '../observe/GetPrettifyEndpoint';
import api from './api';


function ThreatDashboardPage() {
    const [loading, setLoading] = useState(true);
    
    // Summary metrics state
    const [summaryMetrics, setSummaryMetrics] = useState({
        currentPeriod: {
            totalAnalysed: 0,
            totalAttacks: 0,
            criticalActors: 0,
            activeThreats: 0,
        },
        previousPeriod: {
            totalAnalysed: 0,
            totalAttacks: 0,
            criticalActors: 0,
            activeThreats: 0,
        }
    })


    // Chart and table data states
    const [severityDistribution, setSeverityDistribution] = useState({})
    const [threatStatusBreakdown, setThreatStatusBreakdown] = useState({})
    const [topAttackedHosts, setTopAttackedHosts] = useState([])
    const [topAttackedApis, setTopAttackedApis] = useState([])


    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[2]);


    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }
    
    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")


    const fetchData = useCallback(async () => {
        setLoading(true)

        try {
            // Row 1: Summary metrics - Use dummy data
            const summaryData = dummyData.getThreatSummaryData()
            setSummaryMetrics({
                currentPeriod: {
                    totalAnalysed: summaryData.totalAnalysed,
                    totalAttacks: summaryData.totalAttacks,
                    criticalActors: summaryData.criticalActors,
                    activeThreats: summaryData.totalActive,
                },
                previousPeriod: {
                    totalAnalysed: summaryData.oldTotalAnalysed,
                    totalAttacks: summaryData.oldTotalAttacks,
                    criticalActors: summaryData.oldCriticalActors,
                    activeThreats: summaryData.oldTotalActive,
                }
            })

            // Row 2: Sankey Chart and Map use APIs (handled in their components)
            
            // Row 3: Threat Status (dummy) and Severity Distribution (API)
            // Threat Status - Use dummy data
            const statusData = dummyData.getThreatStatusData()
            setThreatStatusBreakdown(statusData)

            // Severity Distribution - Use API
            try {
                const severityResponse = await api.fetchCountBySeverity(startTimestamp, endTimestamp)
                
                if (severityResponse?.categoryCounts && Array.isArray(severityResponse.categoryCounts)) {
                    const categoryCounts = severityResponse.categoryCounts
                    
                    const severityLevels = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
                    const severityColors = severityLevels.reduce((acc, s) => {
                        acc[s] = func.getHexColorForSeverity(s);
                        return acc;
                    }, {});

                    const formattedSeverity = {};

                    // Initialize all severities with 0
                    severityLevels.forEach((severity) => {
                        formattedSeverity[severity] = {
                            text: 0,
                            color: severityColors[severity],
                            filterKey: severity
                        };
                    });

                    categoryCounts.forEach(item => {
                        const raw = item.subCategory || item.severity || '';
                        const severity = String(raw).toUpperCase();
                        if (severity && severityColors[severity]) {
                            formattedSeverity[severity] = {
                                text: item.count || 0,
                                color: severityColors[severity],
                                filterKey: severity
                            };
                        }
                    });

                    setSeverityDistribution(formattedSeverity)
                } 
            } catch (err) {
                // Set empty state but keep structure for display
                const severityColors = {
                    "CRITICAL": "#E45357",
                    "HIGH": "#EF864C",
                    "MEDIUM": "#F6C564",
                    "LOW": "#6FD1A6"
                }
                const emptyFormattedSeverity = {}
                Object.keys(severityColors).forEach(severity => {
                    emptyFormattedSeverity[severity] = {
                        "text": 0,
                        "color": severityColors[severity],
                        "filterKey": severity
                    }
                })
                setSeverityDistribution(emptyFormattedSeverity)
            }

            // Row 4: Top Attacked Hosts and APIs - Use dummy data
            const hostsData = dummyData.getTopHostsData()
            setTopAttackedHosts(hostsData)

            const apisData = dummyData.getTopApisData()
            setTopAttackedApis(apisData)

        } catch (error) {
            // console.error('Error fetching threat detection data:', error)
            
            // Set empty states on error
            setSeverityDistribution({})
            setSummaryMetrics({
                currentPeriod: { totalAnalysed: 0, totalAttacks: 0, criticalActors: 0, activeThreats: 0 },
                previousPeriod: { totalAnalysed: 0, totalAttacks: 0, criticalActors: 0, activeThreats: 0 }
            })
            setThreatStatusBreakdown({})
            setTopAttackedHosts([])
            setTopAttackedApis([])
        } finally {
            setLoading(false)
        }
    }, [startTimestamp, endTimestamp])


    useEffect(() => {
        fetchData()
    }, [fetchData])


    function generateChangeIndicator(currentValue, previousValue) {
        if (!currentValue || !previousValue) return null
        const delta = currentValue - previousValue
        if (delta === 0) return null
        
        const icon = delta > 0 ? ArrowUpMinor : ArrowDownMinor
        const color = delta > 0 ? "success" : "critical"
        
        return (
            <HorizontalStack wrap={false}>
                <Icon source={icon} color={color} />
                <div className='custom-color'>
                    <Text color={color}>{Math.abs(delta)}</Text>
                </div>
            </HorizontalStack>
        )
    }


    const summaryCards = [
        {
            title: 'Total Analysed',
            data: observeFunc.formatNumberWithCommas(summaryMetrics.currentPeriod.totalAnalysed),
            variant: 'heading2xl',
            byLineComponent: generateChangeIndicator(
                summaryMetrics.currentPeriod.totalAnalysed, 
                summaryMetrics.previousPeriod.totalAnalysed
            ),
            smoothChartComponent: (<SmoothAreaChart tickPositions={[summaryMetrics.previousPeriod.totalAnalysed, summaryMetrics.currentPeriod.totalAnalysed]} />),
        },
        {
            title: 'Total Attacks',
            data: observeFunc.formatNumberWithCommas(summaryMetrics.currentPeriod.totalAttacks),
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: generateChangeIndicator(
                summaryMetrics.currentPeriod.totalAttacks, 
                summaryMetrics.previousPeriod.totalAttacks
            ),
            smoothChartComponent: (<SmoothAreaChart tickPositions={[summaryMetrics.previousPeriod.totalAttacks, summaryMetrics.currentPeriod.totalAttacks]} />),
        },
        {
            title: 'Critical Actors',
            data: observeFunc.formatNumberWithCommas(summaryMetrics.currentPeriod.criticalActors),
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: generateChangeIndicator(
                summaryMetrics.currentPeriod.criticalActors, 
                summaryMetrics.previousPeriod.criticalActors
            ),
            smoothChartComponent: (<SmoothAreaChart tickPositions={[summaryMetrics.previousPeriod.criticalActors, summaryMetrics.currentPeriod.criticalActors]} />),
        },
        {
            title: 'Active Threats',
            data: observeFunc.formatNumberWithCommas(summaryMetrics.currentPeriod.activeThreats),
            variant: 'heading2xl',
            color: 'warning',
            byLineComponent: generateChangeIndicator(
                summaryMetrics.currentPeriod.activeThreats, 
                summaryMetrics.previousPeriod.activeThreats
            ),
            smoothChartComponent: (<SmoothAreaChart tickPositions={[summaryMetrics.previousPeriod.activeThreats, summaryMetrics.currentPeriod.activeThreats]} />),
        }
    ]


    const summarySection = (
        <SummaryCard summaryItems={summaryCards} />
    )


    // Row 2: Threat Categories (Sankey Chart) and Threat Actor Map
    const threatCategoriesCard = (
        <ThreatSankeyChart
            startTimestamp={startTimestamp}
            endTimestamp={endTimestamp}
        />
    )


    const threatActorMapCard = (
        <ThreatWorldMap
            startTimestamp={startTimestamp}
            endTimestamp={endTimestamp}
            style={{
                width: "100%",
                marginRight: "auto",
            }}
            key={"threat-actor-world-map"}
        />
    )


    const row2Cards = (
        <HorizontalGrid gap={5} columns={2}>
            <div>{threatCategoriesCard}</div>
            <div>{threatActorMapCard}</div>
        </HorizontalGrid>
    )


    // Row 3: Threat Status and Threat Actors by Severity
    const threatStatusCard = (
        <InfoCard
            component={
                <div style={{ marginTop: "20px" }}>
                    <ChartypeComponent
                        data={threatStatusBreakdown}
                        navUrl="/dashboard/protection/threat-activity"
                        title=""
                        isNormal={true}
                        boxHeight={'250px'}
                        chartOnLeft={true}
                        dataTableWidth="250px"
                        boxPadding={0}
                        pieInnerSize="50%"
                    />
                </div>
            }
            title="Threat Status"
            titleToolTip="Distribution of threats by their current status"            
        />
    )


    const severityDistributionCard = (
        <InfoCard
            component={
                <div style={{ marginTop: "20px" }}>
                    <ChartypeComponent
                        data={severityDistribution}
                        navUrl="/dashboard/protection/threat-activity"
                        title=""
                        isNormal={true}
                        boxHeight={'250px'}
                        chartOnLeft={true}
                        dataTableWidth="250px"
                        boxPadding={0}
                        pieInnerSize="50%"
                    />
                </div>
            }
            title="Threat Actors by Severity"
            titleToolTip="Distribution of threat actors categorized by severity level"
        />
    )


    const row5Cards = (
        <HorizontalGrid gap={5} columns={2}>
            <div>{threatStatusCard}</div>
            <div>{severityDistributionCard}</div>
        </HorizontalGrid>
    )


    // Row 4: Top Attacked Hosts and Top Attacked APIs
    const generateHostTableRows = (hosts) => {
        return hosts.map((host) => ([
            <Text variant='bodyMd'>{host.host}</Text>,
            <Text variant='bodySm' alignment='end'>{host.attacks}</Text>,
            <Text variant='bodySm' alignment='end'>{host.apis}</Text>
        ]))
    }


    const generateApiTableRows = (apis) => {
        return apis.map((api) => ([
            <Box maxWidth='400px'>
                <GetPrettifyEndpoint method={api.method} url={api.endpoint} isNew={false} />
            </Box>,
            <div style={{ textAlign: 'center' }}>
                <Text variant='bodySm'>{api.attacks}</Text>
            </div>,
            <div style={{ display: 'flex', justifyContent: 'center' }}>
                <div className={`badge-wrapper-${api.severity?.toUpperCase() || 'MEDIUM'}`}>
                    <Badge>
                        {api.severity}
                    </Badge>
                </div>
            </div>
        ]))
    }


    const topHostsCard = (
        <InfoCard
            component={
                <Box>
                    <DataTable
                        columnContentTypes={['text', 'numeric', 'numeric']}
                        headings={['Host', 'Attacks', 'APIs']}
                        rows={generateHostTableRows(topAttackedHosts)}
                        hoverable={false}
                        increasedTableDensity
                    />
                </Box>
            }
            title="Top Attacked Hosts"
            titleToolTip="Most targeted hosts by attack volume"
        />
    )


    const topApisCard = (
        <InfoCard
            component={ 
                <Box>
                    <DataTable
                        columnContentTypes={['text', 'text', 'text']}
                        headings={['API Endpoint', 'Attacks', 'Severity']}
                        rows={generateApiTableRows(topAttackedApis)}
                        hoverable={false}
                        increasedTableDensity
                    />
                </Box>
            }
            title="Top Attacked APIs"
            titleToolTip="Most targeted API endpoints"
        />
    )


    const row4Cards = (
        <HorizontalGrid gap={5} columns={2}>
            <div>{topHostsCard}</div>
            <div>{topApisCard}</div>
        </HorizontalGrid>
    )

    // Row 3: Stacked category breakdown (uses same API as Sankey)
    const row3Cards = (
        <div>
            <ThreatCategoryStackedChart startTimestamp={startTimestamp} endTimestamp={endTimestamp} />
        </div>
    )

    const dashboardRows = [
        {id: 'summary', component: summarySection},
        {id: 'row2', component: row2Cards},
        {id: 'row3', component: row3Cards},
        {id: 'row4', component: row4Cards},
        {id: 'row5', component: row5Cards},
    ]


    const dashboardContent = (
        <VerticalStack gap={4}>
            {dashboardRows.map(({id, component}) => (
                <div key={id}>{component}</div>
            ))}
        </VerticalStack>
    )


    const pageContent = [dashboardContent]


  return (
        <Box>
            {loading ? <SpinnerCentered /> :
                <PageWithMultipleCards
                    title={
                        <Text variant='headingLg'>
                            Threat Detection Dashboard
                        </Text>
                    }
                    isFirstPage={true}
                    components={pageContent}
                    primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
                />
            }
        </Box>
  )
}


export default ThreatDashboardPage
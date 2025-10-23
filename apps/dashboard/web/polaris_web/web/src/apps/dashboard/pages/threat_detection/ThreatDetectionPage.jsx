import { useReducer, useState, useEffect, useCallback } from "react";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import SusDataTable from "./components/SusDataTable";
import values from "@/util/values";
import { produce } from "immer"
import func from "@/util/func";
import SampleDetails from "./components/SampleDetails";
import threatDetectionRequests from "./api";
import tempFunc from "./dummyData";
import NormalSampleDetails from "./components/NormalSampleDetails";
import { HorizontalGrid, VerticalStack, HorizontalStack, Popover, Button, ActionList, Box, Icon, Badge, Text} from "@shopify/polaris";
import { FileMinor } from '@shopify/polaris-icons';
import TopThreatTypeChart from "./components/TopThreatTypeChart";
import api from "./api";
import threatDetectionFunc from "./transform";
import InfoCard from "../dashboard/new_components/InfoCard";
import BarGraph from "../../components/charts/BarGraph";
import SessionStore from "../../../main/SessionStore";
import { getDashboardCategory, isApiSecurityCategory, mapLabel } from "../../../main/labelHelper";
import { useNavigate } from "react-router-dom";
import LineChart from "../../components/charts/LineChart";
import P95LatencyGraph from "../../components/charts/P95LatencyGraph";
import { LABELS } from "./constants";

const convertToGraphData = (severityMap) => {
    let dataArr = []
    Object.keys(severityMap).forEach((x) => {
        const color = func.getHexColorForSeverity(x)
        let text = func.toSentenceCase(x)
        const value =  severityMap[x]
        dataArr.push({
            text, value, color
        })
    })
    return dataArr
}

const directionData = [
    {
        name: 'Request',
        data: [
            [1722556800000, 12], // Aug 1
            [1722816000000, 8],  // Aug 4
            [1723161600000, 15], // Aug 8
            [1723507200000, 11], // Aug 12
            [1723852800000, 18], // Aug 16
            [1724198400000, 7],  // Aug 20
            [1724544000000, 14], // Aug 24
            [1724889600000, 9],  // Aug 28
            [1725235200000, 16], // Sept 1
            [1725580800000, 13], // Sept 5
            [1725926400000, 6],  // Sept 9
            [1726272000000, 19], // Sept 13
            [1726617600000, 10], // Sept 17
            [1726963200000, 17], // Sept 21
            [1727308800000, 8],  // Sept 25
            [1727654400000, 15], // Sept 29
        ],
        color: '#6200EA'
    },
    {
        name: 'Response',
        data: [
            [1722556800000, 9],  // Aug 1
            [1722816000000, 14], // Aug 4
            [1723161600000, 7],  // Aug 8
            [1723507200000, 16], // Aug 12
            [1723852800000, 11], // Aug 16
            [1724198400000, 18], // Aug 20
            [1724544000000, 6],  // Aug 24
            [1724889600000, 13], // Aug 28
            [1725235200000, 8],  // Sept 1
            [1725580800000, 17], // Sept 5
            [1725926400000, 12], // Sept 9
            [1726272000000, 5],  // Sept 13
            [1726617600000, 15], // Sept 17
            [1726963200000, 9],  // Sept 21
            [1727308800000, 19], // Sept 25
            [1727654400000, 10], // Sept 29
        ],
        color: '#AF6CF6'
    }
]

const flaggedData = [
    {
        name: 'Safe',
        data: [
            [1722556800000, 53], // Aug 1
            [1722816000000, 76], // Aug 4
            [1723161600000, 56], // Aug 8
            [1723507200000, 51], // Aug 12
            [1723852800000, 57], // Aug 16
            [1724198400000, 75], // Aug 20
            [1724544000000, 50], // Aug 24
            [1724889600000, 76], // Aug 28
            [1725235200000, 72], // Sept 1
            [1725580800000, 70], // Sept 5
            [1725926400000, 74], // Sept 9
            [1726272000000, 32], // Sept 13
            [1726617600000, 85], // Sept 17
            [1726963200000, 88], // Sept 21
            [1727308800000, 81], // Sept 25
            [1727654400000, 95], // Sept 29
        ],
        color: '#AEE9D1'
    },
    {
        name: 'Flagged',
        data: [
            [1722556800000, 21], // Aug 1 (12+9 from directionData)
            [1722816000000, 22], // Aug 4 (8+14)
            [1723161600000, 22], // Aug 8 (15+7)
            [1723507200000, 27], // Aug 12 (11+16)
            [1723852800000, 29], // Aug 16 (18+11)
            [1724198400000, 25], // Aug 20 (7+18)
            [1724544000000, 20], // Aug 24 (14+6)
            [1724889600000, 22], // Aug 28 (9+13)
            [1725235200000, 24], // Sept 1 (16+8)
            [1725580800000, 30], // Sept 5 (13+17)
            [1725926400000, 18], // Sept 9 (6+12)
            [1726272000000, 24], // Sept 13 (19+5)
            [1726617600000, 25], // Sept 17 (10+15)
            [1726963200000, 26], // Sept 21 (17+9)
            [1727308800000, 27], // Sept 25 (8+19)
            [1727654400000, 25], // Sept 29 (15+10)
        ],
        color: '#E45357'
    }
]

const ChartComponent = ({ subCategoryCount, severityCountMap }) => {
    return (
      <VerticalStack gap={4} columns={2}>
        <HorizontalGrid gap={4} columns={2}>
          <TopThreatTypeChart
            key={"top-threat-types"}
            data={subCategoryCount}
          />
          <InfoCard
                title={"Threats by severity"}
                titleToolTip={`Number of ${mapLabel("APIs", getDashboardCategory())} per each category`}
                component={
                    <BarGraph
                        data={severityCountMap}
                        areaFillHex="true"
                        height={"280px"}
                        defaultChartOptions={{
                            "legend": {
                                enabled: false
                            },
                        }}
                        showYAxis={true}
                        yAxisTitle={`Number of ${mapLabel("APIs", getDashboardCategory())}`}
                        showGridLines={true}
                        barWidth={100 - (severityCountMap.length * 6)}
                        barGap={12}
                    />
                }
            />
        </HorizontalGrid>
        {
            func.isDemoAccount() && !isApiSecurityCategory() ? <HorizontalGrid gap={4} columns={2}>
                <InfoCard
                    title={`Threat Messages by Direction (Request/Response)`}
                    titleToolTip="Number of threat messages found in requests vs responses over time"
                    component={
                        <LineChart
                            data={directionData}
                            exportingDisabled={true}
                            height={280}
                            yAxisTitle={`Number of messages`}
                            type="line"
                            text={true}
                            showGridLines={true}
                        />
                    }
                />
                <InfoCard
                    title={`Flagged/Safe messages`}
                    titleToolTip="Number of messages flagged as threats vs those marked safe over time"
                    component={
                        <LineChart
                            data={flaggedData}
                            exportingDisabled={true}
                            height={280}
                            yAxisTitle={`Number of messages`}
                            type="line"
                            text={true}
                            showGridLines={true}
                        />
                    }
                />
            </HorizontalGrid> : <></>
        }
      </VerticalStack>
    );
  };

function ThreatDetectionPage() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [currentRefId, setCurrentRefId] = useState('')
    const [rowDataList, setRowDataList] = useState([])
    const [moreInfoData, setMoreInfoData] = useState({})
    const [currentEventId, setCurrentEventId] = useState('')
    const [currentEventStatus, setCurrentEventStatus] = useState('')
    const [triggerTableRefresh, setTriggerTableRefresh] = useState(0)
    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const [showDetails, setShowDetails] = useState(false);
    const [sampleData, setSampleData] = useState([])
    const [showNewTab, setShowNewTab] = useState(false)
    const [subCategoryCount, setSubCategoryCount] = useState([]);
    const [severityCountMap, setSeverityCountMap] = useState([]);
    const [moreActions, setMoreActions] = useState(false);
    const [latencyData, setLatencyData] = useState([]);

    const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);

    const startTimestamp = parseInt(currDateRange.period.since.getTime()/1000)
    const endTimestamp = parseInt(currDateRange.period.until.getTime()/1000)

    const isDemoMode = func.isDemoAccount();

    /**
     * Generate deterministic latency data for demo mode
     * Creates 2 months of data with 3-day intervals for consistent demo experience
     * @returns {Array} Array of latency data points
     */
    const generateLatencyData = useCallback(() => {
        try {
            const now = Date.now();
            const data = [];
            const totalDays = 60; // 2 months
            const intervalDays = 3; // Every 3 days
            const dataPoints = Math.floor(totalDays / intervalDays) + 1; // +1 to include today
            
            // Predefined latency values for consistent demo data (total 20-30ms)
            const latencyValues = [
                { incoming: 12.5, output: 8.2, total: 21.4 },
                { incoming: 15.3, output: 9.8, total: 25.7 },
                { incoming: 11.7, output: 7.4, total: 20.3 },
                { incoming: 10.3, output: 16.1, total: 27.9 },
                { incoming: 13.8, output: 8.6, total: 23.1 },
                { incoming: 14.6, output: 9.9, total: 25.5 },
                { incoming: 12.2, output: 7.5, total: 20.8 },
                { incoming: 10.6, output: 15.6, total: 26.2 },
                { incoming: 16.4, output: 11.8, total: 29.2 },
                { incoming: 13.1, output: 9.3, total: 23.7 },
                { incoming: 11.9, output: 14.2, total: 27.1 },
                { incoming: 15.7, output: 8.9, total: 25.8 },
                { incoming: 12.8, output: 10.4, total: 24.3 },
                { incoming: 14.2, output: 12.1, total: 27.6 },
                { incoming: 11.4, output: 13.7, total: 26.4 },
                { incoming: 16.1, output: 9.5, total: 27.2 },
                { incoming: 13.5, output: 11.2, total: 25.8 },
                { incoming: 15.8, output: 9.6, total: 26.7 },
                { incoming: 12.1, output: 14.8, total: 28.1 },
                { incoming: 14.9, output: 8.7, total: 24.9 }
            ];
            
            for (let i = 0; i < dataPoints; i++) {
                const daysAgo = i * intervalDays;
                const timestamp = now - (daysAgo * 24 * 60 * 60 * 1000);
                
                // Use deterministic values based on data point index
                const latencyIndex = i % latencyValues.length;
                const latency = latencyValues[latencyIndex];
                
                data.push({
                    timestamp: Math.floor(timestamp / 1000), // Convert to seconds
                    incomingRequestP95: latency.incoming,
                    outputResultP95: latency.output,
                    totalP95: latency.total
                });
            }
            
            return data.sort((a, b) => a.timestamp - b.timestamp);
        } catch (error) {
            console.error('Error generating latency data:', error);
            return [];
        }
    }, []);

    /**
     * Handle latency click events from the P95LatencyGraph component
     * @param {string} latencyType - Type of latency clicked (incoming, output, total)
     */
    const handleLatencyClick = useCallback((latencyType) => {
        // In production, this could trigger analytics events or navigate to detailed views
        // For now, we'll just log the event for debugging purposes
        if (process.env.NODE_ENV === 'development') {
            console.log('Latency clicked:', latencyType);
        }
    }, []);

    const rowClicked = async(data) => {
        if(data?.refId === undefined || data?.refId.length === 0){
            const tempData = tempFunc.getSampleDataOfUrl(data.url);
            const sameRow = func.deepComparison(tempData, sampleData);
            if (!sameRow) {
                setSampleData([{"message": JSON.stringify(tempData),  "highlightPaths": []}])
                setShowDetails(true)
            } else {
                setShowDetails(!showDetails)
            }
            setShowNewTab(false)
        }else{
            setShowNewTab(true)
            const sameRow = currentRefId === data?.refId
            if (!sameRow) {
                let rowData = [];
                await threatDetectionRequests.fetchMaliciousRequest(data?.refId, data?.eventType, data?.actor, data?.filterId).then((res) => {
                    rowData = [...res.maliciousPayloadsResponses]
                }) 
                setRowDataList(rowData)
                setCurrentRefId(data?.refId)
                setCurrentEventId(data?.id)
                setCurrentEventStatus(data?.status || '')
                setShowDetails(true)
                setMoreInfoData({
                    url: data.url,
                    method: data.method,
                    apiCollectionId: data.apiCollectionId,
                    templateId: data.filterId,
                })
            } else {
                setShowDetails(!showDetails)
            }
        }
        
      }

    const handleStatusUpdate = (newStatus) => {
        setCurrentEventStatus(newStatus)
        // Force table refresh by incrementing the trigger
        setTriggerTableRefresh(prev => prev + 1)
    }

      useEffect(() => {
        const fetchThreatCategoryCount = async () => {
            setLoading(true);
            const res = await api.fetchThreatCategoryCount(startTimestamp, endTimestamp);
            const finalObj = threatDetectionFunc.getGraphsData(res);
            setSubCategoryCount(finalObj.subCategoryCount);
            setLoading(false);
          };

          const fetchCountBySeverity = async () => {
            setLoading(true);
            let severityMap = {
                CRITICAL: 0,
                HIGH: 0,
                MEDIUM: 0,
                LOW: 0,
            }
            const res = await api.fetchCountBySeverity(startTimestamp, endTimestamp);
            res.categoryCounts.forEach(({ subCategory, count }) => {
                severityMap[subCategory] = count;
            });
            setSeverityCountMap(convertToGraphData(severityMap));
            setLoading(false);
        };

        fetchThreatCategoryCount();
        fetchCountBySeverity();
        
        // Generate latency data for demo mode
        if (isDemoMode) {
            try {
                const latency = generateLatencyData();
                setLatencyData(latency);
            } catch (error) {
                console.error('Error generating demo latency data:', error);
                setLatencyData([]);
            }
        }
      }, [startTimestamp, endTimestamp, isDemoMode]);

    const components = [
        <ChartComponent subCategoryCount={subCategoryCount} severityCountMap={severityCountMap} />,
        // Add P95 latency graphs for MCP and AI Agent security in demo mode
        ...(isDemoMode && !isApiSecurityCategory() ? [
            <P95LatencyGraph
                key="threat-detection-latency"
                title="Threat Detection Latency"
                subtitle="95th percentile latency metrics for threat-detection"
                dataType="threat-security"
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
                onLatencyClick={(latencyType) => console.log('Latency clicked:', latencyType)}
                latencyData={latencyData}
            />
        ] : []),
        <SusDataTable key={`sus-data-table-${triggerTableRefresh}`}
            currDateRange={currDateRange}
            rowClicked={rowClicked}
            triggerRefresh={() => setTriggerTableRefresh(prev => prev + 1)}
        />,
        !showNewTab ? <NormalSampleDetails
            title={"Attacker payload"}
            showDetails={showDetails}
            setShowDetails={setShowDetails}
            sampleData={sampleData}
            key={"sus-sample-details"}
        /> :  <SampleDetails
                title={"Attacker payload"}
                showDetails={showDetails}
                setShowDetails={setShowDetails}
                data={rowDataList}
                key={"sus-sample-details"}
                moreInfoData={moreInfoData}
                threatFiltersMap={threatFiltersMap}
                eventId={currentEventId}
                eventStatus={currentEventStatus}
                onStatusUpdate={handleStatusUpdate}
            />
            

    ]

    const exportJson = async () => {
        const jsonFileName = "malicious_events.json"
        const res = await api.fetchSuspectSampleData(
            0,
            [],
            [],
            [],
            [],
            {detectedAt: -1},
            startTimestamp,
            endTimestamp,
            [],
            2000,
            'EVENTS',
            null,
            LABELS.THREAT // Filter for threat protection (not guardrail)
        );
        // Transform to match the mongoDB format
        let jsonData = (res?.maliciousEvents || []).map(ev => ({
            _id: ev.id, // or whatever unique id you have
            actor: ev.actor,
            category: ev.category,
            country: ev.country,
            detectedAt: { $numberLong: String(ev.timestamp) || String(ev.detectedAt) },
            eventType: ev.eventType,
            filterId: ev.filterId,
            latestApiCollectionId: ev.apiCollectionId || ev.latestApiCollectionId,
            latestApiEndpoint: ev.url || ev.latestApiEndpoint,
            latestApiIp: ev.ip || ev.latestApiIp,
            latestApiMethod: ev.method || ev.latestApiMethod,
            subCategory: ev.subCategory,
            type: ev.type,
            refId: ev.refId,
            severity: ev.severity,
            latestApiOrig: ev.payload || ev.latestApiOrig,
            metadata: ev.metadata,
        }));

        let blob = new Blob([JSON.stringify(jsonData, null, 2)], {
            type: "application/json;charset=UTF-8"
        });
        saveAs(blob, jsonFileName);
        func.setToast(true, false, "JSON exported successfully");
    }

    const secondaryActionsComp = (
        <HorizontalStack gap={2}>
            <Popover
                active={moreActions}
                activator={(
                    <Button onClick={() => setMoreActions(!moreActions)} disclosure removeUnderline>
                        More Actions
                    </Button>
                )}
                autofocusTarget="first-node"
                onClose={() => { setMoreActions(false) }}
            >
                <Popover.Pane fixed>
                    <ActionList
                        actionRole="menuitem"
                        sections={
                            [
                                {
                                    title: 'Export',
                                    items: [
                                        {
                                            content: 'Export',
                                            onAction: () => exportJson(),
                                            prefix: <Box><Icon source={FileMinor} /></Box>
                                        },
                                        {
                                            content: 'Configure Successful Exploits',
                                            onAction: () => navigate('/dashboard/protection/configure-exploits'),
                                            prefix: <Box><Icon source={FileMinor} /></Box>
                                        }
                                    ]
                                },
                            ]
                        }
                    />
                </Popover.Pane>
            </Popover>
        </HorizontalStack>
    )
    return <PageWithMultipleCards
        title={
            <TitleWithInfo
                titleText={mapLabel("API Threat Activity", getDashboardCategory())}
                tooltipContent={"Identify malicious requests with Akto's powerful threat detection capabilities"}
            />
        }
        isFirstPage={true}
        primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
        components={components}
        secondaryActions={secondaryActionsComp}
    />
}

export default ThreatDetectionPage;
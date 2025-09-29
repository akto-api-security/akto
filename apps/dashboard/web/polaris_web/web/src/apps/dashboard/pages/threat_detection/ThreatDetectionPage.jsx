import { useReducer, useState, useEffect } from "react";
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
import { HorizontalGrid, VerticalStack, HorizontalStack, Popover, Button, ActionList, Box, Icon} from "@shopify/polaris";
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
            [1725076800000, 12], // Sept 1
            [1725336000000, 8],  // Sept 4
            [1725681600000, 15], // Sept 8
            [1726027200000, 11], // Sept 12
            [1726372800000, 18], // Sept 16
            [1726718400000, 7],  // Sept 20
            [1727064000000, 14], // Sept 24
            [1727409600000, 9],  // Sept 28
            [1727755200000, 16], // Oct 2
            [1728100800000, 13], // Oct 6
            [1728446400000, 6],  // Oct 10
            [1728792000000, 19], // Oct 14
            [1729137600000, 10], // Oct 18
            [1729483200000, 17], // Oct 22
            [1729828800000, 8],  // Oct 26
            [1730174400000, 15], // Oct 30
        ],
        color: '#6200EA' 
    },
    {
        name: 'Response',
        data: [
            [1725076800000, 9],  // Sept 1
            [1725336000000, 14], // Sept 4
            [1725681600000, 7],  // Sept 8
            [1726027200000, 16], // Sept 12
            [1726372800000, 11], // Sept 16
            [1726718400000, 18], // Sept 20
            [1727064000000, 6],  // Sept 24
            [1727409600000, 13], // Sept 28
            [1727755200000, 8],  // Oct 2
            [1728100800000, 17], // Oct 6
            [1728446400000, 12], // Oct 10
            [1728792000000, 5],  // Oct 14
            [1729137600000, 15], // Oct 18
            [1729483200000, 9],  // Oct 22
            [1729828800000, 19], // Oct 26
            [1730174400000, 10], // Oct 30
        ],
        color: '#AF6CF6' 
    }
]

const flaggedData = [
    {
        name: 'Safe',
        data: [
            [1725076800000, 53], // Sept 1
            [1725336000000, 76], // Sept 4
            [1725681600000, 56], // Sept 8
            [1726027200000, 51], // Sept 12
            [1726372800000, 57], // Sept 16
            [1726718400000, 75], // Sept 20
            [1727064000000, 50], // Sept 24
            [1727409600000, 76], // Sept 28
            [1727755200000, 72], // Oct 2
            [1728100800000, 70], // Oct 6
            [1728446400000, 74], // Oct 10
            [1728792000000, 32], // Oct 14
            [1729137600000, 85], // Oct 18
            [1729483200000, 88], // Oct 22
            [1729828800000, 81], // Oct 26
            [1730174400000, 95], // Oct 30
        ],
        color: '#AEE9D1' 
    },
    {
        name: 'Flagged',
        data: [
            [1725076800000, 21], // Sept 1 (12+9 from directionData)
            [1725336000000, 22], // Sept 4 (8+14)
            [1725681600000, 22], // Sept 8 (15+7)
            [1726027200000, 27], // Sept 12 (11+16)
            [1726372800000, 29], // Sept 16 (18+11)
            [1726718400000, 25], // Sept 20 (7+18)
            [1727064000000, 20], // Sept 24 (14+6)
            [1727409600000, 22], // Sept 28 (9+13)
            [1727755200000, 24], // Oct 2 (16+8)
            [1728100800000, 30], // Oct 6 (13+17)
            [1728446400000, 18], // Oct 10 (6+12)
            [1728792000000, 24], // Oct 14 (19+5)
            [1729137600000, 25], // Oct 18 (10+15)
            [1729483200000, 26], // Oct 22 (17+9)
            [1729828800000, 27], // Oct 26 (8+19)
            [1730174400000, 25], // Oct 30 (15+10)
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

    const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);

    const startTimestamp = parseInt(currDateRange.period.since.getTime()/1000)
    const endTimestamp = parseInt(currDateRange.period.until.getTime()/1000)

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
      }, [startTimestamp, endTimestamp]);

    const components = [
        <ChartComponent subCategoryCount={subCategoryCount} severityCountMap={severityCountMap} />,
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
            'EVENTS'
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
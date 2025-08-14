import { Card, Divider, Text, VerticalStack } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { useEffect, useReducer, useState } from "react";
import api from "../api";
import func from "@/util/func";
import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import transform from "../transform";
import {produce} from "immer"
import values from "@/util/values";
import ObserveStore from "../observeStore";
import ApiDetails from "./ApiDetails";
import PersistStore from "../../../../main/PersistStore";
import ApiChangesTable from "./component/ApiChangesTable";
import SummaryCardInfo from "../../../components/shared/SummaryCardInfo";
import LineChart from "../../../components/charts/LineChart";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import { useLocation } from "react-router-dom";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";


function ApiChanges() {

    const allCollections = PersistStore(state => state.allCollections);
    const [parametersTrend, setParametersTrend] = useState([])
    const [endpointsTrend, setEndpointsTrend] = useState([])
    const [loading, setLoading] = useState(false);
    const [apiDetail, setApiDetail] = useState({})
    const [newParams, setNewParams] = useState([])
    const [tableHeaders,setTableHeaders] = useState([])
    const [summaryCountObj,setSummaryCountObj] = useState({
        "newEndpointsCount": 0,
        "sensitiveEndpointsCount": 0,
        "newParamsCount": 0,
        "sensitiveParamsCount": 0,
    })

    const location = useLocation()
    const showDetails = ObserveStore(state => state.inventoryFlyout)
    const setShowDetails = ObserveStore(state => state.setInventoryFlyout)
    
    const initialVal = (location.state) ?  { alias: "recencyPeriod", title : (new Date((location.state.timestamp - 5* 60 )*1000)).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: '2-digit', year: 'numeric' }).replace(/,/g, '') ,  period : {since: new Date((location.state.timestamp - 5* 60 )*1000), until: new Date((location.state.timestamp + 5* 60 )*1000)} } : values.ranges[3]

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = (location.state)? (location.state.timestamp - 25*60) : getTimeEpoch("since")
    const endTimestamp = (location.state)? (location.state.timestamp + 25*60) : getTimeEpoch("until")
    
    function handleRowClick(data,headers) {
        const sameRow = func.deepComparison(apiDetail, data);
        let flag = !sameRow || !showDetails
        setShowDetails(flag)
        setTableHeaders((prev) => {
            if(func.deepComparison(headers,tableHeaders)){
                return prev
            }
            return headers
        })
        setApiDetail((prev) => {
            if (sameRow) {
                return prev;
            }
            return { ...data }
        })
    }

    async function fetchData() {
        setLoading(true)
        let apiPromises = [
            api.getSummaryInfoForChanges(startTimestamp, endTimestamp),
            api.fetchNewParametersTrend(startTimestamp, endTimestamp),
            api.fetchNewEndpointsTrendForHostCollections(startTimestamp, endTimestamp),
            api.fetchNewEndpointsTrendForNonHostCollections(startTimestamp, endTimestamp)
        ];
        let results = await Promise.allSettled(apiPromises);
        let countSummaryResp = results[0].status === 'fulfilled' ? results[0].value : {}
        let parametersResp = results[1].status === 'fulfilled' ? results[1].value : {}
        let hostTrend = results[2].status === 'fulfilled' ? results[2].value : {}
        let nonHostTrend = results[3].status === 'fulfilled' ? results[3].value : {}

        setSummaryCountObj((prev) => {
            return{
                ...prev,
                newEndpointsCount:countSummaryResp.newEndpointsCount,
                sensitiveEndpointsCount:countSummaryResp.sensitiveEndpointsCount,
            }
        })

        const mergedArrObj = Object.values([...(hostTrend?.data?.endpoints || []), ...(nonHostTrend?.data?.endpoints || [])].reduce((acc, item) => {
            acc[item._id] = acc[item._id] || { _id: item._id, count: 0 };
            acc[item._id].count += item.count;
            return acc;
        }, {}));

        const trendObj = transform.findNewParametersCountTrend(parametersResp, startTimestamp, endTimestamp)
        setParametersTrend(trendObj.trend)

        const endpointsTrendObj = transform.findNewParametersCountTrend(mergedArrObj, startTimestamp, endTimestamp)
        setLoading(false)
        setEndpointsTrend(endpointsTrendObj.trend)
        await api.fetchRecentParams(startTimestamp, endTimestamp).then((res) => {
            const ret = res.data.endpoints.map((x,index) => transform.prepareEndpointForTable(x,index));
            setNewParams(ret)
            setSummaryCountObj((prev) => {
                return{
                    ...prev,
                    newParamsCount: ret.length
                }
            })
        })
    }

    useEffect(() => {
        if (allCollections.length > 0) {
            fetchData();
        }
    }, [allCollections, currDateRange])

    const infoItems = [
        {
            title: mapLabel("New endpoints", getDashboardCategory()),
            isComp: true,
            data: <div data-testid="new_endpoints_count" style={{fontWeight: 600, color: '#1F2124', fontSize: '14px'}}>{summaryCountObj.newEndpointsCount}</div>,
        },
        {
            title: mapLabel("New sensitive endpoints", getDashboardCategory()),
            data: transform.formatNumberWithCommas(summaryCountObj.sensitiveEndpointsCount),
            color: "critical",
        },
        {
            title: "New parameters",
            data: transform.formatNumberWithCommas(summaryCountObj.newParamsCount)
        },
        {
            title: "New sensitive parameters",
            data: transform.formatNumberWithCommas(summaryCountObj.sensitiveParamsCount),
            color: "critical",
        }
    ]
    const tableComponent = (
        <ApiChangesTable
            handleRowClick={handleRowClick}
            tableLoading={loading}
            startTimeStamp={startTimestamp}
            endTimeStamp={endTimestamp}
            key="table"
            tab={(location.state)?(location.state.tab):0 }
            newParams={newParams}
        />
    )

    const apiChanges = (
        <ApiDetails
            key="details"
            showDetails={showDetails}
            setShowDetails={setShowDetails}
            apiDetail={apiDetail}
            headers={tableHeaders}
        />
    )

    const graphPointClick = ({ point }) => {
        const dateObj = { alias: "custom", title : (new Date((point.x +  (5 * 60 * 1000) ))).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: '2-digit', year: 'numeric' }).replace(/,/g, '') ,  period : {since: new Date((point.x - ( 24 * 60 * 60 * 1000) )), until: new Date((point.x + (24 * 60 * 60 * 1000) ))} }
        dispatchCurrDateRange({ type: "update", period:{period: dateObj.period, title: dateObj.title, alias: dateObj.alias }})
    }

    const processChartData = () => {
        return [
            {
                data: endpointsTrend,
                color: "#61affe",
                name: mapLabel("New endpoints", getDashboardCategory())
            },
            {
                data: parametersTrend,
                color: "#fca130",
                name: "Parameters detected"
            }
        ]
    }
    const defaultChartOptions = {
        "legend": {
            enabled: true
        },
    }

    const graphComponent = (
        <Card key={"graphComponent"}>
            <VerticalStack gap={4}>
                <VerticalStack gap={3}>
                    <Text variant="bodyMd" fontWeight="medium">Changes</Text>
                    <Divider />
                </VerticalStack>
                <VerticalStack gap={2}>
                    <LineChart
                        key={`trend-chart`}
                        type='line'
                        color='#6200EA'
                        areaFillHex="true"
                        height="280"
                        background-color="#ffffff"
                        data={processChartData()}
                        defaultChartOptions={defaultChartOptions}
                        text="true"
                        yAxisTitle="Count"
                        width={20}
                        noGap={true}
                        graphPointClick={graphPointClick}
                    />
                </VerticalStack>
            </VerticalStack>
        </Card>
    )

    const components = [<SummaryCardInfo summaryItems={infoItems} key="infoCard" />, graphComponent, tableComponent, apiChanges]

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    titleText={mapLabel("API changes", getDashboardCategory())}
                    docsUrl={"https://docs.akto.io/api-inventory/concepts/api-changes"}
                    tooltipContent={"Information about endpoints and parameters found in your inventory."}
                />
            }
            isFirstPage={true}
            primaryAction={<DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>}
            components={components}
        />

    )
}

export default ApiChanges
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


function ApiChanges() {

    const allCollections = PersistStore(state => state.allCollections);
    const [newEndpoints, setNewEndpoints] = useState({prettify: [], normal: []})
    const [newParametersCount, setNewParametersCount] = useState(0)
    const [parametersTrend, setParametersTrend] = useState([])
    const [sensitiveParams, setSensitiveParams] = useState([])
    const [loading, setLoading] = useState(true);
    const [apiDetail, setApiDetail] = useState({})
    const [tableHeaders,setTableHeaders] = useState([])

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

    useEffect(() => {
        async function fetchData() {
            let apiCollection, apiCollectionUrls, apiInfoList;
            await api.loadRecentEndpoints(startTimestamp, endTimestamp).then((res) => {
                apiCollection = res.data.endpoints.map(x => { return { ...x._id, startTs: x.startTs } })
                apiCollectionUrls = res.data.endpoints.map(x => x._id.url)
                apiInfoList = res.data.apiInfoList
            })
            await api.fetchSensitiveParamsForEndpoints(apiCollectionUrls).then(allSensitiveFields => {
                let sensitiveParams = allSensitiveFields.data.endpoints
                setSensitiveParams([...sensitiveParams]);
                apiCollection = transform.fillSensitiveParams(sensitiveParams, apiCollection);
            })
            let data = func.mergeApiInfoAndApiCollection(apiCollection, apiInfoList);
            const prettifiedData = transform.prettifyEndpointsData(data)
            setNewEndpoints({prettify: prettifiedData, normal: data});
            await api.fetchNewParametersTrend(startTimestamp, endTimestamp).then((resp) => {
                const trendObj = transform.findNewParametersCountTrend(resp, startTimestamp, endTimestamp)
                setNewParametersCount(trendObj.count)
                setParametersTrend(trendObj.trend)
            })
            setLoading(false);
        }
        if (Object.keys(allCollections).length > 0) {
            fetchData();
        }
    }, [allCollections, currDateRange])

    const infoItems = [
        {
            title: "New endpoints",
            isComp: true,
            data: <div data-testid="new_endpoints_count" style={{fontWeight: 600, color: '#1F2124', fontSize: '14px'}}>{transform.formatNumberWithCommas(newEndpoints.normal.length)}</div>,
        },
        {
            title: "New sensitive endpoints",
            data: transform.formatNumberWithCommas(newEndpoints.normal.filter(x => x.sensitive && x.sensitive.size > 0).length),
            color: "critical",
        },
        {
            title: "New parameters",
            data: transform.formatNumberWithCommas(newParametersCount)
        },
        {
            title: "New sensitive parameters",
            data: transform.formatNumberWithCommas(sensitiveParams.filter(x => x.timestamp > startTimestamp && x.timestamp < endTimestamp && func.isSubTypeSensitive(x)).length),
            color: "critical",
        }
    ]
    const endpointsTrend = transform.changesTrend(newEndpoints.normal, startTimestamp, endTimestamp)

    const tableComponent = (
        <ApiChangesTable
            handleRowClick={handleRowClick}
            tableLoading={loading}
            startTimeStamp={startTimestamp}
            endTimeStamp={endTimestamp}
            newEndpoints={newEndpoints.prettify}
            parametersCount={newParametersCount}
            key="table"
            tab={(location.state)?(location.state.tab):0 }
        />
    )

    const apiChanges = (
        <ApiDetails
            key="details"
            showDetails={showDetails}
            setShowDetails={setShowDetails}
            apiDetail={apiDetail}
            headers={tableHeaders}
            getStatus={() => { return "warning" }}
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
                name: "New endpoints"
            },
            {
                data: parametersTrend,
                color: "#fca130",
                name: "New parameters"
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
                    titleText={"API changes"}
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
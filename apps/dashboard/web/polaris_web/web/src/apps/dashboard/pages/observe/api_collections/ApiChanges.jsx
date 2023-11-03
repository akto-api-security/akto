import { Card, Divider, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
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
import StackedChart from "../../../components/charts/StackedChart";


function ApiChanges() {

    const allCollections = PersistStore(state => state.allCollections);
    const collectionsMap = PersistStore(state => state.collectionsMap);
    const [newEndpoints, setNewEndpoints] = useState({prettify: [], normal: []})
    const [newParametersCount, setNewParametersCount] = useState(0)
    const [parametersTrend, setParametersTrend] = useState([])
    const [sensitiveParams, setSensitiveParams] = useState([])
    const [loading, setLoading] = useState(true);
    const [apiDetail, setApiDetail] = useState({})
    const [tableHeaders,setTableHeaders] = useState([])

    const showDetails = ObserveStore(state => state.inventoryFlyout)
    const setShowDetails = ObserveStore(state => state.setInventoryFlyout)
    
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[3]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")
    
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
            let data = func.mergeApiInfoAndApiCollection(apiCollection, apiInfoList, collectionsMap);
            const prettifiedData = transform.prettifyEndpointsData(data)
            setNewEndpoints({prettify: prettifiedData, normal: data});
            await api.fetchNewParametersTrend(startTimestamp, endTimestamp).then((resp) => {
                const trendObj = transform.findNewParametersCountTrend(resp, startTimestamp, endTimestamp)
                setNewParametersCount(trendObj.count)
                setParametersTrend(trendObj.trend)
            })
            setLoading(false);
        }
        if (allCollections.length > 0) {
            fetchData();
        }
    }, [allCollections, currDateRange])

    const infoItems = [
        {
            title: "New endpoints",
            data: transform.formatNumberWithCommas(newEndpoints.normal.length),
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

    const processChartData = () => {
        return [
            {
                data: endpointsTrend,
                color: "#AEE9D1",
                name: "New endpoints"
            },
            {
                data: parametersTrend,
                color: "#A4E8F2",
                name: "New parameters"
            }
        ]
    }
    const defaultChartOptions = {
        "legend": {
            enabled: false
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
                    <HorizontalStack align="end">
                        <HorizontalStack gap={3}>
                            <HorizontalStack gap={2}>
                                <div style={{background: "#A4E8F2", borderRadius: '50%', height:'8px', width: '8px'}} />
                                <Text variant="bodyMd">Sensitive params</Text>
                            </HorizontalStack>
                            <HorizontalStack gap={2}>
                                <div style={{background: "#AEE9D1", borderRadius: '50%', height:'8px', width: '8px'}} />
                                <Text variant="bodyMd">New endpoints</Text>
                            </HorizontalStack>
                        </HorizontalStack>
                    </HorizontalStack>
                    <StackedChart
                        key={`trend-chart`}
                        type='column'
                        color='#6200EA'
                        areaFillHex="true"
                        height="280"
                        background-color="#ffffff"
                        data={processChartData()}
                        defaultChartOptions={defaultChartOptions}
                        text="true"
                        yAxisTitle="Number of issues"
                        width={20}
                    />
                </VerticalStack>
            </VerticalStack>
        </Card>
    )

    const components = [<SummaryCardInfo summaryItems={infoItems} key="infoCard" />, graphComponent, tableComponent, apiChanges]

    return (
        <PageWithMultipleCards
            title={
                <Text variant='headingLg'>
                    API changes
                </Text>
            }
            isFirstPage={true}
            primaryAction={<DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>}
            components={components}
        />

    )
}

export default ApiChanges
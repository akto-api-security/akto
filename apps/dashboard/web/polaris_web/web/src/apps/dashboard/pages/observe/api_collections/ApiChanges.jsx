import { Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { useEffect, useReducer, useState } from "react";
import api from "../api";
import func from "@/util/func";
import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import transform from "../transform";
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs";
import NewEndpointsTable from "./component/NewEndpointsTable";
import NewParametersTable from "./component/NewParametersTable";
import {produce} from "immer"
import values from "@/util/values";
import ObserveStore from "../observeStore";
import ApiDetails from "./ApiDetails";
import PersistStore from "../../../../main/PersistStore";


function ApiChanges() {

    const allCollections = PersistStore(state => state.allCollections);
    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)
    const [newEndpoints, setNewEndpoints] = useState([])
    const [newParametersCount, setNewParametersCount] = useState("")
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
            let data = func.mergeApiInfoAndApiCollection(apiCollection, apiInfoList, mapCollectionIdToName);
            setNewEndpoints(data);
            await api.fetchNewParametersTrend(startTimestamp, endTimestamp).then((resp) => {
                setNewParametersCount(transform.findNewParametersCount(resp, startTimestamp, endTimestamp))
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
            data: transform.formatNumberWithCommas(newEndpoints.length),
        },
        {
            title: "New sensitive endpoints",
            data: transform.formatNumberWithCommas(newEndpoints.filter(x => x.sensitive && x.sensitive.size > 0).length),
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

    const infoCard = (
        <Card key="info">
            <HorizontalStack gap="1" align="space-between">
                {infoItems.map((item, index) => (
                    <VerticalStack gap="1" key={index}>
                        <Text color="subdued" variant="bodyLg">
                            {item.title}
                        </Text>
                        <Text variant="bodyMd" fontWeight="semibold" color={item?.color}>
                            {item.data}
                        </Text>
                    </VerticalStack>
                ))}
            </HorizontalStack>
        </Card>
    )

    const endpointsTable = {
        id: 'newEndpoints',
        content: "New endpoints",
        component: <NewEndpointsTable loading={loading} newEndpoints={newEndpoints} handleRowClick={handleRowClick}/>
    }

    const parameterTable = {
        id: 'newParameters',
        content: "New parameters",
        component: <NewParametersTable startTimestamp={startTimestamp} endTimestamp={endTimestamp} handleRowClick={handleRowClick}/>
    }

    const tabs = (
        <Card padding={"0"} key="tabs">
            <LayoutWithTabs
                key="tabs"
                tabs={[endpointsTable, parameterTable]}
                currTab={() => { }}
            />
        </Card>
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

    const components = [infoCard, tabs, apiChanges]

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
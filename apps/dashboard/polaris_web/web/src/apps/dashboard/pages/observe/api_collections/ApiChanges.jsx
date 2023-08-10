import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { useEffect, useState } from "react";
import api from "../api";
import func from "../../../../../util/func";
import Store from "../../../store";
import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import transform from "../transform";
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs";
import NewEndpointsTable from "./component/NewEndpointsTable";
import NewParametersTable from "./component/NewParametersTable";


function ApiChanges() {

    const allCollections = Store(state => state.allCollections);
    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)
    const [newEndpoints, setNewEndpoints] = useState([])
    const [newParametersCount, setNewParametersCount] = useState("")
    const [sensitiveParams, setSensitiveParams] = useState([])
    const [loading, setLoading] = useState(true);
    const [startTimestamp, setStartTimestamp] = useState(func.timeNow() - func.recencyPeriod);
    const [endTimestamp, setEndTimestamp] = useState(func.timeNow());

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
    }, [allCollections, startTimestamp, endTimestamp])

    const infoItems = [
        {
            title: "New endpoints",
            data: newEndpoints.length
        },
        {
            title: "New sensitive endpoints",
            data: newEndpoints.filter(x => x.sensitive && x.sensitive.size > 0).length
        },
        {
            title: "New parameters",
            data: newParametersCount
        },
        {
            title: "New sensitive parameters",
            data: sensitiveParams.filter(x => x.timestamp > startTimestamp && x.timestamp < endTimestamp && func.isSubTypeSensitive(x)).length
        }
    ]

    const handleDate = (dateRange) => {
        setStartTimestamp(Math.floor(Date.parse(dateRange.period.since) / 1000))
        setEndTimestamp(Math.floor(Date.parse(dateRange.period.until) / 1000))
    }

    const infoCard = (
        <Card key="info">
            <HorizontalStack gap="1" align="space-between">
                {infoItems.map((item, index) => (
                    <VerticalStack gap="1" key={index}>
                        <Text color="subdued" variant="bodyLg">
                            {item.title}
                        </Text>
                        <Text>
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
        component: <NewEndpointsTable loading={loading} newEndpoints={newEndpoints} />
    }

    const parameterTable = {
        id: 'newParameters',
        content: "New parameters",
        component: <NewParametersTable startTimestamp={startTimestamp} endTimestamp={endTimestamp}/>
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

    const components = [infoCard, tabs]

    return (
        <PageWithMultipleCards
            title={
                <Text variant='headingLg'>
                    API changes
                </Text>
            }
            isFirstPage={true}
            primaryAction={<DateRangeFilter getDate={handleDate} initialDate={{since:startTimestamp, until:endTimestamp}}/>}
            components={components}
        />

    )
}

export default ApiChanges
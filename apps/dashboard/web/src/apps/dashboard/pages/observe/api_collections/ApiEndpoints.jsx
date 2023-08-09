import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Tooltip, Text, HorizontalStack, Button, ButtonGroup, Box } from "@shopify/polaris"
import api from "../api"
import { useEffect, useState } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { useParams } from "react-router-dom"
import { saveAs } from 'file-saver'
import {
    ClockMinor,
    LockMinor,
    CircleAlertMajor,
    GlobeMinor,
    HintMajor,
    RedoMajor,
    ImportMinor
} from '@shopify/polaris-icons';

import "./api_inventory.css"
import ApiDetails from "./ApiDetails"
import UploadFile from "../../../components/shared/UploadFile"
import RunTest from "./RunTest"
import ObserveStore from "../observeStore"
import StyledEndpoint from "./component/StyledEndpoint"
import WorkflowTests from "./WorkflowTests"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"

const headers = [
    {
        text: "Method",
        value: "method",
        showFilter: true
    },
    {
        text: "Endpoint",
        value: "parameterisedEndpoint",
        itemOrder: 1,
        showFilter: true,
        component: StyledEndpoint
    },
    {
        text: 'Tags',
        value: 'tags',
        itemCell: 3,
        showFilter: true
    },
    {
        text: 'Sensitive Params',
        value: 'sensitiveTags',
        itemOrder: 2,
        showFilter: true
    },
    {
        text: 'Last Seen',
        value: 'last_seen',
        icon: HintMajor,
        itemOrder: 3
    },
    {
        text: 'Access Type',
        value: 'access_type',
        icon: GlobeMinor,
        itemOrder: 3,
        showFilter: true
    },
    {
        text: 'Auth Type',
        value: 'auth_type',
        icon: LockMinor,
        itemOrder: 3,
        showFilter: true
    },
    {
        text: "Discovered",
        value: 'added',
        icon: ClockMinor,
        itemOrder: 3
    },
    {
        text: 'Changes',
        value: 'changes',
        icon: CircleAlertMajor,
        itemOrder: 3

    }
]

const sortOptions = [
    { label: 'Method', value: 'method asc', directionLabel: 'A-Z', sortKey: 'method' },
    { label: 'Method', value: 'method desc', directionLabel: 'Z-A', sortKey: 'method' },
    { label: 'Endpoint', value: 'endpoint asc', directionLabel: 'A-Z', sortKey: 'endpoint' },
    { label: 'Endpoint', value: 'endpoint desc', directionLabel: 'Z-A', sortKey: 'endpoint' },
    { label: 'Auth Type', value: 'auth_type asc', directionLabel: 'A-Z', sortKey: 'auth_type' },
    { label: 'Auth Type', value: 'auth_type desc', directionLabel: 'Z-A', sortKey: 'auth_type' },
    { label: 'Access Type', value: 'access_type asc', directionLabel: 'A-Z', sortKey: 'access_type' },
    { label: 'Access Type', value: 'access_type desc', directionLabel: 'Z-A', sortKey: 'access_type' },
];

function ApiEndpoints() {

    const params = useParams()
    const apiCollectionId = params.apiCollectionId

    const showDetails = ObserveStore(state => state.inventoryFlyout)
    const setShowDetails = ObserveStore(state => state.setInventoryFlyout)

    const [apiEndpoints, setApiEndpoints] = useState([])
    const [apiInfoList, setApiInfoList] = useState([])
    const [unusedEndpoints, setUnusedEndpoints] = useState([])

    const [endpointData, setEndpointData] = useState([])
    const [selectedTab, setSelectedTab] = useState("All")
    const [selected, setSelected] = useState(0)
    const [loading, setLoading] = useState(true)
    const [apiDetail, setApiDetail] = useState({})

    const filteredEndpoints = ObserveStore(state => state.filteredItems)
    const setFilteredEndpoints = ObserveStore(state => state.setFilteredItems)

    async function fetchData() {
        setLoading(true)
        let apiCollectionData = await api.fetchAPICollection(apiCollectionId)
        let apiEndpointsInCollection = apiCollectionData.data.endpoints.map(x => { return { ...x._id, startTs: x.startTs, changesCount: x.changesCount, shadow: x.shadow ? x.shadow : false } })
        let apiInfoListInCollection = apiCollectionData.data.apiInfoList
        let unusedEndpointsInCollection = apiCollectionData.unusedEndpoints
        let sensitiveParamsResp = await api.loadSensitiveParameters(apiCollectionId)
        let sensitiveParams = sensitiveParamsResp.data.endpoints

        let sensitiveParamsMap = {}
        sensitiveParams.forEach(p => {
            let key = p.method + " " + p.url
            if (!sensitiveParamsMap[key]) sensitiveParamsMap[key] = new Set()

            if (!p.subType) {
                p.subType = { name: "CUSTOM" }
            }

            sensitiveParamsMap[key].add(p.subType)
        })

        apiEndpointsInCollection.forEach(apiEndpoint => {
            apiEndpoint.sensitive = sensitiveParamsMap[apiEndpoint.method + " " + apiEndpoint.url] || new Set()
        })

        let data = {}
        let allEndpoints = func.mergeApiInfoAndApiCollection(apiEndpointsInCollection, apiInfoListInCollection, null)
        data['All'] = allEndpoints
        data['Sensitive'] = allEndpoints.filter(x => x.sensitive && x.sensitive.size > 0)
        data['Unauthenticated'] = allEndpoints.filter(x => x.open)
        data['Undocumented'] = allEndpoints.filter(x => x.shadow)
        data['Deprecated'] = func.getDeprecatedEndpoints(apiInfoListInCollection, unusedEndpointsInCollection)
        setEndpointData(data)
        setSelectedTab("All")
        setSelected(0)

        setApiEndpoints(apiEndpointsInCollection)
        setApiInfoList(apiInfoListInCollection)
        setUnusedEndpoints(unusedEndpointsInCollection)

        setLoading(false)
    }

    useEffect(() => {
        fetchData()
    }, [])

    const resourceName = {
        singular: 'endpoint',
        plural: 'endpoints',
    };

    const getFilteredItems = (filteredItems) => {
        setFilteredEndpoints(filteredItems)
    }

    const tabStrings = [
        'All',
        'Sensitive',
        'Unauthenticated',
        'Undocumented',
        'Deprecated'
    ]

    const tabs = tabStrings.map((item, index) => ({
        content: item,
        index,
        id: `${item}-${index}`,
    }));

    tabs.push(
        {
            content: "Workflow tests",
            index: tabs.length,
            id: `Tests-${tabs.length}`,
            component: <WorkflowTests   
                    apiCollectionId={apiCollectionId} 
                    endpointsList={loading ? [] : endpointData["All"]}
                />
        },
    )

    const onSelect = (selectedIndex) => {
        setSelectedTab(tabStrings[selectedIndex])
        setSelected(selectedIndex)
    }

    function handleRowClick(data) {
        const sameRow = func.deepComparison(apiDetail, data);
        if (!sameRow) {
            setShowDetails(true)
        } else {
            setShowDetails(!showDetails)
        }
        setApiDetail((prev) => {
            if (sameRow) {
                return prev;
            }
            return { ...data }
        })
    }

    function handleRefresh() {
        fetchData()
        func.setToast(true, false, "Endpoints refreshed")
    }

    async function exportOpenApi() {
        let lastFetchedUrl = null;
        let lastFetchedMethod = null;
        for (let index = 0; index < 10; index++) {
            let result = await api.downloadOpenApiFile(apiCollectionId, lastFetchedUrl, lastFetchedMethod)
            let openApiString = result["openAPIString"]
            let blob = new Blob([openApiString], {
                type: "application/json",
            });
            const fileName = "open_api_" + func.getCollectionName(apiCollectionId) + ".json";
            saveAs(blob, fileName);

            lastFetchedUrl = result["lastFetchedUrl"]
            lastFetchedMethod = result["lastFetchedMethod"]

            if (!lastFetchedUrl || !lastFetchedMethod) break;
        }
        func.setToast(true, false, "OpenAPI spec downlaoded successfully")
    }

    function exportCsv() {
        if (!loading) {
            let headerTextToValueMap = Object.fromEntries(headers.map(x => [x.text, x.value]).filter(x => x[0].length > 0));

            let csv = Object.keys(headerTextToValueMap).join(",") + "\r\n"
            const allEndpoints = endpointData['All']
            allEndpoints.forEach(i => {
                csv += Object.values(headerTextToValueMap).map(h => (i[h] || "-")).join(",") + "\r\n"
            })
            let blob = new Blob([csv], {
                type: "application/csvcharset=UTF-8"
            });
            saveAs(blob, ("All endopints") + ".csv");
            func.setToast(true, false, "CSV exported successfully")
        }
    }

    async function exportPostman() {
        const result = await api.exportToPostman(apiCollectionId)
        if (result)
            func.setToast(true, false, "Postman collection downloaded successfully")
    }

    function handleFileChange(file) {
        if (file) {
            const reader = new FileReader();

            let isHar = file.name.endsWith(".har")
            if (isHar && file.size >= 52428800) {
                func.setToast(true, true, "Please limit the file size to less than 50 MB")
                return
            }
            let isJson = file.name.endsWith(".json")
            let isPcap = file.name.endsWith(".pcap")
            if (isHar || isJson) {
                reader.readAsText(file)
            } else if (isPcap) {
                reader.readAsArrayBuffer(new Blob([file]))
            }
            reader.onload = async () => {
                let skipKafka = false;//window.location.href.indexOf("http://localhost") != -1
                if (isHar) {
                    const formData = new FormData();
                    formData.append("harString", reader.result)
                    formData.append("hsFile", reader.result)
                    formData.append("skipKafka", skipKafka)
                    formData.append("apiCollectionId", apiCollectionId);
                    func.setToast(true, false, "We are uploading your har file, please dont refresh the page!")

                    api.uploadHarFile(formData).then(resp => {
                        if (file.size > 2097152) {
                            func.setToast(true, false, "We have successfully read your file")
                        }
                        else {
                            func.setToast(true, false, "Your Har file has been successfully processed")
                        }
                        fetchData()
                    }).catch(err => {
                        if (err.message.includes(404)) {
                            func.setToast(true, true, "Please limit the file size to less than 50 MB")
                        } else {
                            func.setToast(true, true, "Something went wrong while processing the file")
                        }
                    })

                } else if (isPcap) {
                    var arrayBuffer = reader.result
                    var bytes = new Uint8Array(arrayBuffer);

                    await api.uploadTcpFile([...bytes], apiCollectionId, skipKafka)
                }
            }
        }
    }

    return (
        <PageWithMultipleCards
            title={
                <Text variant='headingLg' truncate>
                    {
                        "API Endpoints"
                    }
                </Text>
            }
            secondaryActions={
                <ButtonGroup>
                    <Tooltip content="Refresh">
                        <Button icon={RedoMajor} onClick={handleRefresh} plain helpText="Refresh" />
                    </Tooltip>
                    <Button
                        icon={ImportMinor}
                        connectedDisclosure={{
                            actions: [
                                { content: 'OpenAPI spec', onAction: exportOpenApi },
                                { content: 'Postman', onAction: exportPostman },
                                { content: 'CSV', onAction: exportCsv },
                            ],
                        }}
                    >
                        Export
                    </Button>
                    <UploadFile
                        fileFormat=".har"
                        fileChanged={file => handleFileChange(file)}
                        tooltipText="Upload traffic(.har)"
                        label="Upload traffic"
                        primary={false} />
                    <RunTest
                        apiCollectionId={apiCollectionId}
                        endpoints={filteredEndpoints}
                        filtered={loading ? false : filteredEndpoints.length !== endpointData["All"].length}
                        disabled={tabs[selected].component !== undefined}
                    />
                </ButtonGroup>
            }
            components={
                loading ? [<SpinnerCentered key="loading" />] :
                    [
                        <div className="apiEndpointsTable" key="table">
                            <GithubSimpleTable
                                key="table"
                                pageLimit={50}
                                data={tabs[selected].component ? [] : endpointData[selectedTab]}
                                sortOptions={sortOptions}
                                resourceName={resourceName}
                                filters={[]}
                                disambiguateLabel={() => { }}
                                headers={headers}
                                getStatus={() => { return "warning" }}
                                tabs={tabs}
                                selected={selected}
                                onSelect={onSelect}
                                onRowClick={handleRowClick}
                                getFilteredItems={getFilteredItems}
                            />
                        </div>,
                        <ApiDetails
                            key="details"
                            showDetails={showDetails}
                            setShowDetails={setShowDetails}
                            apiDetail={apiDetail}
                            headers={headers}
                            getStatus={() => { return "warning" }}
                        />
                    ]}
        />
    )
}

export default ApiEndpoints

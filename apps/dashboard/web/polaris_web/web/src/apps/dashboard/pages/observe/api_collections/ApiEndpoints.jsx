import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, HorizontalStack, Button, Box, Popover, ActionList, Icon, Modal } from "@shopify/polaris"
import api from "../api"
import { useEffect, useState } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { useParams } from "react-router-dom"
import { saveAs } from 'file-saver'
import {
    ClockMinor,
    LockMinor,
    ChevronDownMinor,
    InfoMinor,
    SearchMinor
} from '@shopify/polaris-icons';

import "./api_inventory.css"
import ApiDetails from "./ApiDetails"
import UploadFile from "../../../components/shared/UploadFile"
import RunTest from "./RunTest"
import ObserveStore from "../observeStore"
import StyledEndpoint from "./component/StyledEndpoint"
import WorkflowTests from "./WorkflowTests"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout"
import dashboardFunc from "../../transform"
import settingsRequests from "../../settings/api"
import OpenApiSpec from "../OpenApiSpec"
import PersistStore from "../../../../main/PersistStore"
import TooltipText from "../../../components/shared/TooltipText"

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
        icon: SearchMinor,
        itemOrder: 3
    },
    {
        text: 'Access Type',
        value: 'access_type',
        icon: InfoMinor,
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
        icon: InfoMinor,
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
    { label: 'Last seen', value: 'added asc', directionLabel: 'Newest', sortKey: 'added' },
    { label: 'Last seen', value: 'added desc', directionLabel: 'Oldest', sortKey: 'added' },
];

function ApiEndpoints() {

    const params = useParams()
    const apiCollectionId = params.apiCollectionId

    const showDetails = ObserveStore(state => state.inventoryFlyout)
    const setShowDetails = ObserveStore(state => state.setInventoryFlyout)
    const collectionsMap = PersistStore(state => state.collectionsMap)

    const pageTitle = collectionsMap[apiCollectionId]

    const [apiEndpoints, setApiEndpoints] = useState([])
    const [apiInfoList, setApiInfoList] = useState([])
    const [unusedEndpoints, setUnusedEndpoints] = useState([])

    const [endpointData, setEndpointData] = useState([])
    const [selectedTab, setSelectedTab] = useState("All")
    const [selected, setSelected] = useState(0)
    const [loading, setLoading] = useState(true)
    const [apiDetail, setApiDetail] = useState({})
    const [exportOpen, setExportOpen] = useState(false)

    const filteredEndpoints = ObserveStore(state => state.filteredItems)
    const setFilteredEndpoints = ObserveStore(state => state.setFilteredItems)

    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const [isGptActive, setIsGptActive] = useState(false)

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
        data['Deprecated'] = func.getDeprecatedEndpoints(apiInfoListInCollection, unusedEndpointsInCollection, apiCollectionId)
        // console.log(data)
        setEndpointData(data)
        setSelectedTab("All")
        setSelected(0)

        setApiEndpoints(apiEndpointsInCollection)
        setApiInfoList(apiInfoListInCollection)
        setUnusedEndpoints(unusedEndpointsInCollection)

        setLoading(false)
    }

    const checkGptActive = async() => {
        await settingsRequests.fetchAktoGptConfig(apiCollectionId).then((resp) => {
            if(resp.currentState[0].state === "ENABLED"){
                setIsGptActive(true)
            }
        })
    }

    useEffect(() => {
        fetchData()
        checkGptActive()
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
            />,
            hideQueryField: true
        },
    )

    let openApiSecObj = {
        content: "Documented",
        index: tabs.length,
        id: `Tests-${tabs.length}`,
        component: <OpenApiSpec apiCollectionId={apiCollectionId} />,
        hideQueryField: true,
    }
    
    tabs.push(openApiSecObj)

    const onSelect = (selectedIndex) => {
        setLoading(true);
        setSelectedTab(tabStrings[selectedIndex])
        setSelected(selectedIndex)
        setTimeout(() => {
            setLoading(false);
        }, 300);
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
            const fileName = "open_api_" + collectionsMap[apiCollectionId] + ".json";
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
        func.setToast(true, false, "We have initiated export to Postman, checkout API section on your Postman app in sometime.")
    }

    function disambiguateLabel(key, value) {
        switch (key) {
            case "parameterisedEndpoint":
                return func.convertToDisambiguateLabelObj(value, null, 1)
            case "method":
                return func.convertToDisambiguateLabelObj(value, null, 3)
            default:
              return func.convertToDisambiguateLabelObj(value, null, 2);
          }          
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
                            let message = err?.response?.data?.actionErrors?.[0] || "Something went wrong while processing the file"
                            func.setToast(true, true, message)
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

    function displayGPT(){
        setIsGptScreenActive(true)
        let requestObj = {key: "COLLECTION",filteredItems: filteredEndpoints,apiCollectionId: Number(apiCollectionId)}
        const activePrompts = dashboardFunc.getPrompts(requestObj)
        setPrompts(activePrompts)
    }

    return (
        <PageWithMultipleCards
            title={
                <Box maxWidth="35vw">
                    <TooltipText tooltip={pageTitle} text={pageTitle} textProps={{variant:'headingLg'}} />
                </Box>
            }
            backUrl="/dashboard/observe/inventory"
            secondaryActions={
                <HorizontalStack gap="2">
                    <Box paddingInlineEnd="3" paddingInlineStart="3">
                        <Button onClick={handleRefresh} plain monochrome removeUnderline>
                            <Text fontWeight="medium" variant="bodyMd">Refresh</Text>
                        </Button>
                    </Box>
                    <Popover
                        active={exportOpen}
                        activator={(
                            <Box paddingInlineEnd="1" paddingInlineStart="3">
                            <Button
                                plain monochrome removeUnderline
                                onClick={() => setExportOpen(true)}>
                                <HorizontalStack gap="1">
                                <Text fontWeight="medium" variant="bodyMd">Export</Text>
                                    <Box>
                                        <Icon source={ChevronDownMinor} />
                                    </Box>
                                </HorizontalStack>
                            </Button>
                            </Box>
                        )}
                        autofocusTarget="first-node"
                        onClose={() => { setExportOpen(false) }}
                    >
                        <ActionList
                            items={[
                                { content: 'OpenAPI spec', onAction: exportOpenApi },
                                { content: 'Postman', onAction: exportPostman },
                                { content: 'CSV', onAction: exportCsv },
                            ]}
                        />
                    </Popover>
                    <UploadFile
                        fileFormat=".har"
                        fileChanged={file => handleFileChange(file)}
                        tooltipText="Upload traffic(.har)"
                        label={(<Box paddingInlineEnd="3" paddingInlineStart="3"><Text fontWeight="medium" variant="bodyMd">Upload traffic</Text></Box>)}
                        primary={false} />

                    {isGptActive ? <Button onClick={displayGPT}>Ask AktoGPT</Button>: null}
                    
                    <RunTest
                        apiCollectionId={apiCollectionId}
                        endpoints={filteredEndpoints}
                        filtered={loading ? false : filteredEndpoints.length !== endpointData["All"].length}
                        disabled={tabs[selected].component !== undefined}
                    />
                </HorizontalStack>
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
                                disambiguateLabel={disambiguateLabel}
                                headers={headers}
                                getStatus={() => { return "warning" }}
                                tabs={tabs}
                                selected={selected}
                                onSelect={onSelect}
                                onRowClick={handleRowClick}
                                getFilteredItems={getFilteredItems}
                            />
                            <Modal large open={isGptScreenActive} onClose={()=> setIsGptScreenActive(false)} title="Akto GPT">
                                <Modal.Section flush>
                                    <AktoGptLayout prompts={prompts} closeModal={()=> setIsGptScreenActive(false)}/>
                                </Modal.Section>
                            </Modal>
                        </div>,
                        <ApiDetails
                            key="details"
                            showDetails={showDetails}
                            setShowDetails={setShowDetails}
                            apiDetail={apiDetail}
                            headers={headers}
                            getStatus={() => { return "warning" }}
                            isGptActive={isGptActive}
                        />
                    ]}
        />
    )
}

export default ApiEndpoints

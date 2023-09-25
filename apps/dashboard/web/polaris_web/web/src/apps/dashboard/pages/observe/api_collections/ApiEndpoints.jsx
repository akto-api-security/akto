import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, HorizontalStack, Button, Popover, Modal, IndexFiltersMode, VerticalStack } from "@shopify/polaris"
import api from "../api"
import { useEffect, useState } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { useParams } from "react-router-dom"
import { saveAs } from 'file-saver'

import "./api_inventory.css"
import ApiDetails from "./ApiDetails"
import UploadFile from "../../../components/shared/UploadFile"
import RunTest from "./RunTest"
import ObserveStore from "../observeStore"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout"
import dashboardFunc from "../../transform"
import settingsRequests from "../../settings/api"
import PersistStore from "../../../../main/PersistStore"
import transform from "../transform"

const headings = [
    {
        text: "Endpoint",
        value: "endpointComp",
        title: "Api endpoints",
        itemCell: 1,
    },
    {
        text: "Risk score",
        title: "Risk score",
        value: "riskScoreComp",
        itemCell: 2,
    },
    {
        text: "Hostname",
        value: 'hostName',
        title: "Hostname",
        maxWidth: '100px',
        itemCell: 3,
        isText: true,
    },
    {
        text: 'Access Type',
        value: 'access_type',
        title: 'Access Type',
        itemCell: 4,
        showFilter: true,
        isText: true,
    },
    {
        text: 'Auth Type',
        title: 'Auth Type',
        value: 'auth_type',
        itemCell: 5,
        showFilter: true,
        isText: true,
    },
    {
        text: 'Sensitive Params',
        title: 'Sensitive Params',
        value: 'sensitiveTagsComp',
        itemCell: 6,
        showFilter: true
    },
    {
        text: 'Last Seen',
        title: 'Last Seen',
        value: 'last_seen',
        itemCell: 7,
        isText: true,
    },
]

let headers =  JSON.parse(JSON.stringify(headings))
headers.push({
    text: "Method",
    value: "method",
    showFilter: true
},)

const sortOptions = [
    { label: 'Risk Score', value: 'riskScore asc', directionLabel: 'Highest', sortKey: 'riskScore'},
    { label: 'Risk Score', value: 'riskScore desc', directionLabel: 'Lowest', sortKey: 'riskScore'},
    { label: 'Method', value: 'method asc', directionLabel: 'A-Z', sortKey: 'method' },
    { label: 'Method', value: 'method desc', directionLabel: 'Z-A', sortKey: 'method' },
    { label: 'Endpoint', value: 'endpoint asc', directionLabel: 'A-Z', sortKey: 'endpoint' },
    { label: 'Endpoint', value: 'endpoint desc', directionLabel: 'Z-A', sortKey: 'endpoint' },
    { label: 'Auth Type', value: 'auth_type asc', directionLabel: 'A-Z', sortKey: 'auth_type' },
    { label: 'Auth Type', value: 'auth_type desc', directionLabel: 'Z-A', sortKey: 'auth_type' },
    { label: 'Access Type', value: 'access_type asc', directionLabel: 'A-Z', sortKey: 'access_type' },
    { label: 'Access Type', value: 'access_type desc', directionLabel: 'Z-A', sortKey: 'access_type' },
    { label: 'Last seen', value: 'lastSeenTs asc', directionLabel: 'Newest', sortKey: 'lastSeenTs' },
    { label: 'Last seen', value: 'lastSeenTs desc', directionLabel: 'Oldest', sortKey: 'lastSeenTs' },
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

    const tableTabs = [
        {
            content: 'All',
            index: 0,
            badge: endpointData["All"]?.length?.toString(),
            onAction: ()=> {setSelectedTab('All')},
            id: 'All',
        },
        {
            content: 'New',
            index: 1,
            badge: endpointData["New"]?.length?.toString(),
            onAction: ()=> {setSelectedTab('New')},
            id: 'New',
        },
        {
            content: 'Sensitive',
            index: 2,
            badge: endpointData["Sensitive"]?.length?.toString(),
            onAction: ()=> {setSelectedTab('Sensitive')},
            id:'Sensitive',
        },
        {
            content: 'High risk',
            index: 3,
            badge: endpointData["Risk"]?.length?.toString(),
            onAction: ()=> {setSelectedTab('Risk')},
            id: 'Risk',
        },
        {
            content: 'No auth detected',
            index: 4,
            badge: endpointData["No_auth"]?.length?.toString(),
            onAction: ()=> {setSelectedTab('No_auth')},
            id: 'No_auth'
        },
    ]

    async function fetchData() {
        setLoading(true)
        let apiCollectionData = await api.fetchAPICollection(apiCollectionId)
        let severityInfoMap = {}
        await api.getLastTestedSeverityInfo(apiCollectionId).then((resp)=>{
            severityInfoMap = JSON.parse(JSON.stringify(resp.lastTestedSeverityMap))
        })
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
        const prettifyData = transform.prettifyEndpointsData(allEndpoints, severityInfoMap)
        data['All'] = prettifyData
        data['Sensitive'] = prettifyData.filter(x => x.sensitive && x.sensitive.size > 0)
        data['Risk'] = prettifyData.filter(x=> x.riskScore >= 4)
        data['New'] = prettifyData.filter(x=> x.isNew)
        data['No_auth'] = prettifyData.filter(x => x.open)
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
        func.setToast(true, false, "OpenAPI spec downloaded successfully")
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

    function displayGPT(){
        setIsGptScreenActive(true)
        let requestObj = {key: "COLLECTION",filteredItems: filteredEndpoints,apiCollectionId: Number(apiCollectionId)}
        const activePrompts = dashboardFunc.getPrompts(requestObj)
        setPrompts(activePrompts)
    }
    const secondaryActionsComponent = (
        <HorizontalStack gap="2">

            <Popover 
                active={exportOpen}
                activator={(
                    <Button onClick={() => setExportOpen(true)}>More Actions</Button>
                )}
                autofocusTarget="first-node"
                onClose={() => { setExportOpen(false) }}
            >
                <Popover.Pane fixed>
                    <Popover.Section>
                        <VerticalStack gap={2}>
                            <div onClick={handleRefresh} style={{cursor: 'pointer'}}>
                                <Text fontWeight="regular" variant="bodyMd">Refresh</Text>
                            </div>
                            <UploadFile
                                fileFormat=".har"
                                fileChanged={file => handleFileChange(file)}
                                tooltipText="Upload traffic(.har)"
                                label={<Text fontWeight="regular" variant="bodyMd">Upload traffic</Text>}
                                primary={false} 
                            />
                        </VerticalStack>
                    </Popover.Section>
                    <Popover.Section>
                        <VerticalStack gap={2}>
                            <Text>Export as:</Text>
                                <VerticalStack gap={1}>
                                <div onClick={exportOpenApi} style={{cursor: 'pointer'}}>
                                    <Text fontWeight="regular" variant="bodyMd">OpenAPI spec</Text>
                                </div>
                                <div onClick={exportPostman} style={{cursor: 'pointer'}}>
                                    <Text fontWeight="regular" variant="bodyMd">Postman</Text>
                                </div>
                                <div onClick={exportCsv} style={{cursor: 'pointer'}}>
                                    <Text fontWeight="regular" variant="bodyMd">CSV</Text>
                                </div>
                            </VerticalStack>
                        </VerticalStack>
                    </Popover.Section>
                </Popover.Pane>
            </Popover>

            {isGptActive ? <Button onClick={displayGPT}>Ask AktoGPT</Button>: null}
                    
            <RunTest
                apiCollectionId={apiCollectionId}
                endpoints={filteredEndpoints}
                filtered={loading ? false : filteredEndpoints.length !== endpointData["All"].length}
            />
        </HorizontalStack>
    )

    const handleSelectedTab = (selectedIndex) => {
        setLoading(true)
        setSelected(selectedIndex)
        setTimeout(()=>{
            setLoading(false)
        },200)
    }

    return (
        <PageWithMultipleCards
            title={
                <Text variant='headingLg' truncate>{pageTitle}</Text>
            }
            backUrl="/dashboard/observe/inventory"
            secondaryActions={secondaryActionsComponent}
            components={
                loading ? [<SpinnerCentered key="loading" />] :
                    [
                        <div className="apiEndpointsTable" key="table">
                            <GithubSimpleTable
                                key="table"
                                pageLimit={50}
                                data={endpointData[selectedTab]}
                                sortOptions={sortOptions}
                                resourceName={resourceName}
                                filters={[]}
                                disambiguateLabel={disambiguateLabel}
                                headers={headers}
                                getStatus={() => { return "warning" }}
                                selected={selected}
                                onRowClick={handleRowClick}
                                onSelect={handleSelectedTab}
                                getFilteredItems={getFilteredItems}
                                mode={IndexFiltersMode.Default}
                                headings={headings}
                                useNewRow={true}
                                condensedHeight={true}
                                tableTabs={tableTabs}
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
                            headers={transform.getDetailsHeaders()}
                            getStatus={() => { return "warning" }}
                            isGptActive={isGptActive}
                        />
                    ]}
        />
    )
}

export default ApiEndpoints

import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, HorizontalStack, Button, Popover, Modal, IndexFiltersMode, VerticalStack, Box, Checkbox } from "@shopify/polaris"
import api from "../api"
import { useEffect, useState } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import {useLocation, useParams } from "react-router-dom"
import { saveAs } from 'file-saver'

import "./api_inventory.css"
import ApiDetails from "./ApiDetails"
import UploadFile from "../../../components/shared/UploadFile"
import RunTest from "./RunTest"
import ObserveStore from "../observeStore"
import WorkflowTests from "./WorkflowTests"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout"
import dashboardFunc from "../../transform"
import settingsRequests from "../../settings/api"
import PersistStore from "../../../../main/PersistStore"
import transform from "../transform"
import { CellType } from "../../../components/tables/rows/GithubRow"
import {ApiGroupModal, Operation} from "./ApiGroupModal"
import TooltipText from "../../../components/shared/TooltipText"
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout"
import { ENDPOINTS_PAGE_DOCS_URL } from "../../../../main/onboardingData"
import {TestrunsBannerComponent} from "../../testing/TestRunsPage/TestrunsBannerComponent"
import GetPrettifyEndpoint from "../GetPrettifyEndpoint"
import SourceLocation from "./component/SourceLocation"
import useTable from "../../../components/tables/TableContext"
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip"

const headings = [
    {
        text: "Endpoint",
        value: "endpointComp",
        title: "Api endpoints",
        textValue: "endpoint",
        sortActive: true
    },
    {
        text: "Risk score",
        title: <HeadingWithTooltip 
            title={"Risk score"}
            content={"Risk score is calculated based on the amount of sensitive information the API shares and its current status regarding security issues."}
        />,
        value: "riskScoreComp",
        textValue: "riskScore",
        sortActive: true,
        
    },
    {
        text: "Hostname",
        value: 'hostName',
        title: "Hostname",
        maxWidth: '100px',
        type: CellType.TEXT,
    },
    {
        text: 'Access Type',
        value: 'access_type', 
        title:"Access Type",
        showFilter: true,
        type: CellType.TEXT,
        tooltipContent: "Access type of the API. It can be public, private, partner"
    },
    {
        text: 'Auth Type',
        title: 'Auth type',
        value: 'auth_type',
        showFilter: true,
        textValue: 'authTypeTag',
        tooltipContent: "Authentication type of the API."
    },
    {
        text: 'Sensitive Params',
        title: 'Sensitive params',
        value: 'sensitiveTagsComp',
        filterKey: 'sensitiveTags',
        showFilter: true,
        textValue: "sensitiveDataTags"
    },
    {
        text: 'Last Seen',
        title: <HeadingWithTooltip 
                title={"Last Seen"}
                content={"Time when API was last detected in traffic."}
            />,
        value: 'last_seen',
        isText: true,
        type: CellType.TEXT,
        sortActive: true,
        
    },
    {
        text: "Source location",
        value: "sourceLocationComp",
        textValue: "sourceLocation",
        title: "Source location",
        tooltipContent: "Exact location of the URL in case detected from the source code."    
    }
]

let headers = JSON.parse(JSON.stringify(headings))
headers.push({
    text: 'Method',
    filterKey: 'method',
    showFilter: true,
    textValue: 'method',
    sortActive: true
})


const sortOptions = [
    { label: 'Risk Score', value: 'riskScore asc', directionLabel: 'Highest', sortKey: 'riskScore', columnIndex: 2},
    { label: 'Risk Score', value: 'riskScore desc', directionLabel: 'Lowest', sortKey: 'riskScore', columnIndex: 2},
    { label: 'Method', value: 'method asc', directionLabel: 'A-Z', sortKey: 'method', columnIndex: 8 },
    { label: 'Method', value: 'method desc', directionLabel: 'Z-A', sortKey: 'method', columnIndex: 8 },
    { label: 'Endpoint', value: 'endpoint asc', directionLabel: 'A-Z', sortKey: 'endpoint', columnIndex: 1 },
    { label: 'Endpoint', value: 'endpoint desc', directionLabel: 'Z-A', sortKey: 'endpoint', columnIndex: 1 },
    { label: 'Last seen', value: 'lastSeenTs asc', directionLabel: 'Newest', sortKey: 'lastSeenTs', columnIndex: 7 },
    { label: 'Last seen', value: 'lastSeenTs desc', directionLabel: 'Oldest', sortKey: 'lastSeenTs', columnIndex: 7 }
];

function ApiEndpoints() {

    const params = useParams()
    const location = useLocation()
    const apiCollectionId = params.apiCollectionId

    const showDetails = ObserveStore(state => state.inventoryFlyout)
    const setShowDetails = ObserveStore(state => state.setInventoryFlyout)
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const allCollections = PersistStore(state => state.allCollections);

    const pageTitle = collectionsMap[apiCollectionId]

    const [apiEndpoints, setApiEndpoints] = useState([])
    const [apiInfoList, setApiInfoList] = useState([])
    const [unusedEndpoints, setUnusedEndpoints] = useState([])
    const [showEmptyScreen, setShowEmptyScreen] = useState(false)
    const [runTests, setRunTests ] = useState(false)

    const [endpointData, setEndpointData] = useState([])
    const [selectedTab, setSelectedTab] = useState("all")
    const [selected, setSelected] = useState(0)
    const [loading, setLoading] = useState(true)
    const [apiDetail, setApiDetail] = useState({})
    const [exportOpen, setExportOpen] = useState(false)

    const filteredEndpoints = ObserveStore(state => state.filteredItems)
    const setFilteredEndpoints = ObserveStore(state => state.setFilteredItems)
    const coverageInfo = PersistStore(state => state.coverageMap)

    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const [isGptActive, setIsGptActive] = useState(false)
    const [redacted, setIsRedacted] = useState(false)
    const [showRedactModal, setShowRedactModal] = useState(false)
    const [tableLoading, setTableLoading] = useState(false)

    const queryParams = new URLSearchParams(location.search);
    const selectedUrl = queryParams.get('selected_url')
    const selectedMethod = queryParams.get('selected_method')

    const definedTableTabs = ['All', 'New', 'Sensitive', 'High risk', 'No auth', 'Shadow']

    const { tabsInfo } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, endpointData)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

    async function fetchData() {
        setLoading(true)
        let apiCollectionData = await api.fetchAPICollection(apiCollectionId)
        setShowEmptyScreen(apiCollectionData.data.endpoints.length === 0)
        setIsRedacted(apiCollectionData.redacted)
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
        let allEndpoints = func.mergeApiInfoAndApiCollection(apiEndpointsInCollection, apiInfoListInCollection, collectionsMap)

        // handle code analysis endpoints
        const codeAnalysisCollectionInfo = apiCollectionData.codeAnalysisCollectionInfo
        const codeAnalysisApisMap = codeAnalysisCollectionInfo.codeAnalysisApisMap
        let shadowApis = []

        if (codeAnalysisApisMap) {
            // Don't show empty screen if there are codeanalysis endpoints present
            if (codeAnalysisApisMap && Object.keys(codeAnalysisApisMap).length > 0) {
                setShowEmptyScreen(false)
            }
            shadowApis = { ...codeAnalysisApisMap }

            // Find shadow endpoints and map api endpoint location
            allEndpoints.forEach(api => {
                let apiEndpoint = transform.getTruncatedUrl(api.endpoint)
                // ensure apiEndpoint does not end with a slash
                if (apiEndpoint !== "/" && apiEndpoint.endsWith("/")) 
                    apiEndpoint = apiEndpoint.slice(0, -1)

                const apiKey = api.method + " " + apiEndpoint

                if (Object.hasOwn(codeAnalysisApisMap, apiKey)) {
                    const codeAnalysisApi = codeAnalysisApisMap[apiKey]
                    const location = codeAnalysisApi.location
                    api.sourceLocation = codeAnalysisApi.location.filePath
                    api.sourceLocationComp = <SourceLocation location={location} />

                    delete shadowApis[apiKey]
                }
                else {
                    api.source_location = ""
                }
            })

            shadowApis = Object.entries(shadowApis).map(([ codeAnalysisApiKey, codeAnalysisApi ]) => {
                const { method, endpoint, location } = codeAnalysisApi

                return {
                    id: codeAnalysisApiKey,
                    endpointComp: <GetPrettifyEndpoint method={method} url={endpoint} isNew={false} />,
                    method: method,
                    endpoint: endpoint,
                    codeAnalysisEndpoint: true,
                    sourceLocation: location.filePath, 
                    sourceLocationComp: <SourceLocation location={location} />,
                }
            })
        }

        const prettifyData = transform.prettifyEndpointsData(allEndpoints)

        // append shadow endpoints to all endpoints
        data['all'] = [ ...prettifyData, ...shadowApis ]
        data['sensitive'] = prettifyData.filter(x => x.sensitive && x.sensitive.size > 0)
        data['high_risk'] = prettifyData.filter(x=> x.riskScore >= 4)
        data['new'] = prettifyData.filter(x=> x.isNew)
        data['no_auth'] = prettifyData.filter(x => x.open)
        data['shadow'] = [ ...shadowApis ]

        setEndpointData(data)
        setSelectedTab("all")
        setSelected(0)

        setApiEndpoints(apiEndpointsInCollection)
        setApiInfoList(apiInfoListInCollection)
        setUnusedEndpoints(unusedEndpointsInCollection)

        setLoading(false)
    }

    useEffect(() => {
        if (!endpointData || !endpointData["all"] || !selectedUrl || !selectedMethod) return
        let allData = endpointData["all"]

        const selectedApiDetail = allData.filter((x) => {
            return selectedUrl === x.endpoint && selectedMethod === x.method
        })

        if (!selectedApiDetail || selectedApiDetail.length === 0)  return

        setApiDetail(selectedApiDetail[0])
        setShowDetails(true)

    }, [selectedUrl, selectedMethod, endpointData])

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
    }, [apiCollectionId])

    const resourceName = {
        singular: 'endpoint',
        plural: 'endpoints',
    };

    const getFilteredItems = (filteredItems) => {
        setFilteredEndpoints(filteredItems)
    }

    function handleRowClick(data) {
        // Don't show api details for Code analysis endpoints
        if (data.codeAnalysisEndpoint) 
            return
        
        let tmp = { ...data, endpointComp: "", sensitiveTagsComp: "" }
        
        const sameRow = func.deepComparison(apiDetail, tmp);
        if (!sameRow) {
            setShowDetails(true)
        } else {
            setShowDetails(!showDetails)
        }
        setApiDetail((prev) => {
            if (sameRow) {
                return prev;
            }
            return { ...tmp }
        })
    }



    function handleRefresh() {
        fetchData()
        func.setToast(true, false, "Endpoints refreshed")
    }

    function computeApiGroup() {
        api.computeCustomCollections(pageTitle);
        func.setToast(true, false, "API group is being computed.")
    }

    function redactCheckBoxClicked(){
        if(!redacted){
            setShowRedactModal(true)
        } else {
            setIsRedacted(false)
            redactCollection();
        }
    }

    function redactCollection(){
        setShowRedactModal(false)
        var updatedRedacted = !redacted;
        api.redactCollection(apiCollectionId, updatedRedacted).then(resp => {
            setIsRedacted(updatedRedacted)
            func.setToast(true, false, updatedRedacted ? "Collection redacted" : "Collection unredacted")
        })
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
        func.setToast(true, false, <div data-testid="openapi_spec_download_message">OpenAPI spec downloaded successfully</div>)
    }

    function exportCsv() {
        if (!loading) {
            let headerTextToValueMap = Object.fromEntries(headers.map(x => [x.text, x.type === CellType.TEXT ? x.value : x.textValue]).filter(x => x[0].length > 0));

            let csv = Object.keys(headerTextToValueMap).join(",") + "\r\n"
            const allEndpoints = endpointData['all']
            allEndpoints.forEach(i => {
                csv += Object.values(headerTextToValueMap).map(h => (i[h] || "-")).join(",") + "\r\n"
            })
            let blob = new Blob([csv], {
                type: "application/csvcharset=UTF-8"
            });
            saveAs(blob, ("All endopints") + ".csv");
            func.setToast(true, false, <div data-testid="csv_download_message">CSV exported successfully</div>)
        }
    }

    async function exportPostman() {
        const result = await api.exportToPostman(apiCollectionId)
        if (result)
        func.setToast(true, false, "We have initiated export to Postman, checkout API section on your Postman app in sometime.")
    }

    const [showWorkflowTests, setShowWorkflowTests] = useState(false)

    function toggleWorkflowTests() {
        setShowWorkflowTests(!showWorkflowTests)
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
    const secondaryActionsComponent = (
        <HorizontalStack gap="2">

            <Popover
                active={exportOpen}
                activator={(
                    <Button onClick={() => setExportOpen(true)} disclosure>
                        <div data-testid="more_actions_button">More Actions</div>
                    </Button>
                )}
                autofocusTarget="first-node"
                onClose={() => { setExportOpen(false) }}
                preferredAlignment="right"
            >
                <Popover.Pane fixed>
                    <Popover.Section>
                        <VerticalStack gap={2}>
                            <div onClick={handleRefresh} style={{cursor: 'pointer'}}>
                                <Text fontWeight="regular" variant="bodyMd">Refresh</Text>
                            </div>
                            {
                                allCollections.filter(x => {
                                    return x.id == apiCollectionId && x.type == "API_GROUP"
                                }).length > 0 ?
                                    <div onClick={computeApiGroup} style={{ cursor: 'pointer' }}>
                                        <Text fontWeight="regular" variant="bodyMd">Re-compute api group</Text>
                                    </div> :
                                    null
                            }
                            { allCollections.filter(x => {
                                    return x.id == apiCollectionId && x.type == "API_GROUP"
                                }).length == 0 ?
                                <UploadFile
                                fileFormat=".har"
                                fileChanged={file => handleFileChange(file)}
                                tooltipText="Upload traffic(.har)"
                                label={<Text fontWeight="regular" variant="bodyMd">Upload traffic</Text>}
                                primary={false} 
                                /> : null
                            }
                        </VerticalStack>
                    </Popover.Section>
                    <Popover.Section>
                        <VerticalStack gap={2}>
                            <Text>Export as</Text>
                                <VerticalStack gap={1}>
                                <div data-testid="openapi_spec_option" onClick={exportOpenApi} style={{cursor: 'pointer'}}>
                                    <Text fontWeight="regular" variant="bodyMd">OpenAPI spec</Text>
                                </div>
                                <div data-testid="postman_option" onClick={exportPostman} style={{cursor: 'pointer'}}>
                                    <Text fontWeight="regular" variant="bodyMd">Postman</Text>
                                </div>
                                <div data-testid="csv_option" onClick={exportCsv} style={{cursor: 'pointer'}}>
                                    <Text fontWeight="regular" variant="bodyMd">CSV</Text>
                                </div>
                            </VerticalStack>
                        </VerticalStack>
                    </Popover.Section>
                    <Popover.Section>
                        <VerticalStack gap={2}>
                            <Text>Others</Text>
                                <VerticalStack gap={1}>
                                <Checkbox
                                    label='Redact'
                                    checked={redacted}
                                    onChange={() => redactCheckBoxClicked()}
                                />
                            </VerticalStack>
                        </VerticalStack>
                    </Popover.Section>
                    <Popover.Section>
                        <VerticalStack gap={2}>
                            <div onClick={toggleWorkflowTests} style={{ cursor: 'pointer' }}>
                                <Text fontWeight="regular" variant="bodyMd">
                                    {`${showWorkflowTests ? "Hide" : "Show"} workflow tests`}
                                </Text>
                            </div>
                        </VerticalStack>
                    </Popover.Section>
                </Popover.Pane>
            </Popover>

            {isGptActive ? <Button onClick={displayGPT} disabled={showEmptyScreen}>Ask AktoGPT</Button>: null}
                    
            <RunTest
                apiCollectionId={apiCollectionId}
                endpoints={filteredEndpoints}
                filtered={loading ? false : filteredEndpoints.length !== endpointData["all"].length}
                runTestFromOutside={runTests}
                disabled={showEmptyScreen}
            />
        </HorizontalStack>
    )

    const handleSelectedTab = (selectedIndex) => {
        setTableLoading(true)
        setSelected(selectedIndex)
        setTimeout(()=>{
            setTableLoading(false)
        },200)
    }

    const [showApiGroupModal, setShowApiGroupModal] = useState(false)
    const [apis, setApis] = useState([])
    const [actionOperation, setActionOperation] = useState(Operation.ADD)

    function handleApiGroupAction(selectedResources, operation){

        setActionOperation(operation)
        setApis(selectedResources)
        setShowApiGroupModal(true);
    }

    function toggleApiGroupModal(){
        setShowApiGroupModal(false);
    }

    const promotedBulkActions = (selectedResources) => {

        let isApiGroup = allCollections.filter(x => {
            return x.id == apiCollectionId && x.type == "API_GROUP"
        }).length > 0

        let ret = []
        if (isApiGroup) {
            ret.push(
                {
                    content: 'Remove from API group',
                    onAction: () => handleApiGroupAction(selectedResources, Operation.REMOVE)
                }
            )
        } else {
            ret.push({
                content: <div data-testid="add_to_api_group_button">Add to API group</div>,
                onAction: () => handleApiGroupAction(selectedResources, Operation.ADD)
            })
        }
        return ret;
    }

    let modal = (
        <Modal
            open={showRedactModal}
            onClose={() => setShowRedactModal(false)}
            title="Note!"
            primaryAction={{
                content: 'Enable',
                onAction: redactCollection
            }}
            key="redact-modal"
        >
            <Modal.Section>
                <Text>When enabled, existing sample payload values for this collection will be deleted, and data in all the future payloads for this collection will be redacted. Please note that your API Inventory, Sensitive data etc. will be intact. We will simply be deleting the sample payload values.</Text>
            </Modal.Section>
        </Modal>
    )

      const components = [
        loading ? [<SpinnerCentered key="loading" />] : (
            showWorkflowTests ? [
                <WorkflowTests
                key={"workflow-tests"}
                apiCollectionId={apiCollectionId}
                endpointsList={loading ? [] : endpointData["all"]}
            />
             ] : showEmptyScreen ? [
                <EmptyScreensLayout key={"emptyScreen"}
                            iconSrc={"/public/file_plus.svg"}
                            headingText={"Discover APIs to get started"}
                            description={"Your API collection is currently empty. Import APIs from other collections now."}
                            buttonText={"Import from other collections"}
                            redirectUrl={"/dashboard/observe/inventory"}
                            learnText={"inventory"}
                            docsUrl={ENDPOINTS_PAGE_DOCS_URL}
                />] :[
                    (coverageInfo[apiCollectionId] === 0  || !(coverageInfo.hasOwnProperty(apiCollectionId))? <TestrunsBannerComponent key={"testrunsBanner"} onButtonClick={() => setRunTests(true)} isInventory={true} /> : null),
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
                          selectable={true}
                          promotedBulkActions={promotedBulkActions}
                          loading={tableLoading || loading}
                      />
                      <Modal large open={isGptScreenActive} onClose={() => setIsGptScreenActive(false)} title="Akto GPT">
                          <Modal.Section flush>
                              <AktoGptLayout prompts={prompts} closeModal={() => setIsGptScreenActive(false)} />
                          </Modal.Section>
                      </Modal>
                  </div>,
                  <ApiDetails
                      key="details"
                      showDetails={showDetails && apiDetail && Object.keys(apiDetail).length > 0}
                      setShowDetails={setShowDetails}
                      apiDetail={apiDetail}
                      headers={transform.getDetailsHeaders()}
                      getStatus={() => { return "warning" }}
                      isGptActive={isGptActive}
                  />,
                  <ApiGroupModal
                      key="api-group-modal"
                      showApiGroupModal={showApiGroupModal}
                      toggleApiGroupModal={toggleApiGroupModal}
                      apis={apis}
                      operation={actionOperation}
                      currentApiGroupName={pageTitle}
                      fetchData={fetchData}
                  />,
                  modal
            ]
        )
      ]

    return (
        <PageWithMultipleCards
            title={
                <Box maxWidth="35vw">
                    <TooltipText tooltip={pageTitle} text={pageTitle} textProps={{variant:'headingLg'}} />
                </Box>
            }
            backUrl="/dashboard/observe/inventory"
            secondaryActions={secondaryActionsComponent}
            components={components}
        />
    )
}

export default ApiEndpoints
import { Badge, Box, Button, Checkbox, Divider, Frame, HorizontalGrid, HorizontalStack, LegacyTabs, Modal, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import { ChevronUpMinor } from "@shopify/polaris-icons"

import { useEffect, useRef, useState } from "react";
import DropdownSearch from "../../../components/shared/DropdownSearch";
import TitleWithInfo from "../../../components/shared/TitleWithInfo";
import Dropdown from "../../../components/layouts/Dropdown";
import api from "../../testing/api"
import testEditorRequests from "../api";
import func from "@/util/func";
import TestEditorStore from "../testEditorStore"
import "../TestEditor.css"
import TestRunResultPage from "../../testing/TestRunResultPage/TestRunResultPage";
import PersistStore from "../../../../main/PersistStore";
import editorSetup from "./editor_config/editorSetup";
import SampleData from "../../../components/shared/SampleData";
import transform from "../../../components/shared/customDiffEditor";
import testingFunc from "../../testing/transform";
import { mapLabel, getDashboardCategory } from "../../../../main/labelHelper";
import EmptySampleApi from "./EmptySampleApi";
import Store from "../../../store";
import LocalStore from "../../../../main/LocalStorageStore";
import observeFunc from "../../observe/transform";
import { getCallbackCheckError, getResultColor, getResultDescription, isCallbackTest, markTestResultVulnerable, normalizeCallbackUuids, startCallbackPolling, startPlaygroundPolling } from "../webhookCallbackUtils"

const SampleApi = () => {

    const setToastConfig = Store(state => state.setToastConfig)
    const allCollections = PersistStore(state => state.allCollections);
    const [selected, setSelected] = useState(0);
    const [selectApiActive, setSelectApiActive] = useState(false)
    const [selectedCollectionId, setSelectedCollectionId] = useState(null)
    const [apiEndpoints, setApiEndpoints] = useState([])
    const [selectedApiEndpoint, setSelectedApiEndpoint] = useState(null)
    const [sampleData, setSampleData] = useState(null)
    const [copyCollectionId, setCopyCollectionId] = useState(null)
    const [copySelectedApiEndpoint, setCopySelectedApiEndpoint] = useState(null)
    const [sampleDataList, setSampleDataList] = useState(null)
    const [loading, setLoading] = useState(false)
    const [testResult,setTestResult] = useState(null)
    const [showTestResult, setShowTestResult] = useState(false);
    const [editorData, setEditorData] = useState({message: ''})
    const [showEmptyLayout, setShowEmptyLayout] = useState(false)
    const [callbackStatus, setCallbackStatus] = useState(null)
    const [isCheckingCallback, setIsCheckingCallback] = useState(false)

    const currentContent = TestEditorStore(state => state.currentContent)
    const selectedTest = TestEditorStore(state => state.selectedTest)
    const vulnerableRequestsObj = TestEditorStore(state => state.vulnerableRequestsMap)
    const defaultRequest = TestEditorStore(state => state.defaultRequest)
    const selectedSampleApi = PersistStore(state => state.selectedSampleApi)
    const setSelectedSampleApi = PersistStore(state => state.setSelectedSampleApi)
    const selectedRole = TestEditorStore(state => state.selectedRole)
    const setSelectedRole = TestEditorStore(state => state.setSelectedRole)

    const tabs = [{ id: 'request', content: 'Request' }, { id: 'response', content: 'Response'}];
    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)
    const [conversationsList, setConversationsList] = useState([])

    const [isChatBotOpen, setIsChatBotOpen] = useState(false)
    const [chatBotModal, setChatBotModal] = useState(false)
    const subCategoryMap = LocalStore(state => state.subCategoryMap)
    const [testRoles, setTestRoles] = useState([])
    const [testRolesOptions, setTestRolesOptions] = useState([])
    const [miniTestingServiceNames, setMiniTestingServiceNames] = useState([])
    const [selectedMiniTestingServiceName, setSelectedMiniTestingServiceName] = useState(null)
    const [hasOverriddenTestAppUrl, setHasOverriddenTestAppUrl] = useState(false)
    const [overriddenTestAppUrl, setOverriddenTestAppUrl] = useState("")

    useEffect(() => {
        const fetchRoles = async () => {
            try {
                const response = await api.fetchTestRoles()
                if (response && response.testRoles) {
                    setTestRoles(response.testRoles)
                    // use hexId as value (consistent with RunTestConfiguration) and name as label
                    const options = response.testRoles.map(role => ({
                        label: role.name,
                        value: role.hexId
                    }))
                    setTestRolesOptions(options)
                }
            } catch (error) {
                func.setToast(true, true, "Error fetching test roles");
            }
        }
        fetchRoles()
    }, [])

    useEffect(() => {
        api.fetchMiniTestingServiceNames().then(({ miniTestingServiceNames: names }) => {
            const options = (names || []).map(name => ({ label: name, value: name }));
            setMiniTestingServiceNames(options);
            if (options.length > 0 && selectedMiniTestingServiceName == null) {
                setSelectedMiniTestingServiceName(options[0].value);
            }
        });
    }, []);

    useEffect(()=>{
        if(showEmptyLayout) return
        let testId = selectedTest.value
        let sampleData = null
        if(sampleDataList?.length > 0) {
            sampleData = {
                apiCollectionId: sampleDataList[0].id.apiCollectionId,
                method: {_name: sampleDataList[0].id.method},
                url: sampleDataList[0].id.url
            }
        }
        let selectedUrl = sampleData ? sampleData : Object.keys(selectedSampleApi).length > 0 ? selectedSampleApi : vulnerableRequestsObj?.[testId]
        setSelectedCollectionId(null)
        setCopyCollectionId(null)
        setTestResult(null)
        setConversationsList([])
        if(!selectedUrl){
            selectedUrl = defaultRequest
        }
        setTimeout(()=> {
            setSelectedCollectionId(selectedUrl.apiCollectionId)
            setCopyCollectionId(selectedUrl.apiCollectionId)
            setSelectedApiEndpoint(func.toMethodUrlString({method:selectedUrl.method._name, url:selectedUrl.url}))
        },0)
        setTimeout(() => {
            setCopySelectedApiEndpoint(func.toMethodUrlString({method:selectedUrl.method._name, url:selectedUrl.url}))
        }, 300)
        
    },[selectedTest])

    useEffect(() => {
        let testId = selectedTest.value
        const mappedEndpoint = vulnerableRequestsObj?.[testId]
        if(sampleDataList == null) {
            return
        }

        if(sampleDataList.length === 0) {
            setTimeout(()=> {
                setShowEmptyLayout(true)
                setSelectedCollectionId(0)
                setCopyCollectionId(0)
                setSelectedApiEndpoint('No endpoint found!')
            },0)
            setTimeout(() => {
                setCopySelectedApiEndpoint('No endpoint found!')
            }, 300)
            return
        }

        setShowEmptyLayout(false)

        const sampleDataId = sampleDataList[0].id

        const collectionId = sampleDataId.apiCollectionId
        const endpoint = func.toMethodUrlString({method: sampleDataId.method, url: sampleDataId.url})

        if(mappedEndpoint?.apiCollectionId === collectionId && mappedEndpoint?.method?._name === sampleDataId.method && mappedEndpoint?.url === sampleDataId.url) {
            return
        }
        
        setTimeout(()=> {
            setSelectedCollectionId(collectionId)
            setCopyCollectionId(collectionId)
            setSelectedApiEndpoint(endpoint)
        },0)
        setTimeout(() => {
            setCopySelectedApiEndpoint(endpoint)
        }, 300)

    }, [sampleDataList])

    useEffect(() => {
        fetchApiEndpoints(copyCollectionId)
        setCopySelectedApiEndpoint(null);
        setTestResult(null)
        setConversationsList([])
    }, [copyCollectionId])

    useEffect(() => {
        if (selectedCollectionId && selectedApiEndpoint) {
            fetchSampleData(selectedCollectionId, func.toMethodUrlObject(selectedApiEndpoint).url, func.toMethodUrlObject(selectedApiEndpoint).method)
        }else{
            setEditorData({message: ''})
        }
        setTestResult(null)
        setConversationsList([])
    }, [selectedApiEndpoint])

    useEffect(()=> {
        if(testResult){
            const hasAgentChat = testResult?.agentConversationResults?.length > 0
            if (hasAgentChat) {
                setChatBotModal(true)
                setShowTestResult(false)
            } else {
                setChatBotModal(false)
                const hasCallbackTokens = normalizeCallbackUuids(testResult).length > 0 && isCallbackTest(testResult)
                setShowTestResult(!hasCallbackTokens)
            }
        } else {
            setShowTestResult(false);
        }
    }, [testResult])

    useEffect(()=>{
        if(currentContent && currentContent.length > 0 && currentContent.includes("test_mode") && currentContent.includes(": agent")) {
            setIsChatBotOpen(true)
        }else{
            setIsChatBotOpen(false)
        }
    }, [currentContent])


    const handleTabChange = (selectedTabIndex) => {
        setSelected(selectedTabIndex)
        if (sampleData) {
            let localEditorData = ""
            if (selectedTabIndex === 0) {
                localEditorData = transform.formatData(sampleData?.requestJson, "http")
            } else {
                localEditorData = transform.formatData(sampleData?.responseJson, "http")
            }
            setEditorData({message: localEditorData})
        }else{
            setEditorData({message: ''})
        }
    }


    const activatedCollections = allCollections.filter(collection => collection.deactivated === false)
    const allCollectionsOptions = activatedCollections.map(collection => {
        return {
            label: collection.displayName,
            value: collection.id
        }
    })

    const fetchApiEndpoints = async (collectionId) => {
        const apiEndpointsResponse = await api.fetchCollectionWiseApiEndpoints(collectionId)
        if (apiEndpointsResponse) {
            setApiEndpoints(apiEndpointsResponse.listOfEndpointsInCollection)
        }
    }

    const apiEndpointsOptions = apiEndpoints.map(apiEndpoint => {
        let strLabel = func.toMethodUrlString({...apiEndpoint, shouldParse: true});
        let strValue = func.toMethodUrlString({...apiEndpoint, shouldParse: false});
        return {
            id: strValue,
            label: strLabel,
            value: strValue
        }
    })

    const fetchSampleData = async (collectionId, apiEndpointUrl, apiEndpointMethod) => {
        setShowEmptyLayout(false)
        const sampleDataResponse = await testEditorRequests.fetchSampleData(collectionId, apiEndpointUrl, apiEndpointMethod)
        if (sampleDataResponse) {
            if (sampleDataResponse.sampleDataList.length > 0 && sampleDataResponse.sampleDataList[0].samples && sampleDataResponse.sampleDataList[0].samples.length > 0) {
                const sampleDataJson = JSON.parse(sampleDataResponse.sampleDataList[0].samples[sampleDataResponse.sampleDataList[0].samples.length - 1])
                const requestJson = func.requestJson(sampleDataJson, [])
                const responseJson = func.responseJson(sampleDataJson, [])
                setSampleData({ requestJson, responseJson })
                setEditorData({message: transform.formatData(requestJson, "http")})
                setTimeout(()=> {
                    setSampleDataList(sampleDataResponse.sampleDataList)
                },0)

                setSelected(0)
            }else{
                setSampleDataList(sampleDataResponse.sampleDataList)
                setEditorData({message: ''})
                setSampleData({})
            }
        }else{
            setSampleDataList([])
            setSampleData({})
            setEditorData({message: ''})
        }
    }

    const toggleSelectApiActive = () => setSelectApiActive(prev => !prev)
    const saveFunc = () =>{
        setSelectedApiEndpoint(copySelectedApiEndpoint)
        const urlObj = func.toMethodUrlObject(copySelectedApiEndpoint)
        const sampleApi = {
            apiCollectionId :copyCollectionId, 
            url: urlObj.url,
            method:{
                "_name": urlObj.method
            }
        }
        setSelectedSampleApi(sampleApi)
        setSelectedCollectionId(copyCollectionId)
        toggleSelectApiActive()
    }

    const intervalRef = useRef(null);


    const stopCallbackPollingRef = useRef(null);

    const stopCallbackPolling = () => {
        if (stopCallbackPollingRef.current) {
            stopCallbackPollingRef.current()
            stopCallbackPollingRef.current = null
        }
        setIsCheckingCallback(false)
    }

    const startCallbackPollingWithUuids = (uuids) => {
        stopCallbackPolling()
        setCallbackStatus("pending")
        setIsCheckingCallback(true)
        stopCallbackPollingRef.current = startCallbackPolling({
            uuids,
            fetchStatus: testEditorRequests.fetchCallbackStatusForTestEditor,
            onHit: () => {
                stopCallbackPollingRef.current = null
                setIsCheckingCallback(false)
                setCallbackStatus("hit")
                setTestResult(prev => markTestResultVulnerable(prev))
            },
            onNotHit: () => {
                stopCallbackPollingRef.current = null
                setIsCheckingCallback(false)
                setCallbackStatus("not_hit")
            },
        })
    }

    const callbackUuidsFromResult = testResult ? normalizeCallbackUuids(testResult) : []
    const showCallbackButton = testResult && isCallbackTest(testResult) && callbackUuidsFromResult.length > 0 && !testResult?.testingRunResult?.vulnerable

    const checkCallbackHit = () => {
        const uuids = callbackUuidsFromResult
        const err = getCallbackCheckError(testResult, uuids)
        if (err) {
            setToastConfig({ isActive: true, isError: true, message: err })
            return
        }
        startCallbackPollingWithUuids(uuids)
    }
    const runTest = async()=>{
        if(isChatBotOpen) {
            setChatBotModal(true)
            return;
        }
        setLoading(true)
        setCallbackStatus(null)
        const apiKeyInfo = {
            ...func.toMethodUrlObject(selectedApiEndpoint),
            apiCollectionId: selectedCollectionId
        }


        try {
            const testingRunConfig = {
            overriddenTestAppUrl: hasOverriddenTestAppUrl ? overriddenTestAppUrl : "",
            testRoleId: selectedRole || undefined
        };
        let resp = await testEditorRequests.runTestForTemplate(currentContent, apiKeyInfo, sampleDataList, selectedMiniTestingServiceName, testingRunConfig)
            if(resp?.testingRunPlaygroundHexId != null) {
                await new Promise((resolve) => {
                    intervalRef.current = startPlaygroundPolling({
                        playgroundHexId: resp.testingRunPlaygroundHexId,
                        fetchStatus: testEditorRequests.fetchTestingRunPlaygroundStatus,
                        onComplete: (result) => {
                            if (intervalRef.current) intervalRef.current()
                            intervalRef.current = null
                            setTestResult(result)
                            const uuids = normalizeCallbackUuids(result)
                            if (uuids.length > 0 && isCallbackTest(result) && !result?.testingRunResult?.vulnerable) {
                                startCallbackPollingWithUuids(uuids)
                            }
                            resolve()
                        },
                        onTimeout: () => {
                            intervalRef.current = null
                            setToastConfig({ isActive: true, isError: true, message: "Error while running the test" })
                            resolve()
                        },
                    })
                })
            }
            else {
                setTestResult(resp)
                const uuids = normalizeCallbackUuids(resp)
                if (uuids.length > 0 && isCallbackTest(resp) && !resp?.testingRunResult?.vulnerable) {
                    startCallbackPollingWithUuids(uuids)
                }
            }
            if(resp?.agentConversationResults?.length > 0){
                let result = testingFunc.prepareConversationsList(resp?.agentConversationResults)
                setConversationsList(result.conversations)
            }
        } catch (err){
        }
        setLoading(false)
    }

    useEffect(() => {
        return () => {
            if (intervalRef.current) intervalRef.current()
            if (stopCallbackPollingRef.current) stopCallbackPollingRef.current()
        }
    }, [])

    const showResults = () => {
        if(testResult?.agentConversationResults?.length > 0){
            setChatBotModal(!chatBotModal)
            return;
        }
        setShowTestResult(!showTestResult);
    }

    const getColor = () => getResultColor(testResult, callbackStatus)
    const getDescription = () => getResultDescription({ testResult, callbackStatus, isChatBotOpen, mapLabel, getDashboardCategory })

    const closeModal = () => {
        setShowTestResult(!showTestResult)
        editorSetup.setEditorTheme();
    }

    const resultComponent = (
        <Box background={getColor()} width="100%" padding={"2"}>
            <HorizontalStack gap="2" blockAlign="center" wrap align="space-between">
                <Box className="test-result-strip-label">
                    <Button id={"test-results"} removeUnderline monochrome plain
                        onClick={testResult ? showResults : () => {}}
                        icon={testResult ? ChevronUpMinor : undefined}>
                        {getDescription()}
                    </Button>
                </Box>
                {showCallbackButton && (
                    <Tooltip content="Re-check if the app triggered the callback (e.g. slow SSRF). The result above is from the validate block at run time.">
                        <Button size="slim" onClick={checkCallbackHit} loading={isCheckingCallback} disabled={isCheckingCallback}>
                            {isCheckingCallback ? "Checking webhook hit..." : "Check webhook hit"}
                        </Button>
                    </Tooltip>
                )}
            </HorizontalStack>
        </Box>
    )

    const currentSeverity = selectedTest?.value !== undefined ? subCategoryMap[selectedTest?.value]?.superCategory?.severity._name : "HIGH"
    const copyEndpointLabel = copySelectedApiEndpoint == null ? "No endpoints selected" : func.toMethodUrlString({ ...func.toMethodUrlObject(copySelectedApiEndpoint), shouldParse: true })

    return (
        <div>
            <div className="editor-header">
                <div className="req-resp-tabs">
                    <LegacyTabs tabs={tabs} selected={selected} onSelect={handleTabChange} fitted />
                </div>
                <HorizontalStack gap={2}>
                    <Button id={"select-sample-api"} onClick={toggleSelectApiActive} size="slim">
                        <Box maxWidth="200px">
                            <Tooltip content={copyEndpointLabel} hoverDelay={"100"}>
                                <Text variant="bodyMd" truncate>{copyEndpointLabel}</Text>
                            </Tooltip>
                        </Box>
                    </Button>
                    <Button id={"run-test"} disabled={showEmptyLayout || editorData?.message?.length === 0} loading={loading} primary onClick={runTest} size="slim">{isChatBotOpen ? "Chat" : mapLabel('Run test', getDashboardCategory())}</Button>
                </HorizontalStack>
            </div>

            <Divider />
            {
                showEmptyLayout ?
                <Box minHeight="84.4vh">
                    <EmptySampleApi
                        iconSrc={"/public/file_plus.svg"}
                        headingText={"Discover APIs to get started"}
                        description={"You have an inactive API collection or one with no data. Create or populate a collection now to get started."}
                        buttonText={"Create new a API collection"}
                        redirectUrl={"/dashboard/observe/inventory"}
                    />
                </Box> :
                <>{
                    editorData?.message?.length === 0 ?
                    <Box padding={3} minHeight="84.4vh"><Text>Sample data is not available for this API endpoint. Please choose a different endpoint.</Text></Box>
                        : <><SampleData data={editorData} minHeight="80.4vh"  editorLanguage="custom_http" />
                        {resultComponent}</>
                }</>
            }
            <Modal
                open={showTestResult}
                onClose={() => closeModal()}
                title="Results"
                large
            >
                <Frame >
                <Box paddingBlockEnd={"8"}>
                <TestRunResultPage
                    testingRunResult={testResult?.testingRunResult}
                    runIssues={testResult?.testingRunIssues}
                    testSubCategoryMap={testResult?.subCategoryMap}
                    testId={selectedTest?.value}
                    source="editor"
                />
                </Box>
                </Frame>
            </Modal>
            <Modal
                large
                open={selectApiActive}
                onClose={toggleSelectApiActive}
                title="Test configuration"
                primaryAction={{
                    id:"save",
                    content: 'Save',
                    onAction: saveFunc,
                }}
                secondaryActions={[
                    {
                        id:"cancel",
                        content: 'Cancel',
                        onAction: toggleSelectApiActive,
                    },
                ]}
            >
                <Modal.Section>
                    <DropdownSearch
                        id={"select-api-collection"}
                        label="Select collection"
                        placeholder="Select API collection"
                        optionsList={allCollectionsOptions}
                        setSelected={setCopyCollectionId}
                        value={mapCollectionIdToName?.[copyCollectionId]}
                        preSelected={[copyCollectionId]}
                    />

                    <br />

                    <DropdownSearch
                        id={"select-api-endpoint"}
                        disabled={apiEndpointsOptions.length === 0}
                        label="API"
                        placeholder="Select API endpoint"
                        optionsList={apiEndpointsOptions}
                        setSelected={setCopySelectedApiEndpoint}
                        value={copyEndpointLabel}
                        preSelected={[copySelectedApiEndpoint]}
                    />

                    <br />

                    <DropdownSearch
                        id={"select-test-role"}
                        label={
                            <TitleWithInfo
                                titleText="Role"
                                tooltipContent="Roles support is only available in Akto testing module."
                                textProps={{ variant: 'bodyMd' }}
                            />
                        }
                        placeholder="Select role"
                        optionsList={testRolesOptions}
                        setSelected={setSelectedRole}
                        value={testRolesOptions.find(r => r.value === selectedRole)?.label ?? ''}
                        preSelected={selectedRole ? [selectedRole] : []}
                    />

                    {miniTestingServiceNames?.length > 0 && (
                        <>
                            <br />
                            <Dropdown
                                label="Select Testing Module"
                                menuItems={miniTestingServiceNames}
                                initial={selectedMiniTestingServiceName || miniTestingServiceNames?.[0]?.value}
                                selected={(value) => setSelectedMiniTestingServiceName(value)}
                            />
                        </>
                    )}

                    <br />
                    <HorizontalGrid columns={2}>
                        <Checkbox
                            label={"Use different target for " + mapLabel("testing", getDashboardCategory())}
                            checked={hasOverriddenTestAppUrl}
                            onChange={() => {
                                const next = !hasOverriddenTestAppUrl;
                                setHasOverriddenTestAppUrl(next);
                                if (!next) setOverriddenTestAppUrl("");
                            }}
                        />
                        {hasOverriddenTestAppUrl && (
                            <div style={{ width: '400px' }}>
                                <TextField
                                    placeholder="Override test app host"
                                    value={overriddenTestAppUrl}
                                    onChange={setOverriddenTestAppUrl}
                                />
                            </div>
                        )}
                    </HorizontalGrid>

                </Modal.Section>
            </Modal>
            <Modal
                open={chatBotModal}
                onClose={() => setChatBotModal(false)}
                title="Chat with the agent"
                large
            >
                
                <Modal.Section>
                    <VerticalStack gap={"8"}>
                        <VerticalStack gap={"4"}>
                            <Box padding={"4"}>
                                <HorizontalStack gap={"2"} wrap={false}>
                                    <Text variant="headingMd" alignment="start" breakWord>{selectedTest?.label}</Text>
                                    {testResult?.testingRunResult?.vulnerable === true ? <Box className={`badge-wrapper-${currentSeverity}`}><Badge size="medium" status={observeFunc.getColor(currentSeverity)}>{currentSeverity}</Badge></Box> : null}
                                </HorizontalStack>
                                <br/>
                                <Divider/> 
                            </Box>
                            
                        </VerticalStack>
                    </VerticalStack>
                </Modal.Section>
                
            </Modal>
        </div>
    )
}

export default SampleApi
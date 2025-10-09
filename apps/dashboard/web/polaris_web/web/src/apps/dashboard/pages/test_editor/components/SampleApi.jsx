import { Badge, Box, Button, Divider, Frame, HorizontalStack, LegacyTabs, Modal, Text, Tooltip, VerticalStack} from "@shopify/polaris"
import {ChevronUpMinor } from "@shopify/polaris-icons"

import { useEffect, useRef, useState } from "react";
import DropdownSearch from "../../../components/shared/DropdownSearch";
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
import ChatContainer from "../../../components/shared/ChatContainer";
import ChatInterface from "../../../components/shared/ChatInterface";
import LocalStore from "../../../../main/LocalStorageStore";
import observeFunc from "../../observe/transform"

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

    const currentContent = TestEditorStore(state => state.currentContent)
    const selectedTest = TestEditorStore(state => state.selectedTest)
    const vulnerableRequestsObj = TestEditorStore(state => state.vulnerableRequestsMap)
    const defaultRequest = TestEditorStore(state => state.defaultRequest)
    const selectedSampleApi = PersistStore(state => state.selectedSampleApi)
    const setSelectedSampleApi = PersistStore(state => state.setSelectedSampleApi)

    const tabs = [{ id: 'request', content: 'Request' }, { id: 'response', content: 'Response'}];
    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)
    const [conversationsList, setConversationsList] = useState([])

    const [isChatBotOpen, setIsChatBotOpen] = useState(false)
    const [chatBotModal, setChatBotModal] = useState(false)
    const subCategoryMap = LocalStore(state => state.subCategoryMap)

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
            let temp =  testResult?.agentConversationResults ? testResult?.agentConversationResults?.length > 0 : false;
            setShowTestResult(!temp);
            setChatBotModal(temp);
            
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

    const runTest = async()=>{
        if(isChatBotOpen) {
            setChatBotModal(true)
            return;
        }
        setLoading(true)
        const apiKeyInfo = {
            ...func.toMethodUrlObject(selectedApiEndpoint),
            apiCollectionId: selectedCollectionId
        }


        try {
            let resp = await testEditorRequests.runTestForTemplate(currentContent,apiKeyInfo,sampleDataList)
            if(resp.testingRunPlaygroundHexId !== null && resp?.testingRunPlaygroundHexId !== undefined) {
                await new Promise((resolve) => {
                    let maxAttempts = 100;
                    let pollInterval = 3000;
                    let attempts = 0;
    
                    intervalRef.current = setInterval(async () => {
                        if (attempts >= maxAttempts) {
                            clearInterval(intervalRef.current);
                            intervalRef.current = null;
                            setToastConfig({ isActive: true, isError: true, message: "Error while running the test" });
                            resolve();
                            return;
                        }
    
                        try {
                            const result = await testEditorRequests.fetchTestingRunPlaygroundStatus(resp?.testingRunPlaygroundHexId);
                            if (result?.testingRunPlaygroundStatus === "COMPLETED") {
                                clearInterval(intervalRef.current);
                                intervalRef.current = null;
                                setTestResult(result);
                                resolve();
                                return;
                            }
                        } catch (err) {
                            console.error("Error fetching updateResult:", err);
                        }
    
                        attempts++;
                    }, pollInterval);
                });
            }
            else setTestResult(resp)
            if(resp?.agentConversationResults?.length > 0){
                let conversationsListCopy = testingFunc.prepareConversationsList(resp?.agentConversationResults)
                setConversationsList(conversationsListCopy)
            }
        } catch (err){
        }
        setLoading(false)
    }

    useEffect(() => {
        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
            }
        };
    }, []);

    const showResults = () => {
        if(testResult?.agentConversationResults?.length > 0){
            setChatBotModal(!chatBotModal)
            return;
        }
        setShowTestResult(!showTestResult);
    }

    function getColor(){
        if(testResult){
            if(testResult.testingRunResult.vulnerable){
                let status = func.getRunResultSeverity(testResult.testingRunResult, testResult.subCategoryMap)
                status = status.toUpperCase();
                switch(status){
                    case "HIGH" : return "bg-critical";
                    case "MEDIUM": return "bg-caution";
                    case "LOW": return "bg-info";
                    default:
                        return "bg";
                }
            } else {
                return "bg-success"
            }
        } else {
            return "bg";
        }
    }

    function getResultDescription() {
        if(isChatBotOpen) {
            return "Chat with the agent"
        }
        if (testResult) {
            if(testResult.testingRunResult.vulnerable){
                let status = func.getRunResultSeverity(testResult.testingRunResult, testResult.subCategoryMap)
                return func.toSentenceCase(status) + " vulnerability found";
            } else {
                return "No vulnerability found"
            }
        } else {
            return `${mapLabel('Run test', getDashboardCategory())} to see Results`
        }
    }

    const closeModal = () => {
        setShowTestResult(!showTestResult)
        editorSetup.setEditorTheme();
    }

    const resultComponent = (
        <Box background={getColor()} width="100%" padding={"2"}>
            <Button id={"test-results"} removeUnderline monochrome plain 
            onClick={testResult ? showResults : () => {}}
            icon={testResult ? ChevronUpMinor : undefined}>
                {getResultDescription()}
            </Button>
        </Box>

    )

    const currentSeverity = selectedTest?.value !== undefined ? subCategoryMap[selectedTest?.value]?.superCategory?.severity._name : "HIGH";

    return (
        <div>
            <div className="editor-header">
                <div className="req-resp-tabs">
                    <LegacyTabs tabs={tabs} selected={selected} onSelect={handleTabChange} fitted />
                </div>
                <HorizontalStack gap={2}>
                    <Button id={"select-sample-api"} onClick={toggleSelectApiActive} size="slim">
                        <Box maxWidth="200px">
                            <Tooltip content={func.toMethodUrlString({...func.toMethodUrlObject(copySelectedApiEndpoint), shouldParse: true})} hoverDelay={"100"}>
                                <Text variant="bodyMd" truncate>{func.toMethodUrlString({...func.toMethodUrlObject(copySelectedApiEndpoint), shouldParse: true})}</Text>
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
                open={selectApiActive}
                onClose={toggleSelectApiActive}
                title="Select sample API"
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
                        value={copySelectedApiEndpoint==null ? "No endpoints selected" : func.toMethodUrlString({...func.toMethodUrlObject(copySelectedApiEndpoint), shouldParse: true})}
                        preSelected={[copySelectedApiEndpoint]}
                    />

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
                        {conversationsList?.length > 0 ? <ChatInterface conversations={conversationsList} sort={false}/> : <ChatContainer/>}
                    </VerticalStack>
                </Modal.Section>
                
            </Modal>
        </div>
    )
}

export default SampleApi
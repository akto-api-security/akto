import { Box, Button, Divider, Frame, HorizontalStack, LegacyTabs, Modal, Text, Tooltip} from "@shopify/polaris"
import {ChevronUpMinor } from "@shopify/polaris-icons"

import { useEffect, useRef, useState } from "react";

import { editor } from "monaco-editor/esm/vs/editor/editor.api"
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/esm/vs/editor/contrib/comment/browser/comment';
import 'monaco-editor/esm/vs/editor/contrib/codelens/browser/codelensController';
// import 'monaco-editor/esm/vs/editor/contrib/colorPicker/browser/color';
import 'monaco-editor/esm/vs/editor/contrib/format/browser/formatActions';
import 'monaco-editor/esm/vs/editor/contrib/lineSelection/browser/lineSelection';
import 'monaco-editor/esm/vs/editor/contrib/indentation/browser/indentation';
// import 'monaco-editor/esm/vs/editor/contrib/inlineCompletions/browser/inlineCompletionsController';
import 'monaco-editor/esm/vs/editor/contrib/snippet/browser/snippetController2'
import 'monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController';
import 'monaco-editor/esm/vs/editor/contrib/wordHighlighter/browser/wordHighlighter';
import "monaco-editor/esm/vs/language/json/monaco.contribution"
import "monaco-editor/esm/vs/language/json/json.worker"
import DropdownSearch from "../../../components/shared/DropdownSearch";
import api from "../../testing/api"
import testEditorRequests from "../api";
import func from "@/util/func";
import TestEditorStore from "../testEditorStore"
import "../TestEditor.css"
import TestRunResultPage from "../../testing/TestRunResultPage/TestRunResultPage";
import PersistStore from "../../../../main/PersistStore";
import editorSetup from "./editor_config/editorSetup";

const SampleApi = () => {

    const allCollections = PersistStore(state => state.allCollections);
    const [editorInstance, setEditorInstance] = useState(null);
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

    const currentContent = TestEditorStore(state => state.currentContent)
    const selectedTest = TestEditorStore(state => state.selectedTest)
    const vulnerableRequestsObj = TestEditorStore(state => state.vulnerableRequestsMap)
    const defaultRequest = TestEditorStore(state => state.defaultRequest)
    const selectedSampleApi = TestEditorStore(state => state.selectedSampleApi)
    const setSelectedSampleApi = TestEditorStore(state => state.setSelectedSampleApi)
    

    const jsonEditorRef = useRef(null)

    const tabs = [{ id: 'request', content: 'Request' }, { id: 'response', content: 'Response'}];
    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)

    useEffect(() => {
        const jsonEditorOptions = {
            language: "json",
            minimap: { enabled: false },
            wordWrap: true,
            automaticLayout: true,
            colorDecorations: true,
            scrollBeyondLastLine: false,
            readOnly: true
        }

        setEditorInstance(editor.create(jsonEditorRef.current, jsonEditorOptions))    
    }, [])

    useEffect(()=>{
        let testId = selectedTest.value
        let selectedUrl = selectedSampleApi.hasOwnProperty(testId) ? selectedSampleApi[testId] : vulnerableRequestsObj?.[testId]
        setSelectedCollectionId(null)
        setCopyCollectionId(null)
        setTestResult(null)
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
        fetchApiEndpoints(copyCollectionId)
        setCopySelectedApiEndpoint(null);
        setTestResult(null)
    }, [copyCollectionId])

    useEffect(() => {
        if (selectedCollectionId && selectedApiEndpoint) {
            fetchSampleData(selectedCollectionId, func.toMethodUrlObject(selectedApiEndpoint).url, func.toMethodUrlObject(selectedApiEndpoint).method)
        }else{
            editorInstance?.setValue("")
        }
        setTestResult(null)
    }, [selectedApiEndpoint])

    useEffect(()=> {
        if(testResult){
            setShowTestResult(true);
        } else {
            setShowTestResult(false);
        }
    }, [testResult])


    const handleTabChange = (selectedTabIndex) => {
        setSelected(selectedTabIndex)
        if (sampleData) {

            if (selectedTabIndex == 0) {
                editorInstance.setValue('\n' + sampleData?.requestJson["firstLine"] + '\n\n' + JSON.stringify(sampleData.requestJson["json"], null, 2))
            } else {
                editorInstance.setValue('\n' + sampleData?.responseJson["firstLine"] + '\n\n' + JSON.stringify(sampleData.responseJson["json"], null, 2))
            }
        }
    }


    const allCollectionsOptions = allCollections.map(collection => {
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
        let str = func.toMethodUrlString(apiEndpoint);
        return {
            id: str,
            label: str,
            value: str
        }
    })

    const fetchSampleData = async (collectionId, apiEndpointUrl, apiEndpointMethod) => {
        const sampleDataResponse = await testEditorRequests.fetchSampleData(collectionId, apiEndpointUrl, apiEndpointMethod)
        if (sampleDataResponse) {
            if (sampleDataResponse.sampleDataList.length > 0 && sampleDataResponse.sampleDataList[0].samples && sampleDataResponse.sampleDataList[0].samples.length > 0) {
                setSampleDataList(null)
                const sampleDataJson = JSON.parse(sampleDataResponse.sampleDataList[0].samples[0])
                const requestJson = func.requestJson(sampleDataJson, [])
                const responseJson = func.responseJson(sampleDataJson, [])
                setSampleData({ requestJson, responseJson })

                if (editorInstance) {
                    editorInstance.setValue('\n' + requestJson["firstLine"] + '\n\n' + JSON.stringify(requestJson["json"], null, 2))
                }
                setTimeout(()=> {
                    setSampleDataList(sampleDataResponse.sampleDataList)
                },0)

                setSelected(0)
            }
        }
    }

    const toggleSelectApiActive = () => setSelectApiActive(prev => !prev)
    const saveFunc = () =>{
        setSelectedApiEndpoint(copySelectedApiEndpoint)
        let copySampleApiObj = {...selectedSampleApi}
        const urlObj = func.toMethodUrlObject(copySelectedApiEndpoint)
        copySampleApiObj[selectedTest.value] = {
            apiCollectionId :copyCollectionId, 
            url: urlObj.url,
            method:{
                "_name": urlObj.method
            }
        }
        setSelectedSampleApi(copySampleApiObj)
        setSelectedCollectionId(copyCollectionId)
        toggleSelectApiActive()
    }

    const runTest = async()=>{
        setLoading(true)
        const apiKeyInfo = {
            ...func.toMethodUrlObject(selectedApiEndpoint),
            apiCollectionId: selectedCollectionId
        }

        await testEditorRequests.runTestForTemplate(currentContent,apiKeyInfo,sampleDataList).then((resp)=>{
            setTestResult(resp)
            setLoading(false)
        })
    }

    const showResults = () => {
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
        if (testResult) {
            if(testResult.testingRunResult.vulnerable){
                let status = func.getRunResultSeverity(testResult.testingRunResult, testResult.subCategoryMap)
                return func.toSentenceCase(status) + " vulnerability found";
            } else {
                return "No vulnerability found"
            }
        } else {
            return "Run test to see Results"
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

    return (
        <div>
            <div className="editor-header">
                <div className="req-resp-tabs">
                    <LegacyTabs tabs={tabs} selected={selected} onSelect={handleTabChange} fitted />
                </div>
                <HorizontalStack gap={2}>
                    <Button id={"select-sample-api"} onClick={toggleSelectApiActive} size="slim">
                        <Box maxWidth="200px">
                            <Tooltip content={copySelectedApiEndpoint} hoverDelay={"100"}>
                                <Text variant="bodyMd" truncate>{copySelectedApiEndpoint}</Text>
                            </Tooltip>
                        </Box>
                    </Button>
                    <Button id={"run-test"} loading={loading} primary onClick={runTest} size="slim">Run Test</Button>
                </HorizontalStack>
            </div>

            <Divider />

            <Box ref={jsonEditorRef} minHeight="80.3vh"/>
            {resultComponent}
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
                    testId={selectedTest.value}
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
                        value={copySelectedApiEndpoint==null ? "No endpoints selected" : copySelectedApiEndpoint}
                        preSelected={[copySelectedApiEndpoint]}
                    />

                </Modal.Section>
            </Modal>
        </div>
    )
}

export default SampleApi
import { Box, Button, Divider, LegacyTabs, Modal} from "@shopify/polaris"
import { tokens } from "@shopify/polaris-tokens"

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

import Store from "../../../store";
import DropdownSearch from "../../../components/shared/DropdownSearch";
import api from "../../testing/api"
import testEditorRequests from "../api";
import func from "../../../../../util/func";
import TestEditorStore from "../testEditorStore"

const SampleApi = () => {

    const allCollections = Store(state => state.allCollections);
    const [editorInstance, setEditorInstance] = useState(null);
    const [selected, setSelected] = useState(0);
    const [selectApiActive, setSelectApiActive] = useState(false)
    const [selectedCollectionId, setSelectedCollectionId] = useState(null)
    const [apiEndpoints, setApiEndpoints] = useState([])
    const [selectedApiEndpoint, setSelectedApiEndpoint] = useState(null)
    const [sampleData, setSampleData] = useState(null)
    const [copyCollectionId, setCopyCollectionId] = useState(null)
    const [copySelectedApiEndpoint, setCopySelectedApiEndpoint] = useState(null)

    const selectedTest = TestEditorStore(state => state.selectedTest)
    const vulnerableRequestsObj = TestEditorStore(state => state.vulnerableRequestsMap)
    const defaultRequest = TestEditorStore(state => state.defaultRequest)

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
        let selectedUrl = vulnerableRequestsObj?.[testId]
        setSelectedCollectionId(null)
        setSelectedApiEndpoint(null)
        if(!selectedUrl){
            selectedUrl = defaultRequest
        }
        setTimeout(()=> {
            setSelectedCollectionId(selectedUrl.apiCollectionId)
            let obj = {
                method: selectedUrl.method._name,
                url: selectedUrl.url
            }
            setSelectedApiEndpoint(obj)
        },0)
    },[selectedTest])

    useEffect(() => {
        fetchApiEndpoints(selectedCollectionId)
    }, [selectedCollectionId])

    useEffect(() => {
        if (selectedCollectionId && selectedApiEndpoint) {
            fetchSampleData(selectedCollectionId, selectedApiEndpoint.url, selectedApiEndpoint.method)
        }else{
            editorInstance?.setValue("")
        }
    }, [selectedApiEndpoint])


    const handleTabChange = (selectedTabIndex) => {
        setSelected(selectedTabIndex)

        if (sampleData) {
            if (selectedTabIndex == 0) {
                editorInstance.setValue(JSON.stringify(sampleData.requestJson["json"], null, 2))
            } else {
                editorInstance.setValue(JSON.stringify(sampleData.responseJson["json"], null, 2))
            }
        }
    }


    const allCollectionsOptions = allCollections.map(collection => {
        return {
            label: collection.displayName,
            value: String(collection.id)
        }
    })

    const fetchApiEndpoints = async (collectionId) => {
        const apiEndpointsResponse = await api.fetchCollectionWiseApiEndpoints(collectionId)
        if (apiEndpointsResponse) {
            setApiEndpoints(apiEndpointsResponse.listOfEndpointsInCollection)
        }
    }

    const apiEndpointsOptions = apiEndpoints.map(apiEndpoint => {
        return {
            id: apiEndpoint.method + " " + apiEndpoint.url,
            label: apiEndpoint.method + " " + apiEndpoint.url,
            value: {
                method: apiEndpoint.method,
                url: apiEndpoint.url
            }
        }
    })

    const fetchSampleData = async (collectionId, apiEndpointUrl, apiEndpointMethod) => {
        const sampleDataResponse = await testEditorRequests.fetchSampleData(collectionId, apiEndpointUrl, apiEndpointMethod)
        console.log(sampleDataResponse,collectionId,apiEndpointUrl)
        if (sampleDataResponse) {
            if (sampleDataResponse.sampleDataList.length > 0 && sampleDataResponse.sampleDataList[0].samples && sampleDataResponse.sampleDataList[0].samples.length > 0) {
                const sampleDataJson = JSON.parse(sampleDataResponse.sampleDataList[0].samples[0])
                const requestJson = func.requestJson(sampleDataJson, [])
                const responseJson = func.responseJson(sampleDataJson, [])
                setSampleData({ requestJson, responseJson })

                if (editorInstance) {
                    editorInstance.setValue(JSON.stringify(requestJson["json"], null, 2))
                }

                setSelected(0)
            }
        }
    }

    const toggleSelectApiActive = () => setSelectApiActive(prev => !prev)
    const saveFunc = () =>{
        setSelectedApiEndpoint(copySelectedApiEndpoint)
        setSelectedCollectionId(copyCollectionId)
        toggleSelectApiActive()
    }

    return (
        <div style={{ borderWidth: "0px, 1px, 1px, 0px", borderStyle: "solid", borderColor: "#E1E3E5" }}>
            <div style={{ display: "grid", gridTemplateColumns: "auto max-content max-content", alignItems: "center", gap: "10px", background: tokens.color["color-bg-app"], height: "10vh", padding: "10px" }}>
                <LegacyTabs tabs={tabs} selected={selected} onSelect={handleTabChange} fitted>
                </LegacyTabs>
                <Button onClick={toggleSelectApiActive}>Select Sample API</Button>
                <Button primary>Run Test</Button>
            </div>

            <Divider />
            
            <Box ref={jsonEditorRef} minHeight="80vh">
            </Box>    
            <Modal
                open={selectApiActive}
                onClose={toggleSelectApiActive}
                title="Select sample API"
                primaryAction={{
                    content: 'Save',
                    onAction: saveFunc,
                }}
                secondaryActions={[
                    {
                        content: 'Cancel',
                        onAction: toggleSelectApiActive,
                    },
                ]}
            >
                <Modal.Section>
                    <DropdownSearch
                        label="Select collection"
                        placeholder="Select API collection"
                        optionsList={allCollectionsOptions}
                        setSelected={setCopyCollectionId}
                        value={mapCollectionIdToName?.[selectedCollectionId]}
                    />

                    <br />

                    <DropdownSearch
                        disabled={apiEndpointsOptions.length === 0}
                        label="API"
                        placeholder="Select API endpoint"
                        optionsList={apiEndpointsOptions}
                        setSelected={setCopySelectedApiEndpoint}
                        value={selectedApiEndpoint?.url}
                    />

                </Modal.Section>
            </Modal>
        </div>
    )
}

export default SampleApi
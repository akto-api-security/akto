import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { Box, Button, Divider, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from "@shopify/polaris"
import { InfoMinor, ClipboardMinor, CircleTickMinor, CircleCancelMinor } from "@shopify/polaris-icons"

import Store from "../../../store";
import TestEditorStore from "../testEditorStore";

import testEditorRequests from "../api";

import func from "@/util/func";

import { editor } from "monaco-editor/esm/vs/editor/editor.api"
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/esm/vs/editor/contrib/comment/browser/comment';
import 'monaco-editor/esm/vs/editor/contrib/codelens/browser/codelensController';
import 'monaco-editor/esm/vs/editor/contrib/colorPicker/browser/color';
import 'monaco-editor/esm/vs/editor/contrib/format/browser/formatActions';
import 'monaco-editor/esm/vs/editor/contrib/lineSelection/browser/lineSelection';
import 'monaco-editor/esm/vs/editor/contrib/indentation/browser/indentation';
import 'monaco-editor/esm/vs/editor/contrib/inlineCompletions/browser/inlineCompletionsController';
import 'monaco-editor/esm/vs/editor/contrib/snippet/browser/snippetController2'
import 'monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController';
import 'monaco-editor/esm/vs/editor/contrib/wordHighlighter/browser/wordHighlighter';
import "monaco-editor/esm/vs/basic-languages/yaml/yaml.contribution"

import editorSetup from "./editor_config/editorSetup";
import keywords from "./editor_config/keywords"

const YamlEditor = ({ fetchAllTests }) => {
    const navigate = useNavigate()
    const ref = useRef(null)

    const setToastConfig = Store(state => state.setToastConfig)
    const testsObj = TestEditorStore(state => state.testsObj)
    const selectedTest = TestEditorStore(state => state.selectedTest)
    const setSelectedTest = TestEditorStore(state => state.setSelectedTest)
    const setTestsObj = TestEditorStore(state => state.setTestsObj)
    const setCurrentContent = TestEditorStore(state => state.setCurrentContent)

    const [ isEdited, setIsEdited ] = useState(false)
    const [ editorInstance, _setEditorInstance ] = useState()
    const editorInstanceRef = useRef(editorInstance)
    const setEditorInstance = (EditorInstance) => {
        editorInstanceRef.current = EditorInstance
        _setEditorInstance(EditorInstance)
    }

    const yamlEditorRef = useRef(null)

    const handleYamlUpdate = () => {
        const Editor = editorInstanceRef.current
        const currentYaml = Editor.getValue()
        setCurrentContent(currentYaml)
        const existingYaml = testsObj.mapTestToData[selectedTest.label].content 
        setIsEdited(currentYaml !== existingYaml)
    }

    const handleSave = async () => {
        const Editor = editorInstanceRef.current

        try {
            const addTestTemplateResponse = await testEditorRequests.addTestTemplate(Editor.getValue(), selectedTest.value)
            setToastConfig({
                isActive: true,
                isError: false,
                message: "Test saved successfully!"
            })
            navigate(`/dashboard/test-editor/${addTestTemplateResponse.finalTestId}`) 
            fetchAllTests()
        } catch(error) {
            
        }
        
    }
    
    useEffect(()=>{        
        let Editor = null

        if (!editorInstance) {
            const yamlEditorOptions = {
                language: "custom_yaml",
                minimap: { enabled: false },
                wordWrap: true,
                automaticLayout: true,
                colorDecorations: true,
                scrollBeyondLastLine: false,
                theme: "customTheme"
              }
      
                editorSetup.registerLanguage()
                editorSetup.setTokenizer()
                editorSetup.setEditorTheme()
                editorSetup.setAutoComplete(keywords)

              Editor = editor.create(yamlEditorRef.current, yamlEditorOptions)
              editorSetup.findErrors(Editor, keywords)
              Editor.onDidChangeModelContent(handleYamlUpdate)
              setEditorInstance(Editor)
        } else {
            Editor = editorInstance
        }

        if (selectedTest) {
            const value = testsObj.mapTestToData[selectedTest.label].content
            Editor.setValue(value)
            setIsEdited(false)
        }    
      }, [selectedTest])

    const copyTestName = () =>{
        func.copyToClipboard(editorInstance.getValue(), ref)
    }

    const setTestInactive = () => {

        let newVal = !selectedTest.inactive;
        let activeConf = newVal ? "inactive" : "active"

        testEditorRequests.setTestInactive(selectedTest.value, newVal).then(async (res) => {
            func.setToast(true, false, `Test marked as ${activeConf}`)
            setSelectedTest({...selectedTest, inactive: newVal})
            let obj = {...testsObj}
            let dataObj = obj.mapTestToData[selectedTest.label]
            obj.mapTestToData[selectedTest.label] = {...dataObj, 
                lastUpdated: func.prettifyEpoch(func.timeNow()), inactive: selectedTest.inactive}
            let type = dataObj.type === 'CUSTOM' ? 'customTests' : 'aktoTests'
            
            obj[type][dataObj.superCategory].forEach((x, i) => {
                if(x.value == selectedTest.value){
                    obj[type][dataObj.superCategory][i].inactive = newVal
                }
            })
            setTestsObj(obj);
        }).catch((err) => {
            func.setToast(true, true, `Unable to mark test as ${activeConf}`)
        })
    }

    return (
        <VerticalStack>
            <div className="editor-header">
                <HorizontalStack gap={"1"}>
                    <div ref={ref} />
                    <Tooltip content={selectedTest.label + '.yaml'} width="wide">
                        <Text variant="headingSm" as="h5" truncate>{selectedTest.label + '.yaml'}</Text>
                    </Tooltip>
                    <Tooltip content={`Last Updated ${testsObj.mapTestToData[selectedTest.label].lastUpdated}`} preferredPosition="below" dismissOnMouseOut>
                        <Icon source={InfoMinor}/> 
                    </Tooltip>
                    <Tooltip content="Copy Content" dismissOnMouseOut preferredPosition="below">
                        <Button icon={ClipboardMinor} plain onClick={copyTestName} />
                    </Tooltip>  
                    <Tooltip content={`Set as ${selectedTest.inactive ? "active" : "inactive" }`} dismissOnMouseOut preferredPosition="below">
                        <Button icon={selectedTest.inactive ? CircleTickMinor : CircleCancelMinor} plain onClick={setTestInactive} />
                    </Tooltip>  
                </HorizontalStack>
        
                <Button id={"save-button"} disabled={!isEdited} onClick={handleSave} size="slim">Save</Button>
            </div>

            <Divider />

            <div style={{height: '92.7%', borderRight: '1px solid' , borderColor: '#E1E3E5'}} id={"yaml-editor"} ref={yamlEditorRef} />
        </VerticalStack>

    )
}

export default YamlEditor
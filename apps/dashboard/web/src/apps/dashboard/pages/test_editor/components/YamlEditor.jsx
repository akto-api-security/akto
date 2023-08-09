import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { Box, Button, Divider, HorizontalStack, Icon, Text, Tooltip } from "@shopify/polaris"
import { tokens } from "@shopify/polaris-tokens"
import { InfoMinor, ClipboardMinor } from "@shopify/polaris-icons"

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

const YamlEditor = ({ fetchAllTests }) => {
    const navigate = useNavigate()

    const setToastConfig = Store(state => state.setToastConfig)
    const testsObj = TestEditorStore(state => state.testsObj)
    const selectedTest = TestEditorStore(state => state.selectedTest)
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
                language: "yaml",
                minimap: { enabled: false },
                wordWrap: true,
                automaticLayout: true,
                colorDecorations: true,
                scrollBeyondLastLine: false,
              }
      
              editor.defineTheme('subdued', {
                  base: 'vs',
                  inherit: true,
                  rules: [],
                  colors: {
                      'editor.background': '#FAFBFB',
                  },
              });
              
              editor.setTheme('subdued')
      
              Editor = editor.create(yamlEditorRef.current, yamlEditorOptions)
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
        func.copyToClipboard(editorInstance.getValue())
        setToastConfig({
            isActive: true,
            isError: false,
            message: "Test copied to clipboard"
        })
    }

    return (
        <div>
            <div className="editor-header">
                <HorizontalStack>
                    <Tooltip content={selectedTest.label + '.yaml'} width="wide">
                        <Text variant="headingSm" as="h5" truncate>{selectedTest.label + '.yaml'}</Text>
                    </Tooltip>
                    <Tooltip content={`Last Updated ${testsObj.mapTestToData[selectedTest.label].lastUpdated}`} preferredPosition="below" dismissOnMouseOut>
                        <Icon source={InfoMinor}/> 
                    </Tooltip>
                    <Tooltip content="Copy Content" dismissOnMouseOut preferredPosition="below">
                        <Button icon={ClipboardMinor} plain onClick={copyTestName} />
                    </Tooltip>  
                </HorizontalStack>
        
                <Button id={"save-button"} disabled={!isEdited} onClick={handleSave} size="slim">Save</Button>
            </div>

            <Divider />

            <Box id={"yaml-editor"} ref={yamlEditorRef} minHeight="85vh" borderInlineEndWidth="1" borderColor="border-subdued"/>
        </div>
    )
}

export default YamlEditor
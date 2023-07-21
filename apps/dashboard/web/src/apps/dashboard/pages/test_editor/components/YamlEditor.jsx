import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { Box, Button, Divider, Icon, Text, Tooltip } from "@shopify/polaris"
import { tokens } from "@shopify/polaris-tokens"
import { InfoMinor, ClipboardMinor } from "@shopify/polaris-icons"

import Store from "../../../store";
import TestEditorStore from "../testEditorStore";

import api from "../../testing/api";
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
        const existingYaml = testsObj.mapTestToData[selectedTest.label].content 
        setIsEdited(currentYaml !== existingYaml)
    }

    const handleSave = async () => {
        const Editor = editorInstanceRef.current

        try {
            const addTestTemplateResponse = await api.addTestTemplate(Editor.getValue(), selectedTest.value)
            setToastConfig({
                isActive: true,
                isError: false,
                message: "Test saved successfully!"
            })
            navigate(`/dashboard/test-editor/${addTestTemplateResponse.finalTestId}`) 
            fetchAllTests()
        } catch (error) {
            console.log(error)
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
        <div style={{ borderWidth: "0px, 1px, 1px, 0px", borderStyle: "solid", borderColor: "#E1E3E5"}}>
            <div style={{ display: "grid", gridTemplateColumns: "max-content auto", alignItems: "center", background: tokens.color["color-bg-app"], height: "10vh", padding: "10px"}}>
                <div style={{ maxWidth: "30vw", display: "grid", gridTemplateColumns: "auto max-content max-content"}}>
                    <Text variant="bodyMd" truncate>{selectedTest.label + '.yaml'}</Text>
                    <Tooltip content={`Last Updated ${testsObj.mapTestToData[selectedTest.label].lastUpdated}`} preferredPosition="below" dismissOnMouseOut>
                        <Icon source={InfoMinor}/> 
                    </Tooltip>
                    <Tooltip content="Copy Content" dismissOnMouseOut preferredPosition="below">
                        <Button icon={ClipboardMinor} plain onClick={copyTestName} />
                    </Tooltip>       
                </div>
                <div style={{ textAlign: "right"}}>
                    <Button disabled={!isEdited} onClick={handleSave}>Save</Button>
                </div>
            </div>

            <Divider />
            <Box ref={yamlEditorRef} minHeight="80vh">
            </Box>
        </div>
    )
}

export default YamlEditor
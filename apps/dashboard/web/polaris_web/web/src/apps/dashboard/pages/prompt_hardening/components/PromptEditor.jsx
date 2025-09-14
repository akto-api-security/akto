import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { Box, Button, Divider, HorizontalStack, Text, VerticalStack, Badge, Tooltip, Icon } from "@shopify/polaris"
import { ClipboardMinor, InfoMinor, PlayMinor } from "@shopify/polaris-icons"

import Store from "../../../store";
import PromptHardeningStore from "../promptHardeningStore";

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

const PromptEditor = ({ fetchAllPrompts }) => {
    const navigate = useNavigate()
    const ref = useRef(null)

    const setToastConfig = Store(state => state.setToastConfig)
    const promptsObj = PromptHardeningStore(state => state.promptsObj)
    const selectedPrompt = PromptHardeningStore(state => state.selectedPrompt)
    const setCurrentContent = PromptHardeningStore(state => state.setCurrentContent)
    const setTriggerTest = PromptHardeningStore(state => state.setTriggerTest)

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
        const existingYaml = promptsObj.mapPromptToData?.[selectedPrompt.label]?.content || ""
        setIsEdited(currentYaml !== existingYaml)
    }

    const handleRunTest = async () => {
        const Editor = editorInstanceRef.current
        const currentContent = Editor.getValue()
        
        // Save current content to store and trigger test
        setCurrentContent(currentContent)
        setTriggerTest(true)
        
        // The actual test execution will be handled by the PromptResponse component
        setIsEdited(false)
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
                theme: "vs-light",
                fontSize: 14,
                lineNumbers: "on",
                renderWhitespace: "selection",
                tabSize: 2,
                padding: { top: 10, bottom: 10 }
            }
    
            Editor = editor.create(yamlEditorRef.current, yamlEditorOptions)
            
            Editor.onDidChangeModelContent(() => {
                handleYamlUpdate()
            })

            setEditorInstance(Editor)
        } else {
            Editor = editorInstance
        }

        if (selectedPrompt && selectedPrompt.label && promptsObj.mapPromptToData) {
            const promptData = promptsObj.mapPromptToData[selectedPrompt.label]
            if (promptData && promptData.content) {
                Editor.setValue(promptData.content)
                setCurrentContent(promptData.content)
                setIsEdited(false)
            }
        }

    }, [selectedPrompt])

    return (
        <div >
                {/* Header */}
                <VerticalStack>
                    <div className="editor-header">
                        <HorizontalStack gap={"1"}>
                            <div ref={ref} />
                            <Text variant="headingSm" as="h5" truncate>
                                {selectedPrompt?.label || 'Prompt Configuration'}
                            </Text>
                            <Tooltip 
                                content="Configure and test prompt injection attacks against AI agents. Define attack patterns, detection rules, and severity levels to evaluate agent security."
                                preferredPosition="below" 
                                dismissOnMouseOut
                            >
                                <Icon source={InfoMinor}/> 
                            </Tooltip>
                            <Tooltip content="Copy Content" dismissOnMouseOut preferredPosition="below">
                                <Button icon={ClipboardMinor} plain onClick={() => {}} />
                            </Tooltip>
                        </HorizontalStack>

                        {/* <Button id={"save-button"} onClick={handleRunTest} primary size="slim">Run Prompt</Button> */}
                    </div>
        
                    <Divider />
        
                    <div style={{height: '98vh', borderRight: '1px solid' , borderColor: '#E1E3E5'}} id={"yaml-editor"} ref={yamlEditorRef} />
                </VerticalStack>
                
        </div>
    )
}

export default PromptEditor
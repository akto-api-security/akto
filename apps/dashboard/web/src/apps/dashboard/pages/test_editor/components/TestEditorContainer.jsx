import { Box } from "@shopify/polaris"
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
import { useEffect, useRef, useState } from "react";
import YamlEditor from "./YamlEditor";
import SampleApi from "./SampleApi";

const TestEditorContainer = () => {
    const editorRef = useRef(null)
    const [editorText, setEditorText] = useState([])

    function createEditor(ref, options) {
        let text = null
        text = editor.create(ref, options)
        text.setValue("hello");
        setEditorText((old) => [...old, text.getValue()]);
      }

      useEffect(()=>{
            // createEditor(editorRef.current, {
            //   language: "yaml",
            //   minimap: { enabled: false },
            //   wordWrap: true,
            //   automaticLayout: true,
            //   colorDecorations: true,
            //   scrollBeyondLastLine: false,
            // })
          }, [])

    return (
        <div style={{ display: "grid", gridTemplateColumns: "50% 50%"}}>
            {/* <Box padding={"2"} ref={editorRef}>
            </Box> */}
            <YamlEditor />
            <SampleApi />
        </div>
    )
}

export default TestEditorContainer
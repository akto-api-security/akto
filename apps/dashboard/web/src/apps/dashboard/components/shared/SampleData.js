import React, { useState, useRef, useEffect } from 'react'
import { editor, Range } from "monaco-editor/esm/vs/editor/editor.api"
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/esm/vs/editor/contrib/comment/browser/comment';
import 'monaco-editor/esm/vs/editor/contrib/codelens/browser/codelensController';
import 'monaco-editor/esm/vs/editor/contrib/colorPicker/browser/color';
import 'monaco-editor/esm/vs/editor/contrib/format/browser/formatActions';
import 'monaco-editor/esm/vs/editor/contrib/lineSelection/browser/lineSelection';
import 'monaco-editor/esm/vs/editor/contrib/indentation/browser/indentation';
// import 'monaco-editor/esm/vs/editor/contrib/inlineCompletions/browser/inlineCompletionsController';
import 'monaco-editor/esm/vs/editor/contrib/snippet/browser/snippetController2'
import 'monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController';
import 'monaco-editor/esm/vs/editor/contrib/wordHighlighter/browser/wordHighlighter';
import "monaco-editor/esm/vs/language/json/monaco.contribution"
import "monaco-editor/esm/vs/language/json/json.worker"
import "monaco-editor/esm/vs/basic-languages/yaml/yaml.contribution"
import "./style.css";

function highlightPaths(highlightPathMap, ref){
  highlightPathMap && Object.keys(highlightPathMap).forEach((key) => {
      if (highlightPathMap[key].highlight) {
        let path = key.split("#");
        let mainKey = path[path.length - 1];
        let matches = ref.getModel().findMatches(mainKey, false, false, false, null, true);
        matches.forEach((match) => {
          ref.createDecorationsCollection([
              {
                range: new Range(match.range.startLineNumber, match.range.endColumn +3 , match.range.endLineNumber + 1, 0),
                options: {
                  inlineClassName: highlightPathMap[key].other ? "highlightOther" : "highlight",
                },
              }
            ])
          ref.revealLineInCenter(match.range.startLineNumber);
        })
      }
    })
}

function SampleData(props) {

    let {showDiff, data, minHeight, editorLanguage} = props;

    if(minHeight==undefined){
      minHeight="300px";
    }

    if(editorLanguage==undefined){
      editorLanguage='json'
    }

    const ref = useRef("");
    const [instance, setInstance] = useState(null);

    function createInstance(){
        const options = {
            language: editorLanguage,
            minimap: { enabled: false },
            wordWrap: true,
            automaticLayout: true,
            colorDecorations: true,
            scrollBeyondLastLine: false,
            readOnly: true,
            enableSplitViewResizing: false,
		        renderSideBySide: false
        }
        let instance = "";
        if(showDiff){
          instance = editor.createDiffEditor(ref.current, options)
        } else {
          instance = editor.create(ref.current, options) 
        }
        setInstance(instance)
    }

    function showData(data){
      if (showDiff) {
        let ogModel = editor.createModel(data?.original, "json")
        let model = editor.createModel(data?.message, "json")
        instance.setModel({
          original: ogModel,
          modified: model
        })
      } else {
        instance.setValue(data?.message)
        highlightPaths(data?.highlightPaths, instance);
      }
    }

    useEffect(() => {

      if (!instance) {
        createInstance();
      } else {
        showData(data);
      }
        
    }, [instance, data])

    return (
      <div ref={ref} style={{height:minHeight}} className='editor'/>
    )
}

export default SampleData
import React, { useState, useRef, useEffect } from 'react'
import {
    Box
    } from '@shopify/polaris';
import { editor, Range } from "monaco-editor/esm/vs/editor/editor.api"
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
    const ref = useRef("");
    const [instance, setInstance] = useState(null);

    function createInstance(){
        const options = {
            language: "json",
            minimap: { enabled: false },
            wordWrap: true,
            automaticLayout: true,
            colorDecorations: true,
            scrollBeyondLastLine: false,
            readOnly: true,
        }
        let instance = editor.create(ref.current, options) 
        setInstance(instance)
    }

    useEffect(() => {

        if(!instance){
            createInstance();
        } else {
          instance.setValue((props?.data?.firstLine!=undefined ? props?.data?.firstLine + "\n\n" : "") + JSON.stringify(props?.data?.json, null, 2))
          highlightPaths(props?.data?.highlightPaths, instance);
        }
        
    }, [props.data])

    return (
        <Box 
        ref={ref}
        minHeight={props.minHeight || '300px' }
        />
    )
}

export default SampleData
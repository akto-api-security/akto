import React, { useState, useRef, useEffect } from 'react'
import * as monaco from "monaco-editor"
import "./style.css";
import func from "@/util/func"
import editorSetup from './customEditor';
import yamlEditorSetup from "../../pages/test_editor/components/editor_config/editorSetup"
import keywords from "../../pages/test_editor/components/editor_config/keywords"

function highlightPaths(highlightPathMap, ref){
  highlightPathMap && Object.keys(highlightPathMap).forEach((key) => {
      if (highlightPathMap[key].highlight) {
        let path = key.split("#");
        let mainKey = path[path.length - 1];
        let matches = []
        try {
          matches = ref.getModel().findMatches(mainKey, false, false, false, null, true);
        } catch (error) {
          console.error(error)
          console.log("mainKey: " + mainKey)
        }
        matches.forEach((match) => {
          ref.createDecorationsCollection([
              {
                range: new monaco.Range(match.range.startLineNumber, match.range.endColumn + 3 , match.range.endLineNumber + 1, 1),
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

function highlightHeaders(data, ref, getLineNumbers){
  let diffRange = []
  const headerKeysMap = data.headersMap

  // add classname for first line only
  if(data.isUpdatedFirstLine){
    let strArr = data?.firstLine.split("->")
    ref.createDecorationsCollection([{
      range: new monaco.Range(1, 1, 2, 1),
      options:{
        inlineClassName: "updated-content",
        hoverMessage: [
          {
            supportHtml: true,
            value: `**<span style="color:#916A00;">MODIFIED</span>**`
          },
          {
            supportHtml: true,
            value: `**<span>${strArr[0]} -></span> <span style="color:#916A00;">${strArr[1]}</span>**`,
          }
        ]
      }
    }])
  }

  // add classname for content only
  let changesArr = []
  headerKeysMap && Object.keys(headerKeysMap).forEach((key) => {
    const header = key
    let matchRanges = []
    try {
      matchRanges = ref.getModel().findMatches(header, false, false, true, null, true, 1)
    } catch (error) {
      console.error(error)
      console.log("header: " + header)
    }
    changesArr = [ ...changesArr, ...matchRanges]
    matchRanges.forEach((obj) => {
      let matchRange = obj.range
      let startCol = headerKeysMap[key].className.includes("update") ? (matchRange.startColumn + headerKeysMap[key]?.keyLength + 2) : 1
      if(!headerKeysMap[key].className.includes("update")){
        diffRange.push({range: matchRange.startLineNumber, key: headerKeysMap[key].className})
      }else{
        let strArr = headerKeysMap[key].data.split("->")
        ref.createDecorationsCollection([{
          range: new monaco.Range(matchRange.startLineNumber, startCol, matchRange.endLineNumber + 1, 1),
          options:{
            inlineClassName: headerKeysMap[key].className,
            hoverMessage: [
              {
                supportHtml: true,
                value: `**<span style="color:#916A00;">MODIFIED</span>**`
              },
              {
                supportHtml: true,
                value: `**<span>${strArr[0]} -></span> <span style="color:#916A00;">${strArr[1]}</span>**`
              }
            ]
          }
        }])
      }
    })
    
  })
  changesArr = changesArr.map((item) => item.range.startLineNumber)
  if(data.isUpdatedFirstLine){
    changesArr.push(1)
  }
  changesArr.sort((a,b) => a - b)
  getLineNumbers(changesArr)
  // add classname to whole block to make a box
  diffRange.sort((a,b) => a.range - b.range)
  let currentRange = null
  let result = []
  diffRange = Array.from(new Set(diffRange.map(i => JSON.stringify(i))), JSON.parse);
  for (const obj of diffRange) {
    if (!currentRange) {
      currentRange = { start: obj.range, end: obj.range, key: obj.key };
    } else if (obj.range === currentRange.end + 1 && obj.key === currentRange.key) {
      currentRange.end = obj.range;
    } else {
      result.push(currentRange);
      currentRange = { start: obj.range, end: obj.range, key: obj.key };
    }
  }
  if (currentRange) {
    result.push(currentRange);
  }

  result.forEach((obj)=>{
    let className = obj.key.includes("added") ? "added-block" : "deleted-block"
    ref.createDecorationsCollection([{
      range: new monaco.Range(obj.start, 1, obj.end, 100),
      options: {
        blockClassName: className,
        isWholeLine: true
      }
    }])
  })
}

function SampleData(props) {

    let {showDiff, data, minHeight, editorLanguage, currLine, getLineNumbers, readOnly, getEditorData} = props;

    const ref = useRef(null);
    const [instance, setInstance] = useState(undefined);
    const [editorData, setEditorData] = useState(data);

    if(minHeight==undefined){
      minHeight="300px";
    }

    if(editorLanguage==undefined){
      editorLanguage='json'
    }

    if (readOnly == undefined) {
      readOnly = true
    }

    useEffect(() => {
      if (instance===undefined) {
        createInstance();
      }
    }, [])

    if (instance){
      if (!readOnly) {
        instance.onDidChangeModelContent(()=> {
            getEditorData(instance.getValue())
        })
      }
    }

    useEffect(() => {
      setEditorData((prev) => {
        if(func.deepComparison(prev, data)){
          return prev;
        }
          return data;
      })
    }, [data, currLine])

    useEffect(() => {
      if(instance!==undefined && editorData!==undefined){
        showData(editorData);
      }
    }, [instance, editorData])

    useEffect(()=>{
      instance && instance.revealLineInCenter(currLine)
      let a = instance && instance.createDecorationsCollection([{
        range: new monaco.Range(currLine, 1, currLine, 2),
        options: {
          blockClassName: "active-line"
        }
      }])
      if(a?._decorationIds){
        setTimeout(() => {
          instance && instance.removeDecorations(a?._decorationIds)
        }, 2000)
      }

    },[currLine])

    function createInstance(){
        const options = {
            language: editorLanguage,
            minimap: { enabled: false },
            wordWrap: true,
            automaticLayout: true,
            colorDecorations: true,
            scrollBeyondLastLine: false,
            readOnly: readOnly,
            enableSplitViewResizing: false,
		        renderSideBySide: false,
            // this prop doesn't work currently might be fixed in future versions.
            // solving this using custom CSS.
            lightbulb: { enabled: false },
            scrollbar:{
              alwaysConsumeMouseWheel: false
            },
            fixedOverflowWidgets: true 
        }
        let instance = "";
        if(editorLanguage.includes("custom")){
          options['theme']= "customTheme"
          editorSetup.registerLanguage()
          editorSetup.setTokenizer()
          yamlEditorSetup.setEditorTheme()
        }
        if(editorLanguage.includes("custom_yaml")){
          options['theme']= "customTheme"
          yamlEditorSetup.registerLanguage()
          yamlEditorSetup.setTokenizer()
          yamlEditorSetup.setEditorTheme()
          yamlEditorSetup.setAutoComplete(keywords)
        }
        if(showDiff){
          instance = monaco.editor.createDiffEditor(ref.current, options)
        } else {
          instance = monaco.editor.create(ref.current, options) 
        }
        setInstance(instance)

    }
    
    function showData(data){
      if (showDiff) {
        let ogModel = monaco.editor.createModel(data?.original, editorLanguage)
        let model = monaco.editor.createModel(data?.message, editorLanguage)
        instance.setModel({
          original: ogModel,
          modified: model
        })
      } else {
        let message = data.original ? data.original : data?.message 
        instance.setValue(message)
        highlightPaths(data?.highlightPaths, instance);
        if(data.headersMap){
          highlightHeaders(data, instance,getLineNumbers)
        }
      }
    }

    return (
      <div ref={ref} style={{height:minHeight}} className={'editor ' + (data.headersMap ? 'new-diff' : '')}/>
    )
}

export default SampleData
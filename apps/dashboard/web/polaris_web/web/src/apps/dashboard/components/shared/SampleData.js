import React, { useState, useRef, useEffect } from 'react'
import {Modal, Text} from "@shopify/polaris"
import * as monaco from "monaco-editor"
import "./style.css";
import func from "@/util/func"
import editorSetup from './customEditor';
import yamlEditorSetup from "../../pages/test_editor/components/editor_config/editorSetup"
import keywords from "../../pages/test_editor/components/editor_config/keywords"
import authTypesApi from "@/apps/dashboard/pages/settings/auth_types/api";

function highlightPaths(highlightPathMap, ref){
  highlightPathMap && Object.keys(highlightPathMap).forEach((key) => {
      if (highlightPathMap[key].highlight) {
        let path = key.split("#");
        let mainKey = path[path.length - 1];
        let matches = []
        try {
          matches = ref.getModel().findMatches(mainKey, false, false, false, null, true);
        } catch (error) {
          // Silently handle errors in production
        }
        matches.forEach((match) => {
          let matchDecObj ={
            range: new monaco.Range(match.range.startLineNumber, match.range.endColumn + 3 , match.range.endLineNumber + 1, 1),
            options: {
              inlineClassName: highlightPathMap[key].other ? "highlightOther" : "highlight",
            },
          }
          if(highlightPathMap[key]?.wholeRow === true){
            matchDecObj = {
              range: new monaco.Range(match.range.startLineNumber, 1, match.range.endLineNumber, 100),
              options: {
                blockClassName: highlightPathMap[key]?.className,
                isWholeLine: true
              }
            }
          }
          ref.createDecorationsCollection([
              matchDecObj
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
      // Silently handle errors in production
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

function highlightVulnerabilities(vulnerabilitySegments, ref) {
  if (!ref || !Array.isArray(vulnerabilitySegments) || vulnerabilitySegments.length === 0) {
    return;
  }

  const model = ref.getModel();
  const text = ref.getValue();

  if (!model || !text) {
    return;
  }

  const textLength = text.length;
  const isValidRange = (start, end) => Number.isFinite(start) && Number.isFinite(end) && start >= 0 && end <= textLength && start < end;
  const resolvePhrase = (segment) => {
    if (typeof segment?.phrase === 'string' && segment.phrase.length > 0) {
      return segment.phrase;
    }
    if (typeof segment?.message === 'string' && segment.message.length > 0) {
      return segment.message.replace(/\s*\[chars\s+\d+-\d+\]\s*$/, '');
    }
    return undefined;
  };

  const decorations = [];

  vulnerabilitySegments.forEach((segment) => {
    try {
      let start = Number(segment?.start);
      let end = Number(segment?.end);
      const phrase = resolvePhrase(segment);

      if (phrase) {
        const phraseMatchesProvidedRange = isValidRange(start, end) && text.slice(start, end) === phrase;
        if (!phraseMatchesProvidedRange) {
          const idx = text.indexOf(phrase);
          if (idx >= 0) {
            start = idx;
            end = idx + phrase.length;
          }
        }
      }

      if (!isValidRange(start, end)) {
        return;
      }

      const startPos = model.getPositionAt(start);
      const endPos = model.getPositionAt(end);

      if (!startPos || !endPos) {
        return;
      }

      decorations.push({
        range: new monaco.Range(startPos.lineNumber, startPos.column, endPos.lineNumber, endPos.column),
        options: {
          inlineClassName: "vulnerability-highlight"
        }
      });
    } catch (error) {
      console.error('Error creating vulnerability highlight:', error, segment);
    }
  });

  if (decorations.length > 0) {
    ref._vulnDecorations = ref.createDecorationsCollection(decorations);
  }
}

function SampleData(props) {

    let {showDiff, data, minHeight, editorLanguage, currLine, getLineNumbers, readOnly, getEditorData, wordWrap} = props;

    const ref = useRef(null);
    const [instance, setInstance] = useState(undefined);
    const [editorData, setEditorData] = useState(data);
    const [showActionsModal, setShowActionsModal] = useState(false);
    const [showErrorModal, setShowErrorModal] = useState(false);
    const [selectedWord, setSelectedWord] = useState("");
    const [dynamicHeight, setDynamicHeight] = useState(minHeight || '300px');

    if(minHeight==undefined){
      minHeight="300px";
    }

    if(editorLanguage==undefined){
      editorLanguage='json'
    }

    if (readOnly == undefined) {
      readOnly = true
    }

    if (wordWrap == undefined) {
      wordWrap = true
    }

    useEffect(() => {
      if (instance===undefined) {
        createInstance();
      }
    }, [])

    useEffect(() => {
      if (instance && props?.useDynamicHeight) {
          const disposeOnContentSizeChange = instance.onDidContentSizeChange((e) => {
            const contentHeight = e.contentHeight > 900 ? 900 : e.contentHeight // 3600 means 200 lines (18 == 1 line)
            setDynamicHeight(`${contentHeight}px`)
          })
          return () => disposeOnContentSizeChange.dispose()
      }

  }, [instance])

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

    // Add effect to re-highlight vulnerabilities if they change
    useEffect(() => {
      if (instance && editorData) {
        // Use backend-provided vulnerabilitySegments if available
        const segments = Array.isArray(editorData.vulnerabilitySegments) && editorData.vulnerabilitySegments.length > 0
          ? editorData.vulnerabilitySegments
          : []
        highlightVulnerabilities(segments, instance);
      }
    }, [instance, editorData?.vulnerabilitySegments]);

    function createInstance(){
        const options = {
            language: editorLanguage,
            minimap: { enabled: false },
            wordWrap: wordWrap,
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
        if(editorLanguage.includes("custom_http")){
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
          instance.addAction({
            id: "add_auth_type",
            label: "Add as Header auth type",
            keybindings: [],
            precondition: null,
            keybindingContext: null,
            contextMenuGroupId: "1_modification",
            contextMenuOrder: 1,
            run: function (ed) {
              var textSelected = ed.getModel().getValueInRange(ed.getSelection())
              setSelectedWord(textSelected)
              if (textSelected && textSelected.length > 0) {
                setShowActionsModal(true)
              } else {
                setShowErrorModal(true)
              }
            },
          });
          
        }
        instance.updateOptions({ tabSize: 2 })
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
        if(data.vulnerabilitySegments){
          highlightVulnerabilities(data.vulnerabilitySegments, instance);
        }
      }
    }

    function createAuthTypeHeader(selectedWord) {
      authTypesApi.addCustomAuthType(selectedWord, [selectedWord], [], true).then((res) => {
        func.setToast(true, false, "Auth type added successfully");
        setSelectedWord("")
        setShowActionsModal(false)
      }).catch((err) => {
        func.setToast(true, true, "Unable to add auth type");
        setSelectedWord("")
        setShowActionsModal(false)
      });
    }

    return (
      <div>
        <div ref={ref} style={{height:dynamicHeight}} className={'editor ' + (data.headersMap ? 'new-diff' : '')}/>
        <Modal
            open={showActionsModal}
            onClose={() => setShowActionsModal(false)}
            title="Are you sure?"
            primaryAction={{
                content: 'Create',
                onAction: () => createAuthTypeHeader(selectedWord)
            }}
            key="redact-modal-2"
        >
            <Modal.Section>
                <Text>Are you sure you want to add the header (or cookie) key: <b>{selectedWord.toLowerCase()}</b> as an auth type?</Text>
            </Modal.Section>
        </Modal>
        <Modal
            open={showErrorModal}
            onClose={() => setShowErrorModal(false)}
            title="Incorrect data"
            primaryAction={{
                content: 'OK',
                onAction: () => setShowErrorModal(false)
            }}
            key="redact-modal-3"
        >
            <Modal.Section>
                <Text>Invalid auth type: <b>{(selectedWord && selectedWord.length>0) ? selectedWord.toLowerCase(): "blank"}</b></Text>
            </Modal.Section>
        </Modal>
      </div>
      
    )
}

export default SampleData
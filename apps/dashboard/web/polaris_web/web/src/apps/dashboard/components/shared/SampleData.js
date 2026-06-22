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
  const isWordChar = (ch) => typeof ch === 'string' && /[A-Za-z0-9_-]/.test(ch);

  const resolvePhrase = (segment) => {
    if (typeof segment?.phrase === 'string' && segment.phrase.trim().length > 0) {
      return segment.phrase.trim();
    }
    if (typeof segment?.message === 'string' && segment.message.length > 0) {
      return segment.message.replace(/\s*\[chars\s+\d+-\d+\]\s*$/, '').trim();
    }
    return undefined;
  };

  // The LLM's char offsets are computed against a different text layout than the
  // editor renders, so they are unreliable. We anchor strictly to the phrase the
  // LLM flagged. Build progressively looser candidates so we still match when the
  // phrase is wrapped in quotes, given as a JSON "key":"value" pair (while the
  // editor renders headers as "key: value"), or truncated mid-token.
  const buildPhraseCandidates = (phrase) => {
    const candidates = [];
    const add = (p) => {
      if (typeof p !== 'string') {
        return;
      }
      const trimmed = p.trim().replace(/^[\s"'`]+|[\s"',`]+$/g, '');
      if (trimmed.length >= 2 && !candidates.includes(trimmed)) {
        candidates.push(trimmed);
      }
    };

    add(phrase);
    if (phrase) {
      // JSON pair -> value only (handles `"authorization":"Bearer x"` vs editor `authorization: Bearer x`).
      const colonIdx = phrase.indexOf(':');
      if (colonIdx >= 0 && colonIdx < phrase.length - 1) {
        add(phrase.slice(colonIdx + 1));
      }
    }
    // For long tokens (e.g. JWTs) the LLM often truncates or the formatting
    // differs; a unique prefix is enough to anchor the highlight.
    candidates.slice().forEach((c) => {
      if (c.length > 40) {
        add(c.slice(0, 40));
      }
    });
    return candidates;
  };

  // Exact occurrence, preferring whole-token matches so "envoy" doesn't match
  // inside "x-envoy-..." and disambiguating with the LLM's hint position.
  const findExactRange = (phrase, hintStart) => {
    const matches = [];
    let from = 0;
    while (from <= textLength) {
      const idx = text.indexOf(phrase, from);
      if (idx === -1) {
        break;
      }
      const endIdx = idx + phrase.length;
      const boundedBefore = idx === 0 || !isWordChar(text[idx - 1]) || !isWordChar(phrase[0]);
      const boundedAfter = endIdx >= textLength || !isWordChar(text[endIdx]) || !isWordChar(phrase[phrase.length - 1]);
      matches.push({ start: idx, end: endIdx, wholeToken: boundedBefore && boundedAfter });
      from = idx + 1;
    }
    if (matches.length === 0) {
      return null;
    }
    const wholeTokenMatches = matches.filter((m) => m.wholeToken);
    const candidates = wholeTokenMatches.length > 0 ? wholeTokenMatches : matches;
    if (Number.isFinite(hintStart)) {
      candidates.sort((a, b) => Math.abs(a.start - hintStart) - Math.abs(b.start - hintStart));
    }
    return candidates[0];
  };

  // Whitespace-tolerant, case-insensitive match for when the LLM collapses
  // newlines/indentation or changes header-name casing.
  const findFlexibleRange = (phrase) => {
    const escaped = phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/\s+/g, '\\s+');
    try {
      const match = new RegExp(escaped, 'i').exec(text);
      if (match) {
        return { start: match.index, end: match.index + match[0].length };
      }
    } catch (e) {
      // invalid regex, ignore
    }
    return null;
  };

  const locateSegment = (segment) => {
    const phrase = resolvePhrase(segment);
    if (!phrase) {
      return null;
    }
    const hintStart = Number(segment?.start);
    const candidates = buildPhraseCandidates(phrase);
    for (const candidate of candidates) {
      const range = findExactRange(candidate, hintStart);
      if (range) {
        return range;
      }
    }
    for (const candidate of candidates) {
      const range = findFlexibleRange(candidate);
      if (range) {
        return range;
      }
    }
    return null;
  };

  const decorations = [];
  let firstStart = Infinity;

  vulnerabilitySegments.forEach((segment) => {
    try {
      const range = locateSegment(segment);
      // If we cannot locate the exact phrase, skip rather than highlight the
      // wrong span using unreliable offsets.
      if (!range) {
        return;
      }

      const startPos = model.getPositionAt(range.start);
      const endPos = model.getPositionAt(range.end);

      if (!startPos || !endPos) {
        return;
      }

      firstStart = Math.min(firstStart, range.start);

      const reason = typeof segment?.reason === 'string' ? segment.reason.trim() : '';
      const options = {
        inlineClassName: "vulnerability-highlight",
        // Marker in the scrollbar so evidence is easy to find/jump to in large responses.
        overviewRuler: {
          color: "rgba(139, 69, 255, 0.8)",
          position: monaco.editor.OverviewRulerLane.Right
        }
      };
      if (reason) {
        options.hoverMessage = [
          { value: "**Evidence**" },
          { value: reason }
        ];
      }

      decorations.push({
        range: new monaco.Range(startPos.lineNumber, startPos.column, endPos.lineNumber, endPos.column),
        options
      });
    } catch (error) {
      console.error('Error creating vulnerability highlight:', error, segment);
    }
  });

  if (decorations.length > 0) {
    if (ref._vulnDecorations) {
      ref._vulnDecorations.clear();
    }
    ref._vulnDecorations = ref.createDecorationsCollection(decorations);

    // Scroll the first piece of evidence into view so users don't have to hunt
    // through a large response.
    if (Number.isFinite(firstStart)) {
      const pos = model.getPositionAt(firstStart);
      if (pos) {
        ref.revealLineInCenter(pos.lineNumber);
      }
    }
  }
}

// Registered once — overrides Monaco's default blue heading colour for markdown
// files (e.g. Skill.md in the violations flyout) to Akto purple.
let _violationMarkdownThemeDefined = false;
function ensureViolationMarkdownTheme() {
    if (_violationMarkdownThemeDefined) return;
    monaco.editor.defineTheme('violation-markdown', {
        base: 'vs',
        inherit: true,
        rules: [
            { token: 'keyword',    foreground: '9642FC' },
            { token: 'keyword.md', foreground: '9642FC' },
            { token: 'strong',     foreground: '553C9A', fontStyle: 'bold' },
            { token: 'emphasis',   foreground: '805AD5', fontStyle: 'italic' },
        ],
        colors: {},
    });
    _violationMarkdownThemeDefined = true;
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
      setDynamicHeight(minHeight || '300px')
    }, [minHeight])

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
        if(editorLanguage === 'markdown'){
          ensureViolationMarkdownTheme();
          options['theme'] = 'violation-markdown';
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
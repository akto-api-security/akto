import React, { useState, useRef, useEffect } from 'react'
import {
  ClipboardMinor} from '@shopify/polaris-icons';
import {
  Text,
  VerticalStack,
  HorizontalStack, Box, LegacyCard, HorizontalGrid,
  Pagination, Key, Button, Popover, ActionList
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
import api from '../../pages/observe/api';
import "./style.css";
import Store from '../../store';

function formatJSON(val = {}) {
    try {
      const res = typeof val == 'object' ? val : JSON.parse(val);
      Object.keys(res).forEach((key) => {
        res[key] = formatJSON(res[key])
      })
      return res;
    } catch {
      return val;
    }
  }

function highlightPaths(highlightPathMap, refText){
  highlightPathMap && Object.keys(highlightPathMap).forEach((type) => {
    Object.keys(highlightPathMap[type]).forEach((key) => {
      if (highlightPathMap[type][key].highlight) {
        let path = key.split("#");
        let mainKey = path[path.length - 1];
        let matches = refText[type].getModel().findMatches(mainKey, false, false, false, null, true);
        matches.forEach((match) => {
          refText[type]
            .createDecorationsCollection([
              {
                range: new Range(match.range.startLineNumber, match.range.endColumn +3 , match.range.endLineNumber + 1, 0),
                options: {
                  inlineClassName: "highlight",
                },
              }
            ])
        })
      }
    })
  })
}

function SampleDataList(props) {
    const requestRef = useRef("");
    const responseRef = useRef("");
    const [refText, setRefText] = useState({})
    const [page, setPage] = useState(0);
    const [popoverActive, setPopoverActive] = useState(false);
    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }
    async function copyRequest(type, completeData) {
      let copyString = "";
      let snackBarMessage = ""
      completeData = JSON.parse(completeData);
      if (type=="RESPONSE") {
        let responsePayload = {}
        let responseHeaders = {}
        let statusCode = 0
    
        if (completeData) {
          responsePayload = completeData["response"] ?  completeData["response"]["body"] : completeData["responsePayload"]
          responseHeaders = completeData["response"] ?  completeData["response"]["headers"] : completeData["responseHeaders"]
          statusCode = completeData["response"] ?  completeData["response"]["statusCode"] : completeData["statusCode"]
        }
        let b = {
          "responsePayload": responsePayload,
          "responseHeaders": responseHeaders,
          "statusCode": statusCode
        }
    
        copyString = JSON.stringify(b)
        snackBarMessage = "Response data copied to clipboard"
      } else {
        if (type === "CURL") { 
          snackBarMessage = "Curl request copied to clipboard"
          let resp = await api.convertSampleDataToCurl(JSON.stringify(completeData))
          copyString = resp.curlString
        } else {
          snackBarMessage = "Burp request copied to clipboard"
          let resp = await api.convertSampleDataToBurpRequest(JSON.stringify(completeData))
          copyString = resp.burpRequest
        }
      }
    
      if (copyString) {
        navigator.clipboard.writeText(copyString)
        setToast(true, false, snackBarMessage)
        setPopoverActive(false)
      }
    }

    function createEditor(ref, options, type) {
      let text = null
      text = editor.create(ref, options)
      text.setValue("");
      setRefText((old) => ({
        ...old, [type]:text
      }) )
    }
    useEffect(()=>{
      if(Object.keys(refText).length==0){
        [requestRef, responseRef].map((ref, index) => {
          // handle graphQL APIs
          createEditor(ref.current, {
            language: "json",
            minimap: { enabled: false },
            wordWrap: true,
            automaticLayout: true,
            colorDecorations: true,
            scrollBeyondLastLine: false,
            readOnly: true,
          },index == 0 ? "request" : "response" )
        })
      } else {
        refText.request.setValue("")
        refText.response.setValue("")
      }
      if (props.sampleData?.[page] && Object.keys(refText).length==2) {
        let message = formatJSON(props.sampleData?.[page].message);
        let res = {}, req = {}
        Object.keys(message).forEach((key) => {
          if (key.startsWith("req") || key.startsWith("query")) {
            req[key] = message[key]
          } else if (key.startsWith("res")) {
            res[key] = message[key]
          }
        })
        refText.request.setValue(JSON.stringify(req, null, 2))
        refText.response.setValue(JSON.stringify(res, null, 2))

        highlightPaths(props.sampleData?.[page].highlightPathMap, refText);
      }
    }, [props.sampleData, page, refText])
  
    return (
      <VerticalStack gap="4">
        <HorizontalStack align='space-between'>
        <Text variant='headingLg'>
          {props.heading}
        </Text>
        <Pagination
                label={
                  props.sampleData?.length==0 ? 'No test runs found' :
                  `${page+1} of ${props.sampleData?.length}`
                }
                hasPrevious = {page > 0}
                previousKeys={[Key.LeftArrow]}
                onPrevious={() => {setPage((old) => (old-1))}}
                hasNext = {props.sampleData?.length > (page+1)}
                nextKeys={[Key.RightArrow]}
                onNext={() => {setPage((old) => (old+1))}}
              />
        </HorizontalStack>
        <HorizontalGrid columns="2" gap="2">
          {
            [requestRef, responseRef].map((ref, index) => {
              return (
                <Box key={index}>
                  <LegacyCard>
                    <LegacyCard.Section flush>
                      <Box padding={"2"}>
                        <HorizontalStack padding="2" align='space-between'>
                          {index == 0 ? "Request" : "Response"}
                          {index == 0 ? (
                            <Popover
                              active={popoverActive}
                              activator={<Button icon={ClipboardMinor} plain onClick={() => setPopoverActive(!popoverActive)} />}
                              onClose={() => setPopoverActive(false)}

                            >
                              <ActionList
                                actionRole="menuitem"
                                items={[
                                  {
                                    content: 'Copy as CURL',
                                    onAction: () => {copyRequest("CURL",props.sampleData?.[page].message)} ,
                                  },
                                  {
                                    content: 'Copy as burp',
                                    onAction: () => {copyRequest("BURP",props.sampleData?.[page].message)} ,
                                  },
                                ]}

                              />
                            </Popover>
                          ) : (
                            <Button icon={ClipboardMinor} plain  onClick={() => copyRequest("RESPONSE",props.sampleData?.[page].message)}/>
                          )}
                        </HorizontalStack>
                      </Box>
                    </LegacyCard.Section>
                    <LegacyCard.Section flush>
                      <Box padding={"2"} ref={ref} minHeight="300px">
                      </Box>
                    </LegacyCard.Section>
                  </LegacyCard>
                </Box>
              )
            })
          }
        </HorizontalGrid>
      </VerticalStack>
    )
  }

  export default SampleDataList
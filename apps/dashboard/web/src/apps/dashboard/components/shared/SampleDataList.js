import React, { useState, useRef, useEffect } from 'react'
import {
  ClipboardMinor} from '@shopify/polaris-icons';
import {
  Text,
  VerticalStack,
  HorizontalStack, Icon, Box, LegacyCard, HorizontalGrid,
  Pagination, Key
} from '@shopify/polaris';
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

function SampleDataList(props) {
    const requestRef = useRef("");
    const responseRef = useRef("");
    const [refText, setRefText] = useState([])
    const [page, setPage] = useState(0);
  
    function createEditor(ref, options) {
      let text = null
      text = editor.create(ref, options)
      text.setValue("");
      setRefText((old) => [...old, text]);
    }
    useEffect(()=>{
      if(refText.length==0){
        [requestRef, responseRef].map((ref) => {
          createEditor(ref.current, {
            language: "json",
            minimap: { enabled: false },
            wordWrap: true,
            automaticLayout: true,
            colorDecorations: true,
            scrollBeyondLastLine: false,
            readOnly: true,
          })
        })
      }
      if (props.sampleData?.[page] && refText.length==2) {
        let message = formatJSON(props.sampleData?.[page]);
        let res = {}, req = {}
        Object.keys(message).forEach((key) => {
          if (key.startsWith("req") || key.startsWith("query")) {
            req[key] = message[key]
          } else if (key.startsWith("res")) {
            res[key] = message[key]
          }
        })
          refText[0].setValue(JSON.stringify(req, null, 2))
          refText[1].setValue(JSON.stringify(res, null, 2))
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
                          <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                            <Icon source={ClipboardMinor} />
                          </div>
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
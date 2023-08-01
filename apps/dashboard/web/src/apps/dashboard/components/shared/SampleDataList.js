import React, { useState, useEffect } from 'react'
import {
  ClipboardMinor} from '@shopify/polaris-icons';
import {
  Text,
  VerticalStack,
  HorizontalStack, Box, LegacyCard, HorizontalGrid,
  Pagination, Key, Button, Popover, ActionList
} from '@shopify/polaris';
import SampleData from './SampleData';
import func from "@/util/func";

function formatData(data){
  var allKeys = [];
    var seen = {};
    JSON.stringify(data.json, function (key, value) {
        if (!(key in seen)) {
            allKeys.push(key);
            seen[key] = null;
        }
        return value;
    });
    allKeys.sort();
  return (data?.firstLine ? data?.firstLine + "\n\n" : "") + (data?.json ? JSON.stringify(data.json, allKeys, 2) : "");
}

function SampleDataList(props) {

    const {showDiff, sampleData, heading} = props;

    const [sampleJsonData, setSampleJsonData] = useState({request:"",response:""});
    const [page, setPage] = useState(0);
    const [popoverActive, setPopoverActive] = useState({});

    async function copyRequest(reqType, type, completeData){
      let {copyString, snackBarMessage} = await func.copyRequest(type, completeData)
      if (copyString) {
        navigator.clipboard.writeText(copyString)
        func.setToast(true, false, snackBarMessage)
        setPopoverActive({ [reqType]: !popoverActive[reqType] })
      }
    }

    function getItems(type, data) {
      let items = []
    
      if (type == "request") {
        if (data.message) {
          items.push({
            content: 'Copy request as curl',
            onAction: () => { copyRequest(type, "CURL", data.message) },
          },
            {
              content: 'Copy request as burp',
              onAction: () => { copyRequest(type, "BURP", data.message) },
            })
        }
        if (data.originalMessage) {
          items.push({
            content: 'Copy original request as curl',
            onAction: () => { copyRequest(type, "CURL", data.originalMessage) },
          },
            {
              content: 'Copy original request as burp',
              onAction: () => { copyRequest(type, "BURP", data.originalMessage) },
            })
        }
      } else {
        if (data.message) {
          items.push({
            content: 'Copy response',
            onAction: () => { copyRequest(type, "RESPONSE", data.message) },
          })
        }
        if (data.originalMessage) {
          items.push({
            content: 'Copy original response',
            onAction: () => { copyRequest(type, "RESPONSE", data.originalMessage) },
          })
        }
      }
    
      return items;
    }

    useEffect(()=>{
      let parsed = undefined;
      try{
        parsed = JSON.parse(sampleData?.[page]?.message)
      } catch {
        parsed = undefined
      }
      let responseJson = func.responseJson(parsed, sampleData?.[page].highlightPaths)
      let requestJson = func.requestJson(parsed, sampleData?.[page].highlightPaths)
      
      let originalParsed = undefined;
      try{
        originalParsed = JSON.parse(sampleData?.[page]?.originalMessage)
      } catch {
        originalParsed = undefined
      }
      let originalResponseJson = func.responseJson(originalParsed, sampleData?.[page].highlightPaths)
      let originalRequestJson = func.requestJson(originalParsed, sampleData?.[page].highlightPaths)

      setSampleJsonData({ 
        request: { message: formatData(requestJson), original: formatData(originalRequestJson), highlightPaths:requestJson?.highlightPaths }, 
        response: { message: formatData(responseJson), original: formatData(originalResponseJson), highlightPaths:responseJson?.highlightPaths },
      })
    }, [sampleData, page])
  
    return (
      <VerticalStack gap="4">
        <HorizontalStack align='space-between'>
        <Text variant='headingLg'>
          {heading}
        </Text>
        <Pagination
                label={
                  sampleData?.length==0 ? 'No test runs found' :
                  `${page+1} of ${sampleData?.length}`
                }
                hasPrevious = {page > 0}
                previousKeys={[Key.LeftArrow]}
                onPrevious={() => {setPage((old) => (old-1))}}
                hasNext = {sampleData?.length > (page+1)}
                nextKeys={[Key.RightArrow]}
                onNext={() => {setPage((old) => (old+1))}}
              />
        </HorizontalStack>
        <HorizontalGrid columns="2" gap="2">
          {
            Object.keys(sampleJsonData).map((type, index) => {
              return (
                <Box key={type}>
                  <LegacyCard>
                    <LegacyCard.Section flush>
                      <Box padding={"2"}>
                        <HorizontalStack padding="2" align='space-between'>
                          {func.toSentenceCase(type)}
                            <Popover
                              zIndexOverride={"600"}
                              active={popoverActive[type]}
                              activator={<Button icon={ClipboardMinor} plain onClick={() => setPopoverActive({ [type]: !popoverActive[type] })} />}
                              onClose={() => setPopoverActive(false)}
                            >
                              <ActionList
                                actionRole="menuitem"
                                items={getItems(type, sampleData[page])}
                              />
                            </Popover>
                        </HorizontalStack>
                      </Box>
                    </LegacyCard.Section>
                    <LegacyCard.Section flush>
                      <SampleData data={sampleJsonData[type]} minHeight={"400px"} showDiff={showDiff}/>
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
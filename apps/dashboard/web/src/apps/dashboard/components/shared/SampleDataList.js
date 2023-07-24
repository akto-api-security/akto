import React, { useState, useEffect } from 'react'
import {
  ClipboardMinor} from '@shopify/polaris-icons';
import {
  Text,
  VerticalStack,
  HorizontalStack, Box, LegacyCard, HorizontalGrid,
  Pagination, Key, Button, Popover, ActionList
} from '@shopify/polaris';
import Store from '../../store';
import SampleData from './SampleData';
import func from "@/util/func";

function SampleDataList(props) {
    const [sampleJsonData, setSampleJsonData] = useState({request:"",response:""});
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
    async function copyRequest(type, completeData){
      let {copyString, snackBarMessage} = await func.copyRequest(type, completeData)
      if (copyString) {
        navigator.clipboard.writeText(copyString)
        setToast(true, false, snackBarMessage)
        setPopoverActive(false)
      }
    }

    useEffect(()=>{
      let parsed = JSON.parse(props.sampleData?.[page].message);
      let responseJson = func.responseJson(parsed, props.sampleData?.[page].highlightPaths)
      let requestJson = func.requestJson(parsed, props.sampleData?.[page].highlightPaths)
      setSampleJsonData({ request: requestJson, response: responseJson })
    }, [props.sampleData, page])
  
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
            Object.keys(sampleJsonData).map((type, index) => {
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
                      <SampleData data={sampleJsonData[type]} minHeight={"300px"}/>
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
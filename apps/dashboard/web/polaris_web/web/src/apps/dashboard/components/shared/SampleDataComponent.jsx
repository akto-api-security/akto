import React, { useState, useEffect, useRef } from 'react'
import {
    ClipboardMinor,ArrowDownMinor, ArrowUpMinor
} from '@shopify/polaris-icons';
import {
    HorizontalStack, Box, LegacyCard,
    Button, Popover, ActionList, Icon, Text, Tooltip
} from '@shopify/polaris';
import SampleData from './SampleData';
import func from "@/util/func";
import inventoryApi from "../../pages/observe/api"
import transform from './customDiffEditor';

function SampleDataComponent(props) {

    const { type, sampleData, minHeight, showDiff, isNewDiff, metadata, readOnly = false, getEditorData = () => {}, showResponse = true } = props;
    const [sampleJsonData, setSampleJsonData] = useState({ request: { message: "" }, response: { message: "" } });
    const [popoverActive, setPopoverActive] = useState({});
    const [lineNumbers, setLineNumbers] = useState({request: [], response: []})
    const [currentIndex, setCurrentIndex] = useState({request: 0, response: 0})
    const [responseTime, setResponseTime] = useState(undefined)
    const [ipObj, setIpObj] = useState({sourceIP: "", destIP: ""})

    const ref = useRef(null)

    useEffect(()=>{

        // Metadata parsing: JSON only
        let parsed;
        try{
          parsed = JSON.parse(sampleData?.message)
        } catch {
          parsed = undefined
        }
        if (parsed?.ip != null && parsed?.destIp != null) {
            setIpObj({sourceIP: parsed?.ip, destIP: parsed?.destIp})
        }
        let responseJson = showResponse ? func.responseJson(parsed, sampleData?.highlightPaths || [], metadata) : {}
        let requestJson = func.requestJson(parsed, sampleData?.highlightPaths || [], metadata)

        let responseTime = parsed?.responseTime;
        setResponseTime(responseTime)
        
        let originalParsed;
        try{
          originalParsed = JSON.parse(sampleData?.originalMessage)
        } catch {
          originalParsed = undefined
        }
        let originalResponseJson = func.responseJson(originalParsed, sampleData?.highlightPaths || [])
        let originalRequestJson = func.requestJson(originalParsed, sampleData?.highlightPaths || [])

        // --- Parse metadata to extract vulnerabilitySegments for threat highlighting ---
        const normalizeSegments = (errors = []) => {
          return errors
            .filter(err => !isNaN(err.start) && !isNaN(err.end))
            .map(err => ({
              ...err,
              start: err.start,
              end: err.end,
              phrase: err.phrase
            }));
        };

        const selectMetadataSegments = (rawMetadata) => {
          if (!rawMetadata) {
            return { segments: [], fromMetadata: false };
          }

          if (typeof rawMetadata === 'string') {
            try {
              const parsedMeta = JSON.parse(rawMetadata);
              const segments = normalizeSegments(parsedMeta?.schemaErrors);
              if (segments.length > 0) {
                return { segments, fromMetadata: true };
              }
            } catch (error) {
              console.error('Error parsing metadata JSON:', error);
            }
          } else {
            const segments = normalizeSegments(rawMetadata?.schemaErrors);
            if (segments.length > 0) {
              return { segments, fromMetadata: true };
            }
          }

          return { segments: [], fromMetadata: false };
        };

        const effectiveMetadata = metadata ?? sampleData?.metadata;
        const { segments: metadataSegments, fromMetadata } = selectMetadataSegments(effectiveMetadata);
        const baseSegments = sampleData?.vulnerabilitySegments || [];
        const vulnerabilitySegments = metadataSegments.length > 0 ? metadataSegments : baseSegments;
        const segmentsFromMetadata = fromMetadata;

        if(isNewDiff){
            let lineReqObj = transform.getFirstLine(originalRequestJson?.firstLine,requestJson?.firstLine)
            let lineResObj = transform.getFirstLine(originalResponseJson?.firstLine,responseJson?.firstLine)

            let requestHeaderObj = transform.compareJsonKeys(originalRequestJson?.json?.requestHeaders,requestJson?.json?.requestHeaders)
            let responseHeaderObj = transform.compareJsonKeys(originalResponseJson?.json?.responseHeaders,responseJson?.json?.responseHeaders)
            
            let requestPayloadObj = transform.getPayloadData(originalRequestJson?.json?.requestPayload,requestJson?.json?.requestPayload)
            let responsePayloadObj = transform.getPayloadData(originalResponseJson?.json?.responsePayload,responseJson?.json?.responsePayload)
            
            const requestData = transform.mergeDataObjs(lineReqObj, requestHeaderObj, requestPayloadObj)
            const responseData = transform.mergeDataObjs(lineResObj, responseHeaderObj, responsePayloadObj)

            setSampleJsonData({
                request: requestData,
                response: { ...responseData, vulnerabilitySegments }
            })
        }else{
            setSampleJsonData({ 
                // If segments came from threat metadata, highlight in request; if they were provided by caller (e.g., LLM analysis), pass to both panes
                request: { message: transform.formatData(requestJson,"http"), original: transform.formatData(originalRequestJson,"http"), highlightPaths:requestJson?.highlightPaths, vulnerabilitySegments }, 
                response: showResponse ? { message: transform.formatData(responseJson,"http"), original: transform.formatData(originalResponseJson,"http"), highlightPaths:responseJson?.highlightPaths, ...(segmentsFromMetadata ? {} : {vulnerabilitySegments}) } : {},
            })
        }
      }, [sampleData, metadata])

    const copyContent = async(type,completeData) => {
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
            const completeDataWithoutResponse = { ...completeData };
            // Check and transform the required fields
            if (completeDataWithoutResponse["response"]) {
                completeDataWithoutResponse["response"]= {"body": "{}", "headers": "{}"};
            } else if (completeDataWithoutResponse["responsePayload"]) {
                completeDataWithoutResponse["responsePayload"]= "{}";
                completeDataWithoutResponse["responseHeaders"]= "{}";
            }
            if (type === "CURL") { 
                snackBarMessage = "Curl request copied to clipboard"
                let resp = await inventoryApi.convertSampleDataToCurl(JSON.stringify(completeDataWithoutResponse))
                copyString = resp.curlString
            } else {
            snackBarMessage = "Burp request copied to clipboard"
            let resp = await inventoryApi.convertSampleDataToBurpRequest(JSON.stringify(completeDataWithoutResponse))
            copyString = resp.burpRequest
            }
        }
        return {copyString, snackBarMessage};
    }

    async function copyRequest(reqType, type, completeData) {
        let { copyString, snackBarMessage } = await copyContent(type, completeData)
        if (copyString) {
            func.copyToClipboard(copyString, ref, snackBarMessage)
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
                if(items.length==2){
                    items[0].content = "Copy attempt request as curl"
                    items[1].content = "Copy attempt request as burp"
                }
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
                if(items.length==1){
                    items[0].content = "Copy attempt response"
                }
                items.push({
                    content: 'Copy original response',
                    onAction: () => { copyRequest(type, "RESPONSE", data.originalMessage) },
                })
            }
        }

        return items;
    }

    const getLineNumbers = (linesArr) =>{
        setLineNumbers((prev)=>{
            return {...prev, [type]: linesArr.slice()}
        })
    }

    const checkButtonActive = (buttonType) => {
        if(buttonType === 'next'){
            return currentIndex[type] < (lineNumbers[type]?.length - 1)
        }else{
            return currentIndex[type] > 0
        }
    }

    const changeIndex = (buttonType) => {
        if(buttonType === 'next'){
            setCurrentIndex((prev)=>{
                return {...prev, [type]: prev[type] + 1}
            })
        }else{
            setCurrentIndex((prev)=>{
                return {...prev, [type]: prev[type] - 1}
            })
        }
    }

    let currentLineActive = lineNumbers && lineNumbers[type].length > 0 ? lineNumbers[type][currentIndex[type]] : 1
    return (

        <Box id='sample-data-editor-container'>
            <LegacyCard.Section flush>
                <Box padding={"2"}>
                    <HorizontalStack padding="2" align='space-between'>
                        {func.toSentenceCase(type)} 
                        { type==="response" && responseTime ? (` (${responseTime} ms)`) : "" }
                        { type==="request" && (ipObj?.sourceIP.length>0 || ipObj?.destIP.length>0) ? 
                            (` (${ipObj?.sourceIP ? `Src: ${ipObj.sourceIP}` : ""}${ipObj?.sourceIP && ipObj?.destIP ? " & " : ""}${ipObj?.destIP ? `Dest: ${ipObj.destIP}` : ""})`) 
                            : "" }
                        <HorizontalStack gap={2}>
                        {isNewDiff ? <HorizontalStack gap="2">
                                <Box borderInlineEndWidth='1' borderColor="border-subdued" padding="1">
                                    <Text variant="bodyMd" color="subdued">{ lineNumbers[type].length } changes</Text>
                                </Box>
                                <HorizontalStack gap="1">
                                    <Button plain monochrome disabled={!checkButtonActive("prev")} onClick={() => changeIndex("prev")}>
                                        <Box padding="05" borderWidth="1" borderColor="border" borderRadius="1">
                                            <Icon source={ArrowUpMinor} />
                                        </Box>
                                    </Button>
                                    <Button plain monochrome disabled={!checkButtonActive("next")} onClick={() => changeIndex("next")}>
                                        <Box padding="05" borderWidth="1" borderColor="border" borderRadius="1">
                                            <Icon source={ArrowDownMinor} />
                                        </Box>
                                    </Button>
                                </HorizontalStack>
                            </HorizontalStack> 
                            : null}
                            <Tooltip content={`Copy ${type}`}>
                            <Popover
                                zIndexOverride={"600"}
                                active={popoverActive[type]}
                                activator={<Button icon={ClipboardMinor} plain onClick={() => 
                                    setPopoverActive({ [type]: !popoverActive[type] })} />}
                                onClose={() => setPopoverActive(false)}
                            >
                                <div ref={ref}/>
                                <ActionList
                                    actionRole="menuitem"
                                    items={getItems(type, sampleData)}
                                />
                            </Popover>
                            </Tooltip>
                        </HorizontalStack>
                    </HorizontalStack>
                </Box>
            </LegacyCard.Section>
            <LegacyCard.Section flush>
                {sampleJsonData[type] ? <SampleData data={sampleJsonData[type]} minHeight={minHeight || "400px"} useDynamicHeight={props?.useDynamicHeight || false} showDiff={showDiff} editorLanguage="custom_http" currLine={currentLineActive} getLineNumbers={getLineNumbers} readOnly={readOnly} getEditorData={getEditorData}/> : null}
            </LegacyCard.Section>
        </Box>
    )

}

export default SampleDataComponent
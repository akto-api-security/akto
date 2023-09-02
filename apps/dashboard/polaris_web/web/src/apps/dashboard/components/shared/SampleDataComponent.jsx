import React, { useState, useEffect } from 'react'
import {
    ClipboardMinor
} from '@shopify/polaris-icons';
import {
    HorizontalStack, Box, LegacyCard,
    Button, Popover, ActionList
} from '@shopify/polaris';
import SampleData from './SampleData';
import func from "@/util/func";
import inventoryApi from "../../pages/observe/api"
import transform from './customDiffEditor';

function formatData(data,style){
    let localFirstLine = data?.firstLine
    let finalData = ""
    let payLoad = null
    if(style === "http" && data && Object.keys(data).length > 0){
        if(data.json){
            Object.keys(data?.json).forEach((element)=> {
                if(element.includes("query")){
                    if(data.json[element]){
                        Object.keys(data?.json[element]).forEach((param) => {
                            localFirstLine = localFirstLine + '?' + param + '=' + encodeURI(data.json[element][param])
                        })
                    }
                }else if(element.includes("Header")){
                    if(data.json[element]){
                        Object.keys(data?.json[element]).forEach((key) => {
                            finalData = finalData + key + ': ' + data.json[element][key] + "\n"
                        })
                    }
                }else{
                    payLoad = data.json[element]
                }
            })
        }
        finalData = finalData.split("\n").sort().join("\n");
        return (localFirstLine + "\n\n" + finalData + "\n\n" + transform.formatJson(payLoad))
    }
    return (data?.firstLine ? data?.firstLine + "\n\n" : "") + (data?.json ? transform.formatJson(data.json) : "");
  }

function SampleDataComponent(props) {

    const { type, sampleData, minHeight, showDiff, isNewDiff } = props;
    const [sampleJsonData, setSampleJsonData] = useState({ request: { message: "" }, response: { message: "" } });
    const [popoverActive, setPopoverActive] = useState({});

    useEffect(()=>{
        let parsed;
        try{
          parsed = JSON.parse(sampleData?.message)
        } catch {
          parsed = undefined
        }
        let responseJson = func.responseJson(parsed, sampleData?.highlightPaths)
        let requestJson = func.requestJson(parsed, sampleData?.highlightPaths)
        
        let originalParsed;
        try{
          originalParsed = JSON.parse(sampleData?.originalMessage)
        } catch {
          originalParsed = undefined
        }
        let originalResponseJson = func.responseJson(originalParsed, sampleData?.highlightPaths)
        let originalRequestJson = func.requestJson(originalParsed, sampleData?.highlightPaths)

        if(isNewDiff){
            let lineReqObj = transform.getFirstLine(originalRequestJson?.firstLine,requestJson?.firstLine,originalRequestJson?.json?.queryParams,requestJson?.json?.queryParams)
            let lineResObj = transform.getFirstLine(originalResponseJson?.firstLine,responseJson?.firstLine,originalResponseJson?.json?.queryParams,responseJson?.json?.queryParams)

            let requestHeaderObj = transform.compareJsonKeys(originalRequestJson.json.requestHeaders,requestJson.json.requestHeaders)
            let responseHeaderObj = transform.compareJsonKeys(originalResponseJson.json.responseHeaders,responseJson.json.responseHeaders)
            
            let requestPayloadObj = transform.getPayloadData(originalRequestJson.json.requestPayload,requestJson.json.requestPayload)
            let responsePayloadObj = transform.getPayloadData(originalResponseJson.json.responsePayload,responseJson.json.responsePayload)
            
            const requestData = transform.mergeDataObjs(lineReqObj, requestHeaderObj, requestPayloadObj)
            const responseData = transform.mergeDataObjs(lineResObj, responseHeaderObj, responsePayloadObj)

            setSampleJsonData({
                request: requestData,
                response: responseData
            })
        }else{
            setSampleJsonData({ 
                request: { message: formatData(requestJson,"http"), original: formatData(originalRequestJson,"http"), highlightPaths:requestJson?.highlightPaths }, 
                response: { message: formatData(responseJson,"http"), original: formatData(originalResponseJson,"http"), highlightPaths:responseJson?.highlightPaths },
            })
        }
      }, [sampleData])

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
            if (type === "CURL") { 
                snackBarMessage = "Curl request copied to clipboard"
                let resp = await inventoryApi.convertSampleDataToCurl(JSON.stringify(completeData))
                copyString = resp.curlString
            } else {
            snackBarMessage = "Burp request copied to clipboard"
            let resp = await inventoryApi.convertSampleDataToBurpRequest(JSON.stringify(completeData))
            copyString = resp.burpRequest
            }
        }
        return {copyString, snackBarMessage};
    }

    async function copyRequest(reqType, type, completeData) {
        let { copyString, snackBarMessage } = await copyContent(type, completeData)
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

    return (

        <Box>
            <LegacyCard.Section flush>
                <Box padding={"2"}>
                    <HorizontalStack padding="2" align='space-between'>
                        {func.toSentenceCase(type)}
                        <Popover
                            zIndexOverride={"600"}
                            active={popoverActive[type]}
                            activator={<Button icon={ClipboardMinor} plain onClick={() => 
                                setPopoverActive({ [type]: !popoverActive[type] })} />}
                            onClose={() => setPopoverActive(false)}
                        >
                            <ActionList
                                actionRole="menuitem"
                                items={getItems(type, sampleData)}
                            />
                        </Popover>
                    </HorizontalStack>
                </Box>
            </LegacyCard.Section>
            <LegacyCard.Section flush>
                <SampleData data={sampleJsonData[type]} minHeight={minHeight || "400px"} showDiff={showDiff} editorLanguage="custom_http"/>
            </LegacyCard.Section>
        </Box>
    )

}

export default SampleDataComponent
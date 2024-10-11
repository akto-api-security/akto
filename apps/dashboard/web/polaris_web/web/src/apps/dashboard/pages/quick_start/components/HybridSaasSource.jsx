import { Box, Button, ButtonGroup, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState, useRef } from 'react'
import {useNavigate} from "react-router-dom"
import api from '../api'
import JsonComponent from './shared/JsonComponent'
import func from "@/util/func"

function HybridSaasSource() {
    const navigate = useNavigate()
    //const [burpGithubLink, setBurpGithubLink] = useState("");
    const [apiToken, setApiToken] = useState("");
    const ref = useRef(null)
    const dbSvcCommand = "helm install aktodbsvc ~/akto_code/helm-charts/charts/akto-setup-database-abstractor -n dev --set mongo.aktoMongoConn=\"mongodb://<mongo_ip>/admini\"";
    const copyCommand = ()=>{func.copyToClipboard(dbSvcCommand, ref, null)}
    const runtimeSvcCommand = "helm install akto-mini-runtime akto/akto-mini-runtime -n dev --set mini_runtime.aktoApiSecurityRuntime.env.databaseAbstractorToken=\"" + apiToken + "\"";
    const rcopyCommand = ()=>{func.copyToClipboard(runtimeSvcCommand, ref, null)}
    const helmAddCommand = "helm repo add akto https://akto-api-security.github.io/helm-charts/";
    const copyCommandUtil = (data)=>{func.copyToClipboard(data, ref, null)}

    //const [burpCollectionURL, setBurpCollectionURL] = useState("")

    // const getGithubLink = async() => {
    //     await api.fetchBurpPluginDownloadLink().then((resp) => {
    //         if (resp && resp.burpGithubLink) {
    //             setBurpGithubLink(resp?.burpGithubLink)
    //         }
    //     })
    // }

    const fetchRuntimeHelmCommand = async() => {
        await api.fetchRuntimeHelmCommand().then((resp) => {
            if (!resp) return
            setApiToken(resp?.apiToken)
        })
    }

    // const downloadBurpJar = async() => {
    //     let downloadTime = func.timeNow()
    //     let showBurpPluginConnectedFlag = false

    //     await api.downloadBurpPluginJar()
    //     window.open(burpGithubLink)

    //     let interval = setInterval(() => {
    //         api.fetchBurpPluginInfo().then((response) => {
    //             let lastBootupTimestamp = response?.burpPluginInfo?.lastBootupTimestamp
    //             if (lastBootupTimestamp > downloadTime) {
    //                 if (showBurpPluginConnectedFlag) {
    //                     func.setToast(true, false, "Burp plugin connected")
    //                 }
    //                 showBurpPluginConnectedFlag = false
    //                 if (response.burpPluginInfo.lastDataSentTimestamp > downloadTime) {
    //                     clearInterval(interval)
    //                     setBurpCollectionURL("/dashboard/observe/inventory")
    //                     func.setToast(true, false, "Data received from burp plugin")
    //                 }
    //             }
    //         })
    //     }, 2000)

    // }

    const copyText = (text,messageText) => {
        navigator.clipboard.writeText(text)
        func.setToast(true, false, `${messageText} is copied to clipboard.`)
    }

    const hybridSaasComponent = (
        <VerticalStack gap="2">
          <div ref = {ref}/>

          <span>1. Run the below command to add akto helm repo. </span>

          <VerticalStack gap="1">
            <JsonComponent title="Add akto helm repo" toolTipContent="Copy command" onClickFunc={()=> copyCommandUtil(helmAddCommand)} dataString={helmAddCommand} language="text" minHeight="60px" />
          </VerticalStack>

          <span>2. Run the below command to setup Akto Runtime service. Change the namespace according to your requirements. </span>

          <VerticalStack gap="1">
            <JsonComponent title="Runtime Service Command" toolTipContent="Copy command" onClickFunc={()=> rcopyCommand()} dataString={runtimeSvcCommand} language="text" minHeight="450px" />
          </VerticalStack>

          {/* <VerticalStack gap="1">
            <span>2. Replace the following values:</span>
            <VerticalStack gap="1">
              <span>a. {'{' + 'NAMESPACE' + '}'} : With the namespace of your app</span>
              <span>b. {'{' + 'APP_NAME' + '}'} : Replace with the name of the app where daemonset will be deployed. Note that this has to be done at 3 places in the config</span>
            </VerticalStack>
          </VerticalStack>
  
          <VerticalStack gap="1">
            <span>3. Run the following command with appropriate namespace:</span>
            <JsonComponent title="Command" toolTipContent="Copy the command" onClickFunc={()=> copyText()} dataString="kubectl apply -f akto-daemonset-deploy.yaml -n <NAMESPACE>" language="text/plain" minHeight="50px"/>
          </VerticalStack>
  
          <HorizontalStack gap="1">
            <span>4. Add traffic sources from our docs. Click</span>
            <a href='dashboard/observe/inventory'>here</a>
          </HorizontalStack> */}
        </VerticalStack>
      )

    const steps = [
        {
            text: "Run the below command to setup Akto Runtime service \
                helm install akto ~/akto_code/helm-charts/charts/mini-runtime -n dev \
                --set runtime.aktoApiSecurityRuntime.env.databaseAbstractorToken=\"<token>\" \
                --set context_analyser.aktoApiSecurityContextAnalyser.env.databaseAbstractorToken=\"\"" + apiToken
        },
        // {
        //     text: "Copy the AKTO_IP and AKTO_TOKEN and paste in the options tab.",
        //     component: (
        //         <Box paddingInlineStart={2}>
        //             <VerticalStack gap={1}>
        //                 <HorizontalStack gap={1}>
        //                     <Text variant="bodyMd" fontWeight="medium" color="subdued">AKTO_IP:</Text>
        //                     <Button onClick={() => copyText(aktoIp, "AKTO_IP")} plain>
        //                         <div style={{maxWidth: "260px"}} className='overflow-text'>{aktoIp}</div>
        //                     </Button>
        //                 </HorizontalStack>
        //                 <HorizontalStack gap={1}>
        //                     <Text variant="bodyMd" fontWeight="medium" color="subdued">AKTO_TOKEN:</Text>
        //                     <Button onClick={() => copyText(aktoToken, "AKTO_TOKEN")} plain>
        //                         <div style={{maxWidth: "210px"}} className='overflow-text'>{aktoToken}</div>
        //                     </Button>
        //                 </HorizontalStack>
        //             </VerticalStack>
        //         </Box>
        //     )
        // },
    ]

    // const goToDocs = () => {
    //     window.open("https://docs.akto.io/traffic-connections/burp-suite")
    // }

    // const primaryAction = () => {
    //     navigate("/dashboard/settings/integrations/burp")
    // }

    useEffect(()=> {
        //getGithubLink()
        fetchRuntimeHelmCommand()
    },[])

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Seamlessly deploy Akto with our hybrid setup and start viewing your API traffic in few minutes.
            </Text>
            {hybridSaasComponent}

            {/* <VerticalStack gap="1">
                {steps.map((element,index) => (
                    <VerticalStack gap="1" key={index}>
                        <HorizontalStack gap="1" wrap={false} key={element.text}>
                            <Text>{index + 1}.</Text>
                            <Text variant="bodyMd">{element?.text}</Text>
                        </HorizontalStack>
                        {element?.component}
                    </VerticalStack>
                ))}
            </VerticalStack> */}

            {/* <VerticalStack gap="2">
                <ButtonGroup>
                    <Button onClick={primaryAction} primary>Check Connection</Button>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack> */}

        </div>
    )
}

export default HybridSaasSource
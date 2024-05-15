import { Box, Button, ButtonGroup, HorizontalStack, Link, Text, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState, useRef } from 'react'
import {useNavigate} from "react-router-dom"
import api from '../api'
import func from "@/util/func"
import InformationBannerComponent from './shared/InformationBannerComponent'
 
function BurpSource() {
    const navigate = useNavigate()
    const [burpGithubLink, setBurpGithubLink] = useState("");
    const [aktoIp, setAktoIp] = useState("");
    const [aktoToken, setAktoToken] = useState("");
    const [burpCollectionURL, setBurpCollectionURL] = useState("")
    const ref = useRef(null)

    const getGithubLink = async() => {
        await api.fetchBurpPluginDownloadLink().then((resp) => {
            if (resp && resp.burpGithubLink) {
                setBurpGithubLink(resp?.burpGithubLink)
            }
        })
    }

    const getCredentials = async() => {
        await api.fetchBurpCredentials().then((resp) => {
            if (!resp) return
            setAktoIp(resp?.host)
            setAktoToken(resp?.apiToken?.key)
        })
    }

    const downloadBurpJar = async() => {
        let downloadTime = func.timeNow()
        let showBurpPluginConnectedFlag = false

        await api.downloadBurpPluginJar()
        window.open(burpGithubLink)

        let interval = setInterval(() => {
            api.fetchBurpPluginInfo().then((response) => {
                let lastBootupTimestamp = response?.burpPluginInfo?.lastBootupTimestamp
                if (lastBootupTimestamp > downloadTime) {
                    if (showBurpPluginConnectedFlag) {
                        func.setToast(true, false, "Burp plugin connected")
                    }
                    showBurpPluginConnectedFlag = false
                    if (response.burpPluginInfo.lastDataSentTimestamp > downloadTime) {
                        clearInterval(interval)
                        setBurpCollectionURL("/dashboard/observe/inventory")
                        func.setToast(true, false, "Data received from burp plugin")
                    }
                }
            })
        }, 2000)

    }

    const copyText = (text,messageText) => {
        func.copyToClipboard(text, ref, `${messageText} is copied to clipboard.`)
    }

    const steps = [
        {
            textComponent: (
                <div style={{display: "flex", gap: '4px', alignItems: 'center'}}>
                    <Text variant="bodyMd">1. Download akto's burp extension</Text>
                    <Button size="slim" onClick={downloadBurpJar}>Download</Button>
                </div>
            ),
        },
        {
            text: "Open Burp and add the downloaded jar file in extension tab."
        },
        {
            text: 'Once the plugin is loaded click on "options" tab inside the plugin.'
        },
        {
            text: "Copy the AKTO_IP and AKTO_TOKEN and paste in the options tab.",
            component: (
                <Box paddingInlineStart={2}>
                    <VerticalStack gap={1}>
                        <div ref={ref}/>
                        <HorizontalStack gap={1}>
                            <Text variant="bodyMd" fontWeight="medium" color="subdued">AKTO_IP:</Text>
                            <Button onClick={() => copyText(aktoIp, "AKTO_IP")} plain>
                                <div style={{maxWidth: "260px"}} className='overflow-text'>{aktoIp}</div>
                            </Button>
                        </HorizontalStack>
                        <HorizontalStack gap={1}>
                            <Text variant="bodyMd" fontWeight="medium" color="subdued">AKTO_TOKEN:</Text>
                            <Button onClick={() => copyText(aktoToken, "AKTO_TOKEN")} plain>
                                <div style={{maxWidth: "210px"}} className='overflow-text'>{aktoToken}</div>
                            </Button>
                        </HorizontalStack>
                    </VerticalStack>
                </Box>
            )
        },
        {
            text: "Start Burp proxy and browse any website.",
            component: (
                <HorizontalStack gap={1}>
                    <Text variant="bodyMd">You will see traffic in</Text>
                    {burpCollectionURL.length > 0 ? <Button plain onClick={()=> navigate(burpCollectionURL)}>Burp</Button> : <Text>Burp</Text>}
                    <Text>collection.</Text>
                </HorizontalStack>
            )
        }
    ]

    const goToDocs = () => {
        window.open("https://docs.akto.io/traffic-connections/burp-suite")
    }

    const primaryAction = () => {
        navigate("/dashboard/settings/integrations/burp")
    }

    useEffect(()=> {
        getGithubLink()
        getCredentials()
    },[])

    const content = (
        <HorizontalStack gap={1}>
            <Text variant="bodyMd">Akto Burp plugin will work post</Text>
            <Link target="_blank" url='https://portswigger.net/burp/releases/professional-community-2024-1-1-1'>v2024.1.1.1</Link>
        </HorizontalStack>
    )

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use burp plugin to send traffic to Akto and realize quick value. If you like what you see, we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum false positive experience.
            </Text>

            <InformationBannerComponent content={content} docsUrl={""}/>

            <VerticalStack gap="1">
                {steps.map((element,index) => (
                    <VerticalStack gap="1" key={index}>
                        <HorizontalStack gap="1" wrap={false} key={element.text}>
                            {element?.text ?<Text>{index + 1}.</Text> : null}
                            {element?.text ?<Text variant="bodyMd">{element?.text}</Text> : null}
                            {element?.textComponent}
                        </HorizontalStack>
                        <Box paddingInlineStart={2}>
                            {element?.component}
                        </Box>
                    </VerticalStack>
                ))}
            </VerticalStack>

            <VerticalStack gap="2">
                <ButtonGroup>
                    <Button onClick={primaryAction} primary>Check Connection</Button>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>

        </div>
    )
}

export default BurpSource
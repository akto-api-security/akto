import { Button, ButtonGroup, HorizontalStack, Icon, Spinner, Text, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import { StatusActiveMajor } from "@shopify/polaris-icons"
import {useNavigate} from "react-router-dom"
import api from '../api'
import func from '@/util/func'

function BurpSource() {

    const [downloadInfo, setDownloadInfo] = useState(0)
    const [initialData, setInitialData] = useState(0)
    const [finalData, setFinalData] = useState(0)

    const downloadBurpExt = async() => {
        setDownloadInfo(1)
        await api.downloadBurpPluginJar().then((resp)=> {
            let downloadTime = func.timeNow()

            const href = URL.createObjectURL(resp);
            // create "a" HTML element with href to file & click
            const link = document.createElement('a');
            link.href = href;
            link.setAttribute('download', 'Akto.jar'); //or any other extension
            document.body.appendChild(link);
            link.click();
            // clean up "a" element & remove ObjectURL
            document.body.removeChild(link);
            URL.revokeObjectURL(href);

            setDownloadInfo(2)
            setInitialData(1)

            let interval = setInterval(() => {
                api.fetchBurpPluginInfo().then((response) => {
                let lastBootupTimestamp = response.burpPluginInfo.lastBootupTimestamp
                if (lastBootupTimestamp > downloadTime) {
                    setInitialData(2)
                    setFinalData(1)
                    if (response.burpPluginInfo.lastDataSentTimestamp > downloadTime) {
                        clearInterval(interval)
                        setFinalData(2)
                    }
                }
            })
            }, 5000)
        })
    }

    const navigate = useNavigate()
    const DownloadTextComponent = ({param_value}) => {
        switch(param_value){
            case 1:
                return (<Spinner size='small'/>)
            case 2:
                return (<div><Icon source={StatusActiveMajor} color='success'/></div>)
            default: 
                return null
        }
    }

    const steps = [
        {
            text: "Download Akto's Burp extension.",
            component: <div><Button onClick={downloadBurpExt}>Download</Button></div>,
            textComponent: <DownloadTextComponent param_value={downloadInfo}/>
        },
        {
            text: "Open Burp and add the downloaded jar file in extension tab.",
            textComponent: <DownloadTextComponent param_value={initialData} />
        },
        {
            text: "Start Burp proxy and browse any website. You will see traffic in 'Burp' collection in inventory.",
            textComponent: <DownloadTextComponent param_value={finalData} />,
            component: finalData === 2 ? <Button plain onClick={()=> navigate("dashboard/observe/inventory")}>'Burp Collection'</Button>: null
        }
    ]

    const goToDocs = () => {
        window.open("https://docs.akto.io/traffic-connections/burp-suite")
    }

    const primaryAction = () => {
        navigate("/dashboard/settings/integrations/burp")
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use burp plugin to send traffic to Akto and realize quick value. If you like what you see, we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum false positive experience.
            </Text>

            <VerticalStack gap="1">
                {steps.map((element,index) => (
                    <VerticalStack gap="1" key={index}>
                        <HorizontalStack gap="1" wrap={false} key={element.text}>
                            <span>{index + 1}.</span>
                            <span>{element.text}</span>
                            <span>{element.textComponent}</span>
                        </HorizontalStack>
                        <HorizontalStack gap="3">
                            <div/>
                            {element.component}
                        </HorizontalStack>
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
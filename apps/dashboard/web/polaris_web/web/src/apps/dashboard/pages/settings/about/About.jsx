import { Box, Button, ButtonGroup, Divider, LegacyCard, Page, Text, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import Dropdown from '../../../components/layouts/Dropdown'
import settingRequests from '../api'

function About() {

    const trafficAlertDurations= [
        {label : "1 hour", value: 60*60*1},
        {label : "4 hours", value: 60*60*4},
        {label : "12 hours", value: 60*60*12},
        {label : "1 Day", value: 60*60*24},
        {label : "4 Days", value: 60*60*24*4},
    ]

    const [objArr, setObjectArr] = useState([])
    const [setupType,setSetuptype] = useState('')
    const [redactPayload, setRedactPayload] = useState(false)
    const [newMerging, setNewMerging] = useState(false)
    const [trafficThreshold, setTrafficThreshold] = useState(trafficAlertDurations[0].value)
    const setupOptions = settingFunctions.getSetupOptions()
    const [enableTelemetry, setEnableTelemetry] = useState(false)

    async function fetchDetails(){
        const {arr, resp} = await settingFunctions.fetchAdminInfo()
        setSetuptype(resp.setupType)
        setRedactPayload(resp.redactPayload)
        setNewMerging(resp.urlRegexMatchingEnabled)
        setTrafficThreshold(resp.trafficAlertThresholdSeconds)
        setObjectArr(arr)
        setEnableTelemetry(resp.telemetrySettings.customerEnabled)
    }

    useEffect(()=>{
        fetchDetails()
    },[])

    const titleComponent = (
        <Box paddingBlockEnd="4">
            <Text variant="headingMd">Account Information</Text>
            <Box paddingBlockStart="2">
                <Text variant="bodyMd">Take control of your profile, privacy settings, and preferences all in one place.</Text>
            </Box>
        </Box>
    )

    const infoComponent = (
        <VerticalStack gap={5}>
            {objArr.map((item)=>(
                <Box key={item.title} >
                    <VerticalStack gap={1}>
                        <Text fontWeight='semi-bold' color='subdued'>{item.title}</Text>
                        <Text fontWeight='bold'>{item.text}</Text>
                    </VerticalStack>
                </Box>
            ))}
        </VerticalStack>
    )

    const handleSelect = async(selected) => {
        setSetuptype(selected);
        await settingRequests.updateSetupType(selected)
    }

    const handleRedactPayload = async(val) => {
        setRedactPayload(val);
        await settingRequests.toggleRedactFeature(val);
    }

    const handleNewMerging = async(val) => {
        setNewMerging(val);
        await settingRequests.toggleNewMergingEnabled(val);
    }

    const toggleTelemetry = async(val) => {
        setEnableTelemetry(val);
        await settingRequests.toggleTelemetry(val);
    }

    const handleSelectTraffic = async(val) => {
        setTrafficThreshold(val) ;
        await settingRequests.updateTrafficAlertThresholdSeconds(val);
    }

    function ToggleComponent({text,onToggle,initial}){
        return(
            <VerticalStack gap={1}>
                <Text color="subdued">{text}</Text>
                <ButtonGroup segmented>
                    <Button size="slim" onClick={() => onToggle(true)} pressed={initial === true}>
                        True
                    </Button>
                    <Button size="slim" onClick={() => onToggle(false)} pressed={initial === false}>
                        False
                    </Button>
                </ButtonGroup>
            </VerticalStack>
        )
    }

  return (
    <Page
        title="About"
        divider
    >
        <LegacyCard title={titleComponent}>
            <Divider />
            <LegacyCard.Section>
                <VerticalStack gap={5}>
                    {infoComponent}
                    <VerticalStack gap={1}>
                        <Text color="subdued">Setup type</Text>
                        <Box width='13%'>
                            <Dropdown 
                                selected={handleSelect}
                                menuItems={setupOptions}
                                initial={setupType}
                            />
                        </Box>
                    </VerticalStack>
                    <ToggleComponent text={"Redact sample data"} initial={redactPayload} onToggle={handleRedactPayload} />
                    <ToggleComponent text={"Activate regex matching in merging"} initial={newMerging} onToggle={handleNewMerging} />
                    <ToggleComponent text={"Enable telemetry"} initial={enableTelemetry} onToggle={toggleTelemetry} />
                    <VerticalStack gap={1}>
                        <Text color="subdued">Traffic alert threshold</Text>
                        <Box width='15%'>
                            <Dropdown 
                                selected={handleSelectTraffic}
                                menuItems={trafficAlertDurations}
                                initial={trafficThreshold}
                            />
                        </Box>
                    </VerticalStack>
                </VerticalStack>
            </LegacyCard.Section>
            <LegacyCard.Section subdued>
                View our <a href='https://www.akto.io/terms-and-policies' target="_blank">terms of service</a> and <a href='https://www.akto.io/terms/privacy' target="_blank" >privacy policy  </a>
            </LegacyCard.Section>
        </LegacyCard>
    </Page>
  )
}

export default About
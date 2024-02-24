import { Box, Button, ButtonGroup, Divider, Form, LegacyCard, Text, TextField, VerticalStack, Tag, HorizontalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import Dropdown from '../../../components/layouts/Dropdown'
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import settingRequests from '../api'
import { isIP } from "is-ip"
import isCidr from "is-cidr"
import func from "@/util/func"

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
    const [privateCidrList, setPrivateCidrList] = useState([])
    const [partnerIpsList, setPartnerIpsList] = useState([])

    async function fetchDetails(){
        const {arr, resp} = await settingFunctions.fetchAdminInfo()
        setSetuptype(resp.setupType)
        setRedactPayload(resp.redactPayload)
        setNewMerging(resp.urlRegexMatchingEnabled)
        setTrafficThreshold(resp.trafficAlertThresholdSeconds)
        setObjectArr(arr)
        setPrivateCidrList(resp.privateCidrList || [])
        setPartnerIpsList(resp.partnerIpList || [])
    }

    useEffect(()=>{
        fetchDetails()
    },[])

    function TitleComponent ({title,description}) {
        return(
            <Box paddingBlockEnd="4">
                <Text variant="headingMd">{title}</Text>
                <Box paddingBlockStart="2">
                    <Text variant="bodyMd">{description}</Text>
                </Box>
            </Box>
        )
    }

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

    const handleSelectTraffic = async(val) => {
        setTrafficThreshold(val) ;
        await settingRequests.updateTrafficAlertThresholdSeconds(val);
    }

    const handleIpsChange = async(ip, isAdded, type) => {
        if(type === 'cidr'){
            let updatedIps = []
            if(isAdded){
                updatedIps = [...privateCidrList, ip]
                
            }else{
                updatedIps = privateCidrList.filter(item => item !== ip);
            }
            setPrivateCidrList(updatedIps)
            await settingRequests.configPrivateCidr(updatedIps)
        }else{
            let updatedIps = []
            if(isAdded){
                updatedIps = [...partnerIpsList, ip]
                
            }else{
                updatedIps = partnerIpsList.filter(item => item !== ip);
            }
            setPartnerIpsList(updatedIps)
            await settingRequests.configPartnerIps(updatedIps)
        }
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

    const accountInfoComponent = (
        <LegacyCard title={<TitleComponent title={"Account Information"}
            description={"Take control of your profile, privacy settings, and preferences all in one place."} />}
            key={"accountInfo"}
        >
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
    )

    function UpdateIpsComponent({onSubmit, title, labelText, description, ipsList, removeIp, type}){
        const [value, setValue] = useState('')
        const onFormSubmit = (ip) => {
            if(checkError(ip)){
                func.setToast(true, true, "Invalid ip address")
            }else{
                setValue('')
                onSubmit(ip)
            }
        }

        const checkError = () => {
            if(value.length === 0){
                return false
            }
            if(type === "cidr"){
                console.log(value)
                return isCidr(value) === 0
            }else{
                return !(isIP(value))
            }
        }

        const isError = checkError(type)
        console.log(isError)
        return(
            <LegacyCard title={<TitleComponent title={title} description={description}/>}>
                <Divider />
                <LegacyCard.Section>
                    <VerticalStack gap={"2"}>
                        <Form onSubmit={() => onFormSubmit(value)}>
                            <TextField onChange={setValue} value={value} label={labelText} {...isError ? {error: "Invalid address"} : {}}/>
                        </Form>
                        <HorizontalStack gap={"2"}>
                            {ipsList && ipsList.length > 0 && ipsList.map((ip, index) => {
                                return(
                                    <Tag key={index} onRemove={() => removeIp(ip)}>
                                        <Text>{ip}</Text>
                                    </Tag>
                                )
                            })}
                        </HorizontalStack>
                    </VerticalStack>
                </LegacyCard.Section>
            </LegacyCard>
        )
    }

    const components = [accountInfoComponent, 
                        <UpdateIpsComponent 
                            key={"cidr"} 
                            description={"We use these CIDRs to mark the endpoints as PRIVATE"} 
                            title={"Private CIDRs list"}
                            labelText="Add CIDR"
                            ipsList={privateCidrList}
                            onSubmit={(val) => handleIpsChange(val,true,"cidr")}
                            onRemove={(val) => handleIpsChange(val, false, "cidr")}
                            type={"cidr"}
                        />,
                        <UpdateIpsComponent
                            key={"partner"}
                            description={"We use these IPS to mark the endpoints as PARTNER"} 
                            title={"Third parties Ips list"}
                            labelText="Add IP"
                            ipsList={partnerIpsList}
                            onSubmit={(val) => handleIpsChange(val,true,"partner")}
                            onRemove={(val) => handleIpsChange(val, false, "partner")}
                            type={"partner"}
                        />
        ]

    return (
        <PageWithMultipleCards
            divider={true}
            components={components}
            title={
                <Text variant='headingLg' truncate>
                    About
                </Text>
            }
            isFirstPage={true}

        />
    )
}

export default About
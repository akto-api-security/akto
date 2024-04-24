import { Box, Button, ButtonGroup, Divider, LegacyCard, Page, Text, VerticalStack, HorizontalGrid, HorizontalStack, Icon, Scrollable, TextField, Tooltip, Tag, Form } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import Dropdown from '../../../components/layouts/Dropdown'
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import settingRequests from '../api'
import { DeleteMajor } from "@shopify/polaris-icons"
import TooltipText from "../../../components/shared/TooltipText"
import { isIP } from "is-ip"
import isCidr from "is-cidr"
import func from "@/util/func"
import TextFieldWithInfo from '../../../components/shared/TextFieldWithInfo'
import DropdownSearch from '../../../components/shared/DropdownSearch'

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
    const [trafficFiltersMap, setTrafficFiltersMap] = useState({})
    const [headerKey, setHeaderKey] = useState('');
    const [headerValue, setHeaderValue] = useState('') ;
    const [apiCollectionNameMapper, setApiCollectionNameMapper] = useState({});
    const [replaceHeaderKey, setReplaceHeaderKey] = useState('');
    const [replaceHeaderValueRegex, setReplaceHeaderValueRegex] = useState('');
    const [replaceNewCollectionName, setReplaceNewCollectionName] = useState('');
    const [enableTelemetry, setEnableTelemetry] = useState(false)
    const [privateCidrList, setPrivateCidrList] = useState([])
    const [partnerIpsList, setPartnerIpsList] = useState([])

    const initialUrlsList = settingFunctions.getRedundantUrlOptions()
    const [selectedUrlList, setSelectedUrlsList] = useState([])

    const setupOptions = settingFunctions.getSetupOptions()

    const isOnPrem = window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'on_prem'

    async function fetchDetails(){
        const {arr, resp} = await settingFunctions.fetchAdminInfo()
        setSetuptype(resp.setupType)
        setRedactPayload(resp.redactPayload)
        setNewMerging(resp.urlRegexMatchingEnabled)
        setTrafficThreshold(resp.trafficAlertThresholdSeconds)
        setObjectArr(arr)
        setEnableTelemetry(resp.telemetrySettings.customerEnabled)
        if (resp.filterHeaderValueMap)
            setTrafficFiltersMap(resp.filterHeaderValueMap)

        if (resp.apiCollectionNameMapper)    
            setApiCollectionNameMapper(resp.apiCollectionNameMapper)

        setPrivateCidrList(resp.privateCidrList || [])
        setPartnerIpsList(resp.partnerIpList || [])
        setSelectedUrlsList(resp.allowRedundantEndpointsList || [])
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

    const toggleTelemetry = async(val) => {
        setEnableTelemetry(val);
        await settingRequests.toggleTelemetry(val);
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


    const checkSaveActive = (param) => {
        if(param === 'filterHeader'){
            return(headerKey.length > 0 && headerValue.length > 0)
        } else if(param === 'replaceCollection'){
            return(replaceHeaderKey.length > 0 && replaceHeaderValueRegex.length > 0 && replaceNewCollectionName.length > 0)
        }
    }

    const saveFilterHeader = async() => {
        const updatedTrafficFiltersMap = {
            ...trafficFiltersMap,
            [headerKey]: headerValue
        }
        setTrafficFiltersMap(updatedTrafficFiltersMap)
        await settingRequests.addFilterHeaderValueMap(updatedTrafficFiltersMap);
        func.setToast(true, false, "Traffic filter saved successfully.")
        setHeaderKey('')
        setHeaderValue('')
    }

    const deleteFilterHeader = async(key) => {    
        const updatedTrafficFiltersMap = {
            ...trafficFiltersMap,
        }
        delete updatedTrafficFiltersMap[key]
        setTrafficFiltersMap(updatedTrafficFiltersMap)
        await settingRequests.addFilterHeaderValueMap(updatedTrafficFiltersMap);
        func.setToast(true, false, "Traffic filter deleted successfully.")
    }

    const addApiCollectionNameMapper = async() => {
        const hashStr = func.hashCode(replaceHeaderValueRegex)
        const updatedApiCollectionNameMapper = {
            ...apiCollectionNameMapper,
            [hashStr]: {
                headerName: replaceHeaderKey,
                newName: replaceNewCollectionName,
                regex: replaceHeaderValueRegex
            }
        }
        setApiCollectionNameMapper(updatedApiCollectionNameMapper)
        await settingRequests.addApiCollectionNameMapper(replaceHeaderValueRegex, replaceNewCollectionName, replaceHeaderKey);
        func.setToast(true, false, "Replace collection saved successfully.")
        setReplaceHeaderKey('')
        setReplaceHeaderValueRegex('')
        setReplaceNewCollectionName('')
    }

    const deleteApiCollectionNameMapper = async(regex) => {
        const hashStr = func.hashCode(regex)
        const updatedApiCollectionNameMapper = {
            ...apiCollectionNameMapper,
        }
        delete updatedApiCollectionNameMapper[hashStr]
        setApiCollectionNameMapper(updatedApiCollectionNameMapper)
        await settingRequests.deleteApiCollectionNameMapper(regex);
        func.setToast(true, false, "Replace collection deleted successfully.")
    }

    const handleSelectedUrls = async(urlsList) => {
        setSelectedUrlsList(urlsList)
        await settingRequests.handleRedundantUrls(urlsList)
    }

    const redundantUrlComp = (
        <Box width='220px'>
            <DropdownSearch
                label="Select redundant url types"
                placeholder="Select url types"
                optionsList={initialUrlsList.options}
                setSelected={handleSelectedUrls}
                preSelected={selectedUrlList}
                itemName={"url type"}
                value={`${selectedUrlList.length} url type selected`}
                allowMultiple
                isNested={true}
            />
        </Box>
    )
    
    const filterHeaderComponent = (
        <VerticalStack gap={5}>
            <Text color="subdued">Traffic filters</Text>
            <Scrollable horizontal={false} style={{maxHeight: '100px'}} shadow>
                <VerticalStack gap={1}>
                    {Object.keys(trafficFiltersMap).map((key)=> {
                        return(
                            <HorizontalGrid gap={2} columns={3} key={key}>
                                <TooltipText textProps={{variant:"bodyMd", fontWeight:"medium"}} tooltip={key} text={key}/>
                                <TooltipText textProps={{variant:"bodyMd", color: "subdued"}} tooltip={trafficFiltersMap[key]} text={trafficFiltersMap[key]}/>
                                <Button plain icon={DeleteMajor} onClick={() => deleteFilterHeader(key)}/>
                            </HorizontalGrid>
                        )
                    })}
                </VerticalStack>
            </Scrollable>
            <HorizontalGrid gap={2} columns={3} alignItems="center">
                <TextFieldWithInfo 
                    labelText="Header key"
                    labelTextColor="subdued"
                    labelTooltip="Please enter the name of header key"
                    tooltipIconColor="subdued"
                    value={headerKey}
                    setValue={setHeaderKey}
                />
                <TextFieldWithInfo 
                    labelText="Header value"
                    labelTextColor="subdued"
                    labelTooltip="Please enter the name of header value"
                    tooltipIconColor="subdued"
                    value={headerValue}
                    setValue={setHeaderValue}
                />
            {checkSaveActive('filterHeader') ? <Box paddingBlockStart={5} width="100px"><Button onClick={saveFilterHeader} size="medium" primary>Save</Button></Box> : null}
            </HorizontalGrid>
        </VerticalStack>
    )

    const replaceCollectionComponent = (
        <VerticalStack gap={5}>
            <Text color="subdued">Replace collection</Text>
            <Scrollable horizontal={false} style={{maxHeight: '100px'}} shadow>
                <VerticalStack gap={1}>
                    {Object.keys(apiCollectionNameMapper).map((key)=> {
                        const { headerName, newName, regex } = apiCollectionNameMapper[key]
                        const headerLine = headerName + "=" + regex

                        return(
                            <HorizontalGrid gap={2} columns={3} key={key}>
                                <TooltipText textProps={{variant:"bodyMd", fontWeight:"medium"}} tooltip={headerLine} text={headerLine}/>
                                <TooltipText textProps={{variant:"bodyMd", color: "subdued"}} tooltip={newName} text={newName}/>
                                <Button plain icon={DeleteMajor} onClick={() => deleteApiCollectionNameMapper(regex)}/>
                            </HorizontalGrid>
                        )
                    })}
                </VerticalStack>
            </Scrollable>
            <HorizontalGrid gap={2} columns={3} alignItems="center">
                <TextFieldWithInfo 
                    labelText="Header key"
                    labelTextColor="subdued"
                    labelTooltip="Please enter the header name eg host"
                    tooltipIconColor="subdued"
                    value={replaceHeaderKey}
                    setValue={setReplaceHeaderKey}
                 />
                <TextFieldWithInfo 
                    labelText="Header value regex"
                    labelTextColor="subdued"
                    labelTooltip="Please enter the regex to match"
                    tooltipIconColor="subdued"
                    value={replaceHeaderValueRegex}
                    setValue={setReplaceHeaderValueRegex}
                />
                <TextFieldWithInfo 
                    labelText="Replaced collection name"
                    labelTextColor="subdued"
                    labelTooltip="Please enter the name of new collection"
                    tooltipIconColor="subdued"
                    value={replaceNewCollectionName}
                    setValue={setReplaceNewCollectionName}
                />
            {checkSaveActive('replaceCollection') ? <Box paddingBlockStart={5} width="100px"><Button onClick={addApiCollectionNameMapper} size="medium" primary>Save</Button></Box> : null}
            </HorizontalGrid>
        </VerticalStack>
    )

    const accountInfoComponent = (
        <LegacyCard title={<TitleComponent title={"Account Information"}
            description={"Take control of your profile, privacy settings, and preferences all in one place."} />}
            key={"accountInfo"}
        >
            <Divider />
              <LegacyCard.Section title={<Text variant="headingMd">Details</Text>}>
                  {infoComponent}
              </LegacyCard.Section>
              {isOnPrem ?
                  <LegacyCard.Section title={<Text variant="headingMd">More settings</Text>}>
                      <div style={{ display: 'flex' }}>
                          <div style={{ flex: "1" }}>
                              <VerticalStack gap={5}>
                                  <VerticalStack gap={1}>
                                      <Text color="subdued">Setup type</Text>
                                      <Box width='120px'>
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
                                  {redundantUrlComp}
                                  <VerticalStack gap={1}>
                                      <Text color="subdued">Traffic alert threshold</Text>
                                      <Box width='120px'>
                                          <Dropdown
                                              selected={handleSelectTraffic}
                                              menuItems={trafficAlertDurations}
                                              initial={trafficThreshold}
                                          />
                                      </Box>
                                  </VerticalStack>
                              </VerticalStack>
                          </div>
                          <div style={{ flex: '2' }}>
                              {filterHeaderComponent}
                              <br />
                              {replaceCollectionComponent}
                          </div>
                      </div>
                  </LegacyCard.Section>
                  :<LegacyCard.Section title={<Text variant="headingMd">More settings</Text>}>
                    {redundantUrlComp}
                  </LegacyCard.Section>
              }
            <LegacyCard.Section subdued>
                View our <a href='https://www.akto.io/terms-and-policies' target="_blank">terms of service</a> and <a href='https://www.akto.io/terms/privacy' target="_blank" >privacy policy  </a>
            </LegacyCard.Section>
        </LegacyCard>
    )

    function UpdateIpsComponent({onSubmit, title, labelText, description, ipsList, onRemove, type}){
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
                return isCidr(value) === 0
            }else{
                return !(isIP(value))
            }
        }

        const isError = checkError(type)
        return(
            <LegacyCard title={<TitleComponent title={title} description={description}/>}>
                <Divider />
                <LegacyCard.Section>
                    <VerticalStack gap={"2"}>
                        <Form onSubmit={() => onFormSubmit(value)}>
                            <TextField onChange={setValue} value={value} label={<Text color="subdued" fontWeight="medium" variant="bodySm">{labelText}</Text>} {...isError ? {error: "Invalid address"} : {}}/>
                        </Form>
                        <HorizontalStack gap={"2"}>
                            {ipsList && ipsList.length > 0 && ipsList.map((ip, index) => {
                                return(
                                    <Tag key={index} onRemove={() => onRemove(ip)}>
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
                        window.IS_SAAS !== "true" ? <UpdateIpsComponent 
                            key={"cidr"} 
                            description={"We use these CIDRs to mark the endpoints as PRIVATE"} 
                            title={"Private CIDRs list"}
                            labelText="Add CIDR"
                            ipsList={privateCidrList}
                            onSubmit={(val) => handleIpsChange(val,true,"cidr")}
                            onRemove={(val) => handleIpsChange(val, false, "cidr")}
                            type={"cidr"}
                        /> : null,
                        window.IS_SAAS !== "true" ? <UpdateIpsComponent
                            key={"partner"}
                            description={"We use these IPS to mark the endpoints as PARTNER"} 
                            title={"Third parties Ips list"}
                            labelText="Add IP"
                            ipsList={partnerIpsList}
                            onSubmit={(val) => handleIpsChange(val,true,"partner")}
                            onRemove={(val) => handleIpsChange(val, false, "partner")}
                            type={"partner"}
                        /> : null
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
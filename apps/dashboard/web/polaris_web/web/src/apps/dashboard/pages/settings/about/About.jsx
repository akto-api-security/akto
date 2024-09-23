import { Box, Button, ButtonGroup, Divider, LegacyCard, Text, VerticalStack, HorizontalGrid, HorizontalStack, Scrollable, TextField, Tag, Form } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import Dropdown from '../../../components/layouts/Dropdown'
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import settingRequests from '../api'
import { DeleteMajor, FileFilledMinor } from "@shopify/polaris-icons"
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
    const [accountName, setAccountName] = useState('')
    const [currentTimeZone, setCurrentTimeZone] = useState('')

    const initialUrlsList = settingFunctions.getRedundantUrlOptions()
    const [selectedUrlList, setSelectedUrlsList] = useState([])

    const setupOptions = settingFunctions.getSetupOptions()

    const isOnPrem = window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'on_prem'

    async function fetchDetails(){
        const {arr, resp, accountSettingsDetails} = await settingFunctions.fetchAdminInfo()
        if(accountSettingsDetails?.timezone === 'US/Pacific'){
            accountSettingsDetails.timezone = 'America/Los_Angeles';
        }
        setCurrentTimeZone(accountSettingsDetails?.timezone)
        setAccountName(accountSettingsDetails?.name)
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

    const handleSaveSettings = async(type, value) =>{
        await settingRequests.updateAccountSettings(type, value).then((res) => {
            func.setToast(true, false, `${func.toSentenceCase(type)} updated successfully.`)
        })
    }

    const timezonesAvailable = [
        { label: "Pacific Standard Time (PST) UTC-08:00", value: "America/Los_Angeles" },
        { label: "Mountain Standard Time (MST) UTC-07:00", value: "America/Denver" },
        { label: "Central Standard Time (CST) UTC-06:00", value: "America/Chicago" },
        { label: "Eastern Standard Time (EST) UTC-05:00", value: "America/New_York" },
        { label: "Atlantic Standard Time (AST) UTC-04:00", value: "America/Halifax" },
        { label: "Greenwich Mean Time (GMT) UTC+00:00", value: "Etc/GMT" },
        { label: "Central European Time (CET) UTC+01:00", value: "Europe/Berlin" },
        { label: "Eastern European Time (EET) UTC+02:00", value: "Europe/Kiev" },
        { label: "Moscow Standard Time (MSK) UTC+03:00", value: "Europe/Moscow" },
        { label: "India Standard Time (IST) UTC+05:30", value: "Asia/Kolkata" },
        { label: "China Standard Time (CST) UTC+08:00", value: "Asia/Shanghai" },
        { label: "Japan Standard Time (JST) UTC+09:00", value: "Asia/Tokyo" },
        { label: "Australian Eastern Standard Time (AEST) UTC+10:00", value: "Australia/Sydney" },
        { label: "New Zealand Standard Time (NZST) UTC+12:00", value: "Pacific/Auckland" }
      ];

    const infoComponent = (
        <VerticalStack gap={4}>
            <HorizontalGrid columns={"2"} gap={"3"}>
                <TextField 
                    disabled={window.USER_ROLE !== 'ADMIN'} 
                    connectedRight={(
                        <Button disabled={window.USER_ROLE !== 'ADMIN'} icon={FileFilledMinor} onClick={() => handleSaveSettings("name", accountName)} />
                    )} 
                    onChange={setAccountName} 
                    value={accountName}
                    label={"Account name"}
                />
                <DropdownSearch
                    placeholder="Select timezone"
                    label="Select timezone"
                    optionsList={timezonesAvailable}
                    setSelected={(val) => {handleSaveSettings("timezone", val); setCurrentTimeZone(val)}}
                    value={currentTimeZone}
                />
            </HorizontalGrid>
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
        let ipList = ip.split(",")
        ipList = ipList.map((x) => x.replace(/\s+/g, '') )
        if(type === 'cidr'){
            let updatedIps = []
            if(isAdded){
                updatedIps = [...privateCidrList, ...ipList]
                
            }else{
                updatedIps = privateCidrList.filter(item => item !== ip);
            }
            updatedIps = Array.from(new Set(updatedIps))
            setPrivateCidrList(updatedIps)
            await settingRequests.configPrivateCidr(updatedIps)
            func.setToast(true, false, "Updated private CIDR ranges")
        }else{
            let updatedIps = []
            if(isAdded){
                updatedIps = [...partnerIpsList, ...ipList]
                
            }else{
                updatedIps = partnerIpsList.filter(item => item !== ip);
            }
            updatedIps = Array.from(new Set(updatedIps))
            setPartnerIpsList(updatedIps)
            await settingRequests.configPartnerIps(updatedIps)
            func.setToast(true, false, "Updated partner IPs list")
        }
    }

    const applyIps = async() => {
        await settingRequests.applyAccessType()
        func.setToast(true, false, "Access type configuration is being applied. Please wait for some time for the results to be reflected.")
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

    function UpdateIpsComponent({onSubmit, title, labelText, description, ipsList, onRemove, type, onApply}){
        const [value, setValue] = useState('')
        const onFormSubmit = (ip) => {
            if(checkError(ip, type)){
                func.setToast(true, true, "Invalid ip address")
            }else{
                setValue('')
                onSubmit(ip)
            }
        }

        const checkError = (localVal, localType) => {
            localVal = localVal.replace(/\s+/g, '')
            if (localVal.length === 0) return false
            const values = localVal.split(",")
            let valid = true;
            for (let v of values) {
                if(v.length === 0){
                    return true
                }
                if(localType=== "cidr"){
                    valid = valid && (isCidr(v) !== 0)
                }else{
                    valid = valid && (isIP(v))
                }
            }

            return !valid
        }

        const isError = checkError(value, type)
        return(
            <LegacyCard title={<TitleComponent title={title} description={description} />}
                actions={[
                    { content: 'Apply', onAction: onApply }
                ]}
            >
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
                        !func.checkLocal() ? <UpdateIpsComponent 
                            key={"cidr"} 
                            description={"We use these CIDRs to mark the endpoints as PRIVATE"} 
                            title={"Private CIDRs list"}
                            labelText="Add CIDR"
                            ipsList={privateCidrList}
                            onSubmit={(val) => handleIpsChange(val,true,"cidr")}
                            onRemove={(val) => handleIpsChange(val, false, "cidr")}
                            onApply={() => applyIps()}
                            type={"cidr"}
                        /> : null,
                        !func.checkLocal() ? <UpdateIpsComponent
                            key={"partner"}
                            description={"We use these IPs to mark the endpoints as PARTNER"} 
                            title={"Third parties IPs list"}
                            labelText="Add IP"
                            ipsList={partnerIpsList}
                            onSubmit={(val) => handleIpsChange(val,true,"partner")}
                            onRemove={(val) => handleIpsChange(val, false, "partner")}
                            onApply={() => applyIps()}
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
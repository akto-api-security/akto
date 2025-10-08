import { Box, Button, ButtonGroup, Divider, LegacyCard, Text, VerticalStack, HorizontalGrid, HorizontalStack, Scrollable, TextField, Tag, Form, Tooltip, Checkbox, Modal } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import Dropdown from '../../../components/layouts/Dropdown'
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import settingRequests from '../api'
import { DeleteMajor, FileFilledMinor } from "@shopify/polaris-icons"
import TooltipText from "../../../components/shared/TooltipText"
import func from "@/util/func"
import TextFieldWithInfo from '../../../components/shared/TextFieldWithInfo'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import { handleIpsChange } from '../../../components/shared/ipUtils'
import UpdateIpsComponent from '../../../components/shared/UpdateIpsComponent'

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
    const [toggleCaseSensitiveApis, setToggleCaseSensitiveApis] = useState(false)
    const [isSubscribed, setIsSubscribed] = useState(() => {
        return localStorage.getItem('isSubscribed') === 'true'
    })
    const [modalOpen, setModalOpen] = useState(false)
    const [deleteMaliciousEventsModal, setDeleteMaliciousEventsModal] = useState(false)
    const [disableMalEventButton, setDisableMalEventButton] = useState(false)

    const initialUrlsList = settingFunctions.getRedundantUrlOptions()
    const [selectedUrlList, setSelectedUrlsList] = useState([])
    const [miniTesting, setMiniTesting] = useState(false)
    const [mergingOnVersions, setMergingOnVersions] = useState(false)
    const [retrospective, setRetrospective] = useState(false)
    const [compulsoryDescription, setCompulsoryDescription] = useState({
        falsePositive: false,
        noTimeToFix: false,
        acceptableFix: false
    })

    const setupOptions = settingFunctions.getSetupOptions()

    const isOnPrem = window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'on_prem'

    async function fetchDetails(){
        const {arr, resp, accountSettingsDetails} = await settingFunctions.fetchAdminInfo()
        if(accountSettingsDetails?.timezone === 'Us/Pacific'){
            accountSettingsDetails.timezone = 'America/Los_Angeles';
        }
        setCurrentTimeZone(accountSettingsDetails?.timezone)
        setAccountName(accountSettingsDetails?.name)
        setMiniTesting(!accountSettingsDetails?.hybridTestingEnabled)
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
        setToggleCaseSensitiveApis(resp.handleApisCaseInsensitive || false)
        setMergingOnVersions(resp.allowMergingOnVersions || false)
        if(resp?.compulsoryDescription && Object.keys(resp?.compulsoryDescription).length > 0) {
            setCompulsoryDescription(resp.compulsoryDescription)
        }
    }

    useEffect(()=>{
        if(window.USER_ROLE === 'ADMIN') {
            fetchDetails()
        }
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
        { label: "Baker Island Time (BIT) UTC-12:00", value: "Etc/GMT+12" },
        { label: "Niue Time (NUT) UTC-11:00", value: "Pacific/Niue" },
        { label: "Marquesas Islands Time (MART) UTC-09:30", value: "Pacific/Marquesas" },
        { label: "Hawaii-Aleutian Standard Time (HST) UTC-10:00", value: "Pacific/Honolulu" },
        { label: "Alaska Standard Time (AKST) UTC-09:00", value: "America/Anchorage" },
        { label: "Pacific Standard Time (PST) UTC-08:00", value: "America/Los_Angeles" },
        { label: "Mountain Standard Time (MST) UTC-07:00", value: "America/Denver" },
        { label: "Central Standard Time (CST) UTC-06:00", value: "America/Chicago" },
        { label: "Eastern Standard Time (EST) UTC-05:00", value: "America/New_York" },
        { label: "Venezuelan Standard Time (VET) UTC-04:30", value: "America/Caracas" },
        { label: "Atlantic Standard Time (AST) UTC-04:00", value: "America/Halifax" },
        { label: "Newfoundland Standard Time (NST) UTC-03:30", value: "America/St_Johns" },
        { label: "Argentina Time (ART) UTC-03:00", value: "America/Argentina/Buenos_Aires" },
        { label: "South Georgia Time (GST) UTC-02:00", value: "Etc/GMT+2" },
        { label: "Cape Verde Time (CVT) UTC-01:00", value: "Atlantic/Cape_Verde" },
        { label: "Greenwich Mean Time (GMT) UTC+00:00", value: "Etc/GMT" },
        { label: "Central European Time (CET) UTC+01:00", value: "Europe/Berlin" },
        { label: "Eastern European Time (EET) UTC+02:00", value: "Europe/Kiev" },
        { label: "Moscow Standard Time (MSK) UTC+03:00", value: "Europe/Moscow" },
        { label: "Iran Standard Time (IRST) UTC+03:30", value: "Asia/Tehran" },
        { label: "Gulf Standard Time (GST) UTC+04:00", value: "Asia/Dubai" },
        { label: "Afghanistan Time (AFT) UTC+04:30", value: "Asia/Kabul" },
        { label: "Pakistan Standard Time (PKT) UTC+05:00", value: "Asia/Karachi" },
        { label: "Indian Standard Time (IST) UTC+05:30", value: "Asia/Kolkata" },
        { label: "Nepal Time (NPT) UTC+05:45", value: "Asia/Kathmandu" },
        { label: "Bangladesh Standard Time (BST) UTC+06:00", value: "Asia/Dhaka" },
        { label: "Cocos Islands Time (CCT) UTC+06:30", value: "Indian/Cocos" },
        { label: "Indochina Time (ICT) UTC+07:00", value: "Asia/Bangkok" },
        { label: "China Standard Time (CST) UTC+08:00", value: "Asia/Shanghai" },
        { label: "Australian Western Standard Time (AWST) UTC+08:00", value: "Australia/Perth" },
        { label: "Australian Central Standard Time (ACST) UTC+09:30", value: "Australia/Darwin" },
        { label: "Japan Standard Time (JST) UTC+09:00", value: "Asia/Tokyo" },
        { label: "Australian Eastern Standard Time (AEST) UTC+10:00", value: "Australia/Sydney" },
        { label: "Lord Howe Standard Time (LHST) UTC+10:30", value: "Australia/Lord_Howe" },
        { label: "Solomon Islands Time (SBT) UTC+11:00", value: "Pacific/Guadalcanal" },
        { label: "Norfolk Island Time (NFT) UTC+11:30", value: "Pacific/Norfolk" },
        { label: "Fiji Time (FJT) UTC+12:00", value: "Pacific/Fiji" },
        { label: "New Zealand Standard Time (NZST) UTC+12:00", value: "Pacific/Auckland" }
    ];
    

    const infoComponent = (
        <VerticalStack gap={4}>
            <HorizontalGrid columns={"2"} gap={"3"}>
                <TextField 
                    disabled={window.USER_ROLE !== 'ADMIN'} 
                    connectedRight={(
                        <Tooltip content="Save account name" dismissOnMouseOut>
                            <Button disabled={window.USER_ROLE !== 'ADMIN'} icon={FileFilledMinor} onClick={() => handleSaveSettings("name", accountName)} />
                        </Tooltip>
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
                    sliceMaxVal={40}
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
            <Checkbox
                label="Subscribe to updates"
                checked={isSubscribed}
                onChange={() => {
                    const userProps = {}
                    let subsKey = window.DASHBOARD_MODE === 'ON_PREM' ? "mono_subscribed" : "akto_subscribed"
                    userProps[subsKey] = "yes"
                    if  (window.Intercom) {
                        window.Intercom("update", userProps)
                        window.Intercom("trackEvent", subsKey)
                    }
            
                    if (window.mixpanel) {
                        window.mixpanel.people.set(userProps);
                    }
                    setIsSubscribed(!isSubscribed)
                    localStorage.setItem('isSubscribed', (!isSubscribed).toString())
                    if (!isSubscribed) {
                        func.setToast(true, false, "Successfully subscribed to updates")
                    }
                }}
            />
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

    const handleApisCaseInsensitive = async(val) => {
        setToggleCaseSensitiveApis(val) ;
        await settingRequests.updateApisCaseInsensitive(val);
    }

    const toggleMiniTesting = async(val) => {
        setMiniTesting(val) ;
        await settingRequests.switchTestingModule(!val);
    }

    const handleMergingOnVersions = async(val) => {
        setMergingOnVersions(val) ;
        console.log("val", retrospective)
        await settingRequests.enableMergingOnVersions(val, retrospective);
    }

    
    const handleCidrIpsChange = async(ip, isAdded, type) => {
        if(type === 'cidr'){
            const updatedIps = handleIpsChange(ip, isAdded, privateCidrList, setPrivateCidrList);
            await settingRequests.configPrivateCidr(updatedIps)
            func.setToast(true, false, "Updated private CIDR ranges")
        }else{
            const updatedIps = handleIpsChange(ip, isAdded, partnerIpsList, setPartnerIpsList);
            await settingRequests.configPartnerIps(updatedIps)
            func.setToast(true, false, "Updated partner IPs list")
        }
    }

    const applyIps = async() => {
        await settingRequests.applyAccessType()
        func.setToast(true, false, "Access type configuration is being applied. Please wait for some time for the results to be reflected.")
    }

    function ToggleComponent({text,onToggle,initial, disabled}){
        return(
            <VerticalStack gap={1}>
                <Text color="subdued">{text}</Text>
                <ButtonGroup segmented>
                    <Button size="slim" onClick={() => onToggle(true)} pressed={initial === true} disabled={disabled}>
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

    const printPdf = (pdf) => {
        try {
            const byteCharacters = atob(pdf);
            const byteNumbers = new Array(byteCharacters.length);
            for (let i = 0; i < byteCharacters.length; i++) {
                byteNumbers[i] = byteCharacters.charCodeAt(i);
            }
            const byteArray = new Uint8Array(byteNumbers);
            const blob = new Blob([byteArray], { type: "application/pdf" });
            const link = document.createElement("a");
            link.href = window.URL.createObjectURL(blob);
            link.setAttribute("download", "akto_sample_pdf.pdf");
            document.body.appendChild(link);
            link.click();
            func.setToast(true, false, "Report PDF downloaded.")
        } catch (err) {
            console.log(err)
        }
    }

    const handleDeleteAllMaliciousEvents = async() => {
        setDeleteMaliciousEventsModal(false)
        await settingRequests.deleteAllMaliciousEvents().then(() => {
            func.setToast(true, false, "Deleting malicious events - may take a few minutes.")
            setDisableMalEventButton(true)
        }).catch(() => {
            func.setToast(true, true, "Something went wrong. Please try again.")
        })
    }

    const handleCompulsoryToggle = async (key, checked) => {
        const updated = { ...compulsoryDescription, [key]: checked };
        setCompulsoryDescription(updated);
        try {
            await settingRequests.updateCompulsoryDescription(updated);
            func.setToast(true, false, "Compulsory description settings updated successfully.");
            // Re-fetch to ensure UI is fully in sync
            await fetchDetails();
        } catch (error) {
            func.setToast(true, true, "Failed to update compulsory description settings.");
        }
    }

    const compulsoryDescriptionComponent = (
        <VerticalStack gap={4}>
            <Text variant="headingSm">Compulsory Description Settings</Text>
            <Text variant="bodyMd" color="subdued">
                Configure which issue status changes require mandatory descriptions to be provided.
            </Text>
            <VerticalStack gap={3}>
                <Checkbox
                    label="False Positive - Require description when marking issues as false positive"
                    checked={compulsoryDescription.falsePositive}
                    onChange={(checked) => handleCompulsoryToggle('falsePositive', checked)}
                    disabled={window.USER_ROLE !== 'ADMIN'}
                />
                <Checkbox
                    label="No Time To Fix - Require description when marking issues as no time to fix"
                    checked={compulsoryDescription.noTimeToFix}
                    onChange={(checked) => handleCompulsoryToggle('noTimeToFix', checked)}
                    disabled={window.USER_ROLE !== 'ADMIN'}
                />
                <Checkbox
                    label="Acceptable Fix - Require description when marking issues as acceptable fix"
                    checked={compulsoryDescription.acceptableFix}
                    onChange={(checked) => handleCompulsoryToggle('acceptableFix', checked)}
                    disabled={window.USER_ROLE !== 'ADMIN'}
                />
            </VerticalStack>
        </VerticalStack>
    )

    const redundantUrlComp = (
        <VerticalStack gap={"4"}>
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
            <ToggleComponent text={"Treat URLs as case insensitive"} onToggle={handleApisCaseInsensitive} initial={toggleCaseSensitiveApis} disabled={window.USER_ROLE !== "ADMIN"}/>
            <ToggleComponent text={"Use akto's testing module"} onToggle={toggleMiniTesting} initial={miniTesting} disabled={window.USER_ROLE !== "ADMIN"}/>
            <ToggleComponent text={"Allow merging on versions"} onToggle={() => setModalOpen(true)} initial={mergingOnVersions} disabled={window.USER_ROLE !== "ADMIN"}/>
            {(window?.DASHBOARD_MODE === 'ON_PREM' || window?.USER_NAME?.toLowerCase()?.includes("@akto.io")) &&
                <VerticalStack gap={2}>
                    <Text>Sample PDF Download</Text>
                    <Box width='200px'>
                        <Button onClick={async () => {
                            await settingRequests.downloadSamplePdf().then((res) => {
                                if(res?.status?.toLowerCase() === 'completed') {
                                    printPdf(res?.pdf)
                                }
                            })
                        }}>Download</Button>
                    </Box>
                </VerticalStack>
            }
            <VerticalStack gap={2}>
                <Text color='subdued' variant='bodyMd'>Delete all malicious events</Text>
                <Box width='80px'>
                    <Button disabled={disableMalEventButton} onClick={() => setDeleteMaliciousEventsModal(true)}>Delete</Button>
                </Box>
            </VerticalStack>

            <Modal
                open={deleteMaliciousEventsModal}
                primaryAction={{
                    content: "Yes, Delete All",
                    onAction: handleDeleteAllMaliciousEvents
                }}
                secondaryActions={[{
                    content: "Cancel",
                    onAction: () => {setDeleteMaliciousEventsModal(false)}
                }]}
                title="⚠️ Confirm Permanent Deletion"
                onClose={() => {setDeleteMaliciousEventsModal(false)}}
            >
                <Modal.Section>
                    <VerticalStack>
                        <Text>You are about to permanently delete all malicious events.</Text>
                        <Text>This action cannot be undone and all associated data will be lost forever.</Text>
                        <br/>
                        <Text>Are you sure you want to proceed?</Text>
                    </VerticalStack>
                </Modal.Section>
            </Modal>
        </VerticalStack>
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
                                  {compulsoryDescriptionComponent}
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
                    {compulsoryDescriptionComponent}
                  </LegacyCard.Section>
              }
            <LegacyCard.Section subdued>
                View our <a href='https://www.akto.io/terms-and-policies' target="_blank">terms of service</a> and <a href='https://www.akto.io/terms/privacy' target="_blank" >privacy policy  </a>
            </LegacyCard.Section>
        </LegacyCard>
    )


    const components = [accountInfoComponent, 
                        !func.checkLocal() ? <UpdateIpsComponent 
                            key={"cidr"} 
                            description={"We use these CIDRs to mark the endpoints as PRIVATE"} 
                            title={"Private CIDRs list"}
                            labelText="Add CIDR"
                            ipsList={privateCidrList}
                            onSubmit={(val) => handleCidrIpsChange(val,true,"cidr")}
                            onRemove={(val) => handleCidrIpsChange(val, false, "cidr")}
                            onApply={() => applyIps()}
                            type={"cidr"}
                        /> : null,
                        !func.checkLocal() ? <UpdateIpsComponent
                            key={"partner"}
                            description={"We use these IPs to mark the endpoints as PARTNER"} 
                            title={"Third parties IPs list"}
                            labelText="Add IP"
                            ipsList={partnerIpsList}
                            onSubmit={(val) => handleCidrIpsChange(val,true,"partner")}
                            onRemove={(val) => handleCidrIpsChange(val, false, "partner")}
                            onApply={() => applyIps()}
                            type={"partner"}
                        /> : null,
                        <Modal
                            open={modalOpen}
                            onClose={() => setModalOpen(false)}
                            title={mergingOnVersions ? "Do not merge on versions" : "Allow merging on versions"}
                            primaryAction={{
                                content: 'Save',
                                onAction: () => {
                                    handleMergingOnVersions(!mergingOnVersions)
                                    setModalOpen(false)
                                },
                            }}
                            secondaryActions={[
                                {
                                    content: 'Cancel',
                                    onAction: () => setModalOpen(false),
                                },
                            ]}
                        >
                            <Modal.Section>
                                <Text variant="bodyMd" color="subdued">Allow merging on versions will allow you to merge the endpoints with different versions. This will help you to reduce the number of endpoints in your application. Note this job runs in the background and result might get reflected with slight delay.</Text>
                                {!mergingOnVersions ? <Checkbox
                                    label="Allow retrospective merging on versions"
                                    checked={retrospective}
                                    onChange={() => setTimeout(()=> setRetrospective(!retrospective),[])}
                                    disabled={window.USER_ROLE !== "ADMIN"}
                                /> : null}
                            </Modal.Section>
                        </Modal>
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
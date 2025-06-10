import { Button, ButtonGroup, Card, Checkbox, ContextualSaveBar, Divider, Frame, HorizontalGrid, LegacyCard, LegacyTabs, Tabs, Text, TextField, VerticalStack} from "@shopify/polaris"
import PageWithMultipleCards from "../../../../components/layouts/PageWithMultipleCards"
import { useNavigate, useParams } from "react-router-dom"
import { useEffect, useState } from "react"
import ApiCollectionsDropdown from "../../../../components/shared/ApiCollectionsDropdown"
import settingRequests from "../../api"
import Store from "../../../../store"
import WebhooksStore from "../webhooks/webhooksStore"
import SpinnerCentered from "../../../../components/progress/SpinnerCentered"
import SampleDataList from "../../../../components/shared/SampleDataList"

import SampleData from '../../../../components/shared/SampleData'
import func from "@/util/func"
import TitleWithInfo from "../../../../components/shared/TitleWithInfo"

const testingOptionsObj = {
    "type":"TESTING",
    "title": "Testing run results",
    "value": "TESTING_RUN_RESULTS",
    "collectionSelection": false
}

const infoTab = {
    id: 'info',
    content: 'Info',
    accessibilityLabel: 'Info',
}

const resultTab = {
    id: 'result',
    content: 'Result',
}

function WebhookCore(props) {

    const {webhookType, defaultPayload} = props

    const initialState = {
        name: "",
        url: "",
        method: "POST",
        queryParams: "",
        headers: "{'content-type': 'application/json'}",
        body: "{}",
        selectedWebhookOptions: [],
        newEndpointCollections: [],
        newSensitiveEndpointCollections: [],
        frequencyInSeconds: 900,
        batchSize: 20,
        sendInstantly: false,
        result: []
    }
    const navigate = useNavigate()
    const { webhookId } = useParams('webhookId')

    const setToastConfig = Store(state => state.setToastConfig)
    const customWebhooks = WebhooksStore(state => state.customWebhooks)
    const [webhook, setWebhook] = useState(initialState)
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState(false)
    const [hasChanges, setHasChanges] = useState(false)
    const [selectedWebhookTab, setSelectedWebhookTab] = useState(0);
    const [showOptions, setShowOptions] = useState(true)
    
    const [data, setData] = useState({message:defaultPayload})

    async function loadWebhookById() {
        setIsLoading(true)
        if (webhookId) {
            let customWebhookFindId = customWebhooks.find(customWebhook => customWebhook.id.toString() === webhookId)

            if (!customWebhookFindId) {
                const customWebhooksResponse = await settingRequests.fetchCustomWebhooks(webhookId)
                if (customWebhooksResponse) {
                    const CustomWebhooks = customWebhooksResponse.customWebhooks

                    // remove null values from a given custom webhook object
                    const filterCustomWebhooks = CustomWebhooks.map(obj => 
                        Object.fromEntries(Object.entries(obj).filter(([k, v]) => v !== null))
                    )

                    // prettify custom webhook data
                    const mapCustomWebhooks = filterCustomWebhooks.map(customWebhook => ({
                        ...customWebhook,
                        createTime: func.prettifyEpoch(customWebhook.createTime),
                        lastSentTimestamp: func.prettifyEpoch(customWebhook.createTime),
                        nextUrl: `${customWebhook.id}`
                    }))

                    customWebhookFindId = mapCustomWebhooks.find(customWebhook => customWebhook.id.toString() === webhookId)
                }
            }

            setWebhook({
                name: customWebhookFindId?.webhookName,
                url: customWebhookFindId?.url,
                method: customWebhookFindId?.method,
                queryParams: customWebhookFindId?.queryParams,
                headers: customWebhookFindId?.headerString,
                body: customWebhookFindId?.body,
                selectedWebhookOptions: customWebhookFindId?.selectedWebhookOptions,
                newEndpointCollections: customWebhookFindId?.newEndpointCollections,
                newSensitiveEndpointCollections: customWebhookFindId?.newSensitiveEndpointCollections,
                frequencyInSeconds: customWebhookFindId?.frequencyInSeconds,
                batchSize: customWebhookFindId?.batchSize,
                sendInstantly: customWebhookFindId?.sendInstantly
            })

            let webhookBody = customWebhookFindId?.body;
            setShowOptions(!(webhookBody && webhookBody.indexOf("$") > 0))
            let payload = (webhookBody && webhookBody.indexOf("$") > 0) ? webhookBody : defaultPayload
            setData({
                message: payload
            })

            const customWebhookResult = await settingRequests.fetchLatestWebhookResult(parseInt(webhookId))
            let result = []
            if (customWebhookResult.customWebhookResult) {
                result = [{ 
                    message: customWebhookResult.customWebhookResult.message,
                    highlightPaths: [] 
                }]
            }
            setWebhook(prev => ({ ...prev, result}))
        }
        setIsLoading(false)
    }

    useEffect(() => {
        loadWebhookById()
    }, [])

    let customWebhookOptions = [
        { "type":"TRAFFIC", "title": "Traffic alerts", "value": "TRAFFIC_ALERTS", "collectionSelection": false },
        { "type":"TRAFFIC", "title": "New endpoint", "value": "NEW_ENDPOINT", "collectionSelection": true, "collectionStateField": "newEndpointCollections" },
        { "type":"TRAFFIC", "title": "New endpoint count", "value": "NEW_ENDPOINT_COUNT", "collectionSelection": false },
        { "type":"TRAFFIC", "title": "New sensitive endpoint", "value": "NEW_SENSITIVE_ENDPOINT", "collectionSelection": true, "collectionStateField": "newSensitiveEndpointCollections" },
        { "type":"TRAFFIC", "title": "New sensitive endpoint count", "value": "NEW_SENSITIVE_ENDPOINT_COUNT", "collectionSelection": false },
        { "type":"TRAFFIC", "title": "New sensitive parameter count", "value": "NEW_SENSITIVE_PARAMETER_COUNT", "collectionSelection": false },
        { "type":"TRAFFIC", "title": "New parameter count", "value": "NEW_PARAMETER_COUNT", "collectionSelection": false },
        { "type":"TRAFFIC", "title": "New API runtime threats", "value": "API_THREAT_PAYLOADS", "collectionSelection": false },
        { "type":"TRAFFIC", "title": "Pending tests alerts", "value": "PENDING_TESTS_ALERTS", "collectionSelection": false }
    ]

    if (webhookType === "MICROSOFT_TEAMS") {
        customWebhookOptions.push(
            testingOptionsObj
        )
    }

    if( webhookType === "GMAIL") {
        customWebhookOptions = [testingOptionsObj];
    }

    const intervals = [
        { "name": "15 mins", "value": 900 },
        { "name": "30 mins", "value": 1800 },
        { "name": "1 hour", "value": 3600 },
        { "name": "6 hours", "value": 21600 },
        { "name": "12 hours", "value": 43200 },
        { "name": "24 hours", "value": 86400 }
    ]

    const batchSizes = [
        { "name": "1 payload", "value": 1 },
        { "name": "10 payloads", "value": 10 },
        { "name": "20 payloads", "value": 20 },
        { "name": "50 payloads", "value": 50 },
        { "name": "100 payloads", "value": 100 },
        { "name": "200 payloads", "value": 200 },
    ]

    const booleanOption = [
        { "name": "Instant", "value": true },
        { "name": "Periodic", "value": false },
    ]

    const tabs = webhookType === "GMAIL" ? [infoTab] : [
        infoTab,
        resultTab
    ]

    function handleWebhookTabChange(selectedTabIndex) {
        setSelectedWebhookTab(selectedTabIndex)
    }

    function updateWebhookState(field, value) {
        switch (field) {
            case "selectedWebhookOptions":
                setWebhook(prev => {
                    if (!prev.selectedWebhookOptions)  prev.selectedWebhookOptions = []
                    if (prev.selectedWebhookOptions.includes(value))
                        return { ...prev, selectedWebhookOptions: prev.selectedWebhookOptions.filter(selectedWebhookOption => selectedWebhookOption !== value) }
                    else
                        return { ...prev, selectedWebhookOptions: [...prev.selectedWebhookOptions, value] }
                })
                break
            default:
                setWebhook(prev => ({
                    ...prev,
                    [field]: value
                }))
        }
        setHasChanges(true)
    }

    function handleDiscard() {
        if (webhook) 
            loadWebhookById()
        else 
            setWebhook(initialState)
        setHasChanges(false)
    }

    async function saveWebhook() {
        const webhookName = webhook.name
        const url = webhook.url
        const method = webhook.method
        const queryParams = webhook.queryParams
        const headers = webhook.headers
        const body = showOptions ? "{}" : webhookJson
        const selectedWebhookOptions = showOptions ? webhook.selectedWebhookOptions : null
        const newEndpointCollections = showOptions ? webhook.newEndpointCollections : null
        const newSensitiveEndpointCollections = showOptions ? webhook.newSensitiveEndpointCollections : null
        const frequencyInSeconds = webhook.frequencyInSeconds
        const batchSize = webhook.batchSize
        const sendInstantly = webhook.sendInstantly
        if (webhookName === "") {
            setToastConfig({ isActive: true, isError: true, message: "Webhook name required" })
            return
        }
        else if (url === "") {
            let message = "URL required"
            if (webhookType === "GMAIL") {
                message = "Email ID required"
            }
            setToastConfig({ isActive: true, isError: true, message: message })
            return
        }

        if (webhookId) {
            await settingRequests.updateCustomWebhook(parseInt(webhookId), webhookName, url, queryParams, method, headers, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections, batchSize, sendInstantly)
            setToastConfig({ isActive: true, isError: false, message: "Webhook updated successfully!" })
        } else {
            await settingRequests.addCustomWebhook(webhookName, url, queryParams, method, headers, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections, batchSize, webhookType, sendInstantly)
            setToastConfig({ isActive: true, isError: false, message: "Webhook created successfully!" })
            navigate(-1)
        }

    }

    async function runOnce() {
        await settingRequests.runOnce(parseInt(webhookId))
        setToastConfig({ isActive: true, isError: false, message: "Webhook ran successfully!" })
    }

    function getSelectedCollections(collectionStateField) {
        if (webhook) return webhook[collectionStateField]
        else return []
    }

    const WebhookTabs = (
        <Tabs tabs={tabs} selected={selectedWebhookTab} onSelect={handleWebhookTabChange} key="webhookTabs">
        </Tabs>
    )

    const InfoCard = (
        <LegacyCard title="Details" key="details">
            <LegacyCard.Section>
                <VerticalStack gap={"2"}>
                    <TextField label="Webhook Name" value={webhook.name} placeholder='Name' requiredIndicator onChange={(name) => updateWebhookState("name", name)} />
                    {webhookType !== "GMAIL" ?
                        <VerticalStack gap={"2"}>
                            <TextField label="URL" value={webhook.url} placeholder='URL' requiredIndicator onChange={(url) => updateWebhookState("url", url)} />
                            <TextField label="Query Params" value={webhook.queryParams} placeholder='Query Params' onChange={(queryParams) => updateWebhookState("queryParams", queryParams)} />
                            <TextField label="Headers" value={webhook.headers} placeholder='Headers' onChange={(headers) => updateWebhookState("headers", headers)} />    
                        </VerticalStack>
                    : <VerticalStack gap={"2"}>
                            <TextField label="Email ID" value={webhook.url} placeholder={window.USER_NAME} requiredIndicator={true} onChange={(val) => updateWebhookState("url", val)} />
                            <TextField label="User name" value={webhook.queryParams} placeholder={window.USER_FULL_NAME} onChange={(val) => updateWebhookState("queryParams", val)} />
                        </VerticalStack>
                    }
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const [webhookJson, setWebhookJson] = useState({})

    const CustomWebhookEditor = (
            <SampleData data={data} language="json" minHeight="240px" readOnly={false} getEditorData={setWebhookJson}/>
    )

    const OptionsCard = (type) => {
        const options = customWebhookOptions.filter(x => x.type === type)
        return (<div>
            {options.map(customWebhookOption => {
                return (
                    <div key={customWebhookOption.title} style={{ paddingBottom: "10px" }}>
                        <Checkbox
                            label={
                                <div >
                                    {customWebhookOption.title}
                                    <div style={{ paddingTop: "10px" }}>
                                        {customWebhookOption.collectionSelection && webhook.selectedWebhookOptions && webhook.selectedWebhookOptions.includes(customWebhookOption.value) ?
                                            <ApiCollectionsDropdown
                                                selectedCollections={getSelectedCollections(customWebhookOption.collectionStateField)}
                                                setSelectedCollections={(selectedCollections) =>
                                                    updateWebhookState(
                                                        customWebhookOption.collectionStateField,
                                                        selectedCollections)}
                                            />
                                            : ''}
                                    </div>
                                </div>

                            }
                            checked={webhook.selectedWebhookOptions && webhook.selectedWebhookOptions.includes(customWebhookOption.value)}
                            onChange={() => { updateWebhookState("selectedWebhookOptions", customWebhookOption.value) }}
                        />
                    </div>
                )
            })}
        </div>
    )}

    let CardComponent
    let CardTitle
    let actionContent
    if (showOptions) {
        CardComponent = OptionsCard("TRAFFIC")
        CardTitle = "Traffic options"
        actionContent = "Custom"
    } else {
        CardComponent = CustomWebhookEditor
        CardTitle = "Custom"
        actionContent = "Default"
    }

    const toggleShowOptions = () => {
        setShowOptions(!showOptions);
    }

    const TestingOptionsCard = (
        webhookType === "MICROSOFT_TEAMS" || webhookType === "GMAIL" ? (<LegacyCard title={"Testing options"} key="testingOptions">
            <LegacyCard.Section>
                {OptionsCard("TESTING")}
            </LegacyCard.Section>
        </LegacyCard>) : <></>
    )

    const OverallCard = (
        <LegacyCard title={CardTitle} key="options" actions={[{content: actionContent, onAction: toggleShowOptions}]}>
            <LegacyCard.Section>
                {CardComponent}
                {(webhook.selectedWebhookOptions && !webhook.selectedWebhookOptions.includes("TRAFFIC_ALERTS")
                    && !webhook.selectedWebhookOptions.includes("PENDING_TESTS_ALERTS")) ?
                (<>
                <Divider />
                <div style={{ paddingTop: "10px" }}>
                    <Text variant="headingMd">Run every</Text>
                    <br />
                    {intervals.map(interval => (
                        <span key={interval.name} style={{ padding: "10px" }}>
                            <Button
                                pressed={webhook.frequencyInSeconds === interval.value}
                                onClick={() => updateWebhookState("frequencyInSeconds", interval.value)}
                            >
                                {interval.name
                                }</Button>
                        </span>
                    ))}
                </div>
                </>) : null
                }
                <br />
                {(webhook.selectedWebhookOptions && webhook.selectedWebhookOptions.includes("API_THREAT_PAYLOADS")) ? (
                    <>
                        <Divider />
                        <div style={{ paddingTop: "10px" }}>
                            <Text variant="headingMd">Batch size for threat payloads</Text>
                            <br />
                            {batchSizes.map(batchSize => (
                                <span key={batchSize.name} style={{ padding: "10px" }}>
                                    <Button
                                        pressed={webhook.batchSize === batchSize.value}
                                        onClick={() => updateWebhookState("batchSize", batchSize.value)}
                                    >
                                        {batchSize.name
                                        }</Button>
                                </span>
                            ))}
                        </div>
                    </>) : null
                }
                <br />
                {(webhook.selectedWebhookOptions && 
                webhook.selectedWebhookOptions.length == 1 &&
                webhook.selectedWebhookOptions.includes("API_THREAT_PAYLOADS")) ? (
                    <>
                        <Divider />
                        <div style={{ paddingTop: "10px" }}>
                        <TitleWithInfo titleText={"Alert frequency"} tooltipContent={"Instant alerts are only supported for threat payloads"} textProps={{variant: 'headingMd'}} />
                            <br />
                            {booleanOption.map(option => (
                                <span key={option.name} style={{ padding: "10px" }}>
                                    <Button
                                        pressed={webhook.sendInstantly === option.value}
                                        onClick={() => updateWebhookState("sendInstantly", option.value)}
                                    >
                                        {option.name
                                        }</Button>
                                </span>
                            ))}
                        </div>
                    </>) : null
                }
            </LegacyCard.Section>
        </LegacyCard>
    )

    const components = error ?
        [
            <Card key="error">
                Webhook Does not Exist
            </Card>
        ]
        : [
            WebhookTabs,
            selectedWebhookTab === 0 ?
                <div key="infoTab">
                    {InfoCard}
                    {(webhook.selectedWebhookOptions &&
                        webhook.selectedWebhookOptions.length == 1 &&
                        webhook.selectedWebhookOptions.includes("TESTING_RUN_RESULTS"))
                        || (webhook.selectedWebhookOptions &&
                            webhook.selectedWebhookOptions.length == 0) ?
                        TestingOptionsCard : null}
                    {(webhook.selectedWebhookOptions &&
                        !webhook.selectedWebhookOptions.includes("TESTING_RUN_RESULTS")) && webhookType !== "GMAIL" ?
                        OverallCard : null}
                </div>
                :   webhook.result.length !== 0 ? 
                    <SampleDataList
                        key="Webhook Result"
                        sampleData={webhook.result}
                        heading={"Webhook Result"}
                    />
                    : <Card key="No result"><Text>No result to display</Text></Card>
        ]

    const pageMarkup = (
        isLoading ? <SpinnerCentered /> :
            <PageWithMultipleCards
                title={webhookId ?
                    webhook? webhook.webhookName : ''
                    : "Create custom webhook"}
                divider
                components={components}
            />
    )

    const logo = {
        width: 124,
        contextualSaveBarSource: '/public/logo.svg',
        url: '#',
        accessibilityLabel: 'Akto Icon',
    }

    const contextualMarkup = (
        <ContextualSaveBar
            message={hasChanges ? "Unsaved changes" : "No unsaved changes"}
            secondaryMenu={
                <ButtonGroup>
                    <Button onClick={handleDiscard} disabled={!hasChanges}>Discard</Button>
                    {webhookId ?
                        <Button
                            primary
                            onClick={saveWebhook}
                            connectedDisclosure={{
                                accessibilityLabel: 'Other save actions',
                                actions: [{ content: 'Run once', onAction: () => runOnce() }],
                            }}
                        >
                            Save
                        </Button>
                        : <Button
                            primary
                            onClick={saveWebhook}
                        >
                            Save
                        </Button>
                    }
                </ButtonGroup>
            }
        />
    )

    return (
        <div className='control-frame-padding'>
            <Frame logo={logo}>
                {contextualMarkup}
                {pageMarkup}
            </Frame>
        </div>
    )
}

export default WebhookCore
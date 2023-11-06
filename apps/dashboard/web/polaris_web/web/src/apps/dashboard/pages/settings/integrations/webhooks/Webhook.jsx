import { Button, ButtonGroup, Card, Checkbox, ContextualSaveBar, Divider, Frame, HorizontalGrid, LegacyCard, LegacyTabs, Tabs, Text, TextField} from "@shopify/polaris"
import PageWithMultipleCards from "../../../../components/layouts/PageWithMultipleCards"
import { useNavigate, useParams } from "react-router-dom"
import { useEffect, useState } from "react"
import ApiCollectionsDropdown from "../../../../components/shared/ApiCollectionsDropdown"
import settingRequests from "../../api"
import Store from "../../../../store"
import WebhooksStore from "./webhooksStore"
import SpinnerCentered from "../../../../components/progress/SpinnerCentered"
import SampleDataList from "../../../../components/shared/SampleDataList"

import SampleData from '../../../../components/shared/SampleData'
import func from "@/util/func"

function Webhook() {
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
                name: customWebhookFindId.webhookName,
                url: customWebhookFindId.url,
                method: customWebhookFindId.method,
                queryParams: customWebhookFindId.queryParams,
                headers: customWebhookFindId.headerString,
                body: customWebhookFindId.body,
                selectedWebhookOptions: customWebhookFindId.selectedWebhookOptions,
                newEndpointCollections: customWebhookFindId.newEndpointCollections,
                newSensitiveEndpointCollections: customWebhookFindId.newSensitiveEndpointCollections,
                frequencyInSeconds: customWebhookFindId.frequencyInSeconds
            })

            let webhookBody = customWebhookFindId.body;
            setShowOptions(!(webhookBody && webhookBody.indexOf("$") > 0))

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

    const customWebhookOptions = [
        { "title": "New endpoint", "value": "NEW_ENDPOINT", "collectionSelection": true, "collectionStateField": "newEndpointCollections" },
        { "title": "New endpoint count", "value": "NEW_ENDPOINT_COUNT", "collectionSelection": false },
        { "title": "New sensitive endpoint", "value": "NEW_SENSITIVE_ENDPOINT", "collectionSelection": true, "collectionStateField": "newSensitiveEndpointCollections" },
        { "title": "New sensitive endpoint count", "value": "NEW_SENSITIVE_ENDPOINT_COUNT", "collectionSelection": false },
        { "title": "New sensitive parameter count", "value": "NEW_SENSITIVE_PARAMETER_COUNT", "collectionSelection": false },
        { "title": "New parameter count", "value": "NEW_PARAMETER_COUNT", "collectionSelection": false }
    ]

    const intervals = [
        { "name": "15 mins", "value": 900 },
        { "name": "30 mins", "value": 1800 },
        { "name": "1 hour", "value": 3600 },
        { "name": "6 hours", "value": 21600 },
        { "name": "12 hours", "value": 43200 },
        { "name": "24 hours", "value": 86400 }
    ]

    const tabs = [
        {
            id: 'info',
            content: 'Info',
            accessibilityLabel: 'Info',
        },
        {
            id: 'result',
            content: 'Result',
        },
    ];

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

        if (webhookName === "") {
            setToastConfig({ isActive: true, isError: true, message: "Webhook name required" })
            return
        }
        else if (url === "") {
            setToastConfig({ isActive: true, isError: true, message: "URL required" })
            return
        }

        if (webhookId) {
            await settingRequests.updateCustomWebhook(parseInt(webhookId), webhookName, url, queryParams, method, headers, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections)
            setToastConfig({ isActive: true, isError: false, message: "Webhook updated successfully!" })
        } else {
            await settingRequests.addCustomWebhook(webhookName, url, queryParams, method, headers, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections)
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
                <TextField label="Name" value={webhook.name} placeholder='Name' requiredIndicator onChange={(name) => updateWebhookState("name", name)} />
                <br />
                <TextField label="URL" value={webhook.url} placeholder='URL' requiredIndicator onChange={(url) => updateWebhookState("url", url)} />
                <br />
                <TextField label="Query Params" value={webhook.queryParams} placeholder='Query Params' onChange={(queryParams) => updateWebhookState("queryParams", queryParams)} />
                <br />
                <TextField label="Headers" value={webhook.headers} placeholder='Headers' onChange={(headers) => updateWebhookState("headers", headers)} />
            </LegacyCard.Section>
        </LegacyCard>
    )

    const data = {}
    data["message"] = webhook.body && webhook.body.indexOf("$") > 0 ? webhook.body : "{\r\n  \"New Endpoint\": ${AKTO.changes_info.newEndpoints},\r\n  \"New Endpoint Count\": ${AKTO.changes_info.newEndpointsCount},\r\n  \"New Sensitive Endpoint\": ${AKTO.changes_info.newSensitiveEndpoints},\r\n  \"New Sensitive Endpoint Count\": ${AKTO.changes_info.newSensitiveEndpointsCount},\r\n  \"New Sensitive Parameter Count\": ${AKTO.changes_info.newSensitiveParametersCount},\r\n  \"New Parameter Count\": ${AKTO.changes_info.newParametersCount}\r\n}"

    const [webhookJson, setWebhookJson] = useState({})

    const CustomWebhookEditor = (
            <SampleData data={data} language="json" minHeight="240px" readOnly={false} getEditorData={setWebhookJson}/>
    )

    const OptionsCard = (
        <div>
            {customWebhookOptions.map(customWebhookOption => {
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

    )


    let CardComponent
    let CardTitle
    let actionContent
    if (showOptions) {
        CardComponent = OptionsCard
        CardTitle = "Options"
        actionContent = "Custom"
    } else {
        CardComponent = CustomWebhookEditor
        CardTitle = "Custom"
        actionContent = "Default"
    }

    const toggleShowOptions = () => {
        setShowOptions(!showOptions);
    }

    const OverallCard = (
        <LegacyCard title={CardTitle} key="options" actions={[{content: actionContent, onAction: toggleShowOptions}]}>
            <LegacyCard.Section>
                {CardComponent}
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
                    {OverallCard}
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
                backUrl="/dashboard/settings/integrations/webhooks"
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

export default Webhook
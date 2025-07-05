import React, { useEffect, useState } from 'react'
import { EmptyState, LegacyCard, TextField } from '@shopify/polaris';
import settingFunctions from '../module';
import func from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import settingRequests from '../api';

function Slack() {
    
    const [slackWebhooks, setSlackWebhooks] = useState([])
    const [slackWebhookUrl, setSlackWebhookUrl] = useState("")
    const [slackWebhookName, setSlackWebhookName] = useState("")

    async function fetchWebhooks() {
        let arr = await settingFunctions.getTokenList("SLACK")
        setSlackWebhooks(arr)
    }

    useEffect(() => {
        fetchWebhooks()
    }, [])

    const handleDeleteSlackWebhook = async (id) => {
        const response = await settingRequests.deleteSlackWebhook(id)
        if (response) {
            func.setToast(true, false, "Slack webhook deleted successfully!")
            fetchWebhooks()
        }
        
    }

    const handleAddSlackWebhook = async () => {
        const response = await settingRequests.addSlackWebhook(slackWebhookUrl, slackWebhookName)
        if (response) {
            if (response.error) {
                func.setToast(true, true, response.error)
            } else {
                func.setToast(true, false, "Slack webhook added successfully!")
                fetchWebhooks()
            }
        }
    }

    const cardContent = "Send alerts to your slack to get notified when new endpoints are discovered"

    const listComponent = (
        slackWebhooks.map((slackWebhook, index) => (
            <LegacyCard.Section title={slackWebhook?.name || `Slack webhook ${index}`} key={index}
                actions={[{ content: 'Delete', destructive: true, onAction: () => handleDeleteSlackWebhook(slackWebhook.id) }]}>
                <p>{func.prettifyEpoch(slackWebhook.timestamp)}</p>
                <PasswordTextField field={slackWebhook.key} />
            </LegacyCard.Section>
        ))
    )

    const slackFormComponent = (
        <LegacyCard.Section title="Add Slack Webhook">
            <TextField
                label="Slack Webhook Name"
                value={slackWebhookName}
                onChange={(slackWebhookName) => setSlackWebhookName(slackWebhookName)} />
            <TextField 
                label="Slack Webhook URL"
                value={slackWebhookUrl} 
                onChange={(slackWebhookUrl) => setSlackWebhookUrl(slackWebhookUrl)} />
        </LegacyCard.Section>
    )
    
    const SlackCard = (
        <LegacyCard title="Slack Webhooks"
            primaryFooterAction={{ content: 'Add Slack Webhook', onAction: handleAddSlackWebhook }} 
        >
            {listComponent}
            {slackFormComponent}
        </LegacyCard>
    )
    return (
        <IntegrationsLayout title="Slack" cardContent={cardContent} component={SlackCard} docsUrl="" />
    )
}

export default Slack
import React from 'react'
import WebhookCore from '../webhookLayout/WebhookCore'

function GmailWebhookCore() {
    return (
        <WebhookCore defaultPayload={""} webhookType="GMAIL" />
    )
}

export default GmailWebhookCore
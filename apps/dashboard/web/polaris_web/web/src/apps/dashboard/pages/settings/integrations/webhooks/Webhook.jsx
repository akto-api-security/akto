
import WebhookCore from "../webhookLayout/WebhookCore"

function TeamsWebhook(){

    const ogDefaultPayload = "{\r\n  \"New Endpoint\": ${AKTO.changes_info.newEndpoints},\r\n  \"New Endpoint Count\": ${AKTO.changes_info.newEndpointsCount},\r\n  \"New Sensitive Endpoint\": ${AKTO.changes_info.newSensitiveEndpoints},\r\n  \"New Sensitive Endpoint Count\": ${AKTO.changes_info.newSensitiveEndpointsCount},\r\n  \"New Sensitive Parameter Count\": ${AKTO.changes_info.newSensitiveParametersCount},\r\n  \"New Parameter Count\": ${AKTO.changes_info.newParametersCount},\r\n  \"API Threat payloads\": ${AKTO.changes_info.apiThreatPayloads}\r\n }"

    return (
        <WebhookCore defaultPayload={ogDefaultPayload} webhookType={"DEFAULT"}/>
    )
}

export default TeamsWebhook
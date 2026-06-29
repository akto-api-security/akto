import { Box, Link, Text } from '@shopify/polaris'
import { useMemo, useRef } from 'react'
import func from "@/util/func"
import JsonComponent from './shared/JsonComponent'
import RuntimeTokenField from './RuntimeTokenField'

const DOCS_URL = 'https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/connectors/ai-agent-security/dify'
const DIFY_PATH = '/api/http-proxy/dify'

function getDifyApiEndpoint() {
    const accountId = window.ACTIVE_ACCOUNT
    if (accountId) {
        return `https://${accountId}-guardrails-service.akto.io${DIFY_PATH}`
    }
    return `https://<accountId>-guardrails-service.akto.io${DIFY_PATH}`
}

function DifyConnector() {
    const ref = useRef(null)
    const difyEndpoint = useMemo(() => getDifyApiEndpoint(), [])

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Secure your Dify app inputs and outputs with Akto guardrails using Dify's Moderation API Extension. No extra service to deploy &mdash; Dify calls Akto directly.
            </Text>
            <div ref={ref} />

            <RuntimeTokenField
                id="select-dify-expiry"
                codeStyle
                tokenStepText="Copy the token below. You will paste it as the API Key when adding the API Extension in Dify."
            />

            <Box paddingBlockStart={"3"} paddingBlockEnd={"2"}>
                <span>3. In Dify, go to <Text as="span" fontWeight="semibold">Settings &rarr; API Extension &rarr; Add</Text>. Paste the API Endpoint and API Key below. </span>
            </Box>
            <Box paddingInlineStart={"4"}>
                <JsonComponent title="API Endpoint" toolTipContent="Copy endpoint" onClickFunc={() => func.copyToClipboard(difyEndpoint, ref, null)} dataString={difyEndpoint} language="text" minHeight="40px" />
            </Box>

            <Box paddingBlockStart={"3"}>
                <span>4. In each app, open <Text as="span" fontWeight="semibold">Content Moderation</Text>, choose this API extension, and enable <Text as="span" fontWeight="semibold">Review Input Content</Text> and/or <Text as="span" fontWeight="semibold">Review Output Content</Text>. </span>
            </Box>

            <Box paddingBlockStart={"3"}>
                <Link url={DOCS_URL} external>View full Dify integration documentation</Link>
            </Box>
        </div>
    )
}

export default DifyConnector

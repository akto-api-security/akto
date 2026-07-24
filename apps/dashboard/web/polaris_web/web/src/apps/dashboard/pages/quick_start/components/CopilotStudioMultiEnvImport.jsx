import { Badge, Banner, Box, Button, Divider, HorizontalStack, Link, Scrollable, Text, TextField, VerticalStack } from '@shopify/polaris'
import { useEffect, useState } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'

/**
 * Copilot Studio (Multi Environment) connector.
 * Once an integration exists, the form becomes read-only — settings can't be edited in place.
 * The only way to change credentials is to remove the integration (which also stops its job)
 * and connect again from a clean state.
 */
const CopilotStudioMultiEnvImport = ({ docsUrl }) => {
    const [tenantId, setTenantId] = useState('')
    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')
    const [redirecting, setRedirecting] = useState(false)
    const [removing, setRemoving] = useState(false)

    const [integration, setIntegration] = useState(null)
    const [confirming, setConfirming] = useState(false)

    const loadIntegration = () => {
        api.fetchCopilotStudioMultiEnvIntegration()
            .then((res) => {
                setIntegration(res.integration || null)
                if (res.integration) {
                    setTenantId(res.integration.tenantId || '')
                    setClientId(res.integration.clientId || '')
                    setDataIngestionUrl(res.integration.dataIngestionUrl || '')
                }
            })
            .catch(() => {})
    }

    useEffect(() => {
        loadIntegration()
    }, [])

    const isFormValid = tenantId.trim() && clientId.trim() && clientSecret.trim() && dataIngestionUrl.trim()
    // A PENDING_OAUTH integration means authorization was never completed — keep the form editable so Connect can be retried.
    const isReadOnly = !!integration && integration.status !== 'PENDING_OAUTH'

    const handleConnect = () => {
        setRedirecting(true)
        api.initiateCopilotStudioMultiEnvSetup(tenantId, clientId, clientSecret, dataIngestionUrl)
            .then((res) => {
                window.location.href = res.authorizationUrl
            })
            .catch(() => {
                func.setToast(true, true, 'Failed to start Copilot Studio (Multi Environment) setup. Check your credentials.')
                setRedirecting(false)
            })
    }

    const handleConfirm = () => {
        setConfirming(true)
        api.confirmCopilotStudioMultiEnvIntegration(integration.hexId)
            .then(() => {
                func.setToast(true, false, 'Copilot Studio (Multi Environment) connected. Check the Jobs page for sync status.')
                loadIntegration()
            })
            .catch(() => func.setToast(true, true, 'Failed to confirm the connection. Please try again.'))
            .finally(() => setConfirming(false))
    }

    const handleRemove = () => {
        setRemoving(true)
        api.removeCopilotStudioMultiEnvIntegration()
            .then(() => {
                func.setToast(true, false, 'Integration removed.')
                setIntegration(null)
                setTenantId('')
                setClientId('')
                setClientSecret('')
                setDataIngestionUrl('')
            })
            .catch(() => func.setToast(true, true, 'Failed to remove the integration. Please try again.'))
            .finally(() => setRemoving(false))
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Connect your Power Platform tenant once — Akto automatically discovers every environment, provisions an application user in each, and imports Copilot Studio conversation data from all of them.
                {docsUrl && <> <Link url={docsUrl} target="_blank">Learn more</Link></>}
            </Text>

            {integration && integration.status === 'CONFIRMED' && (
                <Box paddingBlockStart="3">
                    <HorizontalStack gap="2">
                        <Badge status="success">Connected</Badge>
                        <Text variant='bodySm' color='subdued'>
                            {integration.environments?.length || 0} environment(s) syncing. See the Jobs page for status.
                        </Text>
                    </HorizontalStack>
                </Box>
            )}

            {integration && integration.lastError && (
                <Box paddingBlockStart="3">
                    <Banner status="critical" title={integration.lastError} />
                </Box>
            )}

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                <TextField
                    label="Azure AD Tenant ID"
                    value={tenantId}
                    onChange={setTenantId}
                    placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    requiredIndicator
                    autoComplete="off"
                    disabled={isReadOnly}
                />
                <TextField
                    label="Azure AD App Client ID"
                    value={clientId}
                    onChange={setClientId}
                    placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    requiredIndicator
                    autoComplete="off"
                    disabled={isReadOnly}
                />
                <PasswordTextField
                    label="Azure AD App Client Secret"
                    onFunc={true}
                    setField={setClientSecret}
                    field={clientSecret}
                    requiredIndicator
                    disabled={isReadOnly}
                />
                <TextField
                    label="URL for Data Ingestion Service"
                    value={dataIngestionUrl}
                    onChange={setDataIngestionUrl}
                    placeholder="https://ingestion.example.com"
                    requiredIndicator
                    autoComplete="off"
                    disabled={isReadOnly}
                />

                <HorizontalStack align='end'>
                    {isReadOnly ? (
                        <Button destructive onClick={handleRemove} loading={removing}>Remove Integration</Button>
                    ) : (
                        <Button primary onClick={handleConnect} disabled={!isFormValid} loading={redirecting}>
                            Connect
                        </Button>
                    )}
                </HorizontalStack>
            </VerticalStack>

            {integration && integration.status === 'ENVIRONMENTS_DISCOVERED' && (
                <VerticalStack gap="3">
                    <Box paddingBlockStart="3"><Divider /></Box>
                    <Text variant='headingMd'>Review discovered environments</Text>
                    <Text variant='bodyMd'>
                        Akto discovered {integration.environments?.length || 0} environment(s) in this tenant. Confirming will
                        provision Akto as an application user in each and start importing Copilot Studio conversation data.
                    </Text>
                    <Scrollable style={{ maxHeight: '160px' }} shadow>
                        <VerticalStack gap="2">
                            {(integration.environments || []).map((env) => (
                                <HorizontalStack key={env.environmentId} align='space-between'>
                                    <Text variant='bodyMd'>{env.environmentName || env.environmentId}</Text>
                                    <Text variant='bodySm' color='subdued'>{env.environmentUrl}</Text>
                                </HorizontalStack>
                            ))}
                        </VerticalStack>
                    </Scrollable>
                    <HorizontalStack align='end'>
                        <Button primary loading={confirming} onClick={handleConfirm}>Confirm & Connect</Button>
                    </HorizontalStack>
                </VerticalStack>
            )}
        </div>
    )
}

export default CopilotStudioMultiEnvImport

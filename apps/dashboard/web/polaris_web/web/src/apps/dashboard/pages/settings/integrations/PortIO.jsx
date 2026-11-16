import React, { useEffect, useState } from 'react'
import { Button, Divider, HorizontalStack, LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris'
import settingFunctions from '../module'
import IntegrationsLayout from './IntegrationsLayout'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import AllowedAction from '../../../components/shared/AllowedAction'
import SeverityPriorityMapping, { DEFAULT_SEVERITY_TO_PRIORITY } from '../../../components/shared/SeverityPriorityMapping'
import func from "@/util/func"
import { usePermissions, whenAllowed } from "@/util/permissions"
import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'

const DEFAULT_WORKFLOW_ID = 'linear_ticket_creation'

const errorMessageOf = (error, fallback) => error?.response?.data?.actionErrors?.[0] || fallback

const PortIO = () => {

    const { canCall } = usePermissions()

    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [workflowId, setWorkflowId] = useState(DEFAULT_WORKFLOW_ID)
    const [isIntegrated, setIsIntegrated] = useState(false)
    const [isSaving, setIsSaving] = useState(false)
    const [severityToPriority, setSeverityToPriority] = useState(DEFAULT_SEVERITY_TO_PRIORITY)
    const [editingMapping, setEditingMapping] = useState(false)
    const [isSavingMapping, setIsSavingMapping] = useState(false)

    async function loadIntegration() {
        try {
            const portInteg = await settingFunctions.fetchPortIOIntegration()
            if (!portInteg) {
                return
            }
            setIsIntegrated(true)
            setClientId(portInteg.clientId || '')
            setWorkflowId(portInteg.workflowId || DEFAULT_WORKFLOW_ID)
            setSeverityToPriority({ ...DEFAULT_SEVERITY_TO_PRIORITY, ...portInteg.severityToPriority })
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Error fetching Port integration'))
        }
    }

    useEffect(() => {
        loadIntegration()
    }, [])

    async function saveCredentials() {
        setIsSaving(true)
        try {
            await settingFunctions.addPortIOIntegration(clientId.trim(), clientSecret.trim(), workflowId.trim())
            window.PORT_IO_INTEGRATED = 'true'
            setClientSecret('')
            func.setToast(true, false, isIntegrated ? 'Successfully updated Port credentials' : 'Successfully saved Port credentials')
            await loadIntegration()
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to save Port credentials'))
        } finally {
            setIsSaving(false)
        }
    }

    async function saveMapping() {
        setIsSavingMapping(true)
        try {
            await settingFunctions.savePortIOSeverityMapping(severityToPriority)
            setEditingMapping(false)
            func.setToast(true, false, 'Severity mapping saved')
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to save severity mapping'))
        } finally {
            setIsSavingMapping(false)
        }
    }

    async function removeIntegration() {
        setIsSaving(true)
        try {
            await settingFunctions.removePortIOIntegration()
            window.PORT_IO_INTEGRATED = 'false'
            setIsIntegrated(false)
            setClientId('')
            setClientSecret('')
            setWorkflowId(DEFAULT_WORKFLOW_ID)
            setSeverityToPriority(DEFAULT_SEVERITY_TO_PRIORITY)
            setEditingMapping(false)
            func.setToast(true, false, 'Successfully removed Port integration')
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to remove Port integration'))
        } finally {
            setIsSaving(false)
        }
    }

    const canSave = clientId.trim() && workflowId.trim() && clientSecret.trim()

    const CredentialsCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSaving ? 'Saving...' : (isIntegrated ? 'Update' : 'Save'),
                onAction: saveCredentials,
                disabled: isSaving || !canSave,
                loading: isSaving,
                ...whenAllowed(canCall('api/addPortIOIntegration'))
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeIntegration,
                disabled: !isIntegrated || isSaving,
                ...whenAllowed(canCall('api/removePortIOIntegration'))
            }]}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Integrate Port</Text>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <TextField
                        label="Client ID"
                        value={clientId}
                        onChange={setClientId}
                        autoComplete="off"
                        requiredIndicator
                    />
                    <PasswordTextField
                        label="Client secret"
                        helpText={isIntegrated
                            ? 'Your client secret is saved. Enter it again to update the credentials'
                            : 'Port client secret. It is only used to run your Port workflow'}
                        field={clientSecret}
                        onFunc={true}
                        setField={setClientSecret}
                        placeholder="Port client secret"
                        requiredIndicator
                    />
                    <TextField
                        label="Workflow identifier"
                        value={workflowId}
                        onChange={setWorkflowId}
                        helpText="Identifier of the Port workflow that creates the Linear issue. The Linear team is set inside that workflow"
                        autoComplete="off"
                        requiredIndicator
                    />
                </VerticalStack>
            </LegacyCard.Section>
            <Divider />
            <br />
        </LegacyCard>
    )

    const MappingCard = (
        <LegacyCard>
            <LegacyCard.Section>
                <HorizontalStack align="space-between" blockAlign="center">
                    <VerticalStack gap="1">
                        <Text variant="headingMd">Severity to Priority Mapping</Text>
                        <Text variant="bodyMd" color="subdued">
                            Akto sends the mapped priority to your Port workflow as the priority input
                        </Text>
                    </VerticalStack>
                    <AllowedAction allowed={canCall('api/savePortIOSeverityMapping')}>
                        <Button
                            size="slim"
                            disabled={!isIntegrated || isSavingMapping}
                            loading={isSavingMapping}
                            onClick={editingMapping ? saveMapping : () => setEditingMapping(true)}
                        >
                            {editingMapping ? 'Save' : 'Edit Mapping'}
                        </Button>
                    </AllowedAction>
                </HorizontalStack>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <SeverityPriorityMapping
                    rowId="port"
                    severityToPriority={severityToPriority}
                    editing={editingMapping}
                    onChange={(severity, value) => setSeverityToPriority(prev => ({ ...prev, [severity]: value }))}
                />
            </LegacyCard.Section>
        </LegacyCard>
    )

    const cardContent = "Create Linear issues for " + mapLabel('API', getDashboardCategory()) + " vulnerability findings through your Port workflow. Akto only needs Port credentials; the Linear key and team stay in Port"

    return (
        <IntegrationsLayout
            title="Port"
            cardContent={cardContent}
            component={
                <VerticalStack gap="4">
                    {CredentialsCard}
                    {MappingCard}
                </VerticalStack>
            }
            readOnly={!canCall('api/addPortIOIntegration')}
        />
    )
}

export default PortIO

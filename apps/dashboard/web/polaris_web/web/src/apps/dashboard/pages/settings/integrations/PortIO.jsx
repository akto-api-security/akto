import React, { useEffect, useRef, useState } from 'react'
import { Button, Card, Divider, HorizontalStack, LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris'
import settingFunctions from '../module'
import IntegrationsLayout from './IntegrationsLayout'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import func from "@/util/func"
import { usePermissions, whenAllowed } from "@/util/permissions"
import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'

const DEFAULT_WORKFLOW_ID = 'linear_ticket_creation'

const errorMessageOf = (error, fallback) => error?.response?.data?.actionErrors?.[0] || fallback

const PortIO = () => {

    const { canCall } = usePermissions()
    const nextRowId = useRef(0)

    const newTeamRow = (teamId = '', teamName = '') => ({ rowId: nextRowId.current++, teamId, teamName })

    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [workflowId, setWorkflowId] = useState(DEFAULT_WORKFLOW_ID)
    const [isIntegrated, setIsIntegrated] = useState(false)
    const [isSavingCredentials, setIsSavingCredentials] = useState(false)
    const [teams, setTeams] = useState([])
    const [isSavingTeams, setIsSavingTeams] = useState(false)

    async function loadIntegration() {
        try {
            const portInteg = await settingFunctions.fetchPortIOIntegration()
            if (!portInteg) {
                return
            }
            setIsIntegrated(true)
            setClientId(portInteg.clientId || '')
            setWorkflowId(portInteg.workflowId || DEFAULT_WORKFLOW_ID)
            const savedTeams = Object.entries(portInteg.teamMappings || {}).map(([teamId, mapping]) =>
                newTeamRow(teamId, mapping.teamName)
            )
            setTeams(savedTeams.length > 0 ? savedTeams : [newTeamRow()])
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Error fetching Port integration'))
        }
    }

    useEffect(() => {
        loadIntegration()
    }, [])

    async function saveCredentials() {
        setIsSavingCredentials(true)
        try {
            await settingFunctions.addPortIOIntegration(clientId.trim(), clientSecret.trim(), workflowId.trim())
            window.PORT_IO_INTEGRATED = 'true'
            setClientSecret('')
            func.setToast(true, false, isIntegrated ? 'Successfully updated Port credentials' : 'Successfully saved Port credentials')
            await loadIntegration()
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to save Port credentials'))
        } finally {
            setIsSavingCredentials(false)
        }
    }

    async function removeIntegration() {
        setIsSavingCredentials(true)
        try {
            await settingFunctions.removePortIOIntegration()
            window.PORT_IO_INTEGRATED = 'false'
            setIsIntegrated(false)
            setTeams([])
            setClientId('')
            setClientSecret('')
            setWorkflowId(DEFAULT_WORKFLOW_ID)
            func.setToast(true, false, 'Successfully removed Port integration')
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to remove Port integration'))
        } finally {
            setIsSavingCredentials(false)
        }
    }

    async function saveTeams() {
        const teamMappings = {}
        teams.forEach(team => {
            teamMappings[team.teamId.trim()] = { teamName: team.teamName.trim() }
        })

        setIsSavingTeams(true)
        try {
            await settingFunctions.savePortIOTeams(teamMappings)
            func.setToast(true, false, 'Linear teams saved successfully')
            await loadIntegration()
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to save Linear teams'))
        } finally {
            setIsSavingTeams(false)
        }
    }

    function updateTeam(rowId, patch) {
        setTeams(prev => prev.map(team => team.rowId === rowId ? { ...team, ...patch } : team))
    }

    function deleteTeam(rowId) {
        setTeams(prev => prev.filter(team => team.rowId !== rowId))
    }

    const hasCompleteTeams = teams.length > 0 && teams.every(team => team.teamId.trim() && team.teamName.trim())
    const hasDuplicateTeams = new Set(teams.map(team => team.teamId.trim())).size !== teams.length
    const canSaveCredentials = clientId.trim() && workflowId.trim() && clientSecret.trim()

    const CredentialsCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSavingCredentials ? 'Saving...' : (isIntegrated ? 'Update' : 'Save'),
                onAction: saveCredentials,
                disabled: isSavingCredentials || !canSaveCredentials,
                loading: isSavingCredentials,
                ...whenAllowed(canCall('api/addPortIOIntegration'))
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeIntegration,
                disabled: !isIntegrated || isSavingCredentials,
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
                            : 'Port client secret. It is only used to call your Port workflow'}
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
                        helpText="Identifier of the Port workflow that creates the Linear issue"
                        autoComplete="off"
                        requiredIndicator
                    />
                </VerticalStack>
            </LegacyCard.Section>
            <Divider />
            <br />
        </LegacyCard>
    )

    const TeamCards = teams.map((team, index) => (
        <Card roundedAbove="sm" key={`team-${team.rowId}`}>
            <VerticalStack gap="4">
                <HorizontalStack align="space-between">
                    <Text fontWeight="semibold" variant="headingSm">{`Team ${index + 1}`}</Text>
                    <Button plain removeUnderline destructive size="slim" disabled={teams.length <= 1} onClick={() => deleteTeam(team.rowId)}>
                        Delete Team
                    </Button>
                </HorizontalStack>
                <TextField
                    label="Linear team ID"
                    value={team.teamId}
                    onChange={(teamId) => updateTeam(team.rowId, { teamId })}
                    helpText="UUID of the Linear team, not the team key"
                    autoComplete="off"
                    requiredIndicator
                />
                <TextField
                    label="Team name"
                    value={team.teamName}
                    onChange={(teamName) => updateTeam(team.rowId, { teamName })}
                    autoComplete="off"
                    requiredIndicator
                />
            </VerticalStack>
        </Card>
    ))

    const TeamsCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSavingTeams ? 'Saving...' : 'Save',
                onAction: saveTeams,
                disabled: isSavingTeams || !hasCompleteTeams || hasDuplicateTeams,
                loading: isSavingTeams,
                ...whenAllowed(canCall('api/savePortIOTeams'))
            }}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Linear Teams</Text>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <HorizontalStack align="space-between">
                        <Text fontWeight="semibold" variant="headingMd">Teams</Text>
                        <Button plain monochrome onClick={() => setTeams(prev => [...prev, newTeamRow()])}>Add Team</Button>
                    </HorizontalStack>
                    {TeamCards}
                </VerticalStack>
            </LegacyCard.Section>
            <Divider />
            <br />
        </LegacyCard>
    )

    const cardContent = "Create Linear issues for " + mapLabel('API', getDashboardCategory()) + " vulnerability findings through your Port workflow. Akto only needs Port credentials; the Linear key stays in Port"

    return (
        <IntegrationsLayout
            title="Port"
            cardContent={cardContent}
            component={
                <VerticalStack gap="4">
                    {CredentialsCard}
                    {isIntegrated ? TeamsCard : null}
                </VerticalStack>
            }
            readOnly={!canCall('api/addPortIOIntegration')}
        />
    )
}

export default PortIO

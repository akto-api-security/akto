import React, { useEffect, useRef, useState } from 'react'
import { Badge, Box, Button, Card, Divider, HorizontalGrid, HorizontalStack, LegacyCard, Modal, Text, TextField, VerticalStack } from '@shopify/polaris'
import settingFunctions from '../module'
import IntegrationsLayout from './IntegrationsLayout'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import Dropdown from '../../../components/layouts/Dropdown'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import AllowedAction from '../../../components/shared/AllowedAction'
import func from "@/util/func"
import { usePermissions, whenAllowed } from "@/util/permissions"
import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'

const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

const DEFAULT_SEVERITY_TO_PRIORITY = { CRITICAL: '1', HIGH: '2', MEDIUM: '3', LOW: '4' }

const PRIORITY_OPTIONS = [
    { label: 'Urgent', value: '1' },
    { label: 'High', value: '2' },
    { label: 'Medium', value: '3' },
    { label: 'Low', value: '4' },
    { label: 'No priority', value: '0' }
]

const SEVERITY_BADGE_STATUS = { CRITICAL: 'critical', HIGH: 'warning', MEDIUM: 'attention', LOW: 'info' }

const toOptions = (teamsMap) => Object.entries(teamsMap || {}).map(([id, name]) => ({ label: name, value: id }))

const priorityLabel = (value) => PRIORITY_OPTIONS.find(option => option.value === value)?.label || ''

const errorMessageOf = (error, fallback) => error?.response?.data?.actionErrors?.[0] || fallback

const MappingCell = ({ children, header, compact }) => (
    <Box
        padding={compact ? '2' : '4'}
        background={header ? 'bg-surface-secondary' : undefined}
        borderWidth="1"
        borderColor="border"
        borderRadius="2"
    >
        <HorizontalStack align="center" blockAlign="center">
            {children}
        </HorizontalStack>
    </Box>
)

const Linear = () => {

    const { canCall } = usePermissions()
    const nextRowId = useRef(0)

    const newTeamRow = (teamId = '', severityToPriority = DEFAULT_SEVERITY_TO_PRIORITY) => ({
        rowId: nextRowId.current++,
        teamId,
        severityToPriority: { ...severityToPriority },
        editingMapping: false
    })

    const [apiToken, setApiToken] = useState('')
    const [isIntegrated, setIsIntegrated] = useState(false)
    const [isSavingKey, setIsSavingKey] = useState(false)
    const [teams, setTeams] = useState([])
    const [workspaceTeams, setWorkspaceTeams] = useState([])
    const [isSavingTeams, setIsSavingTeams] = useState(false)
    const [loadingTeams, setLoadingTeams] = useState(false)
    const [createModalActive, setCreateModalActive] = useState(false)
    const [newTeamName, setNewTeamName] = useState('')
    const [newTeamKey, setNewTeamKey] = useState('')
    const [isCreatingTeam, setIsCreatingTeam] = useState(false)

    async function fetchWorkspaceTeams() {
        setLoadingTeams(true)
        try {
            const teamsMap = await settingFunctions.fetchLinearTeams(null)
            setWorkspaceTeams(toOptions(teamsMap))
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to fetch Linear teams'))
        } finally {
            setLoadingTeams(false)
        }
    }

    async function loadIntegration() {
        try {
            const linearInteg = await settingFunctions.fetchLinearIntegration()
            if (!linearInteg) {
                return
            }
            setIsIntegrated(true)
            const savedTeams = Object.entries(linearInteg.teamMappings || {}).map(([teamId, mapping]) =>
                newTeamRow(teamId, { ...DEFAULT_SEVERITY_TO_PRIORITY, ...mapping.severityToPriority })
            )
            setTeams(savedTeams.length > 0 ? savedTeams : [newTeamRow()])
            setWorkspaceTeams(toOptions(linearInteg.teamsMap))
            await fetchWorkspaceTeams()
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Error fetching Linear integration'))
        }
    }

    useEffect(() => {
        loadIntegration()
    }, [])

    async function saveApiKey() {
        setIsSavingKey(true)
        try {
            await settingFunctions.addLinearIntegration(apiToken.trim())
            window.LINEAR_INTEGRATED = 'true'
            setApiToken('')
            func.setToast(true, false, isIntegrated ? 'Successfully updated Linear API key' : 'Successfully saved Linear API key')
            await loadIntegration()
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to save Linear API key'))
        } finally {
            setIsSavingKey(false)
        }
    }

    async function removeIntegration() {
        setIsSavingKey(true)
        try {
            await settingFunctions.removeLinearIntegration()
            window.LINEAR_INTEGRATED = 'false'
            setIsIntegrated(false)
            setTeams([])
            setWorkspaceTeams([])
            setApiToken('')
            func.setToast(true, false, 'Successfully removed Linear Integration')
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to remove Linear Integration'))
        } finally {
            setIsSavingKey(false)
        }
    }

    async function saveTeams() {
        const teamMappings = {}
        teams.forEach(team => {
            teamMappings[team.teamId] = { severityToPriority: team.severityToPriority }
        })

        setIsSavingTeams(true)
        try {
            await settingFunctions.saveLinearTeams(teamMappings)
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

    function updatePriority(rowId, severity, value) {
        setTeams(prev => prev.map(team => team.rowId === rowId
            ? { ...team, severityToPriority: { ...team.severityToPriority, [severity]: value } }
            : team))
    }

    function deleteTeam(rowId) {
        setTeams(prev => prev.filter(team => team.rowId !== rowId))
    }

    function optionsFor(rowId) {
        const chosenElsewhere = new Set(teams.filter(team => team.rowId !== rowId).map(team => team.teamId))
        return workspaceTeams.filter(option => !chosenElsewhere.has(option.value))
    }

    function closeCreateModal() {
        setCreateModalActive(false)
        setNewTeamName('')
        setNewTeamKey('')
    }

    async function createTeam() {
        setIsCreatingTeam(true)
        try {
            const teamsMap = await settingFunctions.createLinearTeam(null, newTeamName.trim(), newTeamKey.trim() || null)
            const created = toOptions(teamsMap)
            setWorkspaceTeams(prev => [...prev, ...created.filter(option => !prev.some(existing => existing.value === option.value))])
            setTeams(prev => {
                const emptyIndex = prev.findIndex(team => !team.teamId)
                if (emptyIndex >= 0) {
                    return prev.map((team, index) => index === emptyIndex ? { ...team, teamId: created[0].value } : team)
                }
                return [...prev, newTeamRow(created[0].value)]
            })
            closeCreateModal()
            func.setToast(true, false, 'Team created in Linear. Save to start using it for tickets.')
        } catch (error) {
            func.setToast(true, true, errorMessageOf(error, 'Failed to create Linear team'))
        } finally {
            setIsCreatingTeam(false)
        }
    }

    const canSaveTeams = !isSavingTeams && teams.length > 0 && teams.every(team => team.teamId)
    const isCreateDisabled = isCreatingTeam || newTeamName.trim().length === 0

    const renderSeverityMappingTable = (team) => (
        <VerticalStack gap="2">
            <HorizontalGrid columns={2} gap="2">
                <MappingCell header><Text variant="bodyMd" fontWeight="semibold">Akto Severity</Text></MappingCell>
                <MappingCell header><Text variant="bodyMd" fontWeight="semibold">Linear Priority</Text></MappingCell>
            </HorizontalGrid>
            {SEVERITIES.map(severity => (
                <HorizontalGrid columns={2} gap="2" key={`${team.rowId}-${severity}`}>
                    <MappingCell>
                        <Badge status={SEVERITY_BADGE_STATUS[severity]}>{func.toSentenceCase(severity)}</Badge>
                    </MappingCell>
                    <MappingCell compact={team.editingMapping}>
                        {team.editingMapping
                            ? <Dropdown
                                menuItems={PRIORITY_OPTIONS}
                                initial={team.severityToPriority[severity]}
                                selected={(value) => updatePriority(team.rowId, severity, value)}
                                placeHolder="Select Priority"
                            />
                            : <Text variant="bodyMd">{priorityLabel(team.severityToPriority[severity])}</Text>}
                    </MappingCell>
                </HorizontalGrid>
            ))}
        </VerticalStack>
    )

    const TeamCards = teams.map((team, index) => {
        const options = optionsFor(team.rowId)
        const selected = workspaceTeams.find(option => option.value === team.teamId)

        return (
            <Card key={`team-${team.rowId}`} roundedAbove="sm">
                <VerticalStack gap="4">
                    <HorizontalStack align="space-between">
                        <Text fontWeight="semibold" variant="headingSm">{`Team ${index + 1}`}</Text>
                        <Button plain removeUnderline destructive size="slim" disabled={teams.length <= 1} onClick={() => deleteTeam(team.rowId)}>
                            Delete Team
                        </Button>
                    </HorizontalStack>
                    <DropdownSearch
                        key={`team-select-${team.rowId}-${options.length}`}
                        id={`linear-team-${team.rowId}`}
                        label="Team"
                        placeholder={loadingTeams ? 'Loading teams...' : 'Select Linear team'}
                        optionsList={options}
                        setSelected={(teamId) => updateTeam(team.rowId, { teamId })}
                        preSelected={team.teamId}
                        value={selected ? selected.label : ''}
                        disabled={loadingTeams}
                        textfieldRequiredIndicator={true}
                    />
                    <Divider />
                    <VerticalStack gap="3">
                        <HorizontalStack align="space-between">
                            <VerticalStack gap="1">
                                <Text fontWeight="semibold" variant="headingSm">Severity to Priority Mapping</Text>
                                <Text variant="bodyMd" color="subdued">
                                    Map Akto severity to the Linear priority set on tickets created in this team
                                </Text>
                            </VerticalStack>
                            <AllowedAction allowed={canCall('api/saveLinearTeams')}>
                                <Button size="slim" onClick={() => updateTeam(team.rowId, { editingMapping: !team.editingMapping })}>
                                    {team.editingMapping ? 'Done' : 'Edit Mapping'}
                                </Button>
                            </AllowedAction>
                        </HorizontalStack>
                        {renderSeverityMappingTable(team)}
                    </VerticalStack>
                </VerticalStack>
            </Card>
        )
    })

    const KeyCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSavingKey ? 'Saving...' : (isIntegrated ? 'Update' : 'Save'),
                onAction: saveApiKey,
                disabled: isSavingKey || apiToken.trim().length === 0,
                loading: isSavingKey,
                ...whenAllowed(canCall('api/addLinearIntegration'))
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeIntegration,
                disabled: !isIntegrated || isSavingKey,
                ...whenAllowed(canCall('api/removeLinearIntegration'))
            }]}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Integrate Linear</Text>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <PasswordTextField
                        label="API Key"
                        helpText={isIntegrated
                            ? 'Your API key is saved. Enter a new one only if you want to replace it'
                            : 'Create a personal API key in Linear under Settings > Account > Security & access'}
                        field={apiToken}
                        onFunc={true}
                        setField={setApiToken}
                        placeholder={isIntegrated ? 'Enter a new API key to replace the saved one' : 'Linear API key'}
                        requiredIndicator={!isIntegrated}
                    />
                </VerticalStack>
            </LegacyCard.Section>
            <Divider />
            <br />
        </LegacyCard>
    )

    const TeamsCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSavingTeams ? 'Saving...' : 'Save',
                onAction: saveTeams,
                disabled: !canSaveTeams,
                loading: isSavingTeams,
                ...whenAllowed(canCall('api/saveLinearTeams'))
            }}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Linear Teams</Text>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <HorizontalStack align="space-between">
                        <Text fontWeight="semibold" variant="headingMd">Teams</Text>
                        <HorizontalStack gap="4">
                            <AllowedAction allowed={canCall('api/createLinearTeam')}>
                                <Button plain monochrome onClick={() => setCreateModalActive(true)}>Create Team</Button>
                            </AllowedAction>
                            <Button plain monochrome onClick={() => setTeams(prev => [...prev, newTeamRow()])}>Add Team</Button>
                        </HorizontalStack>
                    </HorizontalStack>
                    {TeamCards}
                </VerticalStack>
            </LegacyCard.Section>
            <Divider />
            <br />
        </LegacyCard>
    )

    const CreateTeamModal = (
        <Modal
            open={createModalActive}
            onClose={closeCreateModal}
            title="Create a Linear team"
            primaryAction={{
                content: 'Create team',
                onAction: createTeam,
                disabled: isCreateDisabled,
                loading: isCreatingTeam
            }}
            secondaryActions={[{ content: 'Cancel', onAction: closeCreateModal }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <TextField
                        label="Team name"
                        value={newTeamName}
                        onChange={setNewTeamName}
                        placeholder="Security"
                        autoComplete="off"
                        requiredIndicator
                    />
                    <TextField
                        label="Team key"
                        value={newTeamKey}
                        onChange={(val) => setNewTeamKey(val.toUpperCase())}
                        placeholder="SEC"
                        helpText="Optional. Used as the issue prefix, for example SEC-123. Your Linear API key must be allowed to create teams."
                        autoComplete="off"
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )

    const cardContent = "Seamlessly enhance your " + mapLabel('API', getDashboardCategory()) + " security workflow with Linear integration. Create Linear issues for " + mapLabel('API', getDashboardCategory()) + " vulnerability findings in the teams you choose, with severity mapped to Linear priority"

    return (
        <IntegrationsLayout
            title="Linear"
            cardContent={cardContent}
            component={
                <VerticalStack gap="4">
                    {KeyCard}
                    {isIntegrated ? TeamsCard : null}
                    {CreateTeamModal}
                </VerticalStack>
            }
            readOnly={!canCall('api/addLinearIntegration')}
        />
    )
}

export default Linear

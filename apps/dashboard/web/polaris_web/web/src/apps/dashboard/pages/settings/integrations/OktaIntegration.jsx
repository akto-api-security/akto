import React, { useEffect, useState } from 'react'
import CopyCommand from '../../../components/shared/CopyCommand';
import IntegrationsLayout from './IntegrationsLayout';
import { Autocomplete, Badge, Box, Button, Divider, Form, FormLayout, HorizontalStack, LegacyCard, Link, Text, TextField, VerticalStack } from '@shopify/polaris';
import { DeleteMinor, EditMinor, PlusMinor } from '@shopify/polaris-icons';
import func from "@/util/func"
import settingRequests from '../api';
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import StepsComponent from './components/StepsComponent';
import Details from './components/Details';
import DeleteModal from './components/DeleteModal';

function dashboardActionError(err, fallback) {
    const list = err?.response?.data?.actionErrors
    return (list && list[0]) || fallback
}

/** Editable mask when a token exists; user selects all + Delete to clear, or paste to replace. */
const TOKEN_EDIT_MASK = '**********'
/** Shown in details list when a token is stored (value is never returned from API). */
const MANAGEMENT_API_TOKEN_CONFIGURED_DISPLAY = '*****'

function managementTokenConfiguredFromResponse(resp) {
    return resp?.managementApiTokenStatus === true
}

const AKTO_ROLE_OPTIONS = [
    { label: 'Admin', value: 'ADMIN' },
    { label: 'Security Engineer', value: 'MEMBER' },
    { label: 'Developer', value: 'DEVELOPER' },
    { label: 'Guest', value: 'GUEST' },
]

function getAktoRoleOptionLabel(value) {
    return AKTO_ROLE_OPTIONS.find((r) => r.value === value)?.label || value
}

function OktaIntegration() {
    const hostname = window.location.origin

    const [componentType, setComponentType] = useState(0)
    const [loading, setLoading] = useState(false)
    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [setupApiToken, setSetupApiToken] = useState('')
    /** From server: whether a non-empty Management API token is stored. */
    const [managementApiTokenConfigured, setManagementApiTokenConfigured] = useState(false)
    const hasSavedManagementToken = managementApiTokenConfigured
    const managementApiTokenDisplay = hasSavedManagementToken
        ? MANAGEMENT_API_TOKEN_CONFIGURED_DISPLAY
        : 'Not set (optional)'
    const [editApiToken, setEditApiToken] = useState('')
    const [savingSettings, setSavingSettings] = useState(false)
    const [oktaDomain, setOktaDomain] = useState('')
    const [authorizationServerId, setAuthorizationServerId] = useState('')
    const [showDeleteModal, setShowDeleteModal] = useState(false)
    const [nextButtonActive, setNextButtonActive] = useState(true)

    /** Okta group → Akto user role (how that Okta user is represented in Akto) */
    const [oktaGroupToAktoUserRoleMap, setOktaGroupToAktoUserRoleMap] = useState({})
    const [editMode, setEditMode] = useState(false)
    const [newGroupName, setNewGroupName] = useState('')
    const [newAktoRole, setNewAktoRole] = useState('')
    /** Text shown in Akto role Autocomplete (matches Okta group name field UX). */
    const [aktoRoleText, setAktoRoleText] = useState('')
    const [savedOktaGroupToAktoUserRoleMap, setSavedOktaGroupToAktoUserRoleMap] = useState({})
    /** Fetched from Okta Management API (all groups) when Edit + API token; used to autosuggest group name. */
    const [oktaGroupNames, setOktaGroupNames] = useState([])
    const [loadingOktaGroups, setLoadingOktaGroups] = useState(false)

    const redirectUri = `${hostname}/authorization-code/callback`
    const initiateLoginUri = `${hostname}/okta-initiate-login?accountId=${window.ACTIVE_ACCOUNT}`

    const integrationSteps = [
        { text: "Go to your Okta admin console. Go inside 'Applications' tab and click on 'Create App Integration' button." },
        { text: "In 'Sign-in Method', choose 'OIDC - OpenID Connect' and in 'Application type', choose 'Web Application'." },
        { text: "In 'App integration name' field, fill 'Akto'." },
        { text: "In 'Sign-in redirect URIs' field, fill the below URL below.", component: <CopyCommand command={redirectUri} /> },
        { text: "In 'Initiate login URI' field, fill the below URL below. (Required only if you are using 'Login initiated by Okta' option)", component: <CopyCommand command={initiateLoginUri} /> },
        { text: "In 'Assignments' choose the access you required and then click on 'Save'." },
        { text: "Copy the 'CLIENT_ID' and 'CLIENT_SECRET'." },
        { text: "Add a groups claim to your Okta authorization server access token so Akto can map Okta groups to Akto roles." },
        { text: "Optional API token (Security → API → Tokens): Akto reads groups from the access token first; if the token has no groups, it fetches membership from Okta’s Management API at login." },
    ]

    const resetMappingDraft = () => {
        setNewGroupName('')
        setNewAktoRole('')
        setAktoRoleText('')
    }

    const handleSubmit = async () => {
        if (!clientId || !clientSecret || !oktaDomain) {
            func.setToast(true, true, "Fill all required fields")
            return
        }
        await settingRequests.addOktaSso(
            clientId,
            clientSecret,
            authorizationServerId,
            oktaDomain,
            redirectUri,
            setupApiToken.trim() || undefined
        )
        func.setToast(true, false, "Okta SSO saved successfully!")
        setManagementApiTokenConfigured(Boolean(setupApiToken.trim()))
        setSetupApiToken('')
        setComponentType(2)
    }

    const formComponent = (
        <LegacyCard.Section title="Fill details">
            <Form onSubmit={handleSubmit}>
                <FormLayout>
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Client ID of Okta's Application</Text>}
                        placeholder="Enter your client Id" onChange={setClientId} value={clientId} />
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Client Secret of Okta's Application</Text>}
                        placeholder="Enter your client secret" onChange={setClientSecret} value={clientSecret} type="password" />
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Authorization server Id of Okta's Application</Text>}
                        placeholder="Enter your authorization server Id" onChange={setAuthorizationServerId} value={authorizationServerId} />
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Domain name of Okta's Application</Text>}
                        placeholder="Enter the domain name of your Okta console" onChange={setOktaDomain} value={oktaDomain} />
                    <TextField
                        label={<Text fontWeight="medium" variant="bodySm">Okta API token (optional)</Text>}
                        placeholder="Security → API → Tokens — used if groups are not in the access token"
                        onChange={setSetupApiToken} value={setupApiToken} type="password"
                        autoComplete="new-password" name="okta-sso-management-api-token-setup"
                        helpText="Used only to read group membership from Okta when groups are not in the access token."
                    />
                    <HorizontalStack align="end">
                        <Button submit primary size="medium">Submit</Button>
                    </HorizontalStack>
                </FormLayout>
            </Form>
        </LegacyCard.Section>
    )

    const fetchData = async () => {
        setLoading(true)
        try {
            const resp = await settingRequests.fetchOktaSso()
            if (resp.clientId != null && resp.clientId.length > 0) {
                setClientId(resp.clientId)
                setAuthorizationServerId(resp.authorisationServerId)
                setOktaDomain(resp.oktaDomain)
                setManagementApiTokenConfigured(managementTokenConfiguredFromResponse(resp))
                const grpMap = resp.oktaGroupToAktoUserRoleMap || {}
                setOktaGroupToAktoUserRoleMap(grpMap)
                setSavedOktaGroupToAktoUserRoleMap(grpMap)
                setComponentType(2)
            }
        } catch {
            setNextButtonActive(false)
        } finally {
            setLoading(false)
        }
    }

    const handleAddMapping = () => {
        const trimmed = newGroupName.trim()
        if (!trimmed) {
            func.setToast(true, true, "Group name cannot be empty")
            return
        }
        if (!newAktoRole) {
            func.setToast(true, true, 'Select an Akto role from the suggestions or type the full role name.')
            return
        }
        const usedRoles = Object.values(oktaGroupToAktoUserRoleMap)
        if (usedRoles.includes(newAktoRole)) {
            func.setToast(true, true, "One-to-one mapping only: this Akto role is already assigned to another Okta group.")
            return
        }
        if (oktaGroupToAktoUserRoleMap[trimmed]) {
            func.setToast(true, true, "This Okta group is already mapped. Remove it first to change.")
            return
        }
        const nextMap = { ...oktaGroupToAktoUserRoleMap, [trimmed]: newAktoRole }
        setOktaGroupToAktoUserRoleMap(nextMap)
        resetMappingDraft()
    }

    const handleRemoveGroupMapping = (name) => {
        setOktaGroupToAktoUserRoleMap(prev => {
            const next = { ...prev }
            delete next[name]
            return next
        })
    }

    const handleSaveSettings = async () => {
        setSavingSettings(true)
        try {
            let toastMsg = 'Group mappings saved successfully!'
            let resp
            if (!hasSavedManagementToken) {
                const t = editApiToken.trim()
                resp = await settingRequests.saveOktaGroupRoleMapping(
                    oktaGroupToAktoUserRoleMap,
                    t ? { managementApiToken: t } : {}
                )
                if (t) toastMsg = 'Group mappings and API token saved.'
            } else {
                const v = editApiToken
                if (v === TOKEN_EDIT_MASK) {
                    resp = await settingRequests.saveOktaGroupRoleMapping(oktaGroupToAktoUserRoleMap, {})
                } else if (!v.trim()) {
                    resp = await settingRequests.saveOktaGroupRoleMapping(oktaGroupToAktoUserRoleMap, { managementApiToken: null })
                    toastMsg = 'Group mappings saved. Management API token removed.'
                } else {
                    resp = await settingRequests.saveOktaGroupRoleMapping(oktaGroupToAktoUserRoleMap, { managementApiToken: v.trim() })
                    toastMsg = 'Group mappings and API token updated.'
                }
            }
            setManagementApiTokenConfigured(managementTokenConfiguredFromResponse(resp))
            setSavedOktaGroupToAktoUserRoleMap({ ...oktaGroupToAktoUserRoleMap })
            setEditMode(false)
            resetMappingDraft()
            setEditApiToken('')
            func.setToast(true, false, toastMsg)
        } catch (e) {
            func.setToast(true, true, dashboardActionError(e, 'Could not save settings.'))
        } finally {
            setSavingSettings(false)
        }
    }

    const handleCancelEdit = () => {
        setOktaGroupToAktoUserRoleMap({ ...savedOktaGroupToAktoUserRoleMap })
        setEditMode(false)
        setEditApiToken('')
        setOktaGroupNames([])
        resetMappingDraft()
    }

    const handleEditClick = async () => {
        setEditMode(true)
        setSavedOktaGroupToAktoUserRoleMap({ ...oktaGroupToAktoUserRoleMap })
        setEditApiToken(hasSavedManagementToken ? TOKEN_EDIT_MASK : '')
        setOktaGroupNames([])
        setNewGroupName('')
        setNewAktoRole('')
        setAktoRoleText('')
        if (hasSavedManagementToken) {
            setLoadingOktaGroups(true)
            try {
                const resp = await settingRequests.fetchOktaGroups()
                const names = resp?.oktaGroupNames || []
                setOktaGroupNames(Array.isArray(names) ? names : [])
            } catch (e) {
                func.setToast(true, true, dashboardActionError(e, 'Could not fetch Okta groups.'))
            } finally {
                setLoadingOktaGroups(false)
            }
        }
    }

    const handleDelete = async () => {
        await settingRequests.deleteOktaSso()
        func.setToast(true, false, "Okta SSO credentials deleted successfully.")
        setShowDeleteModal(false)
        setComponentType(0)
    }

    const listValues = [
        { title: "Client Id", value: clientId },
        { title: "Authorisation server Id", value: authorizationServerId },
        { title: "Domain name", value: oktaDomain },
        { title: "Management API token", value: managementApiTokenDisplay },
    ]

    useEffect(() => { fetchData() }, [])

    const hasGroupMappings = Object.keys(oktaGroupToAktoUserRoleMap).length > 0

    const mappingRows = Object.entries(oktaGroupToAktoUserRoleMap).map(([name, role], index) => (
        <React.Fragment key={name}>
            {index > 0 && <Divider />}
            <Box paddingBlockStart="2" paddingBlockEnd="2">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Box width="40%">
                        <Text variant="bodyMd" fontWeight="medium">{name}</Text>
                    </Box>
                    <HorizontalStack gap="3" blockAlign="center">
                        <Badge>{getAktoRoleOptionLabel(role)}</Badge>
                        {editMode && (
                            <Button plain destructive icon={DeleteMinor} onClick={() => handleRemoveGroupMapping(name)} accessibilityLabel="Remove mapping" />
                        )}
                    </HorizontalStack>
                </HorizontalStack>
            </Box>
        </React.Fragment>
    ))

    const viewModeContent = (
        <VerticalStack gap="4">
            {hasGroupMappings ? (
                <VerticalStack gap="2">
                    <Text fontWeight="semibold" variant="headingXs" color="subdued">Okta group → Akto role</Text>
                    <Box borderWidth="1" borderColor="border-subdued" borderRadius="2" padding="3">
                        <VerticalStack gap="0">
                            <Box paddingBlockEnd="2">
                                <HorizontalStack align="space-between" blockAlign="center">
                                    <Box width="40%"><Text variant="bodySm" fontWeight="semibold" color="subdued">Okta group</Text></Box>
                                    <Text variant="bodySm" fontWeight="semibold" color="subdued">Akto role</Text>
                                </HorizontalStack>
                            </Box>
                            <Divider />
                            {mappingRows}
                        </VerticalStack>
                    </Box>
                </VerticalStack>
            ) : (
                <Box padding="4" borderWidth="1" borderColor="border-subdued" borderRadius="2">
                    <Text variant="bodyMd" color="subdued" alignment="center">
                        No group mappings yet. Use Edit to add mappings and optional API access.
                    </Text>
                </Box>
            )}
            <HorizontalStack gap="2" blockAlign="center" wrap>
                <Text variant="bodySm" fontWeight="semibold" color="subdued">Management API token</Text>
                <Badge status={hasSavedManagementToken ? 'success' : 'info'}>
                    {hasSavedManagementToken ? 'Configured' : 'Not set'}
                </Badge>
                <Text variant="bodySm" color="subdued">Used only when the access token has no groups claim.</Text>
            </HorizontalStack>
        </VerticalStack>
    )

    const usedRolesForAdd = Object.values(oktaGroupToAktoUserRoleMap)
    const availableRoleOptions = AKTO_ROLE_OPTIONS.filter((r) => !usedRolesForAdd.includes(r.value))
    const roleAutocompleteQuery = (aktoRoleText || '').trim().toLowerCase()
    const roleAutocompleteOptions = availableRoleOptions
        .filter((r) => !roleAutocompleteQuery || r.label.toLowerCase().includes(roleAutocompleteQuery) || r.value.toLowerCase().includes(roleAutocompleteQuery))
        .map((r) => ({ label: r.label, value: r.value }))

    const editModeContent = (
        <VerticalStack gap="4">
            <Text fontWeight="semibold" variant="headingXs">Group → role mappings</Text>
            {hasGroupMappings ? (
                <Box borderWidth="1" borderColor="border-subdued" borderRadius="2" padding="3">
                    <VerticalStack gap="0">{mappingRows}</VerticalStack>
                </Box>
            ) : (
                <Text variant="bodySm" color="subdued">
                    Add mappings below, or save with only an API token if you use convention-based group names.
                </Text>
            )}
            <Text fontWeight="semibold" variant="headingXs">Add mapping</Text>
            <Text variant="bodySm" color="subdued">
                One-to-one mapping: each Okta group maps to one Akto role, and each role can be used only once.
                Use the exact Okta group name (from the access token or from Okta when using the Management API).
                {hasSavedManagementToken && oktaGroupNames.length > 0 && ' Suggestions are loaded from Okta.'}
            </Text>
            <HorizontalStack gap="3" blockAlign="end" wrap={false}>
                <Box minWidth="200px" width="100%">
                    {hasSavedManagementToken && oktaGroupNames.length > 0 ? (
                        <Autocomplete
                            options={oktaGroupNames
                                .filter((name) => {
                                    const q = (newGroupName || '').trim().toLowerCase()
                                    return !q || name.toLowerCase().includes(q)
                                })
                                .map((name) => ({ label: name, value: name }))}
                            selected={newGroupName && oktaGroupNames.includes(newGroupName) ? [newGroupName] : []}
                            onSelect={(selected) => setNewGroupName(selected.length > 0 ? selected[0] : '')}
                            textField={
                                <Autocomplete.TextField
                                    label="Okta group name"
                                    value={newGroupName}
                                    onChange={setNewGroupName}
                                    placeholder={loadingOktaGroups ? 'Loading groups…' : 'e.g. Akto Admin'}
                                    autoComplete="off"
                                    disabled={loadingOktaGroups}
                                />
                            }
                        />
                    ) : (
                        <TextField
                            label="Okta group name"
                            placeholder={loadingOktaGroups ? 'Loading groups…' : 'e.g. Akto Admin'}
                            value={newGroupName}
                            onChange={setNewGroupName}
                            autoComplete="off"
                            disabled={loadingOktaGroups}
                        />
                    )}
                </Box>
                <Box minWidth="200px" width="100%">
                    <Autocomplete
                        options={roleAutocompleteOptions}
                        selected={newAktoRole && availableRoleOptions.some((r) => r.value === newAktoRole) ? [newAktoRole] : []}
                        onSelect={(selected) => {
                            const v = selected.length > 0 ? selected[0] : ''
                            setNewAktoRole(v)
                            setAktoRoleText(v ? getAktoRoleOptionLabel(v) : '')
                        }}
                        textField={
                            <Autocomplete.TextField
                                label="Akto role"
                                value={aktoRoleText}
                                onChange={(v) => {
                                    setAktoRoleText(v)
                                    const vTrim = v.trim()
                                    const exact = availableRoleOptions.find(
                                        (r) => r.label.toLowerCase() === vTrim.toLowerCase()
                                            || r.value.toLowerCase() === vTrim.toLowerCase()
                                    )
                                    if (exact) setNewAktoRole(exact.value)
                                    else setNewAktoRole('')
                                }}
                                placeholder="Search or select Akto role"
                                autoComplete="off"
                            />
                        }
                    />
                </Box>
                <Box paddingBlockStart="6"><Button onClick={handleAddMapping}>Add</Button></Box>
            </HorizontalStack>
            <Divider />
            <Text fontWeight="semibold" variant="headingXs">Management API token (optional)</Text>
            <Text variant="bodySm" color="subdued">
                Akto uses groups from the access token first. If that claim is empty, a saved SSWS token loads group membership at login (Okta: Security → API → Tokens).
            </Text>
            <TextField
                label="API token"
                type="password"
                value={editApiToken}
                onChange={setEditApiToken}
                autoComplete="new-password"
                name="okta-sso-management-api-token-edit"
                placeholder={hasSavedManagementToken ? undefined : 'Paste SSWS token if needed'}
                helpText={hasSavedManagementToken ? undefined : 'Optional. Paste SSWS token from Okta if users access tokens do not include a groups claim.'}
            />
            <HorizontalStack align="end" gap="2">
                <Button onClick={handleCancelEdit}>Cancel</Button>
                <Button primary loading={savingSettings} onClick={handleSaveSettings}>Save</Button>
            </HorizontalStack>
        </VerticalStack>
    )

    const roleMappingCard = (
        <LegacyCard>
            <LegacyCard.Section>
                <VerticalStack gap="3">
                    <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                        <VerticalStack gap="1">
                            <Text fontWeight="semibold" variant="headingSm">Group mapping &amp; API access</Text>
                            <Text variant="bodyMd" color="subdued">
                                Map Okta groups to Akto roles and optionally set a Management API token. Edit updates everything in one place.
                            </Text>
                        </VerticalStack>
                        {!editMode && (
                            <Box flexShrink={0}>
                                <Button
                                    icon={hasGroupMappings || hasSavedManagementToken ? EditMinor : PlusMinor}
                                    onClick={handleEditClick} primary size="medium"
                                >
                                    {hasGroupMappings || hasSavedManagementToken ? 'Edit' : 'Set up'}
                                </Button>
                            </Box>
                        )}
                    </HorizontalStack>
                </VerticalStack>
            </LegacyCard.Section>
            <Divider />
            <LegacyCard.Section>
                {editMode ? editModeContent : viewModeContent}
            </LegacyCard.Section>
        </LegacyCard>
    )

    const oktaSSOComponent = loading ? <SpinnerCentered /> : (
        <VerticalStack gap="4">
            <LegacyCard title="Okta SSO">
                {componentType === 0 ? (
                    <StepsComponent integrationSteps={integrationSteps} onClickFunc={() => setComponentType(1)} buttonActive={nextButtonActive} />
                ) : componentType === 1 ? (
                    formComponent
                ) : (
                    <Details values={listValues} onClickFunc={() => setShowDeleteModal(true)} />
                )}
            </LegacyCard>
            {componentType === 2 && roleMappingCard}
        </VerticalStack>
    )

    return (
        <>
            <IntegrationsLayout
                title="Okta SSO"
                cardContent={(
                    <VerticalStack gap="2">
                        <Text>Enable login via Okta SSO in your dashboard.</Text>
                        <HorizontalStack gap="1">
                            <Text>Use</Text>
                            <Link>https://app.akto.io/sso-login</Link>
                            <Text>for signing into AKTO dashboard via SSO.</Text>
                        </HorizontalStack>
                    </VerticalStack>
                )}
                component={oktaSSOComponent}
                docsUrl="https://docs.akto.io/sso/okta-oidc"
            />
            <DeleteModal showDeleteModal={showDeleteModal} setShowDeleteModal={setShowDeleteModal} SsoType="Okta" onAction={handleDelete} />
        </>
    )
}

export default OktaIntegration

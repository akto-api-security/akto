import React, { useEffect, useState } from 'react'
import CopyCommand from '../../../components/shared/CopyCommand';
import IntegrationsLayout from './IntegrationsLayout';
import { Badge, Box, Button, Divider, Form, FormLayout, HorizontalStack, LegacyCard, Link, Select, Text, TextField, VerticalStack } from '@shopify/polaris';
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

function managementTokenStatusFromResponse(resp) {
    return resp?.managementApiTokenStatus === 'CONFIGURED' ? 'CONFIGURED' : 'NOT_SET'
}

const AKTO_ROLE_OPTIONS = [
    { label: 'Admin', value: 'ADMIN' },
    { label: 'Security Engineer', value: 'MEMBER' },
    { label: 'Developer', value: 'DEVELOPER' },
    { label: 'Guest', value: 'GUEST' },
]

function OktaIntegration() {
    const hostname = window.location.origin

    const [componentType, setComponentType] = useState(0)
    const [loading, setLoading] = useState(false)
    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [setupApiToken, setSetupApiToken] = useState('')
    /** CONFIGURED | NOT_SET — from server (stored apiToken); single source of truth. */
    const [managementApiTokenStatus, setManagementApiTokenStatus] = useState('NOT_SET')
    const hasSavedManagementToken = managementApiTokenStatus === 'CONFIGURED'
    const managementApiTokenDisplay = hasSavedManagementToken
        ? 'Configured (SSWS token stored — not shown)'
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
    const [newAktoRole, setNewAktoRole] = useState('MEMBER')
    const [savedOktaGroupToAktoUserRoleMap, setSavedOktaGroupToAktoUserRoleMap] = useState({})

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
        setNewAktoRole('MEMBER')
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
        setManagementApiTokenStatus(setupApiToken.trim() ? 'CONFIGURED' : 'NOT_SET')
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
                setManagementApiTokenStatus(managementTokenStatusFromResponse(resp))
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
        setOktaGroupToAktoUserRoleMap(prev => ({ ...prev, [trimmed]: newAktoRole }))
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
                resp = await settingRequests.saveOktaGroupRoleMapping(oktaGroupToAktoUserRoleMap, t ? { managementApiToken: t } : {})
                if (t) toastMsg = 'Group mappings and API token saved.'
            } else {
                const v = editApiToken
                if (v === TOKEN_EDIT_MASK) {
                    resp = await settingRequests.saveOktaGroupRoleMapping(oktaGroupToAktoUserRoleMap, {})
                } else if (!v.trim()) {
                    resp = await settingRequests.saveOktaGroupRoleMapping(oktaGroupToAktoUserRoleMap, { clearManagementApiToken: true })
                    toastMsg = 'Group mappings saved. Management API token removed.'
                } else {
                    if (v.trim().length < 20) {
                        func.setToast(true, true, 'Paste the full SSWS token from Okta, or delete all characters to remove the saved token.')
                        setSavingSettings(false)
                        return
                    }
                    resp = await settingRequests.saveOktaGroupRoleMapping(oktaGroupToAktoUserRoleMap, { managementApiToken: v.trim() })
                    toastMsg = 'Group mappings and API token updated.'
                }
            }
            setManagementApiTokenStatus(managementTokenStatusFromResponse(resp))
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
        resetMappingDraft()
    }

    const handleEditClick = () => {
        setEditMode(true)
        setSavedOktaGroupToAktoUserRoleMap({ ...oktaGroupToAktoUserRoleMap })
        setEditApiToken(hasSavedManagementToken ? TOKEN_EDIT_MASK : '')
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

    const getAktoRoleLabel = (role) => AKTO_ROLE_OPTIONS.find(r => r.value === role)?.label || role
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
                        <Badge>{getAktoRoleLabel(role)}</Badge>
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
                Use the exact Okta group name (from the access token or from Okta when using the Management API).
            </Text>
            <HorizontalStack gap="3" blockAlign="end" wrap={false}>
                <Box minWidth="200px" width="100%">
                    <TextField label="Okta group name" placeholder="e.g. Akto Admin" value={newGroupName} onChange={setNewGroupName} autoComplete="off" />
                </Box>
                <Box minWidth="200px" width="100%">
                    <Select label="Akto role" options={AKTO_ROLE_OPTIONS} value={newAktoRole} onChange={setNewAktoRole} />
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
                helpText={hasSavedManagementToken ? undefined : 'Optional. Paste SSWS token from Okta if users’ access tokens do not include a groups claim.'}
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

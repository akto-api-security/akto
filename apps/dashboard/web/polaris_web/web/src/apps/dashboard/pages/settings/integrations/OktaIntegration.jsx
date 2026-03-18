import React, { useEffect, useState } from 'react'
import CopyCommand from '../../../components/shared/CopyCommand';
import IntegrationsLayout from './IntegrationsLayout';
import { Badge, Box, Button, Divider, Form, FormLayout, HorizontalStack, LegacyCard, Link, Tabs, Text, TextField, VerticalStack } from '@shopify/polaris';
import { DeleteMinor, EditMinor, RefreshMajor } from '@shopify/polaris-icons';
import func from "@/util/func"
import settingRequests from '../api';
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import StepsComponent from './components/StepsComponent';
import Details from './components/Details';
import DeleteModal from './components/DeleteModal';
import DropdownSearch from '../../../components/shared/DropdownSearch';

function OktaIntegration() {

    const location = window.location ;
    const hostname = location.origin;

    const [componentType, setComponentType] = useState(0) ;
    const [loading, setLoading] = useState(false)

    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [oktaDomain, setOktaDomain] = useState('')
    const [authorizationServerId, setAuthorizationServerId] = useState('')
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [nextButtonActive,setNextButtonActive] = useState(true)

    const [groupRoleMapping, setGroupRoleMapping] = useState({})
    const [oktaRoleMapping, setOktaRoleMapping] = useState({})
    const [editMode, setEditMode] = useState(false)
    const [selectedTab, setSelectedTab] = useState(0)
    const [newSourceName, setNewSourceName] = useState('')
    const [newAktoRole, setNewAktoRole] = useState('MEMBER')
    const [oktaGroups, setOktaGroups] = useState([])
    const [syncing, setSyncing] = useState(false)
    const [savedGroupMapping, setSavedGroupMapping] = useState({})
    const [savedRoleMapping, setSavedRoleMapping] = useState({})

    const aktoRoleOptions = [
        { label: 'Admin', value: 'ADMIN' },
        { label: 'Security Engineer', value: 'MEMBER' },
        { label: 'Developer', value: 'DEVELOPER' },
        { label: 'Guest', value: 'GUEST' },
    ]

    const oktaAdminRoles = [
        { label: 'Super Administrator', value: 'SUPER_ADMIN' },
        { label: 'Organization Administrator', value: 'ORG_ADMIN' },
        { label: 'Application Administrator', value: 'APP_ADMIN' },
        { label: 'User Administrator', value: 'USER_ADMIN' },
        { label: 'Help Desk Administrator', value: 'HELP_DESK_ADMIN' },
        { label: 'Read Only Administrator', value: 'READ_ONLY_ADMIN' },
        { label: 'API Access Management Admin', value: 'API_ACCESS_MANAGEMENT_ADMIN' },
        { label: 'Report Administrator', value: 'REPORT_ADMIN' },
        { label: 'Group Membership Admin', value: 'GROUP_MEMBERSHIP_ADMIN' },
    ]

    const redirectUri = hostname + "/authorization-code/callback"
    const initiateLoginUri = hostname + "/okta-initiate-login?accountId=" + window.ACTIVE_ACCOUNT

    const integrationSteps = [
        {
            text: "Go to your Okta admin console. Go inside 'Applications' tab and click on 'Create App Integration' button.",
        },
        {
            text: "In 'Sign-in Method', choose 'OIDC - OpenID Connect' and in 'Application type', choose 'Web Application'.",
        },
        {
            text: "In 'App integration name' field, fill 'Akto'.",
        },
        {
            text: "In 'Sign-in redirect URIs' field, fill the below URL below.",
            component: <CopyCommand command={redirectUri} />
        },
        {
            text: "In 'Initiate login URI' field, fill the below URL below. (Required only if you are using 'Login initiated by Okta' option)",
            component: <CopyCommand command={initiateLoginUri} />
        },
        {
            text: "In 'Assignments' choose the access you required and then click on 'Save'."
        },
        {
            text: "Copy the 'CLIENT_ID' and 'CLIENT_SECRET'."
        }
    ]

    const handleSubmit = async() => {
        if(clientId.length > 0 && clientSecret.length > 0 && oktaDomain.length > 0){
            await settingRequests.addOktaSso(clientId,clientSecret, authorizationServerId, oktaDomain, redirectUri)
            func.setToast(true, false, "Okta SSO fields saved successfully!")
            setComponentType(2)
        }else{
            func.setToast(true, true, "Fill all fields")
        }
    }

    const formComponent = (
        <LegacyCard.Section title="Fill details">
            <Form onSubmit={handleSubmit}>
                <FormLayout>
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Client ID of Okta's Application</Text>} 
                                placeholder='Enter your client Id'
                                onChange={setClientId}
                                value={clientId}
                    />
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Client Secret of Okta's Application</Text>} 
                                placeholder='Enter your client secret'
                                onChange={setClientSecret}
                                value={clientSecret}
                    />
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Authorization server Id of Okta's Application</Text>} 
                                placeholder='Enter your authorization server Id'
                                onChange={setAuthorizationServerId}
                                value={authorizationServerId}
                    />
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Domain name of Okta's Application</Text>} 
                                placeholder="Enter the domain name of your Okta console"
                                onChange={setOktaDomain}
                                value={oktaDomain}
                    />
                    <HorizontalStack align="end">
                        <Button submit primary size="medium">Submit</Button>
                    </HorizontalStack>
                </FormLayout>
            </Form>
        </LegacyCard.Section>
    )

    const fetchData = async() => {
        setLoading(true)
        try {
            await settingRequests.fetchOktaSso().then((resp) => {
                if(resp.clientId !== null && resp.clientId.length > 0){
                    setClientId(resp.clientId)
                    setAuthorizationServerId(resp.authorisationServerId)
                    setOktaDomain(resp.oktaDomain)
                    const grpMap = resp.groupRoleMapping || {}
                    const roleMap = resp.oktaRoleMapping || {}
                    setGroupRoleMapping(grpMap)
                    setOktaRoleMapping(roleMap)
                    setSavedGroupMapping(grpMap)
                    setSavedRoleMapping(roleMap)
                    if (Object.keys(roleMap).length > 0) setSelectedTab(1)
                    setComponentType(2)
                }
            })
            setLoading(false)
        } catch (error) {
            setNextButtonActive(false)
            setLoading(false)
        }
    }

    const mappingMode = selectedTab === 0 ? 'group' : 'role'

    const handleAddMapping = () => {
        const trimmed = newSourceName.trim()
        if (!trimmed) {
            func.setToast(true, true, (mappingMode === 'group' ? 'Group' : 'Role') + " name cannot be empty")
            return
        }
        if (mappingMode === 'group') {
            setGroupRoleMapping(prev => ({ ...prev, [trimmed]: newAktoRole }))
        } else {
            setOktaRoleMapping(prev => ({ ...prev, [trimmed]: newAktoRole }))
        }
        setNewSourceName('')
        setNewAktoRole('MEMBER')
    }

    const handleRemoveGroupMapping = (name) => {
        setGroupRoleMapping(prev => {
            const updated = { ...prev }
            delete updated[name]
            return updated
        })
    }

    const handleRemoveRoleMapping = (name) => {
        setOktaRoleMapping(prev => {
            const updated = { ...prev }
            delete updated[name]
            return updated
        })
    }

    const handleSaveMapping = async() => {
        const isGroupBased = selectedTab === 0
        const mappingType = isGroupBased ? 'GROUP' : 'ROLE'
        const activeMapping = isGroupBased ? groupRoleMapping : oktaRoleMapping
        await settingRequests.saveOktaGroupRoleMapping(mappingType, activeMapping)
        if (isGroupBased) {
            setSavedGroupMapping({ ...groupRoleMapping })
            setSavedRoleMapping({})
            setOktaRoleMapping({})
        } else {
            setSavedRoleMapping({ ...oktaRoleMapping })
            setSavedGroupMapping({})
            setGroupRoleMapping({})
        }
        setEditMode(false)
        func.setToast(true, false, "Role mappings saved successfully!")
    }

    const handleCancelEdit = () => {
        setGroupRoleMapping({ ...savedGroupMapping })
        setOktaRoleMapping({ ...savedRoleMapping })
        setEditMode(false)
        setNewSourceName('')
        setNewAktoRole('MEMBER')
    }

    const syncOktaData = async() => {
        setSyncing(true)
        try {
            const resp = await settingRequests.fetchOktaGroups()
            if (resp.oktaGroups) {
                setOktaGroups(resp.oktaGroups)
                func.setToast(true, false, "Synced " + resp.oktaGroups.length + " groups from Okta")
            }
        } catch (error) {
            func.setToast(true, true, "Failed to sync. Ensure API token is configured.")
        }
        setSyncing(false)
    }

    const handleEditClick = async() => {
        setEditMode(true)
        setSavedGroupMapping({ ...groupRoleMapping })
        setSavedRoleMapping({ ...oktaRoleMapping })
        await syncOktaData()
    }

    const handleDelete = async() => {
        await settingRequests.deleteOktaSso()
        func.setToast(true,false, "Okta SSO credentials deleted successfully.")
        setShowDeleteModal(false)
        setComponentType(0)
    }
    const listValues = [
        {
            title: "Client Id",
            value: clientId
        },
        {
            title: "Authorisation server Id",
            value: authorizationServerId
        },
        {
            title: "Domain name",
            value: oktaDomain
        }
    ]

    useEffect(()=> {
        fetchData()
    },[])

    const cardContent = "Enable login via Okta SSO in your dashboard."

    const useCardContent = (
        <VerticalStack gap={"2"}>
            <Text>{cardContent}</Text>
            <HorizontalStack gap={"1"}>
                <Text>Use</Text>
                <Link>https://app.akto.io/sso-login</Link>
                <Text>for signing into AKTO dashboard via SSO.</Text>
            </HorizontalStack>
        </VerticalStack>
    )

    
    const unmappedGroups = oktaGroups.filter(g => !groupRoleMapping[g])
    const unmappedRoles = oktaAdminRoles.filter(r => !oktaRoleMapping[r.value])

    const groupOptions = unmappedGroups.map(g => ({ label: g, value: g }))
    const roleOptions = unmappedRoles.map(r => ({ label: r.label, value: r.value }))

    const currentMapping = mappingMode === 'group' ? groupRoleMapping : oktaRoleMapping
    const handleRemoveCurrent = mappingMode === 'group' ? handleRemoveGroupMapping : handleRemoveRoleMapping
    const hasCurrentMappings = Object.keys(currentMapping).length > 0
    const hasGroupMappings = Object.keys(groupRoleMapping).length > 0
    const hasRoleMappings = Object.keys(oktaRoleMapping).length > 0

    const mappingTabs = [
        { id: 'group-mapping', content: 'Map by Group' },
        { id: 'role-mapping', content: 'Map by Role' },
    ]

    const getLabelForRole = (name) => oktaAdminRoles.find(r => r.value === name)?.label || name
    const getAktoRoleLabel = (role) => aktoRoleOptions.find(r => r.value === role)?.label || role

    const renderMappingRows = (mapping, labelFn, removeFn) => (
        Object.entries(mapping).map(([name, role], index) => (
            <React.Fragment key={name}>
                {index > 0 && <Divider />}
                <Box paddingBlockStart="2" paddingBlockEnd="2">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Box width="40%">
                            <Text variant="bodyMd" fontWeight="medium">{labelFn(name)}</Text>
                        </Box>
                        <HorizontalStack gap="3" blockAlign="center">
                            <Badge>{getAktoRoleLabel(role)}</Badge>
                            {editMode && (
                                <Button plain destructive icon={DeleteMinor} onClick={() => removeFn(name)} accessibilityLabel="Remove mapping" />
                            )}
                        </HorizontalStack>
                    </HorizontalStack>
                </Box>
            </React.Fragment>
        ))
    )

    const renderMappingSection = (title, sourceLabel, mapping, labelFn, removeFn) => {
        if (Object.keys(mapping).length === 0) return null
        return (
            <VerticalStack gap="2">
                <Text fontWeight="semibold" variant="headingXs" color="subdued">{title}</Text>
                <Box borderWidth="1" borderColor="border-subdued" borderRadius="2" padding="3">
                    <VerticalStack gap="0">
                        <Box paddingBlockEnd="2">
                            <HorizontalStack align="space-between" blockAlign="center">
                                <Box width="40%">
                                    <Text variant="bodySm" fontWeight="semibold" color="subdued">{sourceLabel}</Text>
                                </Box>
                                <Text variant="bodySm" fontWeight="semibold" color="subdued">Akto Role</Text>
                            </HorizontalStack>
                        </Box>
                        <Divider />
                        {renderMappingRows(mapping, labelFn, removeFn)}
                    </VerticalStack>
                </Box>
            </VerticalStack>
        )
    }

    const viewModeContent = (
        <VerticalStack gap="4">
            {renderMappingSection("Group Mappings", "Okta Group", groupRoleMapping, (name) => name, handleRemoveGroupMapping)}
            {renderMappingSection("Role Mappings", "Okta Role", oktaRoleMapping, getLabelForRole, handleRemoveRoleMapping)}
            {!hasGroupMappings && !hasRoleMappings && (
                <Box padding="4" borderWidth="1" borderColor="border-subdued" borderRadius="2">
                    <Text variant="bodyMd" color="subdued" alignment="center">
                        No custom mappings configured. The default convention (akto_admin, akto_security_engineer, akto_developer, akto_guest) will be used.
                    </Text>
                </Box>
            )}
        </VerticalStack>
    )

    const editModeContent = (
        <VerticalStack gap="4">
            <Tabs tabs={mappingTabs} selected={selectedTab} onSelect={(idx) => { setSelectedTab(idx); setNewSourceName(''); }} fitted>
                <Box paddingBlockStart="4">
                    <VerticalStack gap="4">
                        {hasCurrentMappings && (
                            <Box borderWidth="1" borderColor="border-subdued" borderRadius="2" padding="3">
                                <VerticalStack gap="0">
                                    {renderMappingRows(
                                        currentMapping,
                                        mappingMode === 'role' ? getLabelForRole : (name) => name,
                                        handleRemoveCurrent
                                    )}
                                </VerticalStack>
                            </Box>
                        )}

                        <Divider />
                        <Text fontWeight="semibold" variant="headingXs">Add new mapping</Text>
                        <HorizontalStack gap="3" blockAlign="end">
                            <Box width="100%" maxWidth="45%">
                                <DropdownSearch
                                    id={mappingMode === 'group' ? 'okta-group-search' : 'okta-role-search'}
                                    label={mappingMode === 'group' ? 'Okta Group' : 'Okta Role'}
                                    placeholder={mappingMode === 'group' ? 'Search groups...' : 'Search roles...'}
                                    optionsList={mappingMode === 'group' ? groupOptions : roleOptions}
                                    setSelected={setNewSourceName}
                                    value={newSourceName}
                                    preSelected={newSourceName ? [newSourceName] : []}
                                />
                            </Box>
                            <Box width="100%" maxWidth="45%">
                                <DropdownSearch
                                    id="akto-role-search"
                                    label="Akto Role"
                                    placeholder="Search roles..."
                                    optionsList={aktoRoleOptions}
                                    setSelected={setNewAktoRole}
                                    value={newAktoRole}
                                    preSelected={[newAktoRole]}
                                />
                            </Box>
                            <Box paddingBlockStart="6">
                                <Button onClick={handleAddMapping}>Add</Button>
                            </Box>
                        </HorizontalStack>

                        <Box paddingBlockStart="2">
                            <HorizontalStack align="end" gap="2">
                                <Button onClick={handleCancelEdit}>Cancel</Button>
                                <Button primary onClick={handleSaveMapping}>Save Mappings</Button>
                            </HorizontalStack>
                        </Box>
                    </VerticalStack>
                </Box>
            </Tabs>
        </VerticalStack>
    )

    const roleMappingCard = (
        <LegacyCard>
            <LegacyCard.Section>
                <HorizontalStack align="space-between" blockAlign="center">
                    <VerticalStack gap="1">
                        <Text fontWeight="semibold" variant="headingSm">Role Mapping</Text>
                        <Text variant="bodyMd" color="subdued">
                            Map Okta groups or roles to Akto roles. If no mapping is configured, the default convention is used.
                        </Text>
                    </VerticalStack>
                    {editMode ? (
                        <Button icon={RefreshMajor} onClick={syncOktaData} loading={syncing} size="slim">
                            Sync from Okta
                        </Button>
                    ) : (
                        <Button icon={EditMinor} onClick={handleEditClick} size="slim" primary>
                            Edit
                        </Button>
                    )}
                </HorizontalStack>
            </LegacyCard.Section>
            <Divider />
            <LegacyCard.Section>
                {editMode ? editModeContent : viewModeContent}
            </LegacyCard.Section>
        </LegacyCard>
    )

    const oktaSSOComponent = (
        loading ? <SpinnerCentered /> :
        <VerticalStack gap="4">
            <LegacyCard title="Okta SSO">
                {componentType === 0 ? <StepsComponent integrationSteps={integrationSteps} onClickFunc={()=> setComponentType(1)} buttonActive={nextButtonActive}/> 
                : componentType === 1 ? formComponent : <Details values={listValues} onClickFunc={() => setShowDeleteModal(true)} /> }
            </LegacyCard>
            {componentType === 2 && roleMappingCard}
        </VerticalStack>
    )

    return (
        <>
            <IntegrationsLayout title="Okta SSO" cardContent={useCardContent} component={oktaSSOComponent} docsUrl="https://docs.akto.io/sso/okta-oidc"/>
            <DeleteModal showDeleteModal={showDeleteModal} setShowDeleteModal={setShowDeleteModal} SsoType={"Okta"} onAction={handleDelete} />
        </>
    )
}

export default OktaIntegration
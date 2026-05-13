import { Box, Button, Divider, HorizontalStack, LegacyCard, Spinner, Text, TextField, Tooltip, VerticalStack } from '@shopify/polaris'
import { RefreshMajor } from '@shopify/polaris-icons'
import { useEffect, useState } from 'react'
import { ToggleComponent } from '../about/About'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import settingRequests from '../api'
import func from '@/util/func'

function EndpointShieldSettings() {
    const [manifestUrl, setManifestUrl]     = useState('')
    const [savedManifestUrl, setSavedManifestUrl] = useState('')
    const [autoUpdate, setAutoUpdate]       = useState(true)
    const [targetVersion, setTargetVersion] = useState('')
    const [latestVersion, setLatestVersion] = useState('')
    const [checkedAgo, setCheckedAgo]       = useState('')
    const [checkedAt, setCheckedAt]         = useState('')
    const [refreshing, setRefreshing]       = useState(false)
    const [saving, setSaving]               = useState(false)

    const manifestUrlDirty = manifestUrl !== savedManifestUrl

    const isAdmin = window.USER_ROLE === 'ADMIN'

    function validateManifestUrl(url) {
        if (!url || !url.trim()) return 'Manifest URL is required.'
        try {
            const parsed = new URL(url.trim())
            if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
                return 'URL must start with http:// or https://'
            }
            return null
        } catch {
            return 'Please enter a valid URL.'
        }
    }

    const manifestUrlError = validateManifestUrl(manifestUrl)

    useEffect(() => {
        settingRequests.fetchEndpointShieldSettings().then(res => {
            const s = res?.endpointShieldSettings || {}
            populate(s)
        })
    }, [])

    function populate(s) {
        setManifestUrl(s.manifestUrl || '')
        setSavedManifestUrl(s.manifestUrl || '')
        setAutoUpdate(s.autoUpdateEnabled ?? true)
        setTargetVersion(s.targetVersion || '')
        setLatestVersion(s.latestVersion || '')
        if (s.latestVersionFetchedAt) {
            setCheckedAgo(func.prettifyEpoch(Math.floor(s.latestVersionFetchedAt / 1000)))
            setCheckedAt(new Date(s.latestVersionFetchedAt).toLocaleString())
        }
    }

    async function handleSave() {
        if (manifestUrlError) return
        setSaving(true)
        const urlChanged = manifestUrl !== savedManifestUrl
        await settingRequests.saveEndpointShieldSettings({
            autoUpdateEnabled: autoUpdate,
            manifestUrl,
            targetVersion: targetVersion || null
        })
        setSavedManifestUrl(manifestUrl)
        if (urlChanged) {
            setLatestVersion('')
            setCheckedAgo('')
            setCheckedAt('')
        }
        func.setToast(true, false, "Endpoint Shield settings saved.")
        setSaving(false)
    }

    async function handleRefresh() {
        if (!manifestUrl) {
            func.setToast(true, true, "Please save a Manifest URL first.")
            return
        }
        setRefreshing(true)
        const res = await settingRequests.refreshEndpointShieldLatestVersion()
        setRefreshing(false)
        if (res?.endpointShieldSettings) {
            populate(res.endpointShieldSettings)
            func.setToast(true, false, "Latest version refreshed.")
        } else {
            func.setToast(true, true, "Could not fetch version. Please check the Manifest URL.")
        }
    }

    const policyCard = (
        <LegacyCard
            key="endpoint-shield-policy"
            title={
                <Box paddingBlockEnd="2">
                    <Text variant="headingMd">Update Policy</Text>
                    <Box paddingBlockStart="1">
                        <Text variant="bodyMd" color="subdued">
                            Configure how Endpoint Shield agents receive updates across all devices.
                        </Text>
                    </Box>
                </Box>
            }
        >
            <Divider />
            <LegacyCard.Section>
                <VerticalStack gap="5">

                    <TextField
                        label="Manifest URL"
                        value={manifestUrl}
                        onChange={setManifestUrl}
                        placeholder="https://releases.example.com/endpoint-shield/manifest.json"
                        disabled={!isAdmin}
                        error={manifestUrlDirty ? manifestUrlError : null}
                        helpText={manifestUrlDirty && manifestUrlError ? null : "JSON endpoint that exposes a top-level version field."}
                    />

                    <VerticalStack gap="1">
                        <Text color="subdued">Latest version available</Text>
                        <HorizontalStack gap="3" align="start" blockAlign="center">
                            <Text fontWeight="semibold">{latestVersion || 'N/A'}</Text>
                            {checkedAgo && (
                                <Tooltip content={checkedAt} dismissOnMouseOut>
                                    <Text color="subdued" variant="bodySm">checked {checkedAgo}</Text>
                                </Tooltip>
                            )}
                            {refreshing
                                ? <Spinner size="small" />
                                : (
                                    <Tooltip
                                        content={manifestUrlDirty ? 'Save the Manifest URL before refreshing' : 'Fetch latest version from manifest'}
                                        dismissOnMouseOut
                                    >
                                        <Button
                                            plain
                                            icon={RefreshMajor}
                                            onClick={handleRefresh}
                                            disabled={manifestUrlDirty}
                                        >
                                            Refresh
                                        </Button>
                                    </Tooltip>
                                )
                            }
                        </HorizontalStack>
                    </VerticalStack>

                    <ToggleComponent
                        text="Enable Auto-Update"
                        initial={autoUpdate}
                        onToggle={setAutoUpdate}
                        disabled={!isAdmin}
                    />

                    {!autoUpdate && (
                        <TextField
                            label="Force agents to version"
                            helpText="Leave blank to let agents stay on their installed version. Set a version to push all agents to upgrade or downgrade to it."
                            value={targetVersion}
                            onChange={setTargetVersion}
                            placeholder="e.g. 1.3.0"
                            disabled={!isAdmin}
                        />
                    )}

                    {isAdmin && (
                        <Box width="80px">
                            <Button primary onClick={handleSave} loading={saving} disabled={!!manifestUrlError}>Save</Button>
                        </Box>
                    )}

                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )

    return (
        <PageWithMultipleCards
            title={<Text variant="headingLg">Endpoint Shield</Text>}
            isFirstPage={true}
            components={[policyCard]}
        />
    )
}

export default EndpointShieldSettings

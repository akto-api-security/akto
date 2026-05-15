import { Box, Button, Divider, HorizontalStack, LegacyCard, Spinner, Tabs, Text, TextField, Tooltip, VerticalStack } from '@shopify/polaris'
import { RefreshMajor } from '@shopify/polaris-icons'
import { useEffect, useState } from 'react'
import { ToggleComponent } from '../about/About'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import settingRequests from '../api'
import func from '@/util/func'

const PLATFORMS = [
    { key: 'macos_mdm',      label: 'macOS - MDM (JAMF)' },
    { key: 'windows_mdm',    label: 'Windows - MDM' },
    { key: 'macos_direct',   label: 'macOS - Direct Install' },
    { key: 'windows_direct', label: 'Windows - Direct Install' },
]

const EMPTY_CONFIG = {
    manifestUrl: '',
    savedManifestUrl: '',
    autoUpdateEnabled: true,
    targetVersion: '',
    latestVersion: '',
    checkedAgo: '',
    checkedAt: '',
    refreshing: false,
    saving: false,
}

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

function fromServerConfig(cfg) {
    if (!cfg) return { ...EMPTY_CONFIG }
    const fetchedAt = cfg.latestVersionFetchedAt || 0
    return {
        manifestUrl:       cfg.manifestUrl || '',
        savedManifestUrl:  cfg.manifestUrl || '',
        autoUpdateEnabled: cfg.autoUpdateEnabled ?? true,
        targetVersion:     cfg.targetVersion || '',
        latestVersion:     cfg.latestVersion || '',
        checkedAgo: fetchedAt ? func.prettifyEpoch(Math.floor(fetchedAt / 1000)) : '',
        checkedAt:  fetchedAt ? new Date(fetchedAt).toLocaleString() : '',
        refreshing: false,
        saving:     false,
    }
}

function PlatformPanel({ platformKey, config, onChange, isAdmin }) {
    const { manifestUrl, savedManifestUrl, autoUpdateEnabled, targetVersion,
            latestVersion, checkedAgo, checkedAt, refreshing, saving } = config

    const manifestUrlDirty = manifestUrl !== savedManifestUrl
    const manifestUrlError = validateManifestUrl(manifestUrl)

    function update(patch) {
        onChange(platformKey, patch)
    }

    async function handleSave() {
        if (manifestUrlError) return
        update({ saving: true })
        const urlChanged = manifestUrl !== savedManifestUrl
        await settingRequests.saveEndpointShieldSettings(platformKey, {
            manifestUrl,
            autoUpdateEnabled,
            targetVersion: targetVersion || null,
        })
        update({
            saving: false,
            savedManifestUrl: manifestUrl,
            ...(urlChanged ? { latestVersion: '', checkedAgo: '', checkedAt: '' } : {}),
        })
        func.setToast(true, false, "Settings saved.")
    }

    async function handleRefresh() {
        update({ refreshing: true })
        try {
            const res = await settingRequests.refreshEndpointShieldLatestVersion(platformKey)
            const updated = res?.endpointShieldSettings?.platforms?.[platformKey]
            if (updated) {
                update({
                    refreshing: false,
                    latestVersion: updated.latestVersion || '',
                    checkedAgo: updated.latestVersionFetchedAt
                        ? func.prettifyEpoch(Math.floor(updated.latestVersionFetchedAt / 1000))
                        : '',
                    checkedAt: updated.latestVersionFetchedAt
                        ? new Date(updated.latestVersionFetchedAt).toLocaleString()
                        : '',
                })
                func.setToast(true, false, "Latest version refreshed.")
            } else {
                update({ refreshing: false })
                func.setToast(true, true, "Could not fetch version. Please check the Manifest URL.")
            }
        } catch {
            update({ refreshing: false })
            func.setToast(true, true, "Could not fetch version. Please check the Manifest URL.")
        }
    }

    const refreshDisabled = manifestUrlDirty || !!manifestUrlError
    const refreshTooltip  = manifestUrlDirty
        ? 'Save the Manifest URL before refreshing'
        : manifestUrlError
            ? 'Enter a valid Manifest URL first'
            : 'Fetch latest version from manifest'

    return (
        <LegacyCard.Section>
            <VerticalStack gap="5">
                <TextField
                    label="Manifest URL"
                    value={manifestUrl}
                    onChange={val => update({ manifestUrl: val })}
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
                                <Tooltip content={refreshTooltip} dismissOnMouseOut>
                                    <Button
                                        plain
                                        icon={RefreshMajor}
                                        onClick={handleRefresh}
                                        disabled={refreshDisabled}
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
                    initial={autoUpdateEnabled}
                    onToggle={val => update({ autoUpdateEnabled: val })}
                    disabled={!isAdmin}
                />

                {!autoUpdateEnabled && (
                    <TextField
                        label="Force agents to version"
                        helpText="Leave blank to let agents stay on their installed version. Set a version to push all agents to upgrade or downgrade to it."
                        value={targetVersion}
                        onChange={val => update({ targetVersion: val })}
                        placeholder="e.g. 1.3.0"
                        disabled={!isAdmin}
                    />
                )}

                {isAdmin && (
                    <Box width="80px">
                        <Button primary onClick={handleSave} loading={saving} disabled={!!manifestUrlError}>
                            Save
                        </Button>
                    </Box>
                )}
            </VerticalStack>
        </LegacyCard.Section>
    )
}

function EndpointShieldSettings() {
    if (!window.USER_NAME?.toLowerCase()?.endsWith("@akto.io")) {
        return null
    }

    const isAdmin = window.USER_ROLE === 'ADMIN'
    const [selectedTab, setSelectedTab] = useState(0)
    const [platforms, setPlatforms] = useState(
        () => Object.fromEntries(PLATFORMS.map(p => [p.key, { ...EMPTY_CONFIG }]))
    )

    function updatePlatform(key, patch) {
        setPlatforms(prev => ({ ...prev, [key]: { ...prev[key], ...patch } }))
    }

    useEffect(() => {
        settingRequests.fetchEndpointShieldSettings().then(res => {
            const serverPlatforms = res?.endpointShieldSettings?.platforms || {}
            setPlatforms(Object.fromEntries(
                PLATFORMS.map(p => [p.key, fromServerConfig(serverPlatforms[p.key])])
            ))
        })
    }, [])

    const tabs = PLATFORMS.map((p, i) => ({ id: p.key, content: p.label, panelID: `panel-${p.key}` }))

    const activeKey = PLATFORMS[selectedTab].key

    const card = (
        <LegacyCard
            key="endpoint-shield-platforms"
            title={<Text variant="headingMd">Update Policy</Text>}
        >
            <Divider />
            <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
            <Divider />
            <PlatformPanel
                key={activeKey}
                platformKey={activeKey}
                config={platforms[activeKey]}
                onChange={updatePlatform}
                isAdmin={isAdmin}
            />
        </LegacyCard>
    )

    return (
        <PageWithMultipleCards
            title={<Text variant="headingLg">Endpoint Shield</Text>}
            isFirstPage={true}
            components={[card]}
        />
    )
}

export default EndpointShieldSettings

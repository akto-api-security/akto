import { Badge, Box, Button, Divider, HorizontalStack, LegacyCard, Select, Spinner, Text, TextField, Tooltip, VerticalStack } from '@shopify/polaris'
import { RefreshMajor } from '@shopify/polaris-icons'
import { useEffect, useState } from 'react'
import { ToggleComponent } from '../about/About'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import LayoutWithTabs from '../../../components/layouts/LayoutWithTabs'
import settingRequests from '../api'
import func from '@/util/func'

const PLATFORMS = [
    { key: 'windows_direct', label: 'Windows - Standalone' },
    { key: 'windows_mdm',    label: 'Windows - MDM' },
    { key: 'macos_direct',   label: 'macOS - Standalone' },
    { key: 'macos_mdm',      label: 'macOS - MDM' },
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
    releases: [],
    newestPublishedVersion: '',
    targetVersionLive: '',
    pinnedToOlder: false,
    fleetCounts: null,
    fleetByVersion: null,
    listing: false,
    deploying: false,
    selectedDeployVersion: '',
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
        ...EMPTY_CONFIG,
        manifestUrl:       cfg.manifestUrl || '',
        savedManifestUrl:  cfg.manifestUrl || '',
        autoUpdateEnabled: cfg.autoUpdateEnabled ?? true,
        targetVersion:     cfg.targetVersion || '',
        latestVersion:     cfg.latestVersion || '',
        checkedAgo: fetchedAt ? func.prettifyEpoch(Math.floor(fetchedAt / 1000)) : '',
        checkedAt:  fetchedAt ? new Date(fetchedAt).toLocaleString() : '',
    }
}

function applyVersionControl(patch, res) {
    const vc = res?.versionControl || {}
    return {
        ...patch,
        releases: res?.releases || [],
        newestPublishedVersion: res?.newestPublishedVersion || vc.newestPublishedVersion || '',
        targetVersionLive: res?.targetVersionLive || vc.targetVersion || '',
        pinnedToOlder: !!vc.pinnedToOlder,
        fleetCounts: vc.fleetCounts || null,
        fleetByVersion: vc.fleetByVersion || null,
        selectedDeployVersion: res?.targetVersionLive || vc.targetVersion || '',
        listing: false,
        deploying: false,
    }
}

function PlatformPanel({ platformKey, config, onChange, isAdmin }) {
    const {
        manifestUrl, savedManifestUrl, autoUpdateEnabled, targetVersion,
        latestVersion, checkedAgo, checkedAt, refreshing, saving,
        releases, newestPublishedVersion, targetVersionLive, pinnedToOlder,
        fleetCounts, listing, deploying, selectedDeployVersion,
    } = config

    const manifestUrlDirty = manifestUrl !== savedManifestUrl
    const manifestUrlError = validateManifestUrl(manifestUrl)

    function update(patch) {
        onChange(platformKey, patch)
    }

    async function loadReleases() {
        update({ listing: true })
        try {
            const res = await settingRequests.listEndpointShieldReleases(platformKey)
            update(applyVersionControl({}, res))
        } catch (e) {
            update({ listing: false })
            func.setToast(true, true, e?.message || 'Could not list releases from S3.')
        }
    }

    useEffect(() => {
        if (!manifestUrlDirty && !manifestUrlError) {
            loadReleases()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [platformKey, savedManifestUrl])

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
        func.setToast(true, false, 'Settings saved.')
        if (urlChanged) loadReleases()
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
                func.setToast(true, false, 'Latest version refreshed.')
                loadReleases()
            } else {
                update({ refreshing: false })
                func.setToast(true, true, 'Could not fetch version. Please check the Manifest URL.')
            }
        } catch {
            update({ refreshing: false })
            func.setToast(true, true, 'Could not fetch version. Please check the Manifest URL.')
        }
    }

    async function handleDeploy() {
        if (!selectedDeployVersion) return
        update({ deploying: true })
        try {
            const res = await settingRequests.deployEndpointShieldVersion(platformKey, selectedDeployVersion)
            update(applyVersionControl({
                latestVersion: selectedDeployVersion,
                targetVersion: selectedDeployVersion,
            }, res))
            func.setToast(true, false, `Deployed ${selectedDeployVersion} to latest.json`)
        } catch (e) {
            update({ deploying: false })
            func.setToast(true, true, e?.message || 'Deploy failed.')
        }
    }

    const refreshDisabled = manifestUrlDirty || !!manifestUrlError
    const refreshTooltip  = manifestUrlDirty
        ? 'Save the Manifest URL before refreshing'
        : manifestUrlError
            ? 'Enter a valid Manifest URL first'
            : 'Fetch latest version from manifest'

    const releaseOptions = (releases || []).map(r => ({
        label: r.version === newestPublishedVersion ? `${r.version} (newest)` : r.version,
        value: r.version,
    }))

    return (
        <LegacyCard.Section>
            <VerticalStack gap="5">
                <TextField
                    label="Manifest URL"
                    value={manifestUrl}
                    onChange={val => update({ manifestUrl: val })}
                    placeholder="https://…/atlas-installers/<accountId>/<type>/latest.json"
                    disabled={!isAdmin}
                    error={manifestUrlDirty ? manifestUrlError : null}
                    helpText={manifestUrlDirty && manifestUrlError ? null : 'Account-scoped S3 feed devices poll for updates.'}
                />

                <VerticalStack gap="2">
                    <Text variant="headingSm">Version visibility</Text>
                    <HorizontalStack gap="4" wrap>
                        <Box>
                            <Text color="subdued">Target (live latest.json)</Text>
                            <HorizontalStack gap="2" blockAlign="center">
                                <Text fontWeight="semibold">{targetVersionLive || latestVersion || 'N/A'}</Text>
                                {pinnedToOlder && <Badge status="attention">pinned to older build</Badge>}
                            </HorizontalStack>
                        </Box>
                        <Box>
                            <Text color="subdued">Newest published</Text>
                            <Text fontWeight="semibold">{newestPublishedVersion || 'N/A'}</Text>
                        </Box>
                        <Box>
                            <Text color="subdued">Cached target</Text>
                            <HorizontalStack gap="2" blockAlign="center">
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
                        </Box>
                    </HorizontalStack>
                </VerticalStack>

                {fleetCounts && (
                    <VerticalStack gap="1">
                        <Text variant="headingSm">Fleet installed</Text>
                        <Text color="subdued">
                            {fleetCounts.total} agents · {fleetCounts.onTarget} on target · {fleetCounts.behind} behind · {fleetCounts.ahead} ahead · {fleetCounts.staleHeartbeat} stale heartbeat
                        </Text>
                    </VerticalStack>
                )}

                <ToggleComponent
                    text="Enable Auto-Update"
                    initial={autoUpdateEnabled}
                    onToggle={val => update({ autoUpdateEnabled: val })}
                    disabled={!isAdmin}
                />

                {isAdmin && (
                    <VerticalStack gap="2">
                        <Text variant="headingSm">Deploy / revert</Text>
                        <Text color="subdued">
                            Rewrites this account&apos;s latest.json to the selected published build. Auto-Update ON promotes newest on publish; OFF keeps the fleet until you deploy.
                        </Text>
                        <HorizontalStack gap="3" blockAlign="end">
                            <Box minWidth="240px">
                                <Select
                                    label="Published version"
                                    options={releaseOptions.length ? releaseOptions : [{ label: 'No releases found', value: '' }]}
                                    value={selectedDeployVersion}
                                    onChange={val => update({ selectedDeployVersion: val })}
                                    disabled={!releaseOptions.length || deploying}
                                />
                            </Box>
                            <Button
                                primary
                                onClick={handleDeploy}
                                loading={deploying}
                                disabled={!selectedDeployVersion || selectedDeployVersion === targetVersionLive}
                            >
                                Deploy
                            </Button>
                            <Button onClick={loadReleases} loading={listing} disabled={manifestUrlDirty}>
                                Reload releases
                            </Button>
                        </HorizontalStack>
                    </VerticalStack>
                )}

                {!autoUpdateEnabled && (
                    <TextField
                        label="Force agents to version (Mongo pin)"
                        helpText="Optional dashboard pin. Fleet installers follow latest.json via Deploy above."
                        value={targetVersion}
                        onChange={val => update({ targetVersion: val })}
                        placeholder="e.g. 1.3.0"
                        disabled={!isAdmin}
                    />
                )}

                {isAdmin && (
                    <>
                        <Divider />
                        <Box width="80px">
                            <Button primary onClick={handleSave} loading={saving} disabled={!!manifestUrlError}>
                                Save
                            </Button>
                        </Box>
                    </>
                )}
            </VerticalStack>
        </LegacyCard.Section>
    )
}

function EndpointShieldSettings() {
    if (!window.USER_NAME?.toLowerCase()?.endsWith('@akto.io')) {
        return null
    }

    const isAdmin = window.USER_ROLE === 'ADMIN'
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

    const tabs = PLATFORMS.map(p => ({
        id: p.key,
        content: p.label,
        component: (
            <PlatformPanel
                platformKey={p.key}
                config={platforms[p.key]}
                onChange={updatePlatform}
                isAdmin={isAdmin}
            />
        )
    }))

    const card = (
        <LegacyCard key="endpoint-shield-platforms">
            <LegacyCard.Section>
                <Text variant="headingMd">Installer Version Control</Text>
                <Text color="subdued">
                    Account-scoped feeds under atlas-installers/&lt;accountId&gt;/&lt;type&gt;/. Deploy rewrites latest.json for that type.
                </Text>
            </LegacyCard.Section>
            <Divider />
            <LayoutWithTabs tabs={tabs} currTab={() => {}} noLoading />
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

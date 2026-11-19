import { Badge, Banner, Box, Button, DataTable, HorizontalStack, LegacyCard, Modal, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import { useEffect, useState } from "react"
import settingRequests from "../api"
import func from "@/util/func"
import EmptyCard from "../../dashboard/new_components/EmptyCard"
import RuntimeTokenField from "../../quick_start/components/RuntimeTokenField"
import { usePermissions } from "@/util/permissions"
import { maskedToken } from "./maskedToken"

const DAY = 24 * 60 * 60
const EXPIRING_SOON_SECONDS = 14 * DAY
const IDLE_SECONDS = 30 * DAY
const MAX_USED_BY_CHIPS = 3
const REVOKE_CONFIRM_TEXT = "REVOKE"
// services re-sync their revoked list on this schedule, so a revoke can take this long to reach all of them
const REVOKE_DELAY = "up to 15 minutes"
const REVOKE_ALL_INFO = `Revoke all works after upgrading modules to the latest version. It revokes every active token, including the legacy tokens listed here; a legacy token that has not connected yet is not listed, so it is not included. Revoked tokens can take ${REVOKE_DELAY} to stop working everywhere.`

const nowSeconds = () => Math.floor(Date.now() / 1000)

const scopeLabel = (scope) => (scope && scope.length > 0)
    ? scope.map(value => value.split('_').map(func.toSentenceCase).join(' ')).join(', ')
    : 'Unscoped'

// REVOKED wins over EXPIRED; expiresAt 0 means the token never expires
function tokenState(token, now) {
    if (token.status === 'REVOKED') return 'REVOKED'
    if (token.expiresAt > 0 && token.expiresAt <= now) return 'EXPIRED'
    return 'ACTIVE'
}

const isLegacy = (token) => token.source === 'DISCOVERED'

function TokensTab() {
    const { canCall } = usePermissions()
    const [tokens, setTokens] = useState([])
    const [loaded, setLoaded] = useState(false)
    const [busy, setBusy] = useState(false)
    const [createModal, setCreateModal] = useState({ open: false, key: 0 })
    const [revokeTarget, setRevokeTarget] = useState(null)
    const [revokeReason, setRevokeReason] = useState('')
    const [revokeAllOpen, setRevokeAllOpen] = useState(false)
    const [confirmText, setConfirmText] = useState('')

    const fetchTokens = async () => {
        try {
            const resp = await settingRequests.fetchAuthTokens()
            const list = resp?.authTokens || []
            setTokens([...list].sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0)))
        } catch (e) {
            // the request layer already toasts the error
        } finally {
            setLoaded(true)
        }
    }

    useEffect(() => {
        fetchTokens()
    }, [])

    const now = nowSeconds()
    const activeTokens = tokens.filter(token => tokenState(token, now) === 'ACTIVE')
    const activeLegacyCount = activeTokens.filter(isLegacy).length
    const expiringSoon = activeTokens.filter(token => token.expiresAt > 0 && token.expiresAt - now <= EXPIRING_SOON_SECONDS)
    const isIdle = (token) => now - (token.lastUsedAt || token.createdAt || 0) > IDLE_SECONDS
    const idleTokens = activeTokens.filter(isIdle)

    const openCreateModal = () => setCreateModal(prev => ({ open: true, key: prev.key + 1 }))
    const closeCreateModal = () => setCreateModal(prev => ({ ...prev, open: false }))

    const closeRevokeModal = () => {
        setRevokeTarget(null)
        setRevokeReason('')
    }

    const closeRevokeAllModal = () => {
        setRevokeAllOpen(false)
        setConfirmText('')
        setRevokeReason('')
    }

    const handleRevoke = async () => {
        if (!revokeTarget) return
        setBusy(true)
        try {
            await settingRequests.revokeAuthToken(revokeTarget.id, revokeReason.trim())
            func.setToast(true, false, `Token revoked. It can take ${REVOKE_DELAY} to stop working everywhere.`)
            closeRevokeModal()
            await fetchTokens()
        } catch (e) {
            // the request layer already toasts the error
        } finally {
            setBusy(false)
        }
    }

    const handleRevokeAll = async () => {
        setBusy(true)
        try {
            const resp = await settingRequests.revokeAllAuthTokens(revokeReason.trim())
            func.setToast(true, false, `Revoked ${resp?.revokedCount ?? 0} token(s). They can take ${REVOKE_DELAY} to stop working everywhere.`)
            closeRevokeAllModal()
            await fetchTokens()
        } catch (e) {
            // the request layer already toasts the error
        } finally {
            setBusy(false)
        }
    }

    const statusBadge = (token) => {
        const state = tokenState(token, now)
        return (
            <HorizontalStack gap="1" wrap={false}>
                {state === 'ACTIVE' && <Badge status="success">Active</Badge>}
                {state === 'EXPIRED' && <Badge status="warning">Expired</Badge>}
                {state === 'REVOKED' && <Badge status="critical">Revoked</Badge>}
                {isLegacy(token) && <Badge>Legacy</Badge>}
            </HorizontalStack>
        )
    }

    const usedBy = (token) => {
        const modules = token.modules || []
        if (modules.length === 0) return '-'
        const extra = (token.moduleCount ?? modules.length) - MAX_USED_BY_CHIPS
        return (
            <HorizontalStack gap="1" wrap={false}>
                {modules.slice(0, MAX_USED_BY_CHIPS).map(module => (
                    <Tooltip
                        key={module.id}
                        content={`${module.moduleType || '-'} · last heartbeat ${func.epochToDateTime(module.lastHeartbeatReceived)}`}
                    >
                        <Badge status={module.active ? 'success' : undefined}>{module.name || module.id}</Badge>
                    </Tooltip>
                ))}
                {extra > 0 && <Text variant="bodySm" color="subdued">+{extra} more</Text>}
            </HorizontalStack>
        )
    }

    const lastUsed = (token) => {
        if (!token.lastUsedAt) return <Text color="subdued">Never</Text>
        const label = func.epochToDateTime(token.lastUsedAt)
        return (
            <HorizontalStack gap="1" wrap={false}>
                <span>{label}</span>
                {tokenState(token, now) === 'ACTIVE' && isIdle(token) && <Badge status="attention">Idle</Badge>}
            </HorizontalStack>
        )
    }

    const expiresLabel = (token) => {
        if (!token.expiresAt) return 'Never'
        const soon = tokenState(token, now) === 'ACTIVE' && token.expiresAt - now <= EXPIRING_SOON_SECONDS
        return (
            <HorizontalStack gap="1" wrap={false}>
                <span>{func.epochToDateTime(token.expiresAt)}</span>
                {soon && <Badge status="warning">Expiring soon</Badge>}
            </HorizontalStack>
        )
    }

    const actions = (token) => {
        if (tokenState(token, now) !== 'ACTIVE') return '-'
        return (
            <Button plain destructive disabled={!canCall('api/revokeAuthToken')} onClick={() => setRevokeTarget(token)}>Revoke</Button>
        )
    }

    const rows = tokens.map(token => [
        token.name || '-',
        maskedToken(token.hint),
        scopeLabel(token.scope),
        token.createdByEmail || '-',
        func.epochToDateTime(token.createdAt),
        expiresLabel(token),
        statusBadge(token),
        usedBy(token),
        lastUsed(token),
        actions(token)
    ])

    // the server sends at most a few modules per token plus the real total in moduleCount
    const affectedModules = (revokeTarget?.modules) || []
    const moreAffected = (revokeTarget?.moduleCount ?? affectedModules.length) - affectedModules.length
    const revokeAllModuleCount = activeTokens.reduce((count, token) => count + (token.moduleCount ?? (token.modules || []).length), 0)

    return (
        <VerticalStack gap="4">
            <Banner status="info">
                Revoking a token can take {REVOKE_DELAY} to take effect everywhere.
            </Banner>
            {expiringSoon.length > 0 && (
                <Banner status="warning" title={`${expiringSoon.length} token(s) expire within 14 days`}>
                    {expiringSoon.map(token => token.name).join(', ')}
                </Banner>
            )}
            {idleTokens.length > 0 && (
                <Banner status="info" title={`${idleTokens.length} active token(s) unused for 30+ days`}>
                    Consider revoking tokens that are no longer needed: {idleTokens.map(token => token.name).join(', ')}
                </Banner>
            )}

            {!loaded || tokens.length > 0 ? (
                <LegacyCard>
                    <LegacyCard.Section>
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="headingMd" as="h2">Authentication tokens</Text>
                            <HorizontalStack gap="2" blockAlign="center">
                                <Button disabled={!canCall('api/createAuthToken')} onClick={openCreateModal}>Create token</Button>
                                <Button
                                    destructive
                                    disabled={!canCall('api/revokeAllAuthTokens') || activeTokens.length === 0}
                                    onClick={() => setRevokeAllOpen(true)}
                                >
                                    Revoke all
                                </Button>
                            </HorizontalStack>
                        </HorizontalStack>
                    </LegacyCard.Section>
                    <DataTable
                        columnContentTypes={['text', 'text', 'text', 'text', 'text', 'text', 'text', 'text', 'text', 'text']}
                        headings={['Name', 'Token', 'Scope', 'Created by', 'Created', 'Expires', 'Status', 'Used by', 'Last used', 'Actions']}
                        rows={rows}
                    />
                </LegacyCard>
            ) : (
                <EmptyCard
                    title="No tokens"
                    subTitleComponent={
                        <VerticalStack gap="3" inlineAlign="center">
                            <Text variant="bodyMd" alignment="center" color="subdued">
                                No authentication tokens yet. Tokens you generate will be listed here with the modules using them.
                            </Text>
                            <Button disabled={!canCall('api/createAuthToken')} onClick={openCreateModal}>Create token</Button>
                        </VerticalStack>
                    }
                />
            )}

            <Modal
                open={createModal.open}
                onClose={closeCreateModal}
                title="Create token"
                primaryAction={{ content: 'Done', onAction: closeCreateModal }}
            >
                <Modal.Section>
                    <RuntimeTokenField
                        key={createModal.key}
                        id="tokens-tab-token-expiry"
                        onCreated={fetchTokens}
                    />
                </Modal.Section>
            </Modal>

            <Modal
                open={revokeTarget !== null}
                onClose={closeRevokeModal}
                title="Revoke token"
                primaryAction={{ content: 'Revoke token', destructive: true, loading: busy, onAction: handleRevoke }}
                secondaryActions={[{ content: 'Cancel', onAction: closeRevokeModal }]}
            >
                <Modal.Section>
                    <VerticalStack gap="3">
                        <Text variant="bodyMd">
                            Revoke "{revokeTarget?.name}" ({maskedToken(revokeTarget?.hint)})? It can take {REVOKE_DELAY} to stop working everywhere and cannot be restored.
                        </Text>
                        {affectedModules.length > 0 ? (
                            <VerticalStack gap="1">
                                <Text variant="bodyMd" fontWeight="semibold">These modules will lose access:</Text>
                                {affectedModules.map(module => (
                                    <Text key={module.id} variant="bodyMd">
                                        {module.name || module.id} ({module.moduleType || '-'}), last heartbeat {func.epochToDateTime(module.lastHeartbeatReceived)}
                                    </Text>
                                ))}
                                {moreAffected > 0 && <Text variant="bodyMd" color="subdued">and {moreAffected} more</Text>}
                            </VerticalStack>
                        ) : (
                            <Text variant="bodyMd" color="subdued">No modules are currently using this token.</Text>
                        )}
                        <TextField
                            label="Reason (optional)"
                            value={revokeReason}
                            onChange={setRevokeReason}
                            maxLength={200}
                            autoComplete="off"
                        />
                    </VerticalStack>
                </Modal.Section>
            </Modal>

            <Modal
                open={revokeAllOpen}
                onClose={closeRevokeAllModal}
                title="Revoke all tokens"
                primaryAction={{
                    content: 'Revoke all tokens',
                    destructive: true,
                    loading: busy,
                    disabled: confirmText !== REVOKE_CONFIRM_TEXT,
                    onAction: handleRevokeAll
                }}
                secondaryActions={[{ content: 'Cancel', onAction: closeRevokeAllModal }]}
            >
                <Modal.Section>
                    <VerticalStack gap="3">
                        <Banner status="critical">
                            This revokes {activeTokens.length} active token(s){activeLegacyCount > 0 ? `, including ${activeLegacyCount} legacy token(s),` : ''} affecting {revokeAllModuleCount} module(s).
                        </Banner>
                        <Text variant="bodyMd" color="subdued">{REVOKE_ALL_INFO}</Text>
                        <TextField
                            label="Reason (optional)"
                            value={revokeReason}
                            onChange={setRevokeReason}
                            maxLength={200}
                            autoComplete="off"
                        />
                        <Box>
                            <TextField
                                label={`Type ${REVOKE_CONFIRM_TEXT} to confirm`}
                                value={confirmText}
                                onChange={setConfirmText}
                                autoComplete="off"
                            />
                        </Box>
                    </VerticalStack>
                </Modal.Section>
            </Modal>
        </VerticalStack>
    )
}

export default TokensTab

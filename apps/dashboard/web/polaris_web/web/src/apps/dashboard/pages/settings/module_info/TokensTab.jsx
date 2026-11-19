import { Badge, Banner, Box, Button, HorizontalStack, IndexFiltersMode, Modal, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import { useEffect, useMemo, useState } from "react"
import settingRequests from "../api"
import func from "@/util/func"
import EmptyCard from "../../dashboard/new_components/EmptyCard"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import { CellType } from "../../../components/tables/rows/GithubRow"
import RuntimeTokenField from "../../quick_start/components/RuntimeTokenField"
import { usePermissions } from "@/util/permissions"
import { maskedToken } from "./maskedToken"
import { buildSortOptions, disambiguateLabel } from "./tableConfig"

const DAY = 24 * 60 * 60
const EXPIRING_SOON_SECONDS = 14 * DAY
const IDLE_SECONDS = 30 * DAY
const MAX_USED_BY_CHIPS = 3
const REVOKE_CONFIRM_TEXT = "REVOKE"
const DEFAULT_SORT_KEY = 'createdTs'
const SEARCH_KEYS = ['name', 'tokenMask', 'scopeCell', 'createdByEmail', 'status', 'source', 'usedByNames']
// services re-sync their revoked list on this schedule, so a revoke can take this long to reach all of them
const REVOKE_DELAY = "up to 15 minutes"
const REVOKE_ALL_INFO = `Revoke all works after upgrading modules to the latest version. It revokes every active token, including the legacy tokens listed here; a legacy token that has not connected yet is not listed, so it is not included. Revoked tokens can take ${REVOKE_DELAY} to stop working everywhere.`

const resourceName = { singular: 'token', plural: 'tokens' }

const nowSeconds = () => Math.floor(Date.now() / 1000)

const scopeNames = (scope) => (scope && scope.length > 0)
    ? scope.map(value => value.split('_').map(func.toSentenceCase).join(' '))
    : ['Unscoped']

// REVOKED wins over EXPIRED; expiresAt 0 means the token never expires
function tokenState(token, now) {
    if (token.status === 'REVOKED') return 'Revoked'
    if (token.expiresAt > 0 && token.expiresAt <= now) return 'Expired'
    return 'Active'
}

const isLegacy = (token) => token.source === 'DISCOVERED'
const isIdle = (token, now) => now - (token.lastUsedAt || token.createdAt || 0) > IDLE_SECONDS

const STATUS_BADGE = { Active: 'success', Expired: 'warning', Revoked: 'critical' }

function usedByCell(token) {
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

function lastUsedCell(token, state, now) {
    if (!token.lastUsedAt) return <Text color="subdued">Never</Text>
    return (
        <HorizontalStack gap="1" wrap={false}>
            <span>{func.epochToDateTime(token.lastUsedAt)}</span>
            {state === 'Active' && isIdle(token, now) && <Badge status="attention">Idle</Badge>}
        </HorizontalStack>
    )
}

function expiresCell(token, state, now) {
    if (!token.expiresAt) return 'Never'
    const soon = state === 'Active' && token.expiresAt - now <= EXPIRING_SOON_SECONDS
    return (
        <HorizontalStack gap="1" wrap={false}>
            <span>{func.epochToDateTime(token.expiresAt)}</span>
            {soon && <Badge status="warning">Expiring soon</Badge>}
        </HorizontalStack>
    )
}

const HEADERS = [
    { title: 'Name', text: 'Name', value: 'name', sortKey: 'name', sortActive: true },
    { title: 'Token', text: 'Token', value: 'tokenMask' },
    { title: 'Scope', text: 'Scope', value: 'scopeCell', filterKey: 'scopeList', showFilter: true },
    { title: 'Created by', text: 'Created by', value: 'createdByEmail', filterKey: 'createdByEmail', showFilter: true },
    { title: 'Created', text: 'Created', value: 'createdLabel', sortKey: 'createdTs', sortActive: true },
    { title: 'Expires', text: 'Expires', value: 'expiresLabel', sortKey: 'expiresTs', sortActive: true, sortLabels: ['Latest', 'Earliest'] },
    { title: 'Status', text: 'Status', value: 'statusCell', filterKey: 'status', showFilter: true, sortKey: 'status', sortActive: true },
    { title: 'Source', text: 'Source', value: 'source', filterKey: 'source', showFilter: true },
    { title: 'Used by', text: 'Used by', value: 'usedByLabel' },
    { title: 'Last used', text: 'Last used', value: 'lastUsedLabel', sortKey: 'lastUsedTs', sortActive: true },
]

const SORT_OPTIONS = buildSortOptions(HEADERS, DEFAULT_SORT_KEY)
// with useNewRow the row menu only renders for a header of this type
const ACTION_HEADER = { title: '', type: CellType.ACTION }

function TokensTab() {
    const { canCall } = usePermissions()
    const [tokens, setTokens] = useState([])
    const [loaded, setLoaded] = useState(false)
    const [tableKey, setTableKey] = useState(0)
    const [busy, setBusy] = useState(false)
    const [createModal, setCreateModal] = useState({ open: false, key: 0 })
    const [revokeTarget, setRevokeTarget] = useState(null)
    const [revokeReason, setRevokeReason] = useState('')
    const [revokeAllOpen, setRevokeAllOpen] = useState(false)
    const [confirmText, setConfirmText] = useState('')

    const fetchTokens = async () => {
        try {
            const resp = await settingRequests.fetchAuthTokens()
            setTokens(resp?.authTokens || [])
            setTableKey(key => key + 1)
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
    const activeTokens = tokens.filter(token => tokenState(token, now) === 'Active')
    const activeLegacyCount = activeTokens.filter(isLegacy).length
    const expiringSoon = activeTokens.filter(token => token.expiresAt > 0 && token.expiresAt - now <= EXPIRING_SOON_SECONDS)
    const idleTokens = activeTokens.filter(token => isIdle(token, now))
    // only an active token has a row menu, so the column exists only while one does
    const hasRowActions = activeTokens.length > 0
    const tableHeaders = useMemo(() => (hasRowActions ? [...HEADERS, ACTION_HEADER] : HEADERS), [hasRowActions])
    // the rows carry "Expiring soon" / "Idle" badges; this is only the count
    const attentionSummary = [
        expiringSoon.length > 0 && `${expiringSoon.length} expiring within 14 days`,
        idleTokens.length > 0 && `${idleTokens.length} unused for 30+ days`,
    ].filter(Boolean).join(' · ')

    const rows = useMemo(() => tokens.map(token => {
        const current = nowSeconds()
        const state = tokenState(token, current)
        const scopeList = scopeNames(token.scope)
        const usedByNames = (token.modules || []).map(module => module.name || module.id)
        return {
            id: token.id,
            name: token.name || '-',
            tokenMask: maskedToken(token.hint),
            scopeList,
            scopeCell: scopeList.join(', '),
            createdByEmail: token.createdByEmail || '-',
            createdLabel: func.epochToDateTime(token.createdAt),
            createdTs: token.createdAt || 0,
            expiresLabel: expiresCell(token, state, current),
            expiresTs: token.expiresAt > 0 ? token.expiresAt : Number.MAX_SAFE_INTEGER,
            status: state,
            statusCell: <Badge status={STATUS_BADGE[state]}>{state}</Badge>,
            source: isLegacy(token) ? 'Legacy' : 'Generated',
            usedByLabel: usedByCell(token),
            usedByNames,
            lastUsedLabel: lastUsedCell(token, state, current),
            lastUsedTs: token.lastUsedAt || 0,
            token,
        }
    }), [tokens])

    const getActions = (row) => {
        if (!row?.token) return []
        const state = tokenState(row.token, nowSeconds())
        // the menu button is on every row, so a token that cannot be revoked says why instead of opening empty
        if (state !== 'Active') return [{ items: [{ content: `Token ${state.toLowerCase()}`, disabled: true }] }]
        return [{
            items: [{
                content: 'Revoke',
                destructive: true,
                requires: 'api/revokeAuthToken',
                onAction: () => setRevokeTarget(row.token),
            }],
        }]
    }

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

    // the server sends at most a few modules per token plus the real total in moduleCount
    const affectedModules = (revokeTarget?.modules) || []
    const moreAffected = (revokeTarget?.moduleCount ?? affectedModules.length) - affectedModules.length
    const revokeAllModuleCount = activeTokens.reduce((count, token) => count + (token.moduleCount ?? (token.modules || []).length), 0)

    return (
        <VerticalStack gap="4">
            {loaded && tokens.length === 0 ? (
                <EmptyCard
                    title="No authentication tokens"
                    subTitleComponent={
                        <VerticalStack gap="3" inlineAlign="center">
                            <Text variant="bodyMd" alignment="center" color="subdued">
                                No authentication tokens yet. Tokens you generate will be listed here with the modules using them.
                            </Text>
                            <Button disabled={!canCall('api/createAuthToken')} onClick={openCreateModal}>Create token</Button>
                        </VerticalStack>
                    }
                />
            ) : (
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between" blockAlign="center" gap="4">
                        <VerticalStack gap="05">
                            <Text variant="bodySm" color="subdued">Revoking a token can take {REVOKE_DELAY} to take effect everywhere.</Text>
                            {attentionSummary && <Text variant="bodySm" color="subdued">{attentionSummary}</Text>}
                        </VerticalStack>
                        <HorizontalStack gap="2">
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
                    <GithubSimpleTable
                        key={`tokens-${tableKey}`}
                        pageLimit={20}
                        data={rows}
                        resourceName={resourceName}
                        sortOptions={SORT_OPTIONS}
                        filters={[]}
                        headers={tableHeaders}
                        headings={tableHeaders}
                        searchKeys={SEARCH_KEYS}
                        selectable={false}
                        getActions={getActions}
                        hasRowActions={hasRowActions}
                        useNewRow={true}
                        condensedHeight={true}
                        mode={IndexFiltersMode.Filtering}
                        disambiguateLabel={disambiguateLabel}
                        contentAwareFilterCache={true}
                        skipUrlFilters={true}
                        loading={!loaded}
                    />
                </VerticalStack>
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

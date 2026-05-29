import React, { useState, useCallback } from 'react'
import { Banner, Checkbox, Text, VerticalStack, HorizontalStack, Spinner, Badge, Button, Box, Divider, Modal, TextField } from '@shopify/polaris'
import PatternSettingsPage from '../components/PatternSettingsPage'
import GlobHelper from './GlobHelper'
import MatchTree from './MatchTree'
import FlyLayout from '../../../components/layouts/FlyLayout'
import settingRequests from '../api'
import { CellType } from '../../../components/tables/rows/GithubRow'
import func from '@/util/func'

const resourceName = { singular: 'rule', plural: 'rules' }

function rulesArrayToMap(rules) {
    if (!Array.isArray(rules)) return {}
    const map = {}
    for (const r of rules) {
        if (!r || !r.path) continue
        map[r.path] = {
            path: r.path,
            idHex: r.idHex,
            existenceOnly: !!r.existenceOnly,
            maxDepth: r.maxDepth ?? 0,
            addedBy: r.addedBy,
            updatedTs: r.updatedTs,
        }
    }
    return map
}

const RULE_HEADERS = [
    { text: 'Path', title: 'Path', value: 'patternValue', type: CellType.TEXT },
    { text: 'Existence only', title: 'Existence only', value: 'existenceOnlyText', type: CellType.TEXT },
    { text: 'Created By', title: 'Created By', value: 'addedBy', type: CellType.TEXT },
    { text: 'Last Updated', title: 'Last Updated', value: 'updatedTsFormatted', type: CellType.TEXT },
]

const STATUS_TONE = { OK: 'success', PARTIAL: 'warning', ERROR: 'critical' }

function ExecutionSection({ r, isLast }) {
    const [expanded, setExpanded] = useState(true)
    const matches = r.matches || []

    return (
        <VerticalStack gap="0">
            <Box padding="4">
                <VerticalStack gap="3">
                    <HorizontalStack align="space-between" wrap={false}>
                        <HorizontalStack gap="2">
                            <Badge tone={STATUS_TONE[r.status] || 'info'}>{r.status || '-'}</Badge>
                            <Text variant="bodySm" color="subdued">
                                {r.executedAt ? func.prettifyEpoch(r.executedAt) : '-'}
                            </Text>
                            {matches.length > 0 && (
                                <Text variant="bodySm" color="subdued">
                                    · {matches.length} match{matches.length !== 1 ? 'es' : ''}
                                </Text>
                            )}
                        </HorizontalStack>
                        <HorizontalStack gap="2" align="end">
                            <Text variant="bodySm" color="subdued">{r.deviceLabel || r.deviceId || '-'}</Text>
                            {matches.length > 0 && (
                                <Button plain onClick={() => setExpanded(e => !e)}>
                                    {expanded ? 'Hide' : 'Show'}
                                </Button>
                            )}
                        </HorizontalStack>
                    </HorizontalStack>
                    {!!r.errorMessage && (
                        <Text variant="bodySm" color="critical">{r.errorMessage}</Text>
                    )}
                    {expanded && matches.length > 0 && (
                        <MatchTree matches={matches} />
                    )}
                </VerticalStack>
            </Box>
            {!isLast && <Divider />}
        </VerticalStack>
    )
}

function RuleExecutions({ path, ruleId }) {
    const [results, setResults] = useState(null)
    const [loading, setLoading] = useState(false)

    React.useEffect(() => {
        if (!ruleId) return
        setLoading(true)
        settingRequests.fetchFileInspectionResults(ruleId)
            .then(res => setResults(res?.recentResults || []))
            .catch(() => func.setToast(true, true, 'Failed to load results'))
            .finally(() => setLoading(false))
    }, [ruleId])

    if (loading) {
        return (
            <Box padding="6">
                <HorizontalStack align="center"><Spinner size="small" /></HorizontalStack>
            </Box>
        )
    }

    if (!results || results.length === 0) {
        return (
            <Box padding="4">
                <Text color="subdued">No executions yet. The agent runs every 60 seconds once a rule is saved.</Text>
            </Box>
        )
    }

    return (
        <VerticalStack gap="0">
            {results.map((r, i) => (
                <ExecutionSection key={i} r={r} isLast={i === results.length - 1} />
            ))}
        </VerticalStack>
    )
}

function FileInspection() {
    const [existenceOnly, setExistenceOnly] = useState(false)
    const [recursive, setRecursive] = useState(false)
    const [flyPath, setFlyPath] = useState(null)
    const [flyRuleId, setFlyRuleId] = useState(null)
    const [showFly, setShowFly] = useState(false)

    const [editModal, setEditModal] = useState(false)
    const [editOriginalPath, setEditOriginalPath] = useState('')
    const [editPath, setEditPath] = useState('')
    const [editExistenceOnly, setEditExistenceOnly] = useState(false)
    const [editRecursive, setEditRecursive] = useState(false)
    const [editSaving, setEditSaving] = useState(false)
    const [editPathError, setEditPathError] = useState('')

    const [latestMap, setLatestMap] = useState({})
    const [globTrigger, setGlobTrigger] = useState(null)

    async function onFetch() {
        const res = await settingRequests.fetchFileInspectionRules()
        const map = rulesArrayToMap(res?.rules || [])
        setLatestMap(map)
        return map
    }

    async function onAdd(path) {
        await settingRequests.addFileInspectionRule(path, existenceOnly, recursive ? -1 : 0)
        return onFetch()
    }

    async function onDelete(path) {
        await settingRequests.deleteFileInspectionRule(path)
        return onFetch()
    }

    function openEdit(row) {
        const info = latestMap[row.patternValue] || {}
        setEditOriginalPath(row.patternValue)
        setEditPath(row.patternValue)
        setEditExistenceOnly(!!info.existenceOnly)
        setEditRecursive(info.maxDepth === -1)
        setEditPathError('')
        setEditModal(true)
    }

    async function saveEdit() {
        const trimmed = editPath.trim()
        if (!trimmed) { setEditPathError('Path is required'); return }
        if (trimmed === '/') { setEditPathError("Path cannot be the root '/'"); return }
        setEditSaving(true)
        try {
            if (trimmed !== editOriginalPath) {
                await settingRequests.deleteFileInspectionRule(editOriginalPath)
            }
            await settingRequests.addFileInspectionRule(trimmed, editExistenceOnly, editRecursive ? -1 : 0)
            await onFetch()
            setEditModal(false)
            func.setToast(true, false, 'Rule updated')
        } catch {
            func.setToast(true, true, 'Failed to update rule')
        } finally {
            setEditSaving(false)
        }
    }

    function getRowActions(row) {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => openEdit(row),
            }]
        }]
    }

    function onRowClick(row) {
        setFlyPath(row.patternValue)
        setFlyRuleId(latestMap[row.patternValue]?.idHex || null)
        setShowFly(true)
    }

    const titleComp = (
        <Box width="90%">
            <VerticalStack gap="1">
                <Text variant="headingMd">Execution Results</Text>
                <Text variant="bodySm" color="subdued" breakWord>{flyPath}</Text>
            </VerticalStack>
        </Box>
    )

    const extraContent = (
        <>
            <Checkbox
                label="Recursive - descend into all subdirectories"
                checked={recursive}
                onChange={setRecursive}
            />
            <Checkbox
                label="Existence only - record metadata without reading file contents"
                checked={existenceOnly}
                onChange={setExistenceOnly}
            />
            <Banner status="info">
                Accepts a file path, directory path, glob pattern (<Text as="span" variant="bodySm" fontWeight="semibold">*.conf</Text>),
                or double-star recursive glob (<Text as="span" variant="bodySm" fontWeight="semibold">~/.claude/**/*.json</Text>).
                The agent resolves the pattern, reads matching files, and ships content back.
            </Banner>
        </>
    )

    return (
        <>
            <PatternSettingsPage
                title="File Inspection"
                cardTitle="Add path to inspect"
                description="Enter a file path, directory path, or any pattern you want the agent to inspect."
                extraContent={extraContent}
                inputLabel="Path"
                placeholder="e.g. /etc/hosts  or  ~/.cursor  or  ~/.claude/**/*.json"
                tableKey="file-inspection-rules-table"
                resourceName={resourceName}
                onFetch={onFetch}
                onAdd={onAdd}
                onDelete={onDelete}
                patternKey="path"
                headers={RULE_HEADERS}
                buildRow={(_key, info) => ({
                    existenceOnlyText: info.existenceOnly ? 'Yes' : 'No',
                })}
                onRowClick={onRowClick}
                getRowActions={getRowActions}
                initialValue={globTrigger}
                secondaryActions={<GlobHelper onUse={v => setGlobTrigger({ value: v, id: Date.now() })} />}
            />

            <Modal
                open={editModal}
                onClose={() => setEditModal(false)}
                title="Edit rule"
                primaryAction={{ content: 'Save', onAction: saveEdit, loading: editSaving }}
                secondaryActions={[{ content: 'Cancel', onAction: () => setEditModal(false) }]}
            >
                <Modal.Section>
                    <VerticalStack gap="4">
                        <TextField
                            label="Path"
                            value={editPath}
                            onChange={v => { setEditPath(v); setEditPathError('') }}
                            error={editPathError || undefined}
                            autoComplete="off"
                            helpText="File path, directory path, or any pattern the agent should inspect."
                        />
                        <Checkbox
                            label="Recursive - descend into all subdirectories"
                            checked={editRecursive}
                            onChange={setEditRecursive}
                        />
                        <Checkbox
                            label="Existence only - record metadata without reading file contents"
                            checked={editExistenceOnly}
                            onChange={setEditExistenceOnly}
                        />
                    </VerticalStack>
                </Modal.Section>
            </Modal>

            <FlyLayout
                titleComp={titleComp}
                show={showFly}
                setShow={setShowFly}
                newComp={true}
                showDivider={false}
                components={showFly ? [<RuleExecutions key={flyPath} path={flyPath} ruleId={flyRuleId} />] : []}
            />
        </>
    )
}

export default FileInspection

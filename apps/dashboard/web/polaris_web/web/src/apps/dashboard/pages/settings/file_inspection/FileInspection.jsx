import React, { useState, useCallback, useMemo } from 'react'
import { Autocomplete, Banner, Card, Checkbox, Text, VerticalStack, HorizontalStack, HorizontalGrid, Spinner, Badge, Button, Box, Divider, Icon, Modal, ResourceItem, ResourceList, Select, Tabs, TextField } from '@shopify/polaris'
import { ChevronRightMinor } from '@shopify/polaris-icons'
import PatternSettingsPage from '../components/PatternSettingsPage'
import GlobHelper from './GlobHelper'
import MatchTree from './MatchTree'
import FlyLayout from '../../../components/layouts/FlyLayout'
import settingRequests from '../api'
import { CellType } from '../../../components/tables/rows/GithubRow'
import func from '@/util/func'
import { withPermissions, usePermissions } from '@/util/permissions'
import { AgentIcon } from '../../nhi_governance/nhiViolationsData'

const resourceName = { singular: 'rule', plural: 'rules' }

const DEFAULT_AGENTS = ['Claude Code', 'Cursor', 'Codex', 'GitHub Copilot']

const OS_TABS = [
    { label: 'macOS', value: 'darwin' },
    { label: 'Linux', value: 'linux' },
    { label: 'Windows', value: 'windows' },
]

const OS_OPTIONS = [{ label: 'All OS', value: '' }, ...OS_TABS]

const ROLE_LABELS = { login: 'Login', credentials: 'Credentials', mcp_config: 'MCP config' }

const ROLE_ORDER = ['mcp_config', 'login', 'credentials', '']

const ROLE_OPTIONS = [
    { label: 'None', value: '' },
    ...Object.entries(ROLE_LABELS).map(([value, label]) => ({ label, value })),
]

function osMatches(rule, os) {
    return !rule.os || rule.os === os
}

function rulesArrayToMap(rules) {
    if (!Array.isArray(rules)) return {}
    const map = {}
    for (const r of rules) {
        if (!r || !r.path || r.agent) continue
        map[r.path] = {
            path: r.path,
            idHex: r.idHex,
            existenceOnly: !!r.existenceOnly,
            maxDepth: r.maxDepth ?? 0,
            os: r.os,
            role: r.role,
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

function AgentPicker({ value, onChange, options, error, requireAgent }) {
    const filtered = useMemo(() => {
        const q = value.trim().toLowerCase()
        return options
            .filter(o => !q || o.toLowerCase().includes(q))
            .map(o => ({ value: o, label: o }))
    }, [value, options])

    return (
        <Autocomplete
            options={filtered}
            selected={value ? [value] : []}
            onSelect={sel => onChange(sel[0] || '')}
            textField={
                <Autocomplete.TextField
                    label="Agent"
                    value={value}
                    onChange={onChange}
                    error={error || undefined}
                    autoComplete="off"
                    placeholder="e.g. Claude Code"
                    helpText={requireAgent ? 'Pick an existing agent or type a new name.' : 'Pick an existing agent or type a new name. Leave empty for a custom path.'}
                />
            }
        />
    )
}

function AgentRuleRow({ rule, onOpen, onEdit, onDelete, canUpdate, canDelete }) {
    const shortcutActions = [
        { content: 'Edit', onAction: () => onEdit(rule), disabled: !canUpdate },
        { content: 'Delete', destructive: true, onAction: () => onDelete(rule), disabled: !canDelete },
    ]
    return (
        <ResourceItem
            id={rule.idHex || rule.path}
            onClick={() => onOpen(rule)}
            shortcutActions={shortcutActions}
            persistActions
            accessibilityLabel={`View results for ${rule.path}`}
        >
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <Text variant="bodyMd" breakWord>{rule.path}</Text>
                {!rule.os && <Badge>All OS</Badge>}
                {rule.maxDepth === -1 && <Badge>Recursive</Badge>}
                {rule.existenceOnly && <Badge>Existence only</Badge>}
            </HorizontalStack>
        </ResourceItem>
    )
}

function AgentsCard({ rules, onOpenAgent }) {
    const agents = useMemo(() => {
        const byAgent = {}
        for (const r of rules) {
            if (!byAgent[r.agent]) byAgent[r.agent] = []
            byAgent[r.agent].push(r)
        }
        return Object.keys(byAgent).sort().map(agent => ({
            agent,
            counts: OS_TABS.map(t => byAgent[agent].filter(r => osMatches(r, t.value)).length),
        }))
    }, [rules])

    return (
        <Card>
            <VerticalStack gap="4">
                <VerticalStack gap="1">
                    <Text variant="headingMd">Agents</Text>
                    <Text variant="bodySm" color="subdued">Files the endpoint agent inspects for each AI agent.</Text>
                </VerticalStack>
                {agents.length === 0 ? (
                    <Text color="subdued">
                        No agents yet. Use Restore defaults to add the known login, credential and MCP config files of Claude Code, Cursor, Codex and other AI agents, or Add agent to add your own.
                    </Text>
                ) : (
                    <ResourceList
                        resourceName={{ singular: 'agent', plural: 'agents' }}
                        items={agents}
                        renderItem={a => (
                            <ResourceItem
                                key={a.agent}
                                id={a.agent}
                                onClick={() => onOpenAgent(a.agent)}
                                accessibilityLabel={`View files for ${a.agent}`}
                            >
                                <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                                    <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                                        <AgentIcon name={a.agent} />
                                        <Text variant="bodyMd" fontWeight="semibold">{a.agent}</Text>
                                    </HorizontalStack>
                                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                                        {OS_TABS.map((t, i) => <Badge key={t.value}>{`${t.label} ${a.counts[i]}`}</Badge>)}
                                        <Icon source={ChevronRightMinor} color="subdued" />
                                    </HorizontalStack>
                                </HorizontalStack>
                            </ResourceItem>
                        )}
                    />
                )}
            </VerticalStack>
        </Card>
    )
}

function AgentDetail({ agent, rules, selectedOs, onSelectOs, onAdd, onOpen, onEdit, onDelete }) {
    const { canCall } = usePermissions()
    const canAdd = canCall('api/addFileInspectionRule')
    const canUpdate = canCall('api/updateFileInspectionRule')
    const canDelete = canCall('api/deleteFileInspectionRule')

    const tabs = OS_TABS.map(t => ({
        id: `fi-os-${t.value}`,
        content: `${t.label} (${rules.filter(r => osMatches(r, t.value)).length})`,
    }))
    const selectedIndex = Math.max(0, OS_TABS.findIndex(t => t.value === selectedOs))

    const groups = useMemo(() => ROLE_ORDER
        .map(role => ({ role, rules: rules.filter(r => osMatches(r, selectedOs) && (r.role || '') === role) }))
        .filter(g => g.rules.length > 0), [rules, selectedOs])

    return (
        <Box padding="4">
            <VerticalStack gap="4">
                <HorizontalStack align="end">
                    <Button onClick={() => onAdd(agent)} disabled={!canAdd}>Add file</Button>
                </HorizontalStack>
                <Tabs tabs={tabs} selected={selectedIndex} onSelect={i => onSelectOs(OS_TABS[i].value)} />
                {groups.length === 0 ? (
                    <Text color="subdued">No files for this OS yet.</Text>
                ) : groups.map((g, i) => (
                    <VerticalStack gap="2" key={g.role || 'none'}>
                        {i > 0 && <Divider />}
                        <HorizontalStack>
                            <Badge status="info">{ROLE_LABELS[g.role] || 'No role'}</Badge>
                        </HorizontalStack>
                        <ResourceList
                            resourceName={resourceName}
                            items={g.rules}
                            renderItem={rule => (
                                <AgentRuleRow
                                    key={rule.idHex || rule.path}
                                    rule={rule}
                                    onOpen={onOpen}
                                    onEdit={onEdit}
                                    onDelete={onDelete}
                                    canUpdate={canUpdate}
                                    canDelete={canDelete}
                                />
                            )}
                        />
                    </VerticalStack>
                ))}
            </VerticalStack>
        </Box>
    )
}

const EMPTY_FORM = { ruleId: null, title: 'Add file', agent: '', os: '', role: '', path: '', existenceOnly: false, recursive: false, requireAgent: false }

function FileInspection() {
    const [existenceOnly, setExistenceOnly] = useState(false)
    const [recursive, setRecursive] = useState(false)
    const [flyView, setFlyView] = useState(null)
    const [showFly, setShowFly] = useState(false)
    const [restoreModal, setRestoreModal] = useState(false)
    const [restoring, setRestoring] = useState(false)
    const { canCall } = usePermissions()

    const [ruleModal, setRuleModal] = useState(false)
    const [form, setForm] = useState(EMPTY_FORM)
    const [formSaving, setFormSaving] = useState(false)
    const [formErrors, setFormErrors] = useState({})

    const [latestMap, setLatestMap] = useState({})
    const [allRules, setAllRules] = useState([])
    const [selectedOs, setSelectedOs] = useState('darwin')
    const [refreshKey, setRefreshKey] = useState(0)
    const [globTrigger, setGlobTrigger] = useState(null)

    const agentRules = useMemo(() => allRules.filter(r => r && r.path && r.agent), [allRules])
    const agentOptions = useMemo(
        () => Array.from(new Set([...DEFAULT_AGENTS, ...agentRules.map(r => r.agent)])).sort(),
        [agentRules]
    )

    async function onFetch() {
        const res = await settingRequests.fetchFileInspectionRules()
        const rules = res?.rules || []
        setAllRules(rules)
        const map = rulesArrayToMap(rules)
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

    function openResults(path, ruleId, agent) {
        setFlyView({ type: 'results', path, ruleId: ruleId || null, agent: agent || null })
        setShowFly(true)
    }

    function openAgent(agent) {
        setFlyView({ type: 'agent', agent })
        setShowFly(true)
    }

    async function restoreDefaults() {
        setRestoring(true)
        try {
            const res = await settingRequests.restoreFileInspectionDefaults()
            setRefreshKey(k => k + 1)
            setRestoreModal(false)
            func.setToast(true, false, `Defaults restored: ${res?.createdCount ?? 0} added, ${res?.updatedCount ?? 0} updated`)
        } catch {
            func.setToast(true, true, 'Failed to restore defaults')
        } finally {
            setRestoring(false)
        }
    }

    function updateForm(field, value) {
        setForm(f => ({ ...f, [field]: value }))
        setFormErrors(e => ({ ...e, [field]: '' }))
    }

    function openAddAgentFile(agent) {
        setForm({ ...EMPTY_FORM, title: agent ? 'Add file' : 'Add agent', agent: agent || '', os: agent ? selectedOs : '', requireAgent: true })
        setFormErrors({})
        setRuleModal(true)
    }

    function openEditRule(rule) {
        setForm({
            ruleId: rule.idHex,
            title: 'Edit rule',
            agent: rule.agent || '',
            os: rule.os || '',
            role: rule.role || '',
            path: rule.path || '',
            existenceOnly: !!rule.existenceOnly,
            recursive: rule.maxDepth === -1,
            requireAgent: false,
        })
        setFormErrors({})
        setRuleModal(true)
    }

    function deleteAgentRule(rule) {
        func.showConfirmationModal(`Delete ${rule.path}?`, 'Delete', async () => {
            try {
                await settingRequests.deleteFileInspectionRule(rule.path)
                setRefreshKey(k => k + 1)
                func.setToast(true, false, 'Rule deleted')
            } catch {
                func.setToast(true, true, 'Failed to delete rule')
            }
        })
    }

    async function saveRule() {
        const path = form.path.trim()
        const agent = form.agent.trim()
        const errors = {}
        if (!path) errors.path = 'Path is required'
        else if (path === '/') errors.path = "Path cannot be the root '/'"
        if (form.requireAgent && !agent) errors.agent = 'Agent is required'
        if (Object.keys(errors).length > 0) { setFormErrors(errors); return }
        setFormSaving(true)
        try {
            const maxDepth = form.recursive ? -1 : 0
            if (form.ruleId) {
                await settingRequests.updateFileInspectionRule(form.ruleId, path, form.existenceOnly, maxDepth, agent, form.os, form.role)
            } else {
                await settingRequests.addFileInspectionRule(path, form.existenceOnly, maxDepth, agent, form.os, form.role)
            }
            setRefreshKey(k => k + 1)
            setRuleModal(false)
            func.setToast(true, false, form.ruleId ? 'Rule updated' : 'Rule added')
        } catch {
            func.setToast(true, true, form.ruleId ? 'Failed to update rule' : 'Failed to add rule')
        } finally {
            setFormSaving(false)
        }
    }

    function getRowActions(row) {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => {
                    const info = latestMap[row.patternValue] || {}
                    openEditRule({ ...info, path: row.patternValue })
                },
                requires: ['api/updateFileInspectionRule'],
            }]
        }]
    }

    function onRowClick(row) {
        openResults(row.patternValue, latestMap[row.patternValue]?.idHex)
    }

    const flyAgentRules = flyView?.type === 'agent' ? agentRules.filter(r => r.agent === flyView.agent) : []

    const titleComp = flyView?.type === 'agent' ? (
        <Box width="90%">
            <HorizontalStack gap="2" blockAlign="center">
                <AgentIcon name={flyView.agent} />
                <Text variant="headingMd">{flyView.agent}</Text>
            </HorizontalStack>
        </Box>
    ) : (
        <Box width="90%">
            <VerticalStack gap="1">
                {flyView?.agent && (
                    <HorizontalStack>
                        <Button plain onClick={() => openAgent(flyView.agent)}>{`Back to ${flyView.agent}`}</Button>
                    </HorizontalStack>
                )}
                <Text variant="headingMd">Execution Results</Text>
                <Text variant="bodySm" color="subdued" breakWord>{flyView?.path}</Text>
            </VerticalStack>
        </Box>
    )

    let flyComponents = []
    if (showFly && flyView?.type === 'agent') {
        flyComponents = [
            <AgentDetail
                key={`agent-${flyView.agent}`}
                agent={flyView.agent}
                rules={flyAgentRules}
                selectedOs={selectedOs}
                onSelectOs={setSelectedOs}
                onAdd={openAddAgentFile}
                onOpen={rule => openResults(rule.path, rule.idHex, rule.agent)}
                onEdit={openEditRule}
                onDelete={deleteAgentRule}
            />
        ]
    } else if (showFly && flyView?.type === 'results') {
        flyComponents = [<RuleExecutions key={flyView.path} path={flyView.path} ruleId={flyView.ruleId} />]
    }

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

    const agentsCard = (
        <AgentsCard key="fi-agents" rules={agentRules} onOpenAgent={openAgent} />
    )

    const customPathsHeader = (
        <VerticalStack gap="1" key="fi-custom-header">
            <Text variant="headingMd">Custom paths</Text>
            <Text variant="bodySm" color="subdued">Paths not tied to an agent. Applies to all operating systems.</Text>
        </VerticalStack>
    )

    const saveRequires = form.ruleId ? ['api/updateFileInspectionRule'] : ['api/addFileInspectionRule']

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
                addRequires="api/addFileInspectionRule"
                deleteRequires="api/deleteFileInspectionRule"
                headers={RULE_HEADERS}
                buildRow={(_key, info) => ({
                    existenceOnlyText: info.existenceOnly ? 'Yes' : 'No',
                })}
                onRowClick={onRowClick}
                getRowActions={getRowActions}
                initialValue={globTrigger}
                refreshKey={refreshKey}
                leadingCards={[agentsCard, customPathsHeader]}
                secondaryActions={
                    <>
                        <Button onClick={() => setRestoreModal(true)} disabled={!canCall('api/restoreFileInspectionDefaults')}>Restore defaults</Button>
                        <Button onClick={() => openAddAgentFile('')} disabled={!canCall('api/addFileInspectionRule')}>Add agent</Button>
                        <GlobHelper onUse={v => setGlobTrigger({ value: v, id: Date.now() })} />
                    </>
                }
            />

            <Modal
                open={restoreModal}
                onClose={() => setRestoreModal(false)}
                title="Restore defaults"
                primaryAction={withPermissions({ content: 'Restore defaults', onAction: restoreDefaults, loading: restoring, requires: ['api/restoreFileInspectionDefaults'] })}
                secondaryActions={[{ content: 'Cancel', onAction: () => setRestoreModal(false) }]}
            >
                <Modal.Section>
                    <Text variant="bodyMd">
                        Adds the default login, credential and MCP config files for each known AI agent. Rules that already exist for these paths get the default agent, OS and role. Nothing is deleted.
                    </Text>
                </Modal.Section>
            </Modal>

            <Modal
                open={ruleModal}
                onClose={() => setRuleModal(false)}
                title={form.title}
                primaryAction={withPermissions({ content: 'Save', onAction: saveRule, loading: formSaving, requires: saveRequires })}
                secondaryActions={[{ content: 'Cancel', onAction: () => setRuleModal(false) }]}
            >
                <Modal.Section>
                    <VerticalStack gap="4">
                        <AgentPicker
                            value={form.agent}
                            onChange={v => updateForm('agent', v)}
                            options={agentOptions}
                            error={formErrors.agent}
                            requireAgent={form.requireAgent}
                        />
                        <HorizontalGrid columns={2} gap="4">
                            <Select
                                label="OS"
                                options={OS_OPTIONS}
                                value={form.os}
                                onChange={v => updateForm('os', v)}
                            />
                            <Select
                                label="Role"
                                options={ROLE_OPTIONS}
                                value={form.role}
                                onChange={v => updateForm('role', v)}
                            />
                        </HorizontalGrid>
                        <TextField
                            label="Path"
                            value={form.path}
                            onChange={v => updateForm('path', v)}
                            error={formErrors.path || undefined}
                            autoComplete="off"
                            placeholder="e.g. ~/.claude.json"
                            helpText="File path, directory path, or any pattern the agent should inspect."
                        />
                        <Checkbox
                            label="Recursive - descend into all subdirectories"
                            checked={form.recursive}
                            onChange={v => updateForm('recursive', v)}
                        />
                        <Checkbox
                            label="Existence only - record metadata without reading file contents"
                            checked={form.existenceOnly}
                            onChange={v => updateForm('existenceOnly', v)}
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
                components={flyComponents}
            />
        </>
    )
}

export default FileInspection

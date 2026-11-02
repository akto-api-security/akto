import React, { useState, useCallback, useMemo } from 'react'
import { Autocomplete, Banner, Card, Checkbox, Text, VerticalStack, HorizontalStack, HorizontalGrid, Spinner, Badge, Button, Box, Divider, Modal, ResourceItem, ResourceList, Select, Tabs, TextField } from '@shopify/polaris'
import PatternSettingsPage from '../components/PatternSettingsPage'
import GlobHelper from './GlobHelper'
import MatchTree from './MatchTree'
import FlyLayout from '../../../components/layouts/FlyLayout'
import settingRequests from '../api'
import { CellType } from '../../../components/tables/rows/GithubRow'
import func from '@/util/func'
import { withPermissions, usePermissions } from '@/util/permissions'
import { AgentIcon } from '../../nhi_governance/nhiViolationsData'
import { OsIcon } from '../../observe/agentic/DeviceEndpoints'

const resourceName = { singular: 'rule', plural: 'rules' }

const DEFAULT_AGENTS = ['Claude Code', 'Cursor', 'Codex', 'GitHub Copilot']

const OS_TABS = [
    { label: 'All OS', value: '' },
    { label: 'macOS', value: 'darwin' },
    { label: 'Linux', value: 'linux' },
    { label: 'Windows', value: 'windows' },
]

const OS_ICON_KEY = { darwin: 'mac', linux: 'linux', windows: 'windows' }

const ROLE_LABELS = { login: 'Login', credentials: 'Credentials', mcp_config: 'MCP config' }

const ROLE_OPTIONS = [
    { label: 'None', value: '' },
    ...Object.entries(ROLE_LABELS).map(([value, label]) => ({ label, value })),
]

function osMatches(rule, os) {
    return !os || !rule.os || rule.os === os
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
                {rule.os ? <OsIcon os={OS_ICON_KEY[rule.os]} /> : <Badge>All OS</Badge>}
                {rule.role && <Badge status="info">{ROLE_LABELS[rule.role] || rule.role}</Badge>}
                <Text variant="bodyMd" breakWord>{rule.path}</Text>
                {rule.maxDepth === -1 && <Badge>Recursive</Badge>}
                {rule.existenceOnly && <Badge>Existence only</Badge>}
            </HorizontalStack>
        </ResourceItem>
    )
}

function AgentsCard({ rules, selectedOs, onSelectOs, onAdd, onOpen, onEdit, onDelete }) {
    const { canCall } = usePermissions()
    const canAdd = canCall('api/addFileInspectionRule')
    const canUpdate = canCall('api/updateFileInspectionRule')
    const canDelete = canCall('api/deleteFileInspectionRule')

    const tabs = OS_TABS.map(t => ({
        id: `fi-os-${t.value || 'all'}`,
        content: `${t.label} (${rules.filter(r => osMatches(r, t.value)).length})`,
    }))
    const selectedIndex = Math.max(0, OS_TABS.findIndex(t => t.value === selectedOs))

    const groups = useMemo(() => {
        const byAgent = {}
        for (const r of rules) {
            if (!osMatches(r, selectedOs)) continue
            if (!byAgent[r.agent]) byAgent[r.agent] = []
            byAgent[r.agent].push(r)
        }
        return Object.keys(byAgent).sort().map(agent => ({ agent, rules: byAgent[agent] }))
    }, [rules, selectedOs])

    return (
        <Card>
            <VerticalStack gap="4">
                <HorizontalStack align="space-between" blockAlign="center">
                    <VerticalStack gap="1">
                        <Text variant="headingMd">Agents</Text>
                        <Text variant="bodySm" color="subdued">Files the agent inspects for each AI agent, per operating system.</Text>
                    </VerticalStack>
                    <Button onClick={() => onAdd('')} disabled={!canAdd}>Add agent file</Button>
                </HorizontalStack>
                <Tabs tabs={tabs} selected={selectedIndex} onSelect={i => onSelectOs(OS_TABS[i].value)} />
                {groups.length === 0 ? (
                    <Text color="subdued">No agent files for this OS yet.</Text>
                ) : groups.map((g, i) => (
                    <VerticalStack gap="2" key={g.agent}>
                        {i > 0 && <Divider />}
                        <HorizontalStack align="space-between" blockAlign="center">
                            <HorizontalStack gap="2" blockAlign="center">
                                <AgentIcon name={g.agent} />
                                <Text variant="headingSm">{g.agent}</Text>
                                <Badge>{`${g.rules.length} file${g.rules.length !== 1 ? 's' : ''}`}</Badge>
                            </HorizontalStack>
                            <Button plain onClick={() => onAdd(g.agent)} disabled={!canAdd}>Add file</Button>
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
        </Card>
    )
}

const EMPTY_FORM = { ruleId: null, agent: '', os: '', role: '', path: '', existenceOnly: false, recursive: false, requireAgent: false }

function FileInspection() {
    const [existenceOnly, setExistenceOnly] = useState(false)
    const [recursive, setRecursive] = useState(false)
    const [flyPath, setFlyPath] = useState(null)
    const [flyRuleId, setFlyRuleId] = useState(null)
    const [showFly, setShowFly] = useState(false)

    const [ruleModal, setRuleModal] = useState(false)
    const [form, setForm] = useState(EMPTY_FORM)
    const [formSaving, setFormSaving] = useState(false)
    const [formErrors, setFormErrors] = useState({})

    const [latestMap, setLatestMap] = useState({})
    const [allRules, setAllRules] = useState([])
    const [selectedOs, setSelectedOs] = useState('')
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

    function openResults(path, ruleId) {
        setFlyPath(path)
        setFlyRuleId(ruleId || null)
        setShowFly(true)
    }

    function updateForm(field, value) {
        setForm(f => ({ ...f, [field]: value }))
        setFormErrors(e => ({ ...e, [field]: '' }))
    }

    function openAddAgentFile(agent) {
        setForm({ ...EMPTY_FORM, agent: agent || '', os: selectedOs, requireAgent: true })
        setFormErrors({})
        setRuleModal(true)
    }

    function openEditRule(rule) {
        setForm({
            ruleId: rule.idHex,
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

    const agentsCard = (
        <AgentsCard
            key="fi-agents"
            rules={agentRules}
            selectedOs={selectedOs}
            onSelectOs={setSelectedOs}
            onAdd={openAddAgentFile}
            onOpen={rule => openResults(rule.path, rule.idHex)}
            onEdit={openEditRule}
            onDelete={deleteAgentRule}
        />
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
                secondaryActions={<GlobHelper onUse={v => setGlobTrigger({ value: v, id: Date.now() })} />}
            />

            <Modal
                open={ruleModal}
                onClose={() => setRuleModal(false)}
                title={form.ruleId ? 'Edit rule' : 'Add agent file'}
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
                                options={OS_TABS}
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
                components={showFly ? [<RuleExecutions key={flyPath} path={flyPath} ruleId={flyRuleId} />] : []}
            />
        </>
    )
}

export default FileInspection

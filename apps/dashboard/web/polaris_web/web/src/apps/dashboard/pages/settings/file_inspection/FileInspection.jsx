import React, { useState, useMemo } from 'react'
import { Autocomplete, Banner, Card, Checkbox, Text, VerticalStack, HorizontalStack, HorizontalGrid, Spinner, Badge, Button, Box, Divider, Icon, Modal, ResourceItem, ResourceList, Tabs, TextField } from '@shopify/polaris'
import { ChevronRightMinor } from '@shopify/polaris-icons'
import PatternSettingsPage from '../components/PatternSettingsPage'
import GlobHelper from './GlobHelper'
import MatchTree from './MatchTree'
import FlyLayout from '../../../components/layouts/FlyLayout'
import Dropdown from '../../../components/layouts/Dropdown'
import settingRequests from '../api'
import { CellType } from '../../../components/tables/rows/GithubRow'
import func from '@/util/func'
import { withPermissions, usePermissions } from '@/util/permissions'
import { AgentIcon } from '../../nhi_governance/nhiViolationsData'

const resourceName = { singular: 'rule', plural: 'rules' }

const DEFAULT_AGENTS = ['Claude Code', 'Codex', 'Cursor CLI']

const OS_TABS = [
    { label: 'macOS', value: 'darwin' },
    { label: 'Linux', value: 'linux' },
    { label: 'Windows', value: 'windows' },
]

const ROLE_LABELS = { login: 'Login', credentials: 'Credentials', mcp_config: 'MCP config' }

const ROLE_ORDER = ['mcp_config', 'login', 'credentials']

const ROLE_OPTIONS = ROLE_ORDER.map(value => ({ label: ROLE_LABELS[value], value }))

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

function AgentPicker({ value, onChange, options, error }) {
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
                    helpText="Pick an existing agent or type a new name."
                />
            }
        />
    )
}

function AgentFileRow({ config, onOpen, onEdit, onDelete, canUpdate, canDelete }) {
    const shortcutActions = [
        { content: 'Edit', onAction: () => onEdit(config), disabled: !canUpdate },
        { content: 'Delete', destructive: true, onAction: () => onDelete(config), disabled: !canDelete },
    ]
    return (
        <ResourceItem
            id={config.idHex}
            onClick={() => onOpen(config)}
            shortcutActions={shortcutActions}
            persistActions
            accessibilityLabel={`View results for ${config.path}`}
        >
            <Text variant="bodyMd" breakWord>{config.path}</Text>
        </ResourceItem>
    )
}

function AgentsCard({ configs, onOpenAgent }) {
    const agents = useMemo(() => {
        const byAgent = {}
        for (const c of configs) {
            if (!byAgent[c.agent]) byAgent[c.agent] = []
            byAgent[c.agent].push(c)
        }
        return Object.keys(byAgent).sort().map(agent => ({
            agent,
            counts: OS_TABS.map(t => byAgent[agent].filter(c => c.os === t.value).length),
        }))
    }, [configs])

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

function AgentDetail({ agent, configs, selectedOs, onSelectOs, onAdd, onOpen, onEdit, onDelete }) {
    const { canCall } = usePermissions()
    const canAdd = canCall('api/addFileInspectionRule')
    const canUpdate = canCall('api/updateFileInspectionRule')
    const canDelete = canCall('api/deleteFileInspectionRule')

    const tabs = OS_TABS.map(t => ({
        id: `fi-os-${t.value}`,
        content: `${t.label} (${configs.filter(c => c.os === t.value).length})`,
    }))
    const selectedIndex = Math.max(0, OS_TABS.findIndex(t => t.value === selectedOs))

    const groups = useMemo(() => ROLE_ORDER
        .map(role => ({ role, configs: configs.filter(c => c.os === selectedOs && c.role === role) }))
        .filter(g => g.configs.length > 0), [configs, selectedOs])

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
                    <VerticalStack gap="2" key={g.role}>
                        {i > 0 && <Divider />}
                        <HorizontalStack>
                            <Badge status="info">{ROLE_LABELS[g.role]}</Badge>
                        </HorizontalStack>
                        <ResourceList
                            resourceName={{ singular: 'file', plural: 'files' }}
                            items={g.configs}
                            renderItem={c => (
                                <AgentFileRow
                                    key={c.idHex}
                                    config={c}
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

const EMPTY_AGENT_FORM = { ruleId: null, title: 'Add file', agent: '', os: 'darwin', role: 'mcp_config', path: '', existenceOnly: false, maxDepth: 0 }

const EMPTY_RULE_FORM = { ruleId: null, path: '', existenceOnly: false, recursive: false }

function FileInspection() {
    const [existenceOnly, setExistenceOnly] = useState(false)
    const [recursive, setRecursive] = useState(false)
    const [flyView, setFlyView] = useState(null)
    const [showFly, setShowFly] = useState(false)
    const [restoreModal, setRestoreModal] = useState(false)
    const [restoring, setRestoring] = useState(false)
    const { canCall } = usePermissions()

    const [agentModal, setAgentModal] = useState(false)
    const [agentForm, setAgentForm] = useState(EMPTY_AGENT_FORM)
    const [ruleModal, setRuleModal] = useState(false)
    const [ruleForm, setRuleForm] = useState(EMPTY_RULE_FORM)
    const [formSaving, setFormSaving] = useState(false)
    const [formErrors, setFormErrors] = useState({})

    const [latestMap, setLatestMap] = useState({})
    const [configs, setConfigs] = useState([])
    const [selectedOs, setSelectedOs] = useState('darwin')
    const [refreshKey, setRefreshKey] = useState(0)
    const [globTrigger, setGlobTrigger] = useState(null)

    const agentOptions = useMemo(
        () => Array.from(new Set([...DEFAULT_AGENTS, ...configs.map(c => c.agent)])).sort(),
        [configs]
    )

    async function onFetch() {
        const res = await settingRequests.fetchFileInspectionRules()
        const rules = res?.rules || []
        setConfigs(rules.filter(r => r && r.path && r.agent))
        const map = rulesArrayToMap(rules)
        setLatestMap(map)
        return map
    }

    async function onAdd(path) {
        await settingRequests.addFileInspectionRule(path, existenceOnly, recursive ? -1 : 0)
        return onFetch()
    }

    async function onDelete(path) {
        await settingRequests.deleteFileInspectionRule(latestMap[path]?.idHex)
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

    function openAgentFileResults(config) {
        openResults(config.path, config.idHex, config.agent)
    }

    async function restoreDefaults() {
        setRestoring(true)
        try {
            const res = await settingRequests.restoreFileInspectionDefaults()
            setRefreshKey(k => k + 1)
            setRestoreModal(false)
            func.setToast(true, false, `Defaults restored: ${res?.createdCount ?? 0} added, ${res?.existingCount ?? 0} already present`)
        } catch {
            func.setToast(true, true, 'Failed to restore defaults')
        } finally {
            setRestoring(false)
        }
    }

    function updateAgentForm(field, value) {
        setAgentForm(f => ({ ...f, [field]: value }))
        setFormErrors(e => ({ ...e, [field]: '' }))
    }

    function updateRuleForm(field, value) {
        setRuleForm(f => ({ ...f, [field]: value }))
        setFormErrors(e => ({ ...e, [field]: '' }))
    }

    function openAddAgentFile(agent) {
        setAgentForm({ ...EMPTY_AGENT_FORM, title: agent ? 'Add file' : 'Add agent', agent: agent || '', os: selectedOs })
        setFormErrors({})
        setAgentModal(true)
    }

    function openEditAgentFile(config) {
        setAgentForm({ ruleId: config.idHex, title: 'Edit file', agent: config.agent, os: config.os, role: config.role, path: config.path, existenceOnly: !!config.existenceOnly, maxDepth: config.maxDepth ?? 0 })
        setFormErrors({})
        setAgentModal(true)
    }

    function deleteAgentFile(config) {
        func.showConfirmationModal(`Delete ${config.path} from ${config.agent}?`, 'Delete', async () => {
            try {
                await settingRequests.deleteFileInspectionRule(config.idHex)
                setRefreshKey(k => k + 1)
                func.setToast(true, false, 'File deleted')
            } catch {
                func.setToast(true, true, 'Failed to delete file')
            }
        })
    }

    function validatePath(path) {
        if (!path) return 'Path is required'
        if (path === '/') return "Path cannot be the root '/'"
        return ''
    }

    async function saveAgentFile() {
        const path = agentForm.path.trim()
        const agent = agentForm.agent.trim()
        const errors = {}
        const pathError = validatePath(path)
        if (pathError) errors.path = pathError
        if (!agent) errors.agent = 'Agent is required'
        if (Object.keys(errors).length > 0) { setFormErrors(errors); return }
        setFormSaving(true)
        try {
            if (agentForm.ruleId) {
                await settingRequests.updateFileInspectionRule(agentForm.ruleId, path, agentForm.existenceOnly, agentForm.maxDepth, agent, agentForm.os, agentForm.role)
            } else {
                await settingRequests.addFileInspectionRule(path, agentForm.existenceOnly, agentForm.maxDepth, agent, agentForm.os, agentForm.role)
            }
            setRefreshKey(k => k + 1)
            setAgentModal(false)
            func.setToast(true, false, agentForm.ruleId ? 'File updated' : 'File added')
        } catch {
            func.setToast(true, true, agentForm.ruleId ? 'Failed to update file' : 'Failed to add file')
        } finally {
            setFormSaving(false)
        }
    }

    function openEditRule(rule) {
        setRuleForm({ ruleId: rule.idHex, path: rule.path || '', existenceOnly: !!rule.existenceOnly, recursive: rule.maxDepth === -1 })
        setFormErrors({})
        setRuleModal(true)
    }

    async function saveRule() {
        const path = ruleForm.path.trim()
        const pathError = validatePath(path)
        if (pathError) { setFormErrors({ path: pathError }); return }
        setFormSaving(true)
        try {
            await settingRequests.updateFileInspectionRule(ruleForm.ruleId, path, ruleForm.existenceOnly, ruleForm.recursive ? -1 : 0)
            setRefreshKey(k => k + 1)
            setRuleModal(false)
            func.setToast(true, false, 'Rule updated')
        } catch {
            func.setToast(true, true, 'Failed to update rule')
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

    const flyAgentConfigs = flyView?.type === 'agent' ? configs.filter(c => c.agent === flyView.agent) : []

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
                configs={flyAgentConfigs}
                selectedOs={selectedOs}
                onSelectOs={setSelectedOs}
                onAdd={openAddAgentFile}
                onOpen={openAgentFileResults}
                onEdit={openEditAgentFile}
                onDelete={deleteAgentFile}
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
        <AgentsCard key="fi-agents" configs={configs} onOpenAgent={openAgent} />
    )

    const customPathsHeader = (
        <VerticalStack gap="1" key="fi-custom-header">
            <Text variant="headingMd">Custom paths</Text>
            <Text variant="bodySm" color="subdued">Paths not tied to an agent. Applies to all operating systems.</Text>
        </VerticalStack>
    )

    const agentSaveRequires = agentForm.ruleId ? ['api/updateFileInspectionRule'] : ['api/addFileInspectionRule']

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
                        Adds any missing default login, credential and MCP config files for each known AI agent. Nothing is deleted.
                    </Text>
                </Modal.Section>
            </Modal>

            <Modal
                open={agentModal}
                onClose={() => setAgentModal(false)}
                title={agentForm.title}
                primaryAction={withPermissions({ content: 'Save', onAction: saveAgentFile, loading: formSaving, requires: agentSaveRequires })}
                secondaryActions={[{ content: 'Cancel', onAction: () => setAgentModal(false) }]}
            >
                <Modal.Section>
                    <VerticalStack gap="4">
                        <AgentPicker
                            value={agentForm.agent}
                            onChange={v => updateAgentForm('agent', v)}
                            options={agentOptions}
                            error={formErrors.agent}
                        />
                        <HorizontalGrid columns={2} gap="4">
                            <Dropdown
                                id="agent-file-os"
                                label="OS"
                                menuItems={OS_TABS}
                                initial={agentForm.os}
                                selected={v => updateAgentForm('os', v)}
                            />
                            <Dropdown
                                id="agent-file-role"
                                label="Role"
                                menuItems={ROLE_OPTIONS}
                                initial={agentForm.role}
                                selected={v => updateAgentForm('role', v)}
                            />
                        </HorizontalGrid>
                        <TextField
                            label="Path"
                            value={agentForm.path}
                            onChange={v => updateAgentForm('path', v)}
                            error={formErrors.path || undefined}
                            autoComplete="off"
                            placeholder="e.g. ~/.claude.json"
                            helpText="File path, directory path, or any pattern the agent should inspect."
                        />
                    </VerticalStack>
                </Modal.Section>
            </Modal>

            <Modal
                open={ruleModal}
                onClose={() => setRuleModal(false)}
                title="Edit rule"
                primaryAction={withPermissions({ content: 'Save', onAction: saveRule, loading: formSaving, requires: ['api/updateFileInspectionRule'] })}
                secondaryActions={[{ content: 'Cancel', onAction: () => setRuleModal(false) }]}
            >
                <Modal.Section>
                    <VerticalStack gap="4">
                        <TextField
                            label="Path"
                            value={ruleForm.path}
                            onChange={v => updateRuleForm('path', v)}
                            error={formErrors.path || undefined}
                            autoComplete="off"
                            helpText="File path, directory path, or any pattern the agent should inspect."
                        />
                        <Checkbox
                            label="Recursive - descend into all subdirectories"
                            checked={ruleForm.recursive}
                            onChange={v => updateRuleForm('recursive', v)}
                        />
                        <Checkbox
                            label="Existence only - record metadata without reading file contents"
                            checked={ruleForm.existenceOnly}
                            onChange={v => updateRuleForm('existenceOnly', v)}
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

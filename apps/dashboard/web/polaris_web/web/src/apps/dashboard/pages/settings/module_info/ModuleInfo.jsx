import { Badge, Banner, Button, IndexFiltersMode, Modal, Text, Tooltip } from "@shopify/polaris"
import { useEffect, useMemo, useState } from "react"
import settingRequests from "../api"
import func from "@/util/func"
import ModuleEnvConfigComponent from "../health_logs/ModuleEnvConfig"
import EmptyCard from "../../dashboard/new_components/EmptyCard"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import { usePermissions } from "@/util/permissions"
import { maskedToken } from "./maskedToken"
import { buildSortOptions, disambiguateLabel } from "./tableConfig"

const CONFIGURABLE_MODULE_TYPES = ['TRAFFIC_COLLECTOR', 'AKTO_AGENT_GATEWAY', 'THREAT_DETECTION', 'MINI_RUNTIME']
const VIEWABLE_MODULE_TYPES = [...CONFIGURABLE_MODULE_TYPES, 'MINI_TESTING']
const REBOOT_ELIGIBLE_MODULE_TYPES = VIEWABLE_MODULE_TYPES
const RESTARTABLE_HEARTBEAT_SECONDS = 120
const SEARCH_KEYS = ['moduleType', 'name', 'tokenName', 'currentVersion', 'status']
const DEFAULT_SORT_KEY = 'lastHeartbeatTs'

const resourceName = { singular: 'module', plural: 'modules' }

const STATUS_HINT = {
    'Token revoked': 'Token revoked. Generate a new token and redeploy this module.',
    'Token expired': 'Token expired. Please re-authenticate this module.',
}

// A restart writes a new module_info row while the previous heartbeat stays inside the
// 5-minute fetch window. Same module type + name is the same instance; keep the latest start.
function moduleInstanceKey(module) {
    const name = module?.name || module?.id || ''
    return `${module?.moduleType || ''}::${name}`
}

function isLaterInstance(candidate, current) {
    const candidateStarted = candidate?.startedTs || 0
    const currentStarted = current?.startedTs || 0
    if (candidateStarted !== currentStarted) {
        return candidateStarted > currentStarted
    }
    return (candidate?.lastHeartbeatReceived || 0) > (current?.lastHeartbeatReceived || 0)
}

function latestModuleInstances(modules) {
    const byKey = new Map()
    for (const module of modules || []) {
        const key = moduleInstanceKey(module)
        const existing = byKey.get(key)
        if (!existing || isLaterInstance(module, existing)) {
            byKey.set(key, module)
        }
    }
    return Array.from(byKey.values())
}

function canRebootModule(module) {
    const recently = Math.floor(Date.now() / 1000) - RESTARTABLE_HEARTBEAT_SECONDS
    return module.lastHeartbeatReceived >= recently &&
        module.name &&
        (module.name.startsWith('Default_') || module.name.startsWith('akto-mr') || REBOOT_ELIGIBLE_MODULE_TYPES.includes(module.moduleType))
}

function moduleStatus(module) {
    if (module.additionalData?.tokenRevoked) return 'Token revoked'
    if (module.additionalData?.tokenExpired) return 'Token expired'
    return 'Active'
}

function statusBadge(status) {
    const badge = <Badge status={status === 'Active' ? 'success' : 'critical'}>{status}</Badge>
    return STATUS_HINT[status] ? <Tooltip content={STATUS_HINT[status]}>{badge}</Tooltip> : badge
}

const ModuleInfo = () => {
    const { canCall, canCallAll } = usePermissions()
    // only admins can list tokens, so only they get the Token column
    const canSeeTokens = canCall('api/fetchAuthTokens')
    const [moduleInfos, setModuleInfos] = useState([])
    const [allowedEnvFields, setAllowedEnvFields] = useState([])
    const [loading, setLoading] = useState(true)
    const [tableKey, setTableKey] = useState(0)
    const [tokenByModuleId, setTokenByModuleId] = useState({})
    const [modalActive, setModalActive] = useState(false)
    const [selectedModule, setSelectedModule] = useState(null)

    const fetchModuleInfo = async () => {
        try {
            const response = await settingRequests.fetchModuleInfo()
            setModuleInfos(response?.moduleInfos || [])
            setAllowedEnvFields(response?.allowedEnvFields || [])
            setTableKey(key => key + 1)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchModuleInfo()
    }, [])

    const fetchTokens = async () => {
        try {
            const response = await settingRequests.fetchAuthTokens()
            // each token lists the modules using it, so module rows never need to carry a token id
            const byModule = {}
            ;(response?.authTokens || []).forEach(token => (token.modules || []).forEach(module => { byModule[module.id] = token }))
            setTokenByModuleId(byModule)
        } catch (e) {
            // the Token column just stays empty if the list can't be loaded
        }
    }

    useEffect(() => {
        if (canSeeTokens) fetchTokens()
    }, [canSeeTokens])

    const { rows, eligibleById } = useMemo(() => {
        const eligible = {}
        const built = latestModuleInstances(moduleInfos).map(module => {
            const canReboot = !!canRebootModule(module)
            eligible[module.id] = canReboot
            const status = moduleStatus(module)
            const token = tokenByModuleId[module.id]
            return {
                id: module.id,
                moduleType: module.moduleType || '-',
                typeCell: VIEWABLE_MODULE_TYPES.includes(module.moduleType) ? (
                    <Button plain onClick={(event) => {
                        event?.stopPropagation?.()
                        setSelectedModule(module)
                        setModalActive(true)
                    }}>{module.moduleType}</Button>
                ) : (module.moduleType || '-'),
                name: module.name || '-',
                tokenName: token ? (token.hint ? `${token.name} (${maskedToken(token.hint)})` : token.name) : '-',
                currentVersion: module.currentVersion || '-',
                startedAt: func.epochToDateTime(module.startedTs),
                startedTs: module.startedTs || 0,
                lastHeartbeat: func.epochToDateTime(module.lastHeartbeatReceived),
                lastHeartbeatTs: module.lastHeartbeatReceived || 0,
                status,
                statusCell: statusBadge(status),
                restartable: canReboot ? 'Yes' : 'No',
            }
        })
        return { rows: built, eligibleById: eligible }
    }, [moduleInfos, tokenByModuleId])

    const headers = useMemo(() => [
        { title: 'Type', text: 'Type', value: 'typeCell', filterKey: 'moduleType', showFilter: true, sortKey: 'moduleType', sortActive: true },
        { title: 'Name', text: 'Name', value: 'name', sortKey: 'name', sortActive: true },
        ...(canSeeTokens ? [{ title: 'Token', text: 'Token', value: 'tokenName' }] : []),
        { title: 'Version', text: 'Version', value: 'currentVersion', filterKey: 'currentVersion', showFilter: true },
        { title: 'Started', text: 'Started', value: 'startedAt', sortKey: 'startedTs', sortActive: true },
        { title: 'Last heartbeat', text: 'Last heartbeat', value: 'lastHeartbeat', sortKey: 'lastHeartbeatTs', sortActive: true },
        { title: 'Status', text: 'Status', value: 'statusCell', filterKey: 'status', showFilter: true },
        { title: 'Restartable', text: 'Restartable', value: 'restartable' },
    ], [canSeeTokens])

    const sortOptions = useMemo(() => buildSortOptions(headers, DEFAULT_SORT_KEY), [headers])

    const handleRebootModules = async (selectedIds, deleteTopicAndReboot) => {
        const eligibleIds = selectedIds.filter(id => eligibleById[id])
        if (eligibleIds.length === 0) {
            func.setToast(true, true, "None of the selected modules can be restarted right now")
            return
        }
        try {
            await settingRequests.rebootModules(eligibleIds, deleteTopicAndReboot)
            const rebootType = deleteTopicAndReboot ? "Container reboot" : "Restart process"
            const skipped = selectedIds.length - eligibleIds.length
            func.setToast(true, false, `${rebootType} flag set for ${eligibleIds.length} module(s)${skipped > 0 ? `; ${skipped} ineligible skipped` : ''}`)
            await fetchModuleInfo()
        } catch (error) {
            func.setToast(true, true, "Failed to set reboot flag for modules")
        }
    }

    const promotedBulkActions = (selectedIds) => [
        { content: 'Restart process', requires: 'api/rebootModules', onAction: () => handleRebootModules(selectedIds, false) },
        { content: 'Delete topic and restart process', requires: 'api/rebootModules', onAction: () => handleRebootModules(selectedIds, true) },
    ]

    const handleModalClose = () => {
        setModalActive(false)
        setSelectedModule(null)
    }

    const handleSaveEnv = async (moduleId, moduleName, envData) => {
        try {
            if (selectedModule?.moduleType === 'MINI_RUNTIME') {
                await settingRequests.updateRuntimeEnvOverrides(envData)
                const miniRuntimeIds = latestModuleInstances(moduleInfos)
                    .filter(m => m.moduleType === 'MINI_RUNTIME' && canRebootModule(m))
                    .map(m => m.id)
                await settingRequests.rebootModules(miniRuntimeIds, false)
            } else {
                await settingRequests.updateModuleEnvAndReboot(moduleId, moduleName, envData)
            }
            func.setToast(true, false, "Environment config saved successfully. Module will reboot.")
            handleModalClose()
            await fetchModuleInfo()
        } catch (error) {
            func.setToast(true, true, "Failed to update environment config")
        }
    }

    // MINI_RUNTIME env saves update the shared overrides and then restart every mini-runtime
    const canSaveEnv = selectedModule?.moduleType === 'MINI_RUNTIME'
        ? canCallAll('api/updateRuntimeEnvOverrides', 'api/rebootModules')
        : canCall('api/updateModuleEnvAndReboot')
    const isEnvEditable = CONFIGURABLE_MODULE_TYPES.includes(selectedModule?.moduleType) && canSaveEnv

    return (
        <>
            {!loading && rows.length === 0 ? (
                <EmptyCard
                    title="No modules found"
                    subTitleComponent={
                        <Text variant="bodyMd" alignment="center" tone="subdued">
                            No active modules are currently running. Modules will appear here once they start sending heartbeats.
                        </Text>
                    }
                />
            ) : (
                <GithubSimpleTable
                    key={`modules-${tableKey}`}
                    pageLimit={20}
                    data={rows}
                    resourceName={resourceName}
                    sortOptions={sortOptions}
                    filters={[]}
                    headers={headers}
                    headings={headers}
                    searchKeys={SEARCH_KEYS}
                    selectable={true}
                    promotedBulkActions={promotedBulkActions}
                    useNewRow={true}
                    condensedHeight={true}
                    mode={IndexFiltersMode.Filtering}
                    disambiguateLabel={disambiguateLabel}
                    contentAwareFilterCache={true}
                    skipUrlFilters={true}
                    loading={loading}
                />
            )}

            <Modal
                open={modalActive}
                onClose={handleModalClose}
                title={`Configure ${selectedModule?.moduleType || 'Module'}`}
                large
            >
                <Modal.Section>
                    {selectedModule?.moduleType === 'MINI_RUNTIME' && (
                        <Banner tone="warning" title="Applies to all instances">
                        </Banner>
                    )}
                    <ModuleEnvConfigComponent
                        title="Environment Variables"
                        description={
                            isEnvEditable
                                ? `Configure environment variables for ${selectedModule?.name || 'module'}`
                                : `Environment variables for ${selectedModule?.name || 'module'} (read-only)`
                        }
                        module={selectedModule}
                        allowedEnvFields={allowedEnvFields}
                        onSaveEnv={handleSaveEnv}
                        readOnly={!isEnvEditable}
                    />
                </Modal.Section>
            </Modal>
        </>
    )
}

export default ModuleInfo

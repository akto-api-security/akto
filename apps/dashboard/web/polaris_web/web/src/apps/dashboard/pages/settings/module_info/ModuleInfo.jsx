import { Button, LegacyCard, DataTable, Checkbox, Modal, Text, Tooltip, Icon, HorizontalStack, Banner } from "@shopify/polaris"
import { AlertMinor } from "@shopify/polaris-icons"
import { useEffect, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import ModuleEnvConfigComponent from "../health_logs/ModuleEnvConfig"
import EmptyCard from "../../dashboard/new_components/EmptyCard"
import { usePermissions, whenAllowed } from "@/util/permissions"
import { maskedToken } from "./maskedToken"

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

const ModuleInfo = () => {
    const { canCall, canCallAll } = usePermissions()
    const [ moduleInfos, setModuleInfos ] = useState([])
    const [ allowedEnvFields, setAllowedEnvFields ] = useState([])
    const [ selectedModules, setSelectedModules ] = useState([])
    const [ modalActive, setModalActive ] = useState(false)
    const [ selectedModule, setSelectedModule ] = useState(null)
    const [ tokenByModuleId, setTokenByModuleId ] = useState({})
    // only admins can list tokens, so only they get the Token column
    const canSeeTokens = canCall('api/fetchAuthTokens')

    const CONFIGURABLE_MODULE_TYPES = ['TRAFFIC_COLLECTOR', 'AKTO_AGENT_GATEWAY', 'THREAT_DETECTION', 'MINI_RUNTIME'];
    const VIEWABLE_MODULE_TYPES = [...CONFIGURABLE_MODULE_TYPES, 'MINI_TESTING'];
    const REBOOT_ELIGIBLE_MODULE_TYPES = VIEWABLE_MODULE_TYPES;

    const fetchModuleInfo = async () => {
        const response = await settingRequests.fetchModuleInfo();
        setModuleInfos(response.moduleInfos || []);
        setAllowedEnvFields(response.allowedEnvFields || []);
    }

    useEffect(() => {
        fetchModuleInfo()
    }, [])

    const fetchTokens = async () => {
        try {
            const response = await settingRequests.fetchAuthTokens();
            // each token lists the modules using it, so module rows never need to carry a token id
            const byModule = {};
            (response?.authTokens || []).forEach(token => (token.modules || []).forEach(module => { byModule[module.id] = token }));
            setTokenByModuleId(byModule);
        } catch (e) {
            // the Token column just stays empty if the list can't be loaded
        }
    }

    useEffect(() => {
        if (canSeeTokens) fetchTokens()
    }, [canSeeTokens])

    const handleRebootModules = async (deleteTopicAndReboot) => {
        if (selectedModules.length === 0) {
            func.setToast(true, true, "Please select at least one module to reboot");
            return;
        }
        try {
            await settingRequests.rebootModules(selectedModules, deleteTopicAndReboot);
            const rebootType = deleteTopicAndReboot ? "Container reboot" : "Restart process";
            func.setToast(true, false, `${rebootType} flag set for eligible modules`);
            setSelectedModules([]);
            await fetchModuleInfo();
        } catch (error) {
            func.setToast(true, true, "Failed to set reboot flag for modules");
        }
    }

    const toggleModuleSelection = (moduleId) => {
        setSelectedModules(prev => {
            if (prev.includes(moduleId)) {
                return prev.filter(id => id !== moduleId);
            } else {
                return [...prev, moduleId];
            }
        });
    }

    const canRebootModule = (module) => {
        const twoMinutesAgo = Math.floor(Date.now() / 1000) - 120;
        return module.lastHeartbeatReceived >= twoMinutesAgo &&
               module.name &&
               (module.name.startsWith('Default_') || module.name.startsWith('akto-mr')|| REBOOT_ELIGIBLE_MODULE_TYPES.includes(module.moduleType));
    }

    const handleModuleTypeClick = (module) => {
        setSelectedModule(module);
        setModalActive(true);
    }

    const handleModalClose = () => {
        setModalActive(false);
        setSelectedModule(null);
    }

    const handleSaveEnv = async (moduleId, moduleName, envData) => {
        try {
            if (selectedModule?.moduleType === 'MINI_RUNTIME') {
                await settingRequests.updateRuntimeEnvOverrides(envData);
                const miniRuntimeIds = latestModuleInstances(moduleInfos)
                    .filter(m => m.moduleType === 'MINI_RUNTIME' && canRebootModule(m))
                    .map(m => m.id);
                await settingRequests.rebootModules(miniRuntimeIds, false);
            } else {
                await settingRequests.updateModuleEnvAndReboot(moduleId, moduleName, envData);
            }
            func.setToast(true, false, "Environment config saved successfully. Module will reboot.");
            handleModalClose();
            await fetchModuleInfo();
        } catch (error) {
            func.setToast(true, true, "Failed to update environment config");
        }
    }

    // MINI_RUNTIME env saves update the shared overrides and then restart every mini-runtime
    const canSaveEnv = selectedModule?.moduleType === 'MINI_RUNTIME'
        ? canCallAll('api/updateRuntimeEnvOverrides', 'api/rebootModules')
        : canCall('api/updateModuleEnvAndReboot')
    const isEnvEditable = CONFIGURABLE_MODULE_TYPES.includes(selectedModule?.moduleType) && canSaveEnv

    const sortedModuleInfos = latestModuleInstances(moduleInfos)
        .sort((a, b) => (b.lastHeartbeatReceived || 0) - (a.lastHeartbeatReceived || 0));

    // token name and its last characters, so the module can be matched to a row in the Tokens tab
    const tokenCell = (module) => {
        const token = tokenByModuleId[module.id]
        if (!token) return '-'
        return token.hint ? `${token.name} (${maskedToken(token.hint)})` : token.name
    }

    const moduleInfoRows = sortedModuleInfos.map(module => {
        const isEligible = canRebootModule(module);
        const isSelected = selectedModules.includes(module.id);
        const isViewable = VIEWABLE_MODULE_TYPES.includes(module.moduleType);

        return [
            isViewable ? (
                <button
                    onClick={() => handleModuleTypeClick(module)}
                    style={{ background: 'none', border: 'none', color: '#0070f3', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                >
                    {module.moduleType || '-'}
                </button>
            ) : (
                module.moduleType || '-'
            ),
            module.additionalData?.tokenExpired ? (
                <Tooltip content="Token expired. Please re-authenticate this module.">
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <Icon source={AlertMinor} color="critical" />
                        <span>{module.name || '-'}</span>
                    </span>
                </Tooltip>
            ) : module.additionalData?.tokenRevoked ? (
                <Tooltip content="Token revoked. Generate a new token and redeploy this module.">
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <Icon source={AlertMinor} color="critical" />
                        <span>{module.name || '-'}</span>
                    </span>
                </Tooltip>
            ) : (module.name || '-'),
            ...(canSeeTokens ? [tokenCell(module)] : []),
            module.currentVersion || '-',
            func.epochToDateTime(module.startedTs),
            func.epochToDateTime(module.lastHeartbeatReceived),
            isEligible ? 'Yes' : 'No',
            <Checkbox
                checked={isSelected}
                onChange={() => toggleModuleSelection(module.id)}
                disabled={!isEligible}
            />,
        ];
    });

    return (
        <>
            {sortedModuleInfos.length > 0 ? (
                <LegacyCard
                    sectioned
                    title="Module Information"
                    actions={[
                        {
                            content: 'Restart process',
                            onAction: () => handleRebootModules(false),
                            disabled: selectedModules.length === 0,
                            ...whenAllowed(canCall('api/rebootModules'))
                        },
                        {
                            content: 'Delete topic and restart process',
                            onAction: () => handleRebootModules(true),
                            disabled: selectedModules.length === 0,
                            ...whenAllowed(canCall('api/rebootModules'))
                        }
                    ]}
                >
                    <DataTable
                        columnContentTypes={[
                            'text',
                            'text',
                            ...(canSeeTokens ? ['text'] : []),
                            'text',
                            'text',
                            'text',
                            'text',
                            'text'
                        ]}
                        headings={[
                            'Type',
                            'Name',
                            ...(canSeeTokens ? ['Token'] : []),
                            'Version',
                            'Started At',
                            'Last Heartbeat',
                            'Reboot Eligible',
                            'Select'
                        ]}
                        rows={moduleInfoRows}
                    />
                </LegacyCard>
            ) : (
                <EmptyCard
                    title="No modules found"
                    subTitleComponent={
                        <Text variant="bodyMd" alignment="center" tone="subdued">
                            No active modules are currently running. Modules will appear here once they start sending heartbeats.
                        </Text>
                    }
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

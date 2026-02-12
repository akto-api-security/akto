import { Button, LegacyCard, DataTable, Checkbox, Modal, Text } from "@shopify/polaris"
import { useEffect, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import ModuleEnvConfigComponent from "../health_logs/ModuleEnvConfig"
import EmptyCard from "../../dashboard/new_components/EmptyCard"

const ModuleInfo = () => {
    const [ moduleInfos, setModuleInfos ] = useState([])
    const [ allowedEnvFields, setAllowedEnvFields ] = useState([])
    const [ selectedModules, setSelectedModules ] = useState([])
    const [ modalActive, setModalActive ] = useState(false)
    const [ selectedModule, setSelectedModule ] = useState(null)

    const CONFIGURABLE_MODULE_TYPES = ['TRAFFIC_COLLECTOR', 'AKTO_AGENT_GATEWAY', 'THREAT_DETECTION'];

    const fetchModuleInfo = async () => {
        const response = await settingRequests.fetchModuleInfo();
        setModuleInfos(response.moduleInfos || []);
        setAllowedEnvFields(response.allowedEnvFields || []);
    }

    useEffect(() => {
        fetchModuleInfo()
    }, [])

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
               (module.name.startsWith('Default_') || module.name.startsWith('akto-mr')|| CONFIGURABLE_MODULE_TYPES.includes(module.moduleType));
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
            await settingRequests.updateModuleEnvAndReboot(moduleId, moduleName, envData);
            func.setToast(true, false, "Environment config saved successfully. Module will reboot.");
            handleModalClose();
            await fetchModuleInfo();
        } catch (error) {
            func.setToast(true, true, "Failed to update environment config");
        }
    }

    const sortedModuleInfos = [...moduleInfos].sort((a, b) => (b.lastHeartbeatReceived || 0) - (a.lastHeartbeatReceived || 0));

    const moduleInfoRows = sortedModuleInfos.map(module => {
        const isEligible = canRebootModule(module);
        const isSelected = selectedModules.includes(module.id);
        const isConfigurable = CONFIGURABLE_MODULE_TYPES.includes(module.moduleType);

        return [
            isConfigurable ? (
                <button
                    onClick={() => handleModuleTypeClick(module)}
                    style={{ background: 'none', border: 'none', color: '#0070f3', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                >
                    {module.moduleType || '-'}
                </button>
            ) : (
                module.moduleType || '-'
            ),
            module.name || '-',
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
            {moduleInfos && moduleInfos.length > 0 ? (
                <LegacyCard
                    sectioned
                    title="Module Information"
                    actions={[
                        {
                            content: 'Restart process',
                            onAction: () => handleRebootModules(false),
                            disabled: selectedModules.length === 0
                        },
                        {
                            content: 'Delete topic and restart process',
                            onAction: () => handleRebootModules(true),
                            disabled: selectedModules.length === 0
                        }
                    ]}
                >
                    <DataTable
                        columnContentTypes={[
                            'text',
                            'text',
                            'text',
                            'text',
                            'text',
                            'text',
                            'text'
                        ]}
                        headings={[
                            'Type',
                            'Name',
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
                    <ModuleEnvConfigComponent
                        title="Environment Variables"
                        description={`Configure environment variables for ${selectedModule?.name || 'module'}`}
                        module={selectedModule}
                        allowedEnvFields={allowedEnvFields}
                        onSaveEnv={handleSaveEnv}
                    />
                </Modal.Section>
            </Modal>
        </>
    )
}

export default ModuleInfo

import { Button, ButtonGroup, LegacyCard, Text, VerticalStack, DataTable, Checkbox, HorizontalStack, RadioButton, Modal, Link } from "@shopify/polaris"
import { useEffect, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import LogsContainer from "./LogsContainer";
import Dropdown from "../../../components/layouts/Dropdown"
import { saveAs } from 'file-saver'
import ModuleEnvConfigComponent from "./ModuleEnvConfig"

const Logs = () => {
    const fiveMins = 1000 * 60 * 5

    const [ logs, setLogs ] = useState({
        startTime: null,
        endTime: null,
        logGroup: 'DASHBOARD',
        logData: []
    })
    const [ loading, setLoading ] = useState(false)
    const [ moduleInfos, setModuleInfos ] = useState([])
    const [ allowedEnvFields, setAllowedEnvFields ] = useState([])
    const [ selectedModules, setSelectedModules ] = useState([])
    const [ modalActive, setModalActive ] = useState(false)
    const [ selectedModule, setSelectedModule ] = useState(null)
    const logGroupSelected = logs.logGroup !== ''
    const hasAccess = func.checkUserValidForIntegrations()

    const logGroupOptions = [
        { label: "Runtime", value: "RUNTIME" },
        { label: "Dashboard", value: "DASHBOARD" },
        { label: "Testing", value: "TESTING" },
        { label: "Puppeteer", value: "PUPPETEER" },
        { label: "Threat", value: "THREAT_DETECTION" },
        { label: "Data Ingestion", value: "DATA_INGESTION" },
    ];
  
    const handleSelectLogGroup = (logGroup) => {
       setLogs(previousState => ({ ...previousState, logData: [], logGroup: logGroup }))
    }
    
    const fetchLogsFromDb = async (startTime, endTime, refresh = false) => {
        if (logs.logGroup !== '') {
            setLoading(true)
            const logsResponse = await settingRequests.fetchLogsFromDb(
                Math.floor(startTime / 1000), 
                Math.floor(endTime  / 1000),
                logs.logGroup
            )
            
            setLogs(previousState => (
                {
                    ...logs,
                    startTime: startTime,
                    endTime: endTime,
                    logData: refresh ? [...logsResponse.logs] : [...logsResponse.logs, ...previousState.logData]
                }))

            setLoading(false)
        }
    }

    const fetchModuleInfo = async () => {
        const response = await settingRequests.fetchModuleInfo();
        setModuleInfos(response.moduleInfos || []);
        setAllowedEnvFields(response.allowedEnvFields || []);
    }

    useEffect(() => {
        const startTime = Date.now() - fiveMins
        const endTime = Date.now() 
        if(hasAccess){
            fetchLogsFromDb(startTime, endTime)
            fetchModuleInfo()
        }
    }, [logs.logGroup])

   const exportLogsCsv = () => {
        let headers = ['timestamp', 'log'];
        let csv = headers.join(",")+"\r\n"
        logs.logData.forEach(log => {
            csv += func.epochToDateTime(log.timestamp) +","+ log.log + "\r\n"
        })
        let blob = new Blob([csv], {
            type: "application/csvcharset=UTF-8"
        });
        saveAs(blob, "log.csv");
   } 

    const handleRefresh = () => {
        const startTime = Date.now() - fiveMins;
        const endTime = Date.now();
        if(hasAccess){
            fetchLogsFromDb(startTime, endTime, true)
            fetchModuleInfo()
        }
    }

    const handlePreviousFiveMinutesLogs = () => {
        const startTime = logs.startTime - fiveMins;
        const endTime = logs.startTime;
        if(hasAccess){
            fetchLogsFromDb(startTime, endTime)
        }
    }

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
            await fetchModuleInfo(); // Refresh the module list
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
               (module.name.startsWith('Default_') || module.moduleType === 'TRAFFIC_COLLECTOR');
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

    // Sort moduleInfos by lastHeartbeatReceived in descending order
    const sortedModuleInfos = [...moduleInfos].sort((a, b) => (b.lastHeartbeatReceived || 0) - (a.lastHeartbeatReceived || 0));

    const CONFIGURABLE_MODULE_TYPES = ['TRAFFIC_COLLECTOR', 'THREAT_DETECTION'];

    const moduleInfoRows = sortedModuleInfos.map(module => {
        const isEligible = canRebootModule(module);
        const isSelected = selectedModules.includes(module.id);
        const isConfigurable = CONFIGURABLE_MODULE_TYPES.includes(module.moduleType);

        return [
            isConfigurable ? (
                <Link onClick={() => handleModuleTypeClick(module)} removeUnderline>{module.moduleType || '-'}</Link>
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
        <VerticalStack>
            <LegacyCard
                sectioned
                title="Logs"
                actions={[
                    { content: 'Export', onAction: exportLogsCsv },
                    { content: 'Configure log level'}
                ]}
            >
                <Text variant="bodyMd">
                    API logs capture detailed records of API requests and responses, including metadata such as timestamps, request headers, payload data, and authentication details.
                </Text>
                <br />

                <div style={{ display: "grid", gridTemplateColumns: "auto max-content", gap: "10px"}}>
                    <Dropdown
                        menuItems={logGroupOptions}
                        initial="Dashboard"
                        selected={handleSelectLogGroup}
                        />
                    <ButtonGroup segmented>
                        <Button onClick={handleRefresh} disabled={!logGroupSelected}>Refresh</Button>
                        <Button onClick={handlePreviousFiveMinutesLogs} disabled={!logGroupSelected}>-5 minutes</Button>
                    </ButtonGroup>
                </div>
              
                <br />

                {
                    logGroupSelected ? 
                        // loading ? <SpinnerCentered/> : <LogsContainer logs={logs} />  
                        <LogsContainer logs={logs} />  
                        : <Text variant="bodyMd">Select log group to fetch logs</Text>
                }
            </LegacyCard>

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
            ) : <></>}

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
        </VerticalStack>
    )
}

export default Logs
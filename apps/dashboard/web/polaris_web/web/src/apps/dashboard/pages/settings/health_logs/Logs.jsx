import { Button, ButtonGroup, LegacyCard, Text, DataTable, VerticalStack } from "@shopify/polaris"
import { useEffect, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import LogsContainer from "./LogsContainer";
import Dropdown from "../../../components/layouts/Dropdown"
import { saveAs } from 'file-saver'

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

    // Sort moduleInfos by lastHeartbeatReceived in descending order
    const sortedModuleInfos = [...moduleInfos].sort((a, b) => (b.lastHeartbeatReceived || 0) - (a.lastHeartbeatReceived || 0));
    const moduleInfoRows = sortedModuleInfos.map(module => [
        module.moduleType || '-',
        module.currentVersion || '-',
        func.epochToDateTime(module.startedTs),
        func.epochToDateTime(module.lastHeartbeatReceived)
    ]);

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
                    <LegacyCard sectioned title="Module Information">

                    <DataTable
                        columnContentTypes={[
                            'text',
                            'text',
                            'text',
                            'text'
                        ]}
                        headings={[
                            'Type',
                            'Version',
                            'Started At',
                            'Last Heartbeat'
                        ]}
                        rows={moduleInfoRows}
                    />
                                </LegacyCard>

                ) : <></>}
        </VerticalStack>
    )
}

export default Logs
import { Button, ButtonGroup, HorizontalGrid, HorizontalStack, LegacyCard, Page, Scrollable, Select, Spinner, Text } from "@shopify/polaris"
import { useEffect, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import LogsContainer from "./LogsContainer";
import Dropdown from "../../../components/layouts/Dropdown"

const Logs = () => {
    const fiveMins = 1000 * 60 * 5

    const [ logs, setLogs ] = useState({
        startTime: null,
        endTime: null,
        logGroup: '',
        logData: []
    })
    const [ loading, setLoading ] = useState(false)

    const logGroupSelected = logs.logGroup !== ''

    const logGroupOptions = [
        { label: "Runtime", value: "RUNTIME" },
        { label: "Dashboard", value: "DASHBOARD" },
        { label: "Testing", value: "TESTING" },
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

    useEffect(() => {
        const startTime = Date.now() - fiveMins
        const endTime = Date.now() 
        fetchLogsFromDb(startTime, endTime)
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
        fetchLogsFromDb(startTime, endTime, true)
    }

    const handlePreviousFiveMinutesLogs = () => {
        const startTime = logs.startTime - fiveMins;
        const endTime = logs.startTime;
        fetchLogsFromDb(startTime, endTime)
    }

    return (
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
                    initial="Select log group"
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
    )
}

export default Logs
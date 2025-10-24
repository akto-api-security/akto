import { Button, ButtonGroup, LegacyCard, Text, DataTable, VerticalStack } from "@shopify/polaris"
import { useEffect, useState, useCallback, useMemo } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import LogsContainer from "./LogsContainer";
import Dropdown from "../../../components/layouts/Dropdown"
import SingleDate from "../../../components/layouts/SingleDate"
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
    const [ selectedDate, setSelectedDate ] = useState(new Date())
    const [ selectedHour, setSelectedHour ] = useState(new Date().getHours())
    const [ selectedMinutes, setSelectedMinutes ] = useState("5")
    const [ currentWindowStartMs, setCurrentWindowStartMs ] = useState(null)
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
    
    const fetchLogsFromDb = useCallback(async (startTime, endTime, refresh = false, group) => {
        const activeGroup = group ?? logs.logGroup
        if (activeGroup !== '') {
            setLoading(true)
            const logsResponse = await settingRequests.fetchLogsFromDb(
                Math.floor(startTime / 1000), 
                Math.floor(endTime  / 1000),
                activeGroup
            )
            
            setLogs(previousState => (
                {
                    ...previousState,
                    startTime: startTime,
                    endTime: endTime,
                    logData: refresh ? [...logsResponse.logs] : [...logsResponse.logs, ...previousState.logData]
                }))

            setLoading(false)
        }
    }, [logs.logGroup])

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
    }, [logs.logGroup, hasAccess, fiveMins, fetchLogsFromDb])

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

    // Removed old refresh controls; loading is handled via Load button with selected window

    const minuteWindowOptions = useMemo(() => ([
        { label: "+5 minutes", value: "5" },
        { label: "+10 minutes", value: "10" },
        { label: "+15 minutes", value: "15" }
    ]), [])

    const hourOptions = useMemo(() => Array.from({ length: 24 }, (_, h) => {
        const pad = (n) => String(n).padStart(2, '0')
        return { label: `${pad(h)}:00 - ${pad(h)}:59`, value: String(h) }
    }), [])

    const handleSelectHour = (value) => {
        const hourNum = Number(value)
        setSelectedHour(hourNum)
        const date = selectedDate || new Date()
        const { start } = getHourWindowRange(date, hourNum)
        const stepMs = Number(selectedMinutes) * 60 * 1000
        setCurrentWindowStartMs(start)
        if (hasAccess) {
            fetchLogsFromDb(start, start + stepMs, true)
            fetchModuleInfo()
        }
    }

    const singleDateDispatch = ({ obj }) => {
        const newDate = obj && (obj.date || obj.End || obj.end)
        if (newDate) {
            setSelectedDate(newDate)
            const hour = typeof selectedHour === 'number' ? selectedHour : new Date().getHours()
            const { start } = getHourWindowRange(newDate, hour)
            const stepMs = Number(selectedMinutes) * 60 * 1000
            setCurrentWindowStartMs(start)
            if (hasAccess) {
                fetchLogsFromDb(start, start + stepMs, true)
                fetchModuleInfo()
            }
        }
    }

    function getHourWindowRange(date, hour) {
        const base = date ? new Date(date) : new Date()
        const d = new Date(base.getFullYear(), base.getMonth(), base.getDate(), hour, 0, 0, 0)
        const start = d.getTime()
        const end = start + 60 * 60 * 1000
        return { start, end }
    }

    // Reset current window tracker when date or hour changes
    useEffect(() => {
        setCurrentWindowStartMs(null)
    }, [selectedDate, selectedHour])

    const handleSelectMinuteWindow = (minutes) => {
        setSelectedMinutes(String(minutes))
        const date = selectedDate || new Date()
        const hour = typeof selectedHour === 'number' ? selectedHour : new Date().getHours()
        const { start } = getHourWindowRange(date, hour)
        const windowEnd = start + Number(minutes) * 60 * 1000
        setCurrentWindowStartMs(start)
        if (hasAccess) {
            fetchLogsFromDb(start, windowEnd, true)
            fetchModuleInfo()
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

                <div style={{ display: "grid", gridTemplateColumns: "auto max-content", gap: "10px", alignItems: "center"}}>
                    <Dropdown
                        menuItems={logGroupOptions}
                        initial="Dashboard"
                        selected={handleSelectLogGroup}
                        />
                    <ButtonGroup segmented>
                        <Button onClick={() => {
                            const date = selectedDate || new Date()
                            const hour = typeof selectedHour === 'number' ? selectedHour : new Date().getHours()
                            const { start: hourStart, end: hourEnd } = getHourWindowRange(date, hour)
                            const stepMs = Number(selectedMinutes) * 60 * 1000
                            const currStart = currentWindowStartMs == null ? hourStart : currentWindowStartMs
                            let baseStart = currStart
                            if (baseStart < hourStart || baseStart >= hourEnd) {
                                baseStart = hourStart
                            }
                            let nextStart = baseStart + stepMs
                            if (nextStart + stepMs > hourEnd) {
                                nextStart = hourStart
                            }
                            const nextEnd = nextStart + stepMs
                            setCurrentWindowStartMs(nextStart)
                            if (hasAccess) {
                                fetchLogsFromDb(nextStart, nextEnd, true)
                                fetchModuleInfo()
                            }
                        }} disabled={loading || !logGroupSelected}>Refresh</Button>
                        <Dropdown
                            menuItems={minuteWindowOptions}
                            initial={selectedMinutes}
                            selected={handleSelectMinuteWindow}
                        />
                    </ButtonGroup>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr max-content", gap: "10px", alignItems: "center", marginTop: "10px"}}>
                    <SingleDate
                        dispatch={singleDateDispatch}
                        data={selectedDate}
                        dataKey={"date"}
                        index={0}
                    />
                    <Dropdown
                        menuItems={hourOptions}
                        initial={String(selectedHour)}
                        selected={handleSelectHour}
                    />
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
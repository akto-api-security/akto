import {
    Box,
    Button,
    ButtonGroup,
    Checkbox,
    HorizontalStack,
    LegacyCard,
    Popover,
    Spinner,
    Text,
    TextField,
    VerticalStack,
} from "@shopify/polaris"
import { useCallback, useEffect, useMemo, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import LogsContainer from "./LogsContainer";
import Dropdown from "../../../components/layouts/Dropdown"
import { saveAs } from 'file-saver'
import { ALL_STORED_LOG_KEYS, STORED_LOG_KEY_OPTIONS } from "./logKeysConstants"

const Logs = () => {
    const fiveMins = 1000 * 60 * 5

    const [ logs, setLogs ] = useState({
        startTime: null,
        endTime: null,
        logGroup: 'DASHBOARD',
        logData: []
    })
    const [ selectedLogKeys, setSelectedLogKeys ] = useState(() => [...ALL_STORED_LOG_KEYS])
    const [ logKeysPopoverOpen, setLogKeysPopoverOpen ] = useState(false)
    const [ pendingLogKeys, setPendingLogKeys ] = useState(() => [...ALL_STORED_LOG_KEYS])
    const [ loading, setLoading ] = useState(false)
    const [ logSearchQuery, setLogSearchQuery ] = useState('')
    const logGroupSelected = logs.logGroup !== ''
    const hasAccess = func.checkUserValidForIntegrations()

    const selectedLogKeysSignature = useMemo(
        () => [...selectedLogKeys].sort().join(','),
        [selectedLogKeys]
    )

    const logGroupOptions = [
        { label: "Dashboard", value: "DASHBOARD" },
        { label: "Runtime", value: "RUNTIME" },
        { label: "Testing", value: "TESTING" },
        { label: "Agentic Testing", value: "AGENTIC_TESTING" },
        { label: "Puppeteer", value: "PUPPETEER" },
        { label: "Threat", value: "THREAT_DETECTION" },
        { label: "Data Ingestion", value: "DATA_INGESTION" },
        { label: "AWS API Gateway", value: "AWS_API_GATEWAY" },
    ];
  
    const handleSelectLogGroup = (logGroup) => {
       setLogSearchQuery('')
       setLogs(previousState => ({ ...previousState, logData: [], logGroup: logGroup }))
    }

    const displayedLogData = useMemo(() => {
        const q = logSearchQuery.trim().toLowerCase()
        if (!q) {
            return logs.logData
        }
        return logs.logData.filter((entry) => {
            const line = `${func.epochToDateTime(entry.timestamp)} ${entry.log ?? ''}`.toLowerCase()
            return line.includes(q)
        })
    }, [logs.logData, logSearchQuery])

    const fetchLogsFromDb = useCallback(async (startTime, endTime, refresh = false) => {
        if (logs.logGroup === '') {
            return
        }
        setLoading(true)
        try {
            const logsResponse = await settingRequests.fetchLogsFromDb(
                Math.floor(startTime / 1000), 
                Math.floor(endTime  / 1000),
                logs.logGroup,
                selectedLogKeys
            )
            setLogs(previousState => ({
                ...previousState,
                startTime,
                endTime,
                logData: refresh
                    ? (logsResponse.logs ?? [])
                    : [...(logsResponse.logs ?? []), ...previousState.logData],
            }))
        } finally {
            setLoading(false)
        }
    }, [logs.logGroup, selectedLogKeysSignature])
    
    useEffect(() => {
        const startTime = Date.now() - fiveMins
        const endTime = Date.now()
        if (hasAccess && logs.logGroup !== '') {
            fetchLogsFromDb(startTime, endTime, true)
        }
    }, [logs.logGroup, selectedLogKeysSignature, hasAccess, fetchLogsFromDb])

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
        }
    }

    const handlePreviousFiveMinutesLogs = () => {
        const startTime = logs.startTime - fiveMins;
        const endTime = logs.startTime;
        if(hasAccess){
            fetchLogsFromDb(startTime, endTime)
        }
    }

    const toggleLogKeysPopover = () => {
        if (!logKeysPopoverOpen) {
            setPendingLogKeys([...selectedLogKeys])
        }
        setLogKeysPopoverOpen((open) => !open)
    }

    const togglePendingKey = (key, checked) => {
        setPendingLogKeys((prev) => {
            if (checked) {
                return prev.includes(key) ? prev : [...prev, key]
            }
            return prev.filter((k) => k !== key)
        })
    }

    const applyLogKeys = () => {
        const next = pendingLogKeys.length > 0 ? [...pendingLogKeys] : [...ALL_STORED_LOG_KEYS]
        setSelectedLogKeys(next)
        setLogKeysPopoverOpen(false)
    }

    return (
        <LegacyCard>
            <LegacyCard.Section>
                <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <Text variant="headingMd" as="h2">Logs</Text>
                        {loading ? (
                            <Spinner size="small" accessibilityLabel="Loading logs" />
                        ) : null}
                    </HorizontalStack>
                    <ButtonGroup segmented>
                        <Popover
                            active={logKeysPopoverOpen}
                            activator={
                                <Button disclosure onClick={toggleLogKeysPopover}>
                                    Configure log level
                                </Button>
                            }
                            onClose={() => setLogKeysPopoverOpen(false)}
                            autofocusTarget="first-node"
                        >
                            <Popover.Section>
                                <Box minWidth="300px" paddingInline="3" paddingBlock="3">
                                    <VerticalStack gap="2">
                                        {STORED_LOG_KEY_OPTIONS.map(({ key, label }) => (
                                            <Checkbox
                                                key={key}
                                                label={label}
                                                checked={pendingLogKeys.includes(key)}
                                                onChange={(checked) => togglePendingKey(key, checked)}
                                            />
                                        ))}
                                        <Button fullWidth monochrome onClick={applyLogKeys}>
                                            Apply
                                        </Button>
                                    </VerticalStack>
                                </Box>
                            </Popover.Section>
                        </Popover>
                        <Button onClick={exportLogsCsv}>Export</Button>
                    </ButtonGroup>
                </HorizontalStack>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <Box paddingBlockEnd="3">
                    <div style={{ display: "grid", gridTemplateColumns: "1fr max-content", gap: "10px", width: "100%" }}>
                        <Dropdown
                            menuItems={logGroupOptions}
                            initial="Dashboard"
                            selected={handleSelectLogGroup}
                            />
                        <ButtonGroup segmented>
                            <Button onClick={handleRefresh} disabled={!logGroupSelected}>Refresh</Button>
                            <Button onClick={handlePreviousFiveMinutesLogs} disabled={!logGroupSelected || logs.startTime == null}>-5 minutes</Button>
                        </ButtonGroup>
                    </div>
                </Box>

                {
                    logGroupSelected ?
                        <VerticalStack gap="2">
                            <TextField
                                id="health-logs-search"
                                label="Search loaded logs"
                                value={logSearchQuery}
                                onChange={(value) => setLogSearchQuery(value)}
                                placeholder="Filter by text in timestamp or message"
                                clearButton
                                onClearButtonClick={() => setLogSearchQuery('')}
                                autoComplete="off"
                            />
                            <LogsContainer
                                logs={logs}
                                displayedLogData={displayedLogData}
                            />
                        </VerticalStack>
                        : <Text variant="bodyMd">Select log group to fetch logs</Text>
                }
            </LegacyCard.Section>
        </LegacyCard>
    )
}

export default Logs

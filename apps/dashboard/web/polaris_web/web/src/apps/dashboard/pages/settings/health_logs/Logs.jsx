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
       setLogs(previousState => ({ ...previousState, logData: [], logGroup: logGroup }))
    }

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

    const configureLogLevelActivator = (
        <Button disclosure onClick={toggleLogKeysPopover}>
            Configure log level
        </Button>
    )

    return (
        <VerticalStack>
            <LegacyCard>
                <LegacyCard.Section>
                    <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                        <HorizontalStack gap={"1"} blockAlign="center" wrap={false}>
                            <Text variant="headingMd" as="h2">Logs</Text>
                            {loading ? (
                                <Spinner size="small" accessibilityLabel="Loading logs" />
                            ) : null}
                        </HorizontalStack>
                        <ButtonGroup segmented>
                            <Popover
                                active={logKeysPopoverOpen}
                                activator={configureLogLevelActivator}
                                onClose={() => setLogKeysPopoverOpen(false)}
                                autofocusTarget="first-node"
                            >
                                <Popover.Section>
                                    <Box minWidth="300px" paddingInline="100" paddingBlock="100">
                                        <VerticalStack gap={"1"}>
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

                <div style={{ display: "grid", gridTemplateColumns: "auto max-content", gap: "10px"}}>
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
              
                <br />

                {
                    logGroupSelected ?
                        <LogsContainer logs={logs} />
                        : <Text variant="bodyMd">Select log group to fetch logs</Text>
                }
                </LegacyCard.Section>
            </LegacyCard>
        </VerticalStack>
    )
}

export default Logs

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
import { isDastCategory } from "@/apps/main/labelHelper"

const LOG_GROUP_LABELS = {
    DASHBOARD: "Dashboard",
    RUNTIME: "Runtime",
    TESTING: "Testing",
    AGENTIC_TESTING: "Agentic Testing",
    PUPPETEER: "Puppeteer",
    THREAT_DETECTION: "Threat",
    DATA_INGESTION: "Data Ingestion",
    AWS_API_GATEWAY: "AWS API Gateway",
    GUARDRAILS_SERVICE: "Guardrails Service",
    SMART_TESTING: "Smart Testing",
    MINI_TESTING_ORCHESTRATOR: "Mini Testing Orchestrator",
    DAST: "DAST",
    BILLING: "Billing",
    ANALYSER: "Analyser",
    ENDPOINT_SHIELD: "Endpoint Shield",
    ACCOUNT_JOB_EXECUTOR: "Account Job Executor",
}

const Logs = () => {
    const fiveMins = 1000 * 60 * 5

    const [ logs, setLogs ] = useState({
        startTime: null,
        endTime: null,
        logGroup: '',
        logData: []
    })
    const [ selectedLogKeys, setSelectedLogKeys ] = useState(() => [...ALL_STORED_LOG_KEYS])
    const [ logKeysPopoverOpen, setLogKeysPopoverOpen ] = useState(false)
    const [ pendingLogKeys, setPendingLogKeys ] = useState(() => [...ALL_STORED_LOG_KEYS])
    const [ allowedLogGroups, setAllowedLogGroups ] = useState([])
    const [ loading, setLoading ] = useState(false)
    const [ logSearchQuery, setLogSearchQuery ] = useState('')
    const logGroupSelected = logs.logGroup !== ''
    const hasAccess = func.checkUserValidForIntegrations()

    const selectedLogKeysSignature = useMemo(
        () => [...selectedLogKeys].sort().join(','),
        [selectedLogKeys]
    )

    const logGroupOptions = useMemo(
        () => allowedLogGroups.map((value) => ({ label: LOG_GROUP_LABELS[value] ?? value, value })),
        [allowedLogGroups]
    )

    useEffect(() => {
        if (!hasAccess) return
        settingRequests.fetchAllowedLogGroups().then((resp) => {
            // Which groups a user may see is decided by the backend. dast_logs is only
            // populated for DAST deployments, so hide that option elsewhere.
            const groups = (resp?.allowedLogGroups ?? []).filter((value) => value !== 'DAST' || isDastCategory())
            setAllowedLogGroups(groups)
            setLogs(previousState => ({ ...previousState, logGroup: groups[0] ?? '' }))
        }).catch(() => {})
    }, [hasAccess])

    const handleSelectLogGroup = (logGroup) => {
       setLogSearchQuery('')
       setLogs(previousState => ({ ...previousState, logData: [], logGroup: logGroup }))
    }

    const displayedLogData = useMemo(() => {
        const q = logSearchQuery.trim().toLowerCase()
        if (!q) {
            return [...logs.logData].reverse()
        }
        return logs.logData.filter((entry) => {
            const line = `${func.epochToDateTime(entry.timestamp)} ${entry.log ?? ''}`.toLowerCase()
            return line.includes(q)
        }).reverse()
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
                        {/* Mounted only once the allowed groups are known: Dropdown reads `initial` just once */}
                        {logGroupOptions.length > 0 && (
                            <Dropdown
                                menuItems={logGroupOptions}
                                initial={logGroupOptions[0].label}
                                selected={handleSelectLogGroup}
                            />
                        )}
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

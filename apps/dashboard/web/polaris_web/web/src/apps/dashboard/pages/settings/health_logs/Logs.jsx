import { Button, ButtonGroup, LegacyCard, Text, DataTable, VerticalStack, Box, HorizontalStack, Popover, TextField, DatePicker} from "@shopify/polaris"
import settingRequests from "../api";
import func from "@/util/func";
import LogsContainer from "./LogsContainer";
import Dropdown from "../../../components/layouts/Dropdown"
import { saveAs } from 'file-saver'
import { useEffect, useState } from "react";

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
    const [dateRangePopoverActive, setDateRangePopoverActive] = useState(false)

    const [dateRange, setDateRange] = useState({
        alias: "last5mins",
        title: "Last 5 minutes",
        period: {
        since: new Date(Date.now() - fiveMins),
        until: new Date()
        }
    });

    const [customDateTime, setCustomDateTime] = useState({ startDate: "", endDate: "", startTime: "", endTime: "" });
    const [calendarDate, setCalendarDate] = useState({ month: new Date().getMonth(), year: new Date().getFullYear() });
    const [selectedCalendarRange, setSelectedCalendarRange] = useState({ start: new Date(), end: new Date() });

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

    const formatDateForInput = (date) => {
        return date.toISOString().split("T")[0];
    };

    const formatTimeForInput = (date) => {
        return date.toTimeString().slice(0, 5);
    };

    useEffect(() => {
        setCustomDateTime({
          startDate: formatDateForInput(dateRange.period.since),
          endDate: formatDateForInput(dateRange.period.until),
          startTime: formatTimeForInput(dateRange.period.since),
          endTime: formatTimeForInput(dateRange.period.until)
        });

        setSelectedCalendarRange({
          start: dateRange.period.since,
          end: dateRange.period.until
        });

        setCalendarDate({
          month: dateRange.period.since.getMonth(),
          year: dateRange.period.since.getFullYear()
        });
    }, [dateRange])

    useEffect(() => {
        const startTime = dateRange.period.since.getTime();
        const endTime = dateRange.period.until.getTime();
        if(hasAccess){
            fetchLogsFromDb(startTime, endTime)
            fetchModuleInfo()
        }
    }, [logs.logGroup,dateRange])
    
    const exportLogsCsv = () => {
        let headers = ['timestamp', 'log'];
        let csv = headers.join(",")+"\r\n"
        logs.logData.forEach(log => {
            csv += func.epochToDateTime(log.timestamp) +","+ log.log + "\r\n"
        })
        let blob = new Blob([csv], {
            type: "application/csv;charset=UTF-8"
        });
        saveAs(blob, "log.csv");
    } 

    const handleRefresh = () => {
        const startTime = dateRange.period.since.getTime();
        const endTime = dateRange.period.until.getTime();
        if(hasAccess){
            fetchLogsFromDb(startTime, endTime, true)
            fetchModuleInfo()
        }
    }

    const toggleDateRangePopover = () => setDateRangePopoverActive(!dateRangePopoverActive);

    const handleCustomDateTimeChange = (field, value) => setCustomDateTime(prev => ({ ...prev, [field]: value }));

    const handleCalendarChange = ({ start, end }) => {
        setSelectedCalendarRange({ start, end });
        setCustomDateTime(prev => ({ ...prev, startDate: formatDateForInput(start), endDate: formatDateForInput(end) }));
    };

    const handleCalendarMonthChange = (month, year) => setCalendarDate({ month, year });

    const handleApplyCustomDateTime = () => {
        const { startDate, endDate, startTime, endTime } = customDateTime;

        if (startDate && endDate && startTime && endTime) {
            const startDateTime = new Date(`${startDate}T${startTime}`);
            const endDateTime = new Date(`${endDate}T${endTime}`);

            if (startDateTime <= endDateTime) {
            const newDateRange = { alias: "custom", title: "Custom Range", period: { since: startDateTime, until: endDateTime } };
            setDateRange(newDateRange);
            setDateRangePopoverActive(false);
            if (hasAccess && logs.logGroup !== '') {
                fetchLogsFromDb(startDateTime.getTime(), endDateTime.getTime(), true);
            }
            } else {
            alert("Start date/time must be before end date/time");
            }
        } else {
            alert("Please fill in all date and time fields");
        }
    };

    const handleCancelCustomDateTime = () => {
        setCustomDateTime({ startDate: formatDateForInput(dateRange.period.since), endDate: formatDateForInput(dateRange.period.until), startTime: formatTimeForInput(dateRange.period.since), endTime: formatTimeForInput(dateRange.period.until) });
        setSelectedCalendarRange({ start: dateRange.period.since, end: dateRange.period.until });
        setDateRangePopoverActive(false);
    };

    // Sort moduleInfos by lastHeartbeatReceived in descending order
    const sortedModuleInfos = [...moduleInfos].sort((a, b) => (b.lastHeartbeatReceived || 0) - (a.lastHeartbeatReceived || 0));
    const moduleInfoRows = sortedModuleInfos.map(module => [
        module.moduleType || '-',
        module.currentVersion || '-',
        func.epochToDateTime(module.startedTs),
        func.epochToDateTime(module.lastHeartbeatReceived)
    ]);

    return (
        <VerticalStack gap="5">
            <style>
                {`
                    .Polaris-DatePicker {
                        display: flex !important;
                        gap: 20px;
                    }
                    .Polaris-DatePicker__MonthContainer {
                        width: auto !important;
                    }
                `}
            </style>
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

                <Box paddingBlockStart="4">
                    <HorizontalStack gap="3" wrap>
                        <div style={{ minWidth: '500px', maxWidth: '600px', width: '100%' }}>
                            <Dropdown menuItems={logGroupOptions} initial="Dashboard" selected={handleSelectLogGroup} />
                        </div>

                        <Popover
                            active={dateRangePopoverActive}
                            activator={
                                <Button onClick={toggleDateRangePopover} disabled={!logGroupSelected}>
                                {dateRange.title}
                                </Button>
                            }
                            onClose={() => setDateRangePopoverActive(false)}
                        >
                            <div style={{ padding: '20px', width: '600px', background: '#fff', borderRadius: '8px', boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)' }}>
                                <div style={{ border: '1px solid #e1e3e5', borderRadius: '8px', background: '#fff', padding: '16px', marginBottom: '20px' }}>
                                    <DatePicker
                                        month={calendarDate.month}
                                        year={calendarDate.year}
                                        selected={selectedCalendarRange}
                                        onMonthChange={handleCalendarMonthChange}
                                        onChange={handleCalendarChange}
                                        allowRange
                                        multiMonth
                                    />
                                </div>
                                <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '10px', marginBottom: '20px' }}>
                                    <TextField
                                        label="Start Time"
                                        type="time"
                                        value={customDateTime.startTime}
                                        onChange={(value) => handleCustomDateTimeChange("startTime", value)}
                                    />
                                    <span style={{ fontWeight: 'bold', color: '#6d7175', margin: '22px 0px 0px 0px' }}>to</span>
                                    <TextField
                                        label="End Time"
                                        type="time"
                                        value={customDateTime.endTime}
                                        onChange={(value) => handleCustomDateTimeChange("endTime", value)}
                                    />
                                </div>
                                <div style={{ display: 'flex', justifyContent: 'flex-end', borderTop: '1px solid #e1e3e5', paddingTop: '12px', gap: '8px' }}>
                                    <ButtonGroup>
                                        <Button onClick={handleCancelCustomDateTime}>Cancel</Button>
                                        <Button primary onClick={handleApplyCustomDateTime}>Apply</Button>
                                    </ButtonGroup>
                                </div>
                            </div>
                        </Popover>

                        <Button onClick={handleRefresh} disabled={!logGroupSelected} primary>Refresh</Button>
                    </HorizontalStack>
                </Box>

                <Box paddingBlockStart="4">
                    {
                    logGroupSelected ? 
                        // loading ? <SpinnerCentered/> : <LogsContainer logs={logs} />  
                        <LogsContainer logs={logs} />  
                        : <Text variant="bodyMd">Select log group to fetch logs</Text>
                }
                </Box>
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

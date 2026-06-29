import { useState, useEffect, useCallback } from "react";
import { Card, HorizontalStack, VerticalStack, Text, Button, TextField } from "@shopify/polaris";
import { RefreshMajor } from "@shopify/polaris-icons";
import api from "../api";
import LogsContainer from "../../settings/health_logs/LogsContainer";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";

const TestRunLogs = ({ summaryHexId, startTimestamp, endTimestamp }) => {
    const [logData, setLogData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchValue, setSearchValue] = useState("");

    const fetchLogs = useCallback(async () => {
        if (!summaryHexId) {
            return;
        }
        setLoading(true);
        try {
            const resp = await api.fetchTestRunLogs(summaryHexId);
            setLogData(resp?.logs ?? []);
        } finally {
            setLoading(false);
        }
    }, [summaryHexId]);

    useEffect(() => {
        fetchLogs();
    }, [fetchLogs]);

    const displayedLogData = searchValue
        ? logData.filter((entry) => `${entry.log ?? ""}`.toLowerCase().includes(searchValue.toLowerCase()))
        : logData;

    const logs = {
        startTime: (startTimestamp || 0) * 1000,
        endTime: (endTimestamp || 0) * 1000,
        logData,
    };

    return (
        <Card key="test-run-logs">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingMd" as="h2">Logs</Text>
                    <Button icon={RefreshMajor} onClick={fetchLogs} loading={loading}>Refresh</Button>
                </HorizontalStack>
                <TextField
                    value={searchValue}
                    onChange={setSearchValue}
                    placeholder="Filter by text in timestamp or message"
                    autoComplete="off"
                    clearButton
                    onClearButtonClick={() => setSearchValue("")}
                />
                {loading
                    ? <SpinnerCentered />
                    : (logData.length === 0
                        ? <Text color="subdued">No logs found for this test run.</Text>
                        : <LogsContainer logs={logs} displayedLogData={displayedLogData} />)}
            </VerticalStack>
        </Card>
    );
};

export default TestRunLogs;

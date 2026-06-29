import { useState, useEffect, useCallback } from "react";
import { Modal, HorizontalStack, VerticalStack, Text, Button, TextField } from "@shopify/polaris";
import { RefreshMajor } from "@shopify/polaris-icons";
import api from "../api";
import LogsContainer from "../../settings/health_logs/LogsContainer";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";

const TestRunLogs = ({ open, onClose, summaryHexId, startTimestamp, endTimestamp }) => {
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
        if (open) {
            fetchLogs();
        }
    }, [open, fetchLogs]);

    const matchesSearch = (entry) => {
        const term = searchValue.toLowerCase();
        return `${entry.log ?? ""}`.toLowerCase().includes(term)
            || `${entry.testRunResultSummaryId ?? ""}`.toLowerCase().includes(term);
    };

    const displayedLogData = searchValue ? logData.filter(matchesSearch) : logData;

    const logs = {
        startTime: (startTimestamp || 0) * 1000,
        endTime: (endTimestamp || 0) * 1000,
        logData,
    };

    return (
        <Modal
            large
            open={open}
            onClose={onClose}
            title="Test run logs"
        >
            <Modal.Section>
                <VerticalStack gap="3">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="bodySm" color="subdued" as="span">Run summary id: {summaryHexId}</Text>
                        <Button icon={RefreshMajor} onClick={fetchLogs} loading={loading}>Refresh</Button>
                    </HorizontalStack>
                    <TextField
                        value={searchValue}
                        onChange={setSearchValue}
                        placeholder="Filter by text in timestamp, message or summary id"
                        autoComplete="off"
                        clearButton
                        onClearButtonClick={() => setSearchValue("")}
                    />
                    {loading
                        ? <SpinnerCentered />
                        : (logData.length === 0
                            ? <Text color="subdued">No logs found for this test run.</Text>
                            : <LogsContainer logs={logs} displayedLogData={displayedLogData} runSummaryHexId={summaryHexId} />)}
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default TestRunLogs;

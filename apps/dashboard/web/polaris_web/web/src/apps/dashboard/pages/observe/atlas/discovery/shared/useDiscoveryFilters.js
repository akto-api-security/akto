import { useReducer, useState } from "react";
import { produce } from "immer";
import func from "@/util/func";
import discoveryApi from "../discoveryApi";
import { DEVICE_SCOPES, EXPORT_PAGE_SIZE, EXPORT_ROW_LIMIT } from "../constants";
import { toCsvLines } from "../transform";

const epochOf = (dateRange, key) => Math.floor(Date.parse(dateRange.period[key]) / 1000);

// Date range and device scope for the overview and its CSV export.
export default function useDiscoveryFilters() {
    const [dateRange, dispatchDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), func.getLast30DaysRange());
    const [deviceScope, setDeviceScope] = useState(DEVICE_SCOPES[0].value);
    return {
        dateRange,
        dispatchDateRange,
        deviceScope,
        setDeviceScope,
        startTimestamp: epochOf(dateRange, "since"),
        endTimestamp: epochOf(dateRange, "until"),
    };
}

// CSV of every tool in scope, fetched a page at a time.
export function useToolsExport({ startTimestamp, endTimestamp, deviceScope }) {
    const [exporting, setExporting] = useState(false);
    const exportTools = async () => {
        setExporting(true);
        try {
            const rows = [];
            for (let skip = 0; skip < EXPORT_ROW_LIMIT; skip += EXPORT_PAGE_SIZE) {
                const response = await discoveryApi.fetchAtlasTools({ startTimestamp, endTimestamp, deviceScope, skip, limit: EXPORT_PAGE_SIZE });
                const page = response?.tools || [];
                rows.push(...page);
                if (page.length < EXPORT_PAGE_SIZE || rows.length >= (response?.total || 0)) break;
            }
            func.saveCsv(toCsvLines(rows), "atlas-ai-tools");
        } catch (error) {
            func.setToast(true, true, "Failed to export the tools");
        } finally {
            setExporting(false);
        }
    };
    return { exportTools, exporting };
}

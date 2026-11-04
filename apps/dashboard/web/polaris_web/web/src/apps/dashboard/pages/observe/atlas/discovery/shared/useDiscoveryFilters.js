import { useState } from "react";
import func from "@/util/func";
import discoveryApi from "../discoveryApi";
import { DEVICE_SCOPES, EXPORT_PAGE_SIZE, EXPORT_ROW_LIMIT } from "../constants";
import { toCsvLines } from "../transform";

// Zero asks the backend for all time, up to now.
const ALL_TIME = 0;

// Device scope for the overview and its CSV export; the range is always all time.
export default function useDiscoveryFilters() {
    const [deviceScope, setDeviceScope] = useState(DEVICE_SCOPES[0].value);
    return {
        deviceScope,
        setDeviceScope,
        startTimestamp: ALL_TIME,
        endTimestamp: ALL_TIME,
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

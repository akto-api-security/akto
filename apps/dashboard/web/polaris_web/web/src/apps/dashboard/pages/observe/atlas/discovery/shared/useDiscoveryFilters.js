import { useState } from "react";
import func from "@/util/func";
import discoveryApi from "../discoveryApi";
import { DEVICE_SCOPES, EXPORT_PAGE_SIZE, EXPORT_ROW_LIMIT } from "../constants";
import { toCsvLines } from "../transform";
import observeFunc from "@/apps/dashboard/pages/observe/transform";

// Device scope for the overview and its CSV export.
export default function useDiscoveryFilters() {
    const [deviceScope, setDeviceScope] = useState(DEVICE_SCOPES[0].value);
    return { deviceScope, setDeviceScope };
}

// CSV of every tool in scope, fetched a page at a time.
export function useToolsExport({ deviceScope }) {
    const [exporting, setExporting] = useState(false);
    const exportTools = async () => {
        setExporting(true);
        try {
            const rows = [];
            let total = 0;
            for (let skip = 0; skip < EXPORT_ROW_LIMIT; skip += EXPORT_PAGE_SIZE) {
                const response = await discoveryApi.fetchAtlasTools({ deviceScope, skip, limit: EXPORT_PAGE_SIZE });
                const page = response?.tools || [];
                total = response?.total || 0;
                rows.push(...page);
                if (page.length < EXPORT_PAGE_SIZE || rows.length >= (response?.total || 0)) break;
            }
            const lines = toCsvLines(rows);
            // The byte-order mark keeps Excel from garbling non-ASCII tool names.
            lines[0] = `\uFEFF${lines[0]}`;
            func.saveCsv(lines, "atlas-ai-tools");
            if (total > rows.length) func.setToast(true, false, `Exported the first ${observeFunc.formatNumberWithCommas(rows.length)} of ${observeFunc.formatNumberWithCommas(total)} tools`);
        } catch (error) {
            func.setToast(true, true, "Failed to export the tools");
        } finally {
            setExporting(false);
        }
    };
    return { exportTools, exporting };
}

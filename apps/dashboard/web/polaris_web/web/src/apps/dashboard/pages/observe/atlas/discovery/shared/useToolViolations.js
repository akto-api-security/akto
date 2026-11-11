import { useEffect, useState } from "react";
import { fetchActiveToolViolationCount } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";
import { VIOLATION_WINDOW_SECONDS } from "../constants";

// Last 7 days ending on the next quarter hour, so tools opened close together share one server lookup.
const violationWindow = () => {
    const end = Math.ceil(Date.now() / 900000) * 900;
    return { startTimestamp: end - VIOLATION_WINDOW_SECONDS, endTimestamp: end };
};

// Active violations of one tool, counted as the activity page counts them: { key, count, startTimestamp, endTimestamp }; null until known, count null when the lookup failed.
export default function useToolViolations(toolKey) {
    const [violations, setViolations] = useState(null);

    useEffect(() => {
        setViolations(null);
        if (!toolKey) return undefined;
        let ignore = false;
        const range = violationWindow();
        fetchActiveToolViolationCount(toolKey, range.startTimestamp, range.endTimestamp)
            .then((count) => { if (!ignore) setViolations({ key: toolKey, count, ...range }); });
        return () => { ignore = true; };
    }, [toolKey]);

    return violations;
}

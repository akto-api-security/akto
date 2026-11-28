import { useCallback, useEffect, useState } from "react";
import api from "@/apps/dashboard/pages/threat_detection/api";
import { fetchEndpointViolationCounts } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";

function useSummaryRequest(fetchData, refresh) {
    const [state, setState] = useState({ data: null, loading: true, error: false });
    useEffect(() => {
        let cancelled = false;
        setState({ data: null, loading: true, error: false });
        Promise.resolve().then(fetchData).then(
            data => { if (!cancelled) setState({ data, loading: false, error: false }); },
            () => { if (!cancelled) setState({ data: null, loading: false, error: true }); },
        );
        return () => { cancelled = true; };
    }, [fetchData, refresh]);
    return state;
}

export default function useViolationsSummary({ startTimestamp, endTimestamp, status, skillEvaluationMode, configEvaluationMode, endpoint, agentic, refresh }) {
    const fetchSeverity = useCallback(() => api.fetchCountBySeverity(
        startTimestamp, endTimestamp, "ACTIVE", endpoint ? "exclude" : undefined, endpoint ? "exclude" : undefined,
    ), [startTimestamp, endTimestamp, endpoint]);
    const fetchCategory = useCallback(() => api.fetchThreatCategoryCount(
        startTimestamp, endTimestamp, status, true,
    ), [startTimestamp, endTimestamp, status]);
    const fetchHosts = useCallback(() => api.fetchThreatTopNData(
        startTimestamp, endTimestamp, [], 5, status, skillEvaluationMode, configEvaluationMode, true,
    ), [startTimestamp, endTimestamp, status, skillEvaluationMode, configEvaluationMode]);
    const fetchCounts = useCallback(async () => {
        if (endpoint) {
            const counts = await fetchEndpointViolationCounts(startTimestamp, endTimestamp);
            if ([counts.active, counts.underReview, counts.ignored, counts.skillsEvaluations, counts.misconfiguredSettings].some(count => count == null)) {
                throw new Error("Could not load violation counts");
            }
            return counts;
        }
        const [daily, humanApproval] = await Promise.all([
            api.getDailyThreatActorsCount(startTimestamp, endTimestamp, []),
            agentic
                ? api.fetchSuspectSampleData(0, [], [], [], [], {}, startTimestamp, endTimestamp, [], 1, "HUMAN_APPROVAL", undefined, undefined, undefined, undefined, undefined, false, [], undefined, undefined, undefined, undefined, "PENDING")
                : Promise.resolve(null),
        ]);
        return {
            active: daily?.totalActiveStatus || 0,
            underReview: daily?.totalUnderReviewStatus || 0,
            ignored: daily?.totalIgnoredStatus || 0,
            humanApproval: humanApproval?.total || 0,
        };
    }, [startTimestamp, endTimestamp, endpoint, agentic]);

    const severity = useSummaryRequest(fetchSeverity, refresh);
    const category = useSummaryRequest(fetchCategory, refresh);
    const hosts = useSummaryRequest(fetchHosts, refresh);
    const counts = useSummaryRequest(fetchCounts, refresh);
    return { severity, category, hosts, counts };
}

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import func from "@/util/func";
import { INSIGHT_DEEP_LINK_PARAM } from "./insightsHelpers";

// Shared behavior behind every "open the insights flyout" entry point (AgenticAssetsPage,
// GuardrailPolicies, ViolationsPage): the DASHBOARD_INSIGHTS feature gate, the flyout's
// open/initialInsightId state, and the `?insight=<id>` deep-link handoff from the header's
// pinned insights popover (empty value opens the list, a real id opens straight to it).
// A page wires the returned pieces into wherever its button/flyout actually live — this hook
// owns none of the JSX, only the behavior that was getting copy-pasted with it.
export default function useInsightsEntryPoint() {
    const granted = func.checkForFeatureSaas("DASHBOARD_INSIGHTS");
    const [searchParams, setSearchParams] = useSearchParams();
    const [open, setOpen] = useState(false);
    const [initialInsightId, setInitialInsightId] = useState(null);

    const handleOpen = useCallback(() => { setInitialInsightId(null); setOpen(true); }, []);
    const handleClose = useCallback(() => setOpen(false), []);

    useEffect(() => {
        if (!granted || !searchParams.has(INSIGHT_DEEP_LINK_PARAM)) return;
        setInitialInsightId(searchParams.get(INSIGHT_DEEP_LINK_PARAM) || null);
        setOpen(true);
        const next = new URLSearchParams(searchParams);
        next.delete(INSIGHT_DEEP_LINK_PARAM);
        setSearchParams(next, { replace: true });
    }, [granted, searchParams, setSearchParams]);

    return { granted, open, initialInsightId, handleOpen, handleClose };
}

import { useEffect, useReducer, useState } from "react";
import { useNavigate } from "react-router-dom";
import { produce } from "immer";
import { Button, HorizontalGrid } from "@shopify/polaris";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import { ACTIVITY_TAB, THREAT_ACTIVITY_PATH, clearSavedActivityFilters, openThreatActivityPage } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";
import func from "@/util/func";
import KpiStrip from "./KpiStrip";
import AttentionCard from "./AttentionCard";
import TriageStatusCard from "./TriageStatusCard";
import DecisionsTrendCard from "./DecisionsTrendCard";
import PolicyViolationsCard from "./PolicyViolationsCard";
import ViolationsSourcesCard from "./ViolationsSourcesCard";
import TopEmployeesCard from "./TopEmployeesCard";
import TopAgenticAssetsCard from "./TopAgenticAssetsCard";
import guardrailOverviewMock from "./guardrailOverviewMock";
import guardrailsApi from "../api";
import { policyLink } from "../overview/GuardrailOverview";

const LIST_PAGE_SIZE = 5;

function AtlasGuardrailsOverview() {
    const navigate = useNavigate();
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        func.getLast30DaysRange(),
    );
    const [kpis, setKpis] = useState(null);
    const [triage, setTriage] = useState(null);
    const [decisions, setDecisions] = useState(null);
    const [policies, setPolicies] = useState(null);
    const [violationsSources, setViolationsSources] = useState(null);
    const [topAgenticAssets, setTopAgenticAssets] = useState(null);
    const [topEmployees, setTopEmployees] = useState(null);
    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    useEffect(() => {
        let cancelled = false;
        setKpis(null);
        setTriage(null);
        guardrailsApi.fetchSummary(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setKpis(resp); })
            .catch(() => { if (!cancelled) setKpis({}); });
        guardrailsApi.fetchTriageStatus(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setTriage(resp); })
            .catch(() => { if (!cancelled) setTriage({}); });
        setDecisions(null);
        guardrailsApi.fetchDecisionsTrend(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setDecisions(resp); })
            .catch(() => { if (!cancelled) setDecisions({}); });
        setPolicies(null);
        guardrailsApi.fetchViolationsByPolicy(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setPolicies(resp); })
            .catch(() => { if (!cancelled) setPolicies({}); });
        setViolationsSources(null);
        guardrailsApi.fetchViolationsSources(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setViolationsSources(resp); })
            .catch(() => { if (!cancelled) setViolationsSources({}); });
        setTopAgenticAssets(null);
        guardrailsApi.fetchTopAgenticAssets(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setTopAgenticAssets(resp); })
            .catch(() => { if (!cancelled) setTopAgenticAssets({}); });
        setTopEmployees(null);
        guardrailsApi.fetchTopEmployees(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setTopEmployees(resp); })
            .catch(() => { if (!cancelled) setTopEmployees({}); });
        return () => { cancelled = true; };
    }, [startTimestamp, endTimestamp]);

    const data = guardrailOverviewMock;
    const openActivity = (filters = {}) => {
        clearSavedActivityFilters(THREAT_ACTIVITY_PATH);
        openThreatActivityPage({ startTimestamp, endTimestamp, ...filters });
    };
    const listSizes = [violationsSources?.assetTypes, topEmployees?.employees, topAgenticAssets?.assets]
        .map((list) => list?.length || 0)
        .filter((n) => n > 0);
    const listPageSize = Math.min(LIST_PAGE_SIZE, ...listSizes);
    const openPolicy = (policy) => navigate(policyLink(policy.misconfigured ? null : policy.name));

    const components = [
        <KpiStrip key="kpis" kpis={kpis} onOpenActivity={openActivity} />,
        <HorizontalGrid key="attention" columns={{ xs: 1, md: ["twoThirds", "oneThird"] }} gap="4">
            <AttentionCard rows={data.attention} onReview={() => openActivity()} onViewAll={() => openActivity()} />
            <TriageStatusCard triage={triage} onOpenActivity={openActivity} />
        </HorizontalGrid>,
        <HorizontalGrid key="decisions" columns={{ xs: 1, md: 2 }} gap="4">
            <DecisionsTrendCard decisions={decisions} onOpenActivity={openActivity} />
            <PolicyViolationsCard data={policies} onOpenActivity={openActivity} onTune={openPolicy} />
        </HorizontalGrid>,
        <HorizontalGrid key="lists" columns={{ xs: 1, md: 3 }} gap="4">
            <ViolationsSourcesCard data={violationsSources} pageSize={listPageSize} onOpenActivity={openActivity} />
            <TopEmployeesCard data={topEmployees} pageSize={listPageSize} onOpenActivity={openActivity} />
            <TopAgenticAssetsCard data={topAgenticAssets} pageSize={listPageSize} onOpenActivity={openActivity} />
        </HorizontalGrid>,
    ];

    return (
        <PageWithMultipleCards
            title="Guardrail overview"
            subtitle="Violations across all managed devices and discovered AI agents, apps and MCP servers"
            isFirstPage
            secondaryActions={
                <DateRangeFilter
                    initialDispatch={currDateRange}
                    dispatch={(dateObj) =>
                        dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })
                    }
                />
            }
            primaryAction={<Button primary onClick={() => openActivity({ eventStatus: ACTIVITY_TAB.ACTIVE })}>Triage open violations</Button>}
            components={components}
        />
    );
}

export default AtlasGuardrailsOverview;

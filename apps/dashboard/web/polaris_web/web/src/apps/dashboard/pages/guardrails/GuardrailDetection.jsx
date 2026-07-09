import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { useNavigate, useSearchParams, useLocation } from "react-router-dom";
import { Box, HorizontalGrid, HorizontalStack, VerticalStack, Popover, ActionList, Button, Icon } from '@shopify/polaris';
import {FileMinor} from '@shopify/polaris-icons';
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import func from "@/util/func";
import values from "@/util/values";
import { produce } from "immer"
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";
import SessionStore from "../../../main/SessionStore";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import LocalStore from "@/apps/main/LocalStorageStore";
import NewLayoutTooltip from "@/apps/dashboard/pages/observe/agentic/NewLayoutTooltip";
import guardRailData from "./dummyData";
import SampleDetails from "../threat_detection/components/SampleDetails";
import { LABELS } from "../threat_detection/constants";
import SusDataTable from "../threat_detection/components/SusDataTable";
import NormalSampleDetails from "../threat_detection/components/NormalSampleDetails";
import TopThreatTypeChart from "../threat_detection/components/TopThreatTypeChart";
import InfoCard from "../dashboard/new_components/InfoCard";
import BarGraph from "../../components/charts/BarGraph";
import P95LatencyGraph from "../../components/charts/P95LatencyGraph";
import threatDetectionRequests from "../threat_detection/api";
import threatDetectionFunc from "../threat_detection/transform";

const convertToGraphData = (severityMap) => {
    return Object.keys(severityMap).map(x => ({
        text: func.toSentenceCase(x),
        value: severityMap[x],
        color: func.getHexColorForSeverity(x),
    }));
};


function GuardrailDetection() {

    const navigate = useNavigate();
    const location = useLocation();
    const [searchParams] = useSearchParams();
    const isMountedRef = useRef(true);

    const newLayout = LocalStore((state) => state.guardrailViolationsNewLayout);
    const setGuardrailViolationsNewLayout = LocalStore((state) => state.setGuardrailViolationsNewLayout);

    useEffect(() => {
        isMountedRef.current = true;
        return () => { isMountedRef.current = false; };
    }, []);

    useEffect(() => {
        if (func.isDemoAccount() && newLayout) {
            navigate("/dashboard/guardrails/violations", { replace: true });
        }
    }, [navigate, newLayout]);

    const handleLayoutToggle = useCallback(() => {
        setGuardrailViolationsNewLayout(true);
        navigate("/dashboard/guardrails/violations");
    }, [navigate, setGuardrailViolationsNewLayout]);

    // Parse deep-link query params (from Agentic Assets / Discovery click-throughs)
    const queryParams = useMemo(() => ({
        refId: searchParams.get("refId"),
        eventType: searchParams.get("eventType"),
        actor: searchParams.get("actor") || "",
        filterId: searchParams.get("filterId"),
        status: searchParams.get("eventStatus") || "ACTIVE",
        url: searchParams.get("url") || '',
        method: searchParams.get("method") || '',
        ruleViolated: searchParams.get("ruleViolated") || '-',
        hasQueryEvent: Boolean(
            searchParams.get("refId") &&
            searchParams.get("eventType") &&
            searchParams.get("filterId")
        )
    }), [searchParams]);

    // Initial tab from URL hash (#active, #under_review, #ignored)
    const initialTab = location.hash.replace('#', '') || 'active';

    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const [moreActions, setMoreActions] = useState(false);
    const [showDetails, setShowDetails] = useState(false);
    const [showNewTab, setShowNewTab] = useState(false)
    const [rowDataList, setRowDataList] = useState([])
    const [moreInfoData, setMoreInfoData] = useState({})
    const [sampleData, setSampleData] = useState({})
    const [currentEventId, setCurrentEventId] = useState(null)
    const [currentEventStatus, setCurrentEventStatus] = useState(null)
    const [triggerTableRefresh, setTriggerTableRefresh] = useState(0)
    const [subCategoryCount, setSubCategoryCount] = useState([]);
    const [severityCountMap, setSeverityCountMap] = useState([]);
    const [latencyData, setLatencyData] = useState([]);

    const startTimestamp = currDateRange?.period?.since
        ? Math.floor(Date.parse(currDateRange.period.since) / 1000)
        : Math.floor((Date.now() - 30 * 24 * 60 * 60 * 1000) / 1000);
    const endTimestamp = currDateRange?.period?.until
        ? Math.floor(Date.parse(currDateRange.period.until) / 1000)
        : Math.floor(Date.now() / 1000);

    useEffect(() => {
        const fetchChartData = async () => {
            try {
                const [categoryRes, severityRes] = await Promise.all([
                    threatDetectionRequests.fetchThreatCategoryCount(startTimestamp, endTimestamp),
                    threatDetectionRequests.fetchCountBySeverity(startTimestamp, endTimestamp),
                ]);
                const finalObj = threatDetectionFunc.getGraphsData(categoryRes);
                setSubCategoryCount(finalObj.subCategoryCount);

                const severityMap = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
                (severityRes?.categoryCounts || []).forEach(({ subCategory, count }) => {
                    if (subCategory in severityMap) severityMap[subCategory] = count;
                });
                setSeverityCountMap(convertToGraphData(severityMap));
            } catch (e) {
                // charts degrade gracefully with empty data
            }
        };

        const fetchLatencyData = async () => {
            try {
                const res = await threatDetectionRequests.fetchGuardrailLatency(startTimestamp, endTimestamp);
                const metrics = res?.result?.metrics || [];
                const byTimestamp = {};
                metrics.forEach(m => {
                    if (!byTimestamp[m.timestamp]) byTimestamp[m.timestamp] = {};
                    if (m.metricId === 'GUARDRAIL_REQUEST_LATENCY')  byTimestamp[m.timestamp].incomingRequestP95 = m.value;
                    if (m.metricId === 'GUARDRAIL_RESPONSE_LATENCY') byTimestamp[m.timestamp].outputResultP95 = m.value;
                });
                const raw = Object.entries(byTimestamp)
                    .map(([ts, vals]) => {
                        const req = vals.incomingRequestP95 || 0;
                        const resp = vals.outputResultP95 || 0;
                        return { timestamp: parseInt(ts), incomingRequestP95: req, outputResultP95: resp, totalP95: req + resp };
                    })
                    .sort((a, b) => a.timestamp - b.timestamp);
                setLatencyData(raw);
            } catch (e) {
                setLatencyData([]);
            }
        };

        fetchChartData();
        fetchLatencyData();
    }, [startTimestamp, endTimestamp]);

    // Auto-open flyout when page is deep-linked from Agentic Assets / Discovery
    useEffect(() => {
        if (!queryParams.hasQueryEvent) return;

        setShowDetails(true);
        setShowNewTab(true);
        setMoreInfoData({
            url: queryParams.url,
            method: queryParams.method,
            templateId: queryParams.filterId,
            ruleViolated: queryParams.ruleViolated,
        });
        setCurrentEventStatus(queryParams.status);

        threatDetectionRequests.fetchMaliciousRequest(
            queryParams.refId,
            queryParams.eventType,
            queryParams.actor,
            queryParams.filterId
        ).then(resp => {
            if (!isMountedRef.current) return;
            const payloads = (resp?.maliciousPayloadsResponses || []).map(p => ({
                orig: typeof p.orig === 'string' ? p.orig : JSON.stringify(p.orig || p),
            }));
            setRowDataList(payloads.length > 0 ? payloads : [{ orig: '{}' }]);
        }).catch(() => {});
    // Only run once when the page mounts with deep-link params
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [queryParams.hasQueryEvent, queryParams.refId]);

    const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);

    const handleStatusUpdate = () => {
        setTriggerTableRefresh(prev => prev + 1)
    }

    const rowClicked = async(data) => {
        // Use real payload data if available, otherwise fallback to dummy data for testing
        const payloadData = data.payload ? JSON.parse(data.payload) : guardRailData.sampleDataMap[data.url];
        const tempData = {"orig": JSON.stringify(payloadData)};
        setShowNewTab(true)
        const sameRow = false
        if (!sameRow) {
            let rowData = [tempData];
            setRowDataList(rowData)
            setShowDetails(true)
            setSampleData(data)
            setCurrentEventId(data.id)
            setCurrentEventStatus(data.status)
            setMoreInfoData({
                url: data.url,
                method: data.method,
                apiCollectionId: data.apiCollectionId,
                templateId: data.filterId,
                sessionId: data.sessionId,
                severity: data.severity,
                ruleViolated: data.ruleViolated,
                complianceMap: data.complianceMapData || {}
            })
        } else {
            setShowDetails(!showDetails)
        }

      }

    const components = [
        <VerticalStack key="guardrail-charts" gap="4">
            <HorizontalGrid gap="4" columns={2}>
                <TopThreatTypeChart
                    key="top-guardrail-types"
                    data={subCategoryCount}
                />
                <InfoCard
                    title={`${mapLabel("Guardrail", getDashboardCategory())} by severity`}
                    titleToolTip="Number of agentic components per severity"
                    component={
                        <BarGraph
                            data={severityCountMap}
                            areaFillHex="true"
                            height="280px"
                            defaultChartOptions={{ legend: { enabled: false } }}
                            showYAxis={true}
                            yAxisTitle="Number of Agentic Components"
                            showGridLines={true}
                            barWidth={100 - (severityCountMap.length * 6)}
                            barGap={12}
                        />
                    }
                />
            </HorizontalGrid>
            {latencyData.length > 0 && (
                <P95LatencyGraph
                    key="guardrail-detection-latency"
                    title="Guardrail Detection Latency"
                    subtitle="95th percentile latency metrics for Guardrail detection"
                    dataType="threat-security"
                    startTimestamp={startTimestamp}
                    endTimestamp={endTimestamp}
                    latencyData={latencyData}
                />
            )}
        </VerticalStack>,
        <SusDataTable
            key={`guardrail-data-table-${triggerTableRefresh}`}
            currDateRange={currDateRange}
            rowClicked={rowClicked}
            triggerRefresh={() => setTriggerTableRefresh(prev => prev + 1)}
            label={LABELS.GUARDRAIL}
            initialTab={initialTab}
        />,
        !showNewTab ? <NormalSampleDetails
            title={"Attacker payload"}
            showDetails={showDetails}
            setShowDetails={setShowDetails}
            sampleData={sampleData}
            key={"sus-sample-details"}
        /> :  <SampleDetails
                title={"Attacker payload"}
                showDetails={showDetails}
                setShowDetails={setShowDetails}
                data={rowDataList}
                key={"sus-sample-details"}
                moreInfoData={moreInfoData}
                threatFiltersMap={threatFiltersMap}
                eventId={currentEventId}
                eventStatus={currentEventStatus}
                onStatusUpdate={handleStatusUpdate}
            />
    ]

    const secondaryActionsComp = (
        <HorizontalStack gap={2}>
            <Popover
                active={moreActions}
                activator={(
                    <Button onClick={() => setMoreActions(!moreActions)} disclosure removeUnderline>
                        More Actions
                    </Button>
                )}
                autofocusTarget="first-node"
                onClose={() => { setMoreActions(false) }}
            >
                <Popover.Pane fixed>
                    <ActionList
                        actionRole="menuitem"
                        sections={
                            [
                                {
                                    title: 'Export',
                                    items: [
                                        {
                                            content: 'Export',
                                            onAction: () => {},
                                            prefix: <Box><Icon source={FileMinor} /></Box>
                                        }
                                    ]
                                },
                            ]
                        }
                    />
                </Popover.Pane>
            </Popover>
        </HorizontalStack>
    )

    return <PageWithMultipleCards
            title={
                <TitleWithInfo
                    titleText={mapLabel("Violations", getDashboardCategory())}
                    tooltipContent={"Identify malicious requests with Akto's powerful guardrailing capabilities"}
                />
            }
            isFirstPage={true}
            secondaryActions={func.isDemoAccount() && <NewLayoutTooltip checked={false} onChange={handleLayoutToggle} />}
            primaryAction={
                <HorizontalStack gap="3" blockAlign="center">
                    {secondaryActionsComp}
                    <DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />
                </HorizontalStack>
            }
            components={components}
        />
}

export default GuardrailDetection;
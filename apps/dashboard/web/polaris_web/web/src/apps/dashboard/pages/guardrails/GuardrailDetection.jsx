import { useMemo, useReducer, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Box, HorizontalStack, Popover, ActionList, Button, Icon } from '@shopify/polaris';
import {FileMinor} from '@shopify/polaris-icons';
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import func from "@/util/func";
import values from "@/util/values";
import { produce } from "immer"
import { getDashboardCategory, getReportCategoryShortName, mapLabel, shortNameToCategory } from "../../../main/labelHelper";
import PersistStore from "../../../main/PersistStore";
import SessionStore from "../../../main/SessionStore";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import guardRailData from "./dummyData";
import SampleDetails from "../threat_detection/components/SampleDetails";
import { LABELS } from "../threat_detection/constants";
import SusDataTable from "../threat_detection/components/SusDataTable";
import NormalSampleDetails from "../threat_detection/components/NormalSampleDetails";
import { extractBehaviour } from "../threat_detection/utils/formatUtils";

// Apply ?category= before the first render — same as GuardrailPolicies.jsx. A link opened in a new
// tab has no PersistStore session, so without this the page would load the wrong category.
const categoryOverride = shortNameToCategory[getReportCategoryShortName()];
if (categoryOverride) {
    PersistStore.getState().setDashboardCategory(categoryOverride);
}

function GuardrailDetection() {

    const [searchParams] = useSearchParams();
    // A link into this page (e.g. from a flagged trace) carries the time range it wants to land on,
    // either as a preset alias or as a start/end pair. Same params ThreatDetectionPage accepts.
    const initialVal = useMemo(() => {
        const preset = values.ranges.find((r) => r.alias === searchParams.get("range"));
        if (preset) return preset;
        const startTs = parseInt(searchParams.get("startTimestamp"), 10);
        const endTs = parseInt(searchParams.get("endTimestamp"), 10);
        if (!Number.isNaN(startTs) && !Number.isNaN(endTs)) {
            return {
                alias: "custom",
                title: "Custom",
                period: { since: new Date(startTs * 1000), until: new Date(endTs * 1000) },
            };
        }
        return values.ranges[3];
    }, [searchParams]);
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const [moreActions, setMoreActions] = useState(false);
    const [showDetails, setShowDetails] = useState(false);
    const [showNewTab, setShowNewTab] = useState(false)
    const [rowDataList, setRowDataList] = useState([])
    const [moreInfoData, setMoreInfoData] = useState({})
    const [sampleData, setSampleData] = useState({})
    const [currentEventId, setCurrentEventId] = useState(null)
    const [currentEventStatus, setCurrentEventStatus] = useState(null)
    const [currentHumanResponse, setCurrentHumanResponse] = useState(null)
    const [triggerTableRefresh, setTriggerTableRefresh] = useState(0)
    const applyPayloadSearchRef = useRef(() => {});

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
            setCurrentHumanResponse(data.humanResponse)
            setMoreInfoData({
                url: data.url,
                method: data.method,
                apiCollectionId: data.apiCollectionId,
                templateId: data.filterId,
                sessionId: data.sessionId,
                severity: data.severity,
                ruleViolated: data.ruleViolated,
                complianceMap: data.complianceMapData || {},
                // For the "Approve server" action on approval-behaviour guardrail events.
                // Prefer the precomputed row field (data.metadata passthrough is dropped by the table).
                behaviour: data.behaviourRaw || extractBehaviour(data.metadata),
                host: data.host,
            })
        } else {
            setShowDetails(!showDetails)
        }

      }

    const components =  [
        <SusDataTable
            key={`guardrail-data-table-${triggerTableRefresh}`}
            currDateRange={currDateRange}
            rowClicked={rowClicked}
            triggerRefresh={() => setTriggerTableRefresh(prev => prev + 1)}
            label={LABELS.GUARDRAIL}
            onRegisterPayloadSearch={(fn) => { applyPayloadSearchRef.current = fn; }}
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
                humanResponse={currentHumanResponse}
                onStatusUpdate={handleStatusUpdate}
                onAddAsSearchFilter={(text, side, line) => applyPayloadSearchRef.current?.(text, side, line)}
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
                    titleText={mapLabel("Guardrail Activity", getDashboardCategory())}
                    tooltipContent={"Identify malicious requests with Akto's powerful guardrailing capabilities"}
                />
            }
            isFirstPage={true}
            primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
            components={components}
            secondaryActions={secondaryActionsComp}
        />
}

export default GuardrailDetection;
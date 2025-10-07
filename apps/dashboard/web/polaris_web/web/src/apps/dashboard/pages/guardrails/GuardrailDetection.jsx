import { useReducer, useState } from "react";
import { Box, HorizontalStack, Popover, ActionList, Button, Icon } from '@shopify/polaris';
import {FileMinor} from '@shopify/polaris-icons';
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import func from "@/util/func";
import values from "@/util/values";
import { produce } from "immer"
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";
import SessionStore from "../../../main/SessionStore";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import guardRailData from "./dummyData";
import SampleDetails from "../threat_detection/components/SampleDetails";
import { LABELS } from "../threat_detection/constants";
import SusDataTable from "../threat_detection/components/SusDataTable";
import NormalSampleDetails from "../threat_detection/components/NormalSampleDetails";


function GuardrailDetection() {

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
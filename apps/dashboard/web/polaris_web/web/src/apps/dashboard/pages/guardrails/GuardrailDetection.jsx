import { useReducer, useState, useEffect } from "react";
import { Box, EmptySearchResult, HorizontalStack, Popover, ActionList, Button, Icon, Badge} from '@shopify/polaris';
import {FileMinor, HideMinor, ViewMinor} from '@shopify/polaris-icons';
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import func from "@/util/func";
import values from "@/util/values";
import { produce } from "immer"
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";
import SessionStore from "../../../main/SessionStore";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { labelMap } from '../../../main/labelHelperMap';
import PersistStore from '@/apps/main/PersistStore';
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import guardRailData from "./dummyData";
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";
import dayjs from "dayjs";
import SampleDetails from "../threat_detection/components/SampleDetails";

const resourceName = {
  singular: "sample",
  plural: "samples",
};

const headings = [
  {
    text: "Severity",
    value: "severityComp",
    title: "Severity",
  },
  {
    text: labelMap[PersistStore.getState().dashboardCategory]["API endpoint"],
    value: "endpointComp",
    title: labelMap[PersistStore.getState().dashboardCategory]["API endpoint"],
  },
  {
    text: "Actor",
    value: "actorComp",
    title: "Actor",
    filterKey: 'actor'
  },
  {
    text: "Filter",
    value: "filterId",
    title: "Guardrail type",
  },
  {
    text: "Collection",
    value: "apiCollectionName",
    title: "Collection",
    maxWidth: "95px",
    type: CellType.TEXT,
  },
  {
    text: "Discovered",
    title: "Detected",
    value: "discoveredTs",
    type: CellType.TEXT,
    sortActive: true,
  },
];

const sortOptions = [
  {
    label: "Discovered time",
    value: "detectedAt asc",
    directionLabel: "Newest",
    sortKey: "detectedAt",
    columnIndex: 5,
  },
  {
    label: "Discovered time",
    value: "detectedAt desc",
    directionLabel: "Oldest",
    sortKey: "detectedAt",
    columnIndex: 5,
  },
];


function GuardrailDetection() {

    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const [moreActions, setMoreActions] = useState(false);
    const [showDetails, setShowDetails] = useState(false);
    const [sampleData, setSampleData] = useState([])
    const [showNewTab, setShowNewTab] = useState(false)
    const [rowDataList, setRowDataList] = useState([])
    const [moreInfoData, setMoreInfoData] = useState({})
    
    const collectionsMap = PersistStore((state) => state.collectionsMap);
    const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);

    const data = guardRailData.guardRailDummyData.maliciousEvents.map((x) => {
      const severity = threatFiltersMap[x?.filterId]?.severity || "HIGH"
      return {
        ...x,
        id: x.id,
        actorComp: x.actor,
        endpointComp: x.url,
        apiCollectionName: collectionsMap[x.apiCollectionId] || "-",
        discoveredTs: dayjs(x.timestamp*1000).format("DD-MM-YYYY HH:mm:ss"),
        sourceIPComponent: x?.ip || "-",
        type: x?.type || "-",
        severityComp:  severity
        
      };
    });


    const emptyStateMarkup = (
        <EmptySearchResult
          title={'No guardrail activity found'}
          withIllustration
        />
      );


    const rowClicked = async(data) => {
        const tempData = {"orig": JSON.stringify(guardRailData.sampleDataMap[data.url])};
        setShowNewTab(true)
        const sameRow = false
        if (!sameRow) {
            let rowData = [tempData];
            setRowDataList(rowData)
            setShowDetails(true)
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
        <GithubSimpleTable
            key={data.length}
            resourceName={resourceName}
            useNewRow={true}
            headers={headings}
            headings={headings}
            data={data}
            hideQueryField={true}
            hidePagination={true}
            showFooter={false}
            sortOptions={sortOptions}
            emptyStateMarkup={emptyStateMarkup}   
            onRowClick={rowClicked}    
            rowClickable={true} 
        ></GithubSimpleTable>,
         <SampleDetails
                title={"Attacker payload"}
                showDetails={showDetails}
                setShowDetails={setShowDetails}
                data={rowDataList}
                key={"sus-sample-details"}
                moreInfoData={moreInfoData}
                threatFiltersMap={threatFiltersMap}
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
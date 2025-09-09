import { useReducer, useState, useEffect } from "react";
import { Box, EmptySearchResult, HorizontalStack, Popover, ActionList, Button, Icon, Badge} from '@shopify/polaris';
import {CancelMinor, EditMinor, FileMinor, HideMinor, ViewMinor} from '@shopify/polaris-icons';
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
import SampleData from '@/apps/dashboard/components/shared/SampleData'
import FlyLayout from '@/apps/dashboard/components/layouts/FlyLayout';

const resourceName = {
  singular: "policy",
  plural: "policies",
};

const headings = [
  {
    text: "Severity",
    value: "severityComp",
    title: "Severity",
  },
  {
    text: "Policy",
    value: "policy",
    title: "Policy",
  },
  {
    text: "Category",
    value: "category",
    title: "Category",
  },
  {
    text: "Created on",
    title: "Created on",
    value: "createdTs",
    type: CellType.TEXT,
    sortActive: true,
  },
  {
    title: '',
    type: CellType.ACTION,
  }
];

const sortOptions = [
  {
    label: "Created on",
    value: "createdTs asc",
    directionLabel: "Newest",
    sortKey: "createdTs",
    columnIndex: 3,
  },
  {
    label: "Created on",
    value: "createdTs desc",
    directionLabel: "Oldest",
    sortKey: "createdTs",
    columnIndex: 3,
  },
];

function GuardrailPolicies() {
    const [showDetails, setShowDetails] = useState(false);
    const [sampleData, setSampleData] = useState([])
  

    const emptyStateMarkup = (
        <EmptySearchResult
          title={'No guardrail policy found'}
          withIllustration
        />
      );
      
    const policyData = guardRailData.policies

    const rowClicked = async(data) => {
        setShowDetails(true)
        setSampleData(data.yaml)
    }

    const currentComponentsForYamlPolicy = [
        <Box paddingBlockStart={5} paddingInlineEnd={4} paddingInlineStart={4}>

            <SampleData data={{message: sampleData}} minHeight="70vh" language="YAML" editorLanguage={"custom_yaml"} />

        </Box>
    ]

    const getActionsList = (item) => {
      const actionItems = [{title: 'Actions',
        items: [
          {
            content: 'Disable policy',
            icon: CancelMinor,
            onAction: () => {},
        },
        {
            content: 'View policy',
            icon: ViewMinor,
            onAction: () => {rowClicked(item)},
        }
        ]
    }]
    return actionItems
    }


      const components = [
        <GithubSimpleTable
            key={0}
            resourceName={resourceName}
            useNewRow={true}
            headers={headings}
            headings={headings}
            data={policyData}
            hideQueryField={true}
            hidePagination={true}
            showFooter={false}
            sortOptions={sortOptions}
            emptyStateMarkup={emptyStateMarkup}   
            onRowClick={rowClicked}    
            rowClickable={true} 
            getActions={getActionsList}
            hasRowActions={true}
            hardCodedKey={true}

        />,   
        <FlyLayout
            title={"Policy Details"}
            show={showDetails}
            setShow={setShowDetails}
            components={currentComponentsForYamlPolicy}
            loading={false}
            showDivider={true}
            newComp={true}
            isHandleClose={false}
        />
    ];


    return <PageWithMultipleCards
            title={
                <TitleWithInfo
                    titleText={mapLabel("Guardrail Policies", getDashboardCategory())}
                    tooltipContent={"Identify malicious requests with Akto's powerful guardrailing capabilities"}
                />
            }
            isFirstPage={true}
            components={components}
        />
}

export default GuardrailPolicies;
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useState } from "react";
import api from "../api"
import Store from "../../../store";
import transform from "../transform";
import func from "@/util/func";
import { ClockMinor,DynamicSourceMinor,LinkMinor } from '@shopify/polaris-icons';
import PersistStore from "../../../../main/PersistStore";
import { Button } from "@shopify/polaris";

const headers = [
    {
        text:"Issue",
        value:"categoryName",
        itemOrder:1
    },
    
    {
        text: "Collection",
        value: "collection",
        itemOrder: 3,
        icon: DynamicSourceMinor,
    },
    {
        text: "API Collection ID",
        value: "apiCollectionId",
    },
    {
        text: "Discovered",
        value: "detected_timestamp",
        itemOrder: 3,
        icon: ClockMinor,
    },
    {
        text: "Timestamp",
        value: "timestamp",
    },
    {
        text: "Endpoint",
        value: "url",
        itemOrder: 3,
        icon: LinkMinor,
    },
    {
        text:"Severity",
        value:"severity",
        itemOrder:2
    },
    {
        text:"Status",
        value:"issueStatus"
    },
    {
        text:"Ignore reason",
        value:"ignoreReason",
        itemCell:2
    },
    {
        value: 'collectionIds'
    }
]

const sortOptions = [
    { label: 'Discovered time', value: 'timestamp asc', directionLabel: 'Newest', sortKey: 'timestamp' },
    { label: 'Discovered time', value: 'timestamp desc', directionLabel: 'Oldest', sortKey: 'timestamp' },
    { label: 'Issue', value: 'categoryName asc', directionLabel: 'A-Z', sortKey: 'categoryName' },
    { label: 'Issue', value: 'categoryName desc', directionLabel: 'Z-A', sortKey: 'categoryName' },    
];

let filtersOptions = [
    {
        key: 'apiCollectionId',
        label: 'Collection',
        title: 'Collection',
        choices: [],
    },
    {
        key: 'severity',
        label: 'Severity',
        title: 'Severity',
        choices: [
            { label: "High", value: "HIGH" }, 
            { label: "Medium", value: "MEDIUM" },
            { label: "Low", value: "LOW" }
        ],
    },
    {
        key:"issueCategory",
        label: "Issue category",
        title:"Issue category",
        choices:[]
    },
    {
        key:"issueStatus",
        label:"Status",
        title:"Status",
        singleSelect:true,
        choices:[
            { label:"Open", value:"OPEN" },
            { label:"Fixed", value:"FIXED" },
            { label:"Ignored", value:"IGNORED" },
        ]
    },
    {
        key:"startTimestamp",
        label: "Time",
        title:"Time",
        singleSelect:true,
        choices:[
            { label:"Last 1 day", value:func.timeNow() - 24 * 60 * 60 },
            { label:"Last week", value:func.timeNow() - 7 * 24 * 60 * 60 },
            { label:"Last month", value:func.timeNow() + 60 - 30 * 24 * 60 * 60 },
        ]
    },
    {
        key: 'collectionIds',
        label: 'API groups',
        title: 'API groups',
        choices: [],
    }
]

let appliedFilters = [
    {
        key: 'issueStatus',
        label: 'Open',
        value: ['OPEN'],
        onRemove: () => {}
    }
]

const resourceName = {
    singular: 'issue',
    plural: 'issues',
};

async function getNextUrl(issueId){
    const res = await api.fetchTestingRunResult(JSON.parse(issueId))
    return "/dashboard/testing/issues/result/"+res.testingRunResult.hexId;
}

function IssuesPage(){

    const [loading, setLoading] = useState(true);
    const subCategoryMap = PersistStore(state => state.subCategoryMap);
    const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
    const [issueStatus, setIssueStatus] = useState([]);
    const [issuesFilters, setIssuesFilters] = useState({})
    const [key, setKey] = useState(false);
    const apiCollectionMap = PersistStore(state => state.collectionsMap);
    const allCollections = PersistStore(state => state.allCollections);

    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }

    filtersOptions = func.getCollectionFilters(filtersOptions)

    let promotedBulkActions = (selectedResources) => {
        selectedResources = selectedResources.map((item) => JSON.parse(item));
        
        
        function ignoreAction(ignoreReason){
            api.bulkUpdateIssueStatus(selectedResources, "IGNORED", ignoreReason ).then((res) => {
                setToast(true, false, `Issue${selectedResources.length==1 ? "" : "s"} ignored`)
                setKey(!key);
            })
        }
        
        function reopenAction(){
            api.bulkUpdateIssueStatus(selectedResources, "OPEN", "" ).then((res) => {
                setToast(true, false, `Issue${selectedResources.length==1 ? "" : "s"} re-opened`)
                setKey(!key);
            })
        }
        
        let issues = [{
            content: 'False positive',
            onAction: () => { ignoreAction("False positive") }
        },
        {
            content: 'Acceptable risk',
            onAction: () => { ignoreAction("Acceptable risk") }
        },
        {
            content: 'No time to fix',
            onAction: () => { ignoreAction("No time to fix") }
        }]
        
        let reopen =  [{
            content: 'Reopen',
            onAction: () => { reopenAction() }
        }]
        
        let ret = [];
        let status = issueStatus[0];
        
        switch (status) {
            case "OPEN": ret = [].concat(issues); break;
            case "IGNORED": if (selectedResources.length == 1) {
                ret = [].concat(issues);
            }
                ret = ret.concat(reopen);
                appliedFilters[0].label = "Ignored"
                appliedFilters[0].value = ["IGNORED"]
                break;
            case "FIXED":
        }

        return ret;
    }
    
    let store = {}
    let result = []
    Object.values(subCategoryMap).forEach((x) => {
        let superCategory = x.superCategory
        if (!store[superCategory.name]) {
            result.push({ "label": superCategory.displayName, "value": superCategory.name })
            store[superCategory.name] = []
        }
        store[superCategory.name].push(x._name);
    })
    filtersOptions[2].choices = [].concat(result)
    let categoryToSubCategories = store

    function disambiguateLabel(key, value) {
        switch (key) {
            case "startTimestamp":
                return func.convertToDisambiguateLabel(value, func.prettifyEpoch, 2)
            case "issueStatus":
            case "severity":
                return func.convertToDisambiguateLabel(value, func.toSentenceCase, 2)
            case "issueCategory":
                return func.convertToDisambiguateLabelObj(value, null, 3)
            case "collectionIds":
            case "apiCollectionId":
                return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 2)
            default:
              return value;
          }          
    }

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);

        let total =0;
        let ret = []
        let filterCollectionsId = filters.apiCollectionId.concat(filters.collectionIds);
        let filterSeverity = filters.severity
        let filterSubCategory = []
        filters?.issueCategory?.forEach((issue) => {
            filterSubCategory = filterSubCategory.concat(categoryToSubCategories[issue])
        })
        let filterStatus = filters.issueStatus
        setIssueStatus(filterStatus);
        let startTimestamp = filters?.startTimestamp?.[0] || 0;
        let obj = {
            'filterStatus': filterStatus,
            'filterCollectionsId': filterCollectionsId,
            'filterSeverity': filterSeverity,
            filterSubCategory: filterSubCategory,
            startEpoch: startTimestamp
        }
        setIssuesFilters(obj)
        await api.fetchIssues(skip, limit,filterStatus,filterCollectionsId,filterSeverity,filterSubCategory,startTimestamp).then((res) => {
            total = res.totalIssuesCount;
            ret = transform.prepareIssues(res, subCategoryMap, subCategoryFromSourceConfigMap, apiCollectionMap);
            setLoading(false);
        })
        ret = func.sortFunc(ret, sortKey, sortOrder)
        return {value:ret , total:total};
    }

    const openVulnerabilityReport = () => {
        let summaryId = btoa(JSON.stringify(issuesFilters))
    window.open('/dashboard/issues/summary/' + summaryId, '_blank');
    }
    
    return (
        <PageWithMultipleCards
            title="Issues"
            isFirstPage={true}
            components = {[
                <GithubServerTable
                    key={key}
                    headers={headers}
                    resourceName={resourceName} 
                    appliedFilters={appliedFilters}
                    sortOptions={sortOptions}
                    disambiguateLabel={disambiguateLabel}
                    selectable = {true}
                    loading={loading}
                    fetchData={fetchData}
                    filters={filtersOptions}
                    promotedBulkActions={promotedBulkActions}
                    hideQueryField={true}
                    getNextUrl={getNextUrl}
                    rowClickable={true}
                    getStatus={func.getTestResultStatus}
                />
            ]}
            primaryAction={<Button monochrome removeUnderline plain onClick={() => openVulnerabilityReport()}>Export vulnerability report</Button>}
            />
    )
}

export default IssuesPage
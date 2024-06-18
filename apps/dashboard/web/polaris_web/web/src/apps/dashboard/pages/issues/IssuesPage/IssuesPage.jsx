import AktoButton from './../../../components/shared/AktoButton';
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useState } from "react";
import api from "../api"
import Store from "../../../store";
import transform from "../transform";
import func from "@/util/func";
import { ClockMinor,DynamicSourceMinor,LinkMinor, MarkFulfilledMinor, ReportMinor, ExternalMinor } from '@shopify/polaris-icons';
import PersistStore from "../../../../main/PersistStore";
import { Button } from "@shopify/polaris";
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout";
import { ISSUES_PAGE_DOCS_URL } from "../../../../main/onboardingData";
import {SelectCollectionComponent} from "../../testing/TestRunsPage/TestrunsBannerComponent"
import { useEffect } from "react";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";

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
        sortActive: true
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
    },
    {
        text:"unread",
        value:"unread",
        itemOrder:2
    },
]

const sortOptions = [
    { label: 'Discovered time', value: 'timestamp asc', directionLabel: 'Newest', sortKey: 'timestamp', columnIndex: 5 },
    { label: 'Discovered time', value: 'timestamp desc', directionLabel: 'Oldest', sortKey: 'timestamp', columnIndex: 5 },
    { label: 'Issue', value: 'categoryName asc', directionLabel: 'A-Z', sortKey: 'categoryName', columnIndex: 1 },
    { label: 'Issue', value: 'categoryName desc', directionLabel: 'Z-A', sortKey: 'categoryName', columnIndex: 1 },
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
    const userRole = window.USER_ROLE
    const disableButton = (userRole === "GUEST" || userRole === "DEVELOPER")

    const [loading, setLoading] = useState(true);
    const subCategoryMap = PersistStore(state => state.subCategoryMap);
    const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
    const [issueStatus, setIssueStatus] = useState([]);
    const [issuesFilters, setIssuesFilters] = useState({})
    const [key, setKey] = useState(false);
    const apiCollectionMap = PersistStore(state => state.collectionsMap);
    const [showEmptyScreen, setShowEmptyScreen] = useState(true)

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
            onAction: () => { ignoreAction("False positive") },
            'disabled': disableButton
        },
        {
            content: 'Acceptable risk',
            onAction: () => { ignoreAction("Acceptable risk") },
            'disabled': disableButton
        },
        {
            content: 'No time to fix',
            onAction: () => { ignoreAction("No time to fix") },
            'disabled': disableButton
        }]
        
        let reopen =  [{
            content: 'Reopen',
            onAction: () => { reopenAction() },
            'disabled': disableButton
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
        const res = await api.fetchIssues(skip, 1, null, null, null, null, 0)
        if(res.totalIssuesCount === 0){
            setShowEmptyScreen(true)
            return {value:{} , total:0};
        }
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

    const infoItems = [
        {
            title: "Triage",
            icon: MarkFulfilledMinor,
            description: "Prioritize, assign them to team members and manage API issues effectively.",
        },
        {
            title: "Download vulnerability report",
            icon: ReportMinor,
            description: "Export and share detailed report of vulnerabilities in your APIs.",
        },
        {
            title: "Send them to GitHub",
            icon: ExternalMinor,
            description: "Integrate Akto with GitHub to send all issues to your developers on GitHub."
        }
    ]

  useEffect(() => {
    if (subCategoryMap && Object.keys(subCategoryMap).length > 0 && apiCollectionMap && Object.keys(apiCollectionMap).length > 0) {
        setShowEmptyScreen(false)
    }
  }, [subCategoryMap, apiCollectionMap])
    
    return (
        <PageWithMultipleCards
            title={<TitleWithInfo
                    titleText={"Issues"}
                    tooltipContent={"Issues are created when a test from test library has passed validation and thus a potential vulnerability is found."}
                />}
            isFirstPage={true}
            components = {[
                showEmptyScreen ? 
                <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/alert_hexagon.svg"}
                    headingText={"No issues yet!"}
                    description={"There are currently no issues with your APIs. Haven't run your tests yet? Start testing now to prevent any potential issues."}
                    buttonText={"Run test"}
                    infoItems={infoItems}
                    infoTitle={"Once you have issues:"}
                    learnText={"issues"}
                    docsUrl={ISSUES_PAGE_DOCS_URL}
                    bodyComponent={<SelectCollectionComponent />}
                />

            
            : <GithubServerTable
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
                    getStatus={func.getTestResultStatus}
                    filterStateUrl={"/dashboard/issues"}
                />
            ]}
            primaryAction={<AktoButton  primary onClick={() => openVulnerabilityReport()} disabled={showEmptyScreen || disableButton}>Export vulnerability report</AktoButton>}
            />
    )
}

export default IssuesPage
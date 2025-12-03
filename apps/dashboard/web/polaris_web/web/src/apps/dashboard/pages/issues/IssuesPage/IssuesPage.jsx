import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useReducer, useState } from "react";
import api from "../api"
import Store from "../../../store";
import func from "@/util/func";
import { MarkFulfilledMinor, ReportMinor, ExternalMinor } from '@shopify/polaris-icons';
import PersistStore from "../../../../main/PersistStore";
import { ActionList, Button, HorizontalGrid, HorizontalStack, IndexFiltersMode, Popover, Modal, TextField, Text, VerticalStack } from "@shopify/polaris";
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout";
import { ISSUES_PAGE_DOCS_URL } from "../../../../main/onboardingData";
import {SelectCollectionComponent} from "../../testing/TestRunsPage/TestrunsBannerComponent"
import { useEffect } from "react";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import { useSearchParams } from "react-router-dom";
import TestRunResultPage from "../../testing/TestRunResultPage/TestRunResultPage";
import LocalStore from "../../../../main/LocalStorageStore";
import { CellType } from "../../../components/tables/rows/GithubRow.js";
import DateRangeFilter from "../../../components/layouts/DateRangeFilter.jsx";
import { produce } from "immer";
import "./style.css"
import transform from "../transform.js";
import SummaryInfo from "./SummaryInfo.jsx";
import useTable from "../../../components/tables/TableContext.js";
import values from "@/util/values";
import SpinnerCentered from "../../../components/progress/SpinnerCentered.jsx";
import TableStore from "../../../components/tables/TableStore.js";
import CriticalFindingsGraph from "./CriticalFindingsGraph.jsx";
import AllUnsecuredAPIsOverTimeGraph from "./AllUnsecuredAPIsOverTimeGraph.jsx";
import ApisWithMostOpenIsuuesGraph from './ApisWithMostOpenIsuuesGraph.jsx';
import IssuesByCollection from './IssuesByCollection.jsx';
import CriticalUnresolvedApisByAge from './CriticalUnresolvedApisByAge.jsx';
import settingFunctions from "../../settings/module.js";
import JiraTicketCreationModal from "../../../components/shared/JiraTicketCreationModal.jsx";
import testingApi from "../../testing/api.js"
import { saveAs } from 'file-saver'
import issuesFunctions from '@/apps/dashboard/pages/issues/module';
import IssuesGraphsGroup from "./IssuesGraphsGroup.jsx";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper.js";


const sortOptions = [
    { label: 'Severity', value: 'severity asc', directionLabel: 'Highest', sortKey: 'severity', columnIndex: 2 },
    { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest', sortKey: 'severity', columnIndex: 2 },
    { label: 'Number of endpoints', value: 'numberOfEndpoints asc', directionLabel: 'More', sortKey: 'numberOfEndpoints', columnIndex: 5 },
    { label: 'Number of endpoints', value: 'numberOfEndpoints desc', directionLabel: 'Less', sortKey: 'numberOfEndpoints', columnIndex: 5 },
    { label: 'Discovered time', value: 'creationTime asc', directionLabel: 'Newest', sortKey: 'creationTime', columnIndex: 7 },
    { label: 'Discovered time', value: 'creationTime desc', directionLabel: 'Oldest', sortKey: 'creationTime', columnIndex: 7 },
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
            {label: 'Critical', value: 'CRITICAL'},
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
        key: `issueName`,
        label: 'Issue name',
        title: 'Issue name',
        choices: [],
    },
    {
        key: 'collectionIds',
        label: mapLabel('Api', getDashboardCategory()) + ' groups',
        title: mapLabel('Api', getDashboardCategory()) + ' groups',
        choices: [],
    },
    {
        key: 'activeCollections',
        label: 'Active collections',
        title: 'Active collections',
        choices: [
            {
                label:"Active collections",
                value:true
            },
            {
                label:"All collections",
                value:false
            }],
        singleSelect:true
    },
    {
        key: 'tagsId',
        label: 'Tags',
        title: 'Tags',
        choices: [],
    }
]

const resourceName = {
    singular: 'issue',
    plural: 'issues',
};

function IssuesPage() {
    const [headers, setHeaders] = useState([
        {
          title: '',
          type: CellType.COLLAPSIBLE
        },
        {
            title: "Severity",
            text: "Severity",
            value: "severity",
            textValue: "severityVal",
            sortActive: true
        },
        {
            title: "Issue name",
            text: "Issue name",
            value: "issueName",
        },
        {
            title: "Category",
            text: "Category",
            value: "category"
        },
        {
            title: "Number of endpoints",
            text: "Number of endpoints",
            value: "numberOfEndpoints",
            sortActive: true
        },
        {
            title: "Domains",
            text: "Domains",
            value: "domains",
            textValue:"domainVal"
        },
        {
            title: "Compliance",
            text: "Compliance",
            value: "compliance",
            sortActive: true,
            textValue: "complianceVal"
        },
        {
            title: "Discovered",
            text: "Discovered",
            value: "creationTime",
            sortActive: true
        },
        {
            value: 'collectionIds'
        },
    ])

    const subCategoryMap = LocalStore(state => state.subCategoryMap);
    const [issuesFilters, setIssuesFilters] = useState({})
    const [key, setKey] = useState(false);
    const apiCollectionMap = PersistStore(state => state.collectionsMap);
    const [showEmptyScreen, setShowEmptyScreen] = useState(true)
    const [selectedTab, setSelectedTab] = useState("open")
    const [loading, setLoading] = useState(true)
    const [selected, setSelected] = useState(0)
    const [tableLoading, setTableLoading] = useState(false)
    const [issuesDataCount, setIssuesDataCount] = useState([])
    const [jiraModalActive, setJiraModalActive] = useState(false)
    const [selectedIssuesItems, setSelectedIssuesItems] = useState([])
    const [jiraProjectMaps,setJiraProjectMap] = useState({})
    const [issueType, setIssueType] = useState('');
    const [projId, setProjId] = useState('')

    const [boardsModalActive, setBoardsModalActive] = useState(false)
    const [projectToWorkItemsMap, setProjectToWorkItemsMap] = useState({})
    const [projectId, setProjectId] = useState('')
    const [workItemType, setWorkItemType] = useState('')
    const [issuesByApis, setIssuesByApis] = useState({});

    const [serviceNowModalActive, setServiceNowModalActive] = useState(false)
    const [serviceNowTables, setServiceNowTables] = useState([])
    const [serviceNowTable, setServiceNowTable] = useState('')
    const [labelsText, setLabelsText] = useState('')

    // Compulsory description modal states
    const [compulsoryDescriptionModal, setCompulsoryDescriptionModal] = useState(false)
    const [pendingIgnoreAction, setPendingIgnoreAction] = useState(null)
    const [mandatoryDescription, setMandatoryDescription] = useState("")
    const [modalLoading, setModalLoading] = useState(false)
    const [compulsorySettings, setCompulsorySettings] = useState({
        falsePositive: false,
        noTimeToFix: false,
        acceptableFix: false
    })

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5])

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    const hostNameMap = PersistStore.getState().hostNameMap
    const tagsCollectionsMap = PersistStore.getState().tagCollectionsMap


    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }

    const handleSelectedTab = (selectedIndex) => {
        setTableLoading(true)
        setSelected(selectedIndex)
        setTimeout(()=> {
            setTableLoading(false)
        }, 200)
    }

    const definedTableTabs = ["Open", "Fixed", "Ignored"]

    const { tabsInfo, selectItems } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, {}, issuesDataCount)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

    const resetResourcesSelected = () => {
        TableStore.getState().setSelectedItems([])
        selectItems([])
        setKey(!key)
        setSelectedIssuesItems([])
    }
    
    useEffect(() => {
        const statusHeader = {
            title: "Status",
            text: "Status",
            value: "issueStatus"
        }
    
        if (selectedTab.toUpperCase() === 'OPEN') {
            if (!headers.some(header => header.value === "issueStatus")) {
                setHeaders(prevHeaders => {
                    const newHeaders = [...prevHeaders, statusHeader]
                    return newHeaders
                })
            }
        } else {
            setHeaders(prevHeaders => prevHeaders.filter(header => header.value !== "issueStatus"))
        }
        resetResourcesSelected();
    }, [selectedTab])

    useEffect(() => {
        setKey(!key)
    }, [startTimestamp, endTimestamp])

    useEffect(() => {
        if (selectedTab.toUpperCase() === 'OPEN') {
            setKey(!key)
        }
    }, [])

    // Fetch compulsory description settings
    useEffect(() => {
        const fetchCompulsorySettings = async () => {
            try {
                const {resp} = await settingFunctions.fetchAdminInfo();
                
                if (resp?.compulsoryDescription) {
                    setCompulsorySettings(resp.compulsoryDescription);
                }
            } catch (error) {
                console.error("Error fetching compulsory settings:", error);
            }
        };
        fetchCompulsorySettings();
    }, []);

    const [searchParams, setSearchParams] = useSearchParams();
    const resultId = searchParams.get("result")

    const filterParams = searchParams.get('filters')
    let initialValForResponseFilter = true
    if(filterParams && filterParams !== undefined &&filterParams.split('activeCollections').length > 1){
        let isRequestVal =  filterParams.split("activeCollections__")[1].split('&')[0]
        if(isRequestVal.length > 0){
            initialValForResponseFilter = (isRequestVal === 'true' || isRequestVal.includes('true'))
        }
    }

    const appliedFilters = [
        {
            key: 'activeCollections',
            value: [initialValForResponseFilter],
            onRemove: () => {}
        }
    ]

    filtersOptions = func.getCollectionFilters(filtersOptions)

    const tagsFilter = filtersOptions.find(filter => filter.key === 'tagsId');
        if (tagsFilter) {
            tagsFilter.choices = Object.keys(tagsCollectionsMap).map((key) => {
                return {
                    label: key,
                    value: key
                }
            });
        }

    const handleSaveJiraAction = (issueId, labels) => {
        let jiraMetaData;
        try {
            jiraMetaData = issuesFunctions.prepareAdditionalIssueFieldsJiraMetaData(projId, issueType);
            // Use labels parameter if provided, otherwise fall back to state
            const labelsToUse = labels !== undefined ? labels : labelsText;
            if (labelsToUse && labelsToUse.trim()) {
                jiraMetaData.labels = labelsToUse.trim();
            }
        } catch (error) {
            setToast(true, true, "Please fill all required fields before creating a Jira ticket.");
            resetResourcesSelected()
            return;
        }

        setToast(true, false, "Please wait while we create your Jira ticket.")
        setJiraModalActive(false)
        api.bulkCreateJiraTickets(selectedIssuesItems, window.location.origin, projId, issueType, jiraMetaData).then((res) => {
            if(res?.errorMessage) {
                setToast(true, false, res?.errorMessage)
            } else {
                setToast(true, false, `${selectedIssuesItems.length} jira ticket${selectedIssuesItems.length === 1 ? "" : "s"} created.`)
            }
            resetResourcesSelected()
        })
    }

    const handleSaveBulkAzureWorkItemsAction = () => {
        let customABWorkItemFieldsPayload = [];
        try {
            customABWorkItemFieldsPayload = issuesFunctions.prepareCustomABWorkItemFieldsPayload(projectId, workItemType);
        } catch (error) {
            setToast(true, true, "Please fill all required fields before creating a Azure boards work item.");
            return;
        }

        setToast(true, false, "Please wait while we create your Azure Boards Work Item.")
        setBoardsModalActive(false)
        api.bulkCreateAzureWorkItems(selectedIssuesItems, projectId, workItemType, window.location.origin, customABWorkItemFieldsPayload).then((res) => {
            if(res?.errorMessage) {
                setToast(true, false, res?.errorMessage)
            } else {
                setToast(true, false, `${selectedIssuesItems.length} Azure Boards Work Item${selectedIssuesItems.length === 1 ? "" : "s"} created.`)
            }
            resetResourcesSelected()
        })
    }

    const handleSaveBulkServiceNowTicketsAction = () => {
        setToast(true, false, "Please wait while we create your ServiceNow tickets.")
        setServiceNowModalActive(false)
        api.bulkCreateServiceNowTickets(selectedIssuesItems, serviceNowTable).then((res) => {
            if(res?.errorMessage) {
                setToast(true, false, res?.errorMessage)
            } else {
                setToast(true, false, `${selectedIssuesItems.length} ServiceNow ticket${selectedIssuesItems.length === 1 ? "" : "s"} created.`)
            }
            resetResourcesSelected()
        })
    }

    // Use keys directly for reasons and compulsorySettings
    const requiresDescription = (reasonKey) => {
        return compulsorySettings[reasonKey] || false;
    };


    const handleIgnoreWithDescription = () => {
        if (pendingIgnoreAction && mandatoryDescription.trim()) {
            // Use the same endpoint as TestRunResultFlyout.jsx for description update
            const updatePromises = pendingIgnoreAction.items.map(item => 
                testingApi.updateIssueDescription(item, mandatoryDescription)
            );
            Promise.allSettled(updatePromises).then(() => {
                performBulkIgnoreAction(pendingIgnoreAction.items, pendingIgnoreAction.reason, mandatoryDescription);
                setCompulsoryDescriptionModal(false);
                setPendingIgnoreAction(null);
                setMandatoryDescription("");
            });
        }
    };

    useEffect(() => {
        if (compulsoryDescriptionModal && pendingIgnoreAction && pendingIgnoreAction.items?.length > 0) {
            setMandatoryDescription("");
            setModalLoading(false);
        }
    }, [compulsoryDescriptionModal, pendingIgnoreAction]);

    const performBulkIgnoreAction = (items, ignoreReason, description = "") => {
        api.bulkUpdateIssueStatus(items, "IGNORED", ignoreReason, { description }).then((res) => {
            setToast(true, false, `Issue${items.length === 1 ? "" : "s"} ignored${description ? " with description" : ""}`);
            if (items.length === 1 && typeof setMandatoryDescription === 'function') {
                setMandatoryDescription(description);
            }
            resetResourcesSelected();
        });
    };

    let promotedBulkActions = (selectedResources) => {
        let items
        if(selectedResources.length > 0 && typeof selectedResources[0][0] === 'string') {
            const flatSelectedResources = selectedResources.flat()
            items = flatSelectedResources.map((item) => JSON.parse(item))
        } else {
            items = selectedResources.map((item) => JSON.parse(item))
        }
        
        function ignoreAction(reasonKey){
            if (requiresDescription(reasonKey)) {
                setPendingIgnoreAction({ items, reason: reasonKey });
                setCompulsoryDescriptionModal(true);
                return;
            }
            performBulkIgnoreAction(items, reasonKey);
        }
        
        function reopenAction(){
            api.bulkUpdateIssueStatus(items, "OPEN", "" ).then((res) => {
                setToast(true, false, `Issue${items.length==1 ? "" : "s"} re-opened`)
                resetResourcesSelected()
            })
        }

        function createJiraTicketBulk () {
            setSelectedIssuesItems(items)
            settingFunctions.fetchJiraIntegration().then((jirIntegration) => {
                if(jirIntegration.projectIdsMap !== null && Object.keys(jirIntegration.projectIdsMap).length > 0){
                    setJiraProjectMap(jirIntegration.projectIdsMap)
                    if(Object.keys(jirIntegration.projectIdsMap).length > 0){
                        setProjId(Object.keys(jirIntegration.projectIdsMap)[0])
                    }
                }else{
                    setProjId(jirIntegration.projId)
                    setIssueType(jirIntegration.issueType)
                }
                setJiraModalActive(true)
            })
        }

        function createAzureBoardWorkItemBulk() {
            setSelectedIssuesItems(items)
            settingFunctions.fetchAzureBoardsIntegration().then((azureBoardsIntegration) => {
                if(azureBoardsIntegration.projectToWorkItemsMap != null && Object.keys(azureBoardsIntegration.projectToWorkItemsMap).length > 0){
                    setProjectToWorkItemsMap(azureBoardsIntegration.projectToWorkItemsMap)
                    if(Object.keys(azureBoardsIntegration.projectToWorkItemsMap).length > 0){
                        setProjectId(Object.keys(azureBoardsIntegration.projectToWorkItemsMap)[0])
                        setWorkItemType(Object.values(azureBoardsIntegration.projectToWorkItemsMap)[0]?.[0])
                    }
                }else{
                    setProjectId(azureBoardsIntegration?.projectId)
                    setWorkItemType(azureBoardsIntegration?.workItemType)
                }
                setBoardsModalActive(true)
            })
        }

        function createServiceNowTicketBulk() {
            setSelectedIssuesItems(items)
            settingFunctions.fetchServiceNowIntegration().then((serviceNowIntegration) => {
                if(serviceNowIntegration.tableNames && serviceNowIntegration.tableNames.length > 0){
                    setServiceNowTables(serviceNowIntegration.tableNames)
                    setServiceNowTable(serviceNowIntegration.tableNames[0])
                }
                setServiceNowModalActive(true)
            })
        }
        
        let issues = [
            {
                content: 'False positive',
                key: 'falsePositive',
                onAction: () => { ignoreAction('falsePositive') }
            },
            {
                content: 'Acceptable fix',
                key: 'acceptableFix',
                onAction: () => { ignoreAction('acceptableFix') }
            },
            {
                content: 'No time to fix',
                key: 'noTimeToFix',
                onAction: () => { ignoreAction('noTimeToFix') }
            },
            {
                content: 'Export selected Issues',
                onAction: () => { openVulnerabilityReport(items, false) }
            },
            {
                content: 'Export selected Issues summary',
                onAction: () => { openVulnerabilityReport(items, true) }
            },
            {
                content: 'Create jira ticket',
                onAction: () => { createJiraTicketBulk() },
                disabled: (window.JIRA_INTEGRATED === 'false')
            },
            {
                content: 'Create azure work item',
                onAction: () => { createAzureBoardWorkItemBulk() },
                disabled: (window.AZURE_BOARDS_INTEGRATED === 'false')
            },
            {
                content: 'Create ServiceNow ticket',
                onAction: () => { createServiceNowTicketBulk() },
                disabled: (window.SERVICENOW_INTEGRATED === 'false')
            }
        ];
        
        let reopen =  [{
            content: 'Reopen',
            onAction: () => { reopenAction() }
        }]
        
        let ret = [];
        let status = selectedTab.toUpperCase()
        
        switch (status) {
            case "OPEN": ret = [].concat(issues); break;
            case "IGNORED": 
                ret = ret.concat(reopen);
                break;
            case "FIXED":
            default:
                ret = []
        }

        return ret;
    }
    
    let store = {}
    let result = []
    let issueName = []
    Object.values(subCategoryMap).forEach((x) => {
        let superCategory = x.superCategory
        if (!store[superCategory.name]) {
            result.push({ "label": superCategory.displayName, "value": superCategory.name })
            store[superCategory.name] = []
        }
        store[superCategory.name].push(x._name);
        issueName.push({"label": x.testName, "value": x._name})
    })
    filtersOptions[2].choices = [].concat(result)
    filtersOptions[3].choices = [].concat(issueName)
    let categoryToSubCategories = store

    function disambiguateLabel(key, value) {
        switch (key) {
            case "startTimestamp":
                return func.convertToDisambiguateLabel(value, func.prettifyEpoch, 2)
            case "issueStatus":
            case "severity":
                return func.convertToDisambiguateLabel(value, func.toSentenceCase, 2)
            case "compliance":
                return func.convertToDisambiguateLabel(value, func.toUpperCase(), 2)
            case "issueName":
            case "issueCategory":
                return func.convertToDisambiguateLabelObj(value, null, 3)
            case "collectionIds":
            case "apiCollectionId":
                return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 2)
            case "tagsId":
                return func.convertToDisambiguateLabelObj(value, null, 2)
            case "activeCollections":
                if(value[0]){
                    return "Active collections only"
                }else{
                    return "All collections"
                }
            default:
              return value;
          }          
    }

    const openVulnerabilityReport = async (items = [], summaryMode = false) => {
        await testingApi.generatePDFReport(issuesFilters, items).then((res) => {
            const responseId = res.split("=")[1];
            const summaryModeQueryParam = summaryMode === true ? 'summaryMode=true' : '';
            const redirectUrl = `/dashboard/issues/summary/${responseId.split("}")[0]}?${summaryModeQueryParam}`;
            window.open(redirectUrl, '_blank');
        })

        resetResourcesSelected();
    }

    const fetchIssuesByApisData = async () => {
        await api.fetchIssuesByApis().then((res) => {
            if (res && res.countByAPIs) {
                setIssuesByApis(res.countByAPIs)
            } else {
                setIssuesByApis({})
            }
        })
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
        setLoading(false)
    }
  }, [subCategoryMap, apiCollectionMap])


    useEffect(() => {
        fetchIssuesByApisData()
        issuesFunctions.fetchIntegrationCustomFieldsMetadata();
    }, [])



    const fetchTableData = async (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) => {
        setTableLoading(true)
        let filterStatus = [selectedTab.toUpperCase()]
        let filterSeverity = filters.severity
        let filterCompliance = filters.compliance
        const activeCollections = (filters?.activeCollections !== undefined && filters?.activeCollections.length > 0) ? filters?.activeCollections[0] : initialValForResponseFilter;
        const apiCollectionId = filters.apiCollectionId || []
        let filterCollectionsId = apiCollectionId.concat(filters.collectionIds)
        let filterSubCategory = []
        filters?.issueCategory?.forEach((issue) => {
            filterSubCategory = filterSubCategory.concat(categoryToSubCategories[issue])
        })
        filterSubCategory = [...filterSubCategory, ...filters?.issueName]
        const selectedTagsCollectionId = filters.tagsId || []
        const tagCollectionIds = selectedTagsCollectionId.map((tag) => tagsCollectionsMap[tag]).flat()
        filterCollectionsId = filterCollectionsId.concat(tagCollectionIds)
        const collectionIdsArray = filterCollectionsId.map((x) => {return x.toString()})

        let obj = {
            'filterStatus': filterStatus,
            'filterCollectionsId': collectionIdsArray,
            'filterSeverity': filterSeverity,
            'filterCompliance': filterCompliance,
            filterSubCategory: filterSubCategory,
            startEpoch: [startTimestamp.toString()],
            endTimeStamp: [endTimestamp.toString()],
            activeCollections: [activeCollections.toString()]
        }
        setIssuesFilters(obj)

        let ret = []
        let total = 0

        let issueItem = []

        await api.fetchIssues(skip, limit, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, sortKey, sortOrder, startTimestamp, endTimestamp, activeCollections, filterCompliance).then((issuesDataRes) => {
            const uniqueIssuesMap = new Map()
            issuesDataRes.issues.forEach(item => {
                const key = `${item?.id?.testSubCategory || ''}|${item?.severity || ''}|${item?.testRunIssueStatus || ''}`
                if (!uniqueIssuesMap.has(key)) {
                    uniqueIssuesMap.set(key, {
                        id: item?.id,
                        issueName: item?.id?.testSubCategory,
                        severity: item?.severity,
                        testRunIssueStatus: item?.testRunIssueStatus,
                        creationTime: item?.creationTime,
                        ignoreReason: item?.ignoreReason,
                        severityType: item?.severity,
                        issueStatus: item?.issueStatus,
                        compliance: item?.compliance || [],
                        testRunName: "Test Run",
                        domains: [
                            (item?.id?.apiInfoKey?.apiCollectionId && hostNameMap[item?.id?.apiInfoKey?.apiCollectionId] !== null)
                                ? hostNameMap[item?.id?.apiInfoKey?.apiCollectionId]
                                : (item?.id?.apiInfoKey?.apiCollectionId ? apiCollectionMap[item?.id?.apiInfoKey?.apiCollectionId] : null)
                        ],
                        urls: [{
                            method: item?.id?.apiInfoKey?.method,
                            url: item?.id?.apiInfoKey?.url,
                            id: item?.id ? JSON.stringify(item.id) : '',
                            issueDescription: item?.description,
                            jiraIssueUrl: item?.jiraIssueUrl || "",
                        }],
                        numberOfEndpoints: 1
                    })
                } else {
                    const existingIssue = uniqueIssuesMap.get(key)
                    const domain = (item?.id?.apiInfoKey?.apiCollectionId && hostNameMap[item?.id?.apiInfoKey?.apiCollectionId] !== null)
                        ? hostNameMap[item?.id?.apiInfoKey?.apiCollectionId]
                        : (item?.id?.apiInfoKey?.apiCollectionId ? apiCollectionMap[item?.id?.apiInfoKey?.apiCollectionId] : null)
                    if (domain && !existingIssue.domains.includes(domain)) {
                        existingIssue.domains.push(domain)
                    }
                    existingIssue.urls.push({
                        method: item?.id?.apiInfoKey?.method,
                        url: item?.id?.apiInfoKey?.url,
                        id: item?.id ? JSON.stringify(item.id) : '',
                        issueDescription: item?.description,
                        jiraIssueUrl: item?.jiraIssueUrl || ""
                    })
                    existingIssue.numberOfEndpoints = (existingIssue.numberOfEndpoints || 1) + 1
                }
            })
            issueItem = Array.from(uniqueIssuesMap.values())

            total = selectedTab.toUpperCase() === 'OPEN' ? issuesDataRes.openIssuesCount : selectedTab.toUpperCase() === 'FIXED' ? issuesDataRes.fixedIssuesCount : issuesDataRes.ignoredIssuesCount
            setIssuesDataCount([issuesDataRes.openIssuesCount, issuesDataRes.fixedIssuesCount, issuesDataRes.ignoredIssuesCount])
        }).catch((e) => {
            func.setToast(true, true, e.message)
            setTableLoading(false)
            setLoading(false)
        })

        const sortedIssueItem = transform.sortIssues(issueItem, sortKey, sortOrder)

        const issueTableData = await transform.convertToIssueTableData(sortedIssueItem, subCategoryMap)
        ret.push(...issueTableData)
        setTableLoading(false)
        setLoading(false)

        return {value: ret, total: total}
    }

    async function modifyDataForCSV(){
        const filters= {}
        const filtersFromPersistStore = PersistStore.getState().filtersMap;
        const currentPageKey = "/dashboard/reports/issues/#" + selectedTab
        let selectedFilters = filtersFromPersistStore[currentPageKey]?.filters || [];
        selectedFilters.forEach((filter) => {
            filters[filter.key] = filter.value
        })

        let filterStatus = [selectedTab.toUpperCase()]
        let filterSeverity = filters?.severity || []
        let filterCompliance = filters?.compliance || []
        const activeCollections = (filters?.activeCollections !== undefined && filters?.activeCollections.length > 0) ? filters?.activeCollections[0] : initialValForResponseFilter;
        const apiCollectionId = filters?.apiCollectionId || []
        let filterCollectionsId = (apiCollectionId || []).concat(filters?.collectionIds || [])
        let filterSubCategory = []
        filters?.issueCategory?.forEach((issue) => {
            filterSubCategory = filterSubCategory.concat(categoryToSubCategories[issue])
        })
        if(filters?.issueName !== undefined && filters?.issueName.length > 0){
            filterSubCategory = filterSubCategory.concat(filters?.issueName)
        }
        let issueItems = []

        await api.fetchIssues(0, 20000, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, "severity", -1, startTimestamp, endTimestamp, activeCollections, filterCompliance).then((issuesDataRes) => {
            issuesDataRes.issues.forEach((item) => {
                if (!item || !item.id || !item.id.testSubCategory || !subCategoryMap[item.id.testSubCategory]) return;
                const issue = {
                    id: item.id,
                    severityVal: func.toSentenceCase(item.severity),
                    complianceVal: Object.keys(subCategoryMap[item.id.testSubCategory]?.compliance?.mapComplianceToListClauses || {}),
                    issueName: item.id.testSubCategory,
                    category: subCategoryMap[item.id.testSubCategory]?.superCategory?.shortName,
                    numberOfEndpoints: 1,
                    creationTime: func.prettifyEpoch(item.creationTime),
                    issueStatus: item.unread && item.unread.toString() === 'false' ? "read" : "unread",
                    domainVal:[(item.id.apiInfoKey && hostNameMap[item.id.apiInfoKey.apiCollectionId] !== null ? hostNameMap[item.id.apiInfoKey.apiCollectionId] : apiCollectionMap[item.id.apiInfoKey.apiCollectionId])],
                    url:`${item.id.apiInfoKey?.method || ""} ${item.id.apiInfoKey?.url || ""}`
                }
                issueItems.push(issue)
            })
        }).catch((e) => {
            func.setToast(true, true, e.message)
        })
        return issueItems;

    }
    


    async function exportCsv() {
        func.setToast(true, false, "CSV export in progress")
        let headerTextToValueMap = {
            ...Object.fromEntries(
                headers
                    .map(x => [x.text, x.textValue ? x.textValue : x.value])
                    .filter(x => x[0]?.length > 0)
            ),
            URL: "url"
        };


        let csv = Object.keys(headerTextToValueMap).join(",") + "\r\n"
        const allIssues = await modifyDataForCSV()
        allIssues.forEach(i => {
            csv += Object.values(headerTextToValueMap)
                .map(h => (Array.isArray(i[h]) ? `"${i[h].join(" ")}"` : (i[h] || "-"))).join(",") + "\r\n";
        })
        let blob = new Blob([csv], {
            type: "application/csvcharset=UTF-8"
        });
        saveAs(blob, ("All issues") + ".csv");
        func.setToast(true, false, <div data-testid="csv_download_message">CSV exported successfully</div>)

    }

    const components = (
        <>
            <SummaryInfo
                key={"issues-summary-graph-details"}
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
            />

            <IssuesGraphsGroup heading="Issues summary">
              {[
                <HorizontalGrid gap={5} columns={2} key="critical-issues-graph-detail">
                  <CriticalUnresolvedApisByAge />
                  <CriticalFindingsGraph startTimestamp={startTimestamp} endTimestamp={endTimestamp} linkText={""} linkUrl={""} />
                </HorizontalGrid>,
                <HorizontalGrid columns={2} gap={4} key="open-issues-graphs">
                  <ApisWithMostOpenIsuuesGraph issuesData={issuesByApis} />
                  <IssuesByCollection collectionsData={issuesByApis} />
                </HorizontalGrid>,
                <AllUnsecuredAPIsOverTimeGraph key="unsecured-over-time" startTimestamp={startTimestamp} endTimestamp={endTimestamp} linkText={""} linkUrl={""} />
              ]}
            </IssuesGraphsGroup>

            <GithubServerTable
                key={key}
                pageLimit={50}
                fetchData={fetchTableData}
                appliedFilters={appliedFilters}
                sortOptions={sortOptions}
                resourceName={resourceName}
                filters={filtersOptions}
                disambiguateLabel={disambiguateLabel}
                headers={headers}
                getStatus={() => { return "warning" }}
                selected={selected}
                onRowClick={() => {}}
                onSelect={handleSelectedTab}
                getFilteredItems={()=>{}}
                mode={IndexFiltersMode.Default}
                headings={headers}
                useNewRow={true}
                condensedHeight={true}
                tableTabs={tableTabs}
                selectable={true}
                promotedBulkActions={promotedBulkActions}
                loading={loading || tableLoading}
                hideQueryField={true}
                isMultipleItemsSelected={true}
            />
        </>
    )

    const [popOverActive, setPopOverActive] = useState(false)
    
    return (
        <>
        <PageWithMultipleCards
            title={
                <HorizontalStack gap={4}>
                    <TitleWithInfo
                        titleText={"Issues"}
                        tooltipContent={"Issues are created when a test from test library has passed validation and thus a potential vulnerability is found."}
                    />
                </HorizontalStack>
            }
            isFirstPage={true}
            components = {loading ? [<SpinnerCentered />] : [
                showEmptyScreen ? 
                <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/alert_hexagon.svg"}
                    headingText={"No issues yet!"}
                    description={"There are currently no issues with your APIs. Haven't run your tests yet? Start testing now to prevent any potential issues."}
                    buttonText={mapLabel("Run test", getDashboardCategory())}
                    infoItems={infoItems}
                    infoTitle={"Once you have issues:"}
                    learnText={"issues"}
                    docsUrl={ISSUES_PAGE_DOCS_URL}
                    bodyComponent={<SelectCollectionComponent />}
                />

            
            : components
            ]}
            primaryAction={<Button primary onClick={() => openVulnerabilityReport([], false)} disabled={showEmptyScreen}>Export results</Button>}
            secondaryActions={
            <HorizontalStack  gap={2}>
                <DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />
                <Popover
                active={popOverActive}
                activator={<Button onClick={() => setPopOverActive((prev)=>!prev)} disabled={showEmptyScreen} disclosure>More Actions</Button>}
                autofocusTarget="first-node"
                onClose={() => setPopOverActive(false)}
                >
                <ActionList
                  actionRole="menuitem"
                  items={[
                    {
                      content: 'Export results as CSV',
                      onAction: exportCsv,
                    },
                    {
                      content: 'Export summary report',
                      onAction: () => openVulnerabilityReport([], true),
                    },
                  ]}
                />
              </Popover>
            </HorizontalStack>}
        />
            {(resultId !== null && resultId.length > 0) ? <TestRunResultPage /> : null}
            <JiraTicketCreationModal
                modalActive={jiraModalActive}
                setModalActive={setJiraModalActive}
                handleSaveAction={handleSaveJiraAction}
                jiraProjectMaps={jiraProjectMaps}
                setProjId={setProjId}
                setIssueType={setIssueType}
                projId={projId}
                issueType={issueType}
                labelsText={labelsText}
                setLabelsText={setLabelsText}
            />

            <JiraTicketCreationModal
                modalActive={boardsModalActive}
                setModalActive={setBoardsModalActive}
                handleSaveAction={handleSaveBulkAzureWorkItemsAction}
                jiraProjectMaps={projectToWorkItemsMap}
                setProjId={setProjectId}
                setIssueType={setWorkItemType}
                projId={projectId}
                issueType={workItemType}
                isAzureModal={true}
            />

            <JiraTicketCreationModal
                modalActive={serviceNowModalActive}
                setModalActive={setServiceNowModalActive}
                handleSaveAction={handleSaveBulkServiceNowTicketsAction}
                jiraProjectMaps={serviceNowTables}
                setProjId={setServiceNowTable}
                setIssueType={() => {}}
                projId={serviceNowTable}
                issueType=""
                isServiceNowModal={true}
            />

            <Modal
                open={compulsoryDescriptionModal}
                onClose={() => setCompulsoryDescriptionModal(false)}
                title="Description Required"
                primaryAction={{
                    content: modalLoading ? 'Loading...' : 'Confirm',
                    onAction: handleIgnoreWithDescription,
                    disabled: mandatoryDescription.trim().length === 0 || modalLoading
                }}
                secondaryActions={[
                    {
                        content: 'Cancel',
                        onAction: () => setCompulsoryDescriptionModal(false)
                    }
                ]}
            >
                <Modal.Section>
                    <VerticalStack gap="4">
                        <Text variant="bodyMd">
                            A description is required for this action based on your account settings. Please provide a reason for marking these issues as "{pendingIgnoreAction?.reason}".
                        </Text>
                        <TextField
                            label="Description"
                            value={mandatoryDescription}
                            onChange={setMandatoryDescription}
                            multiline={4}
                            autoComplete="off"
                            placeholder="Please provide a description for this action..."
                            disabled={modalLoading}
                        />
                    </VerticalStack>
                </Modal.Section>
            </Modal>
        </>
    )
}

export default IssuesPage
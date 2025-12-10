import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useReducer, useState } from "react";
import api from "../api"
import Store from "../../../store";
import func from "@/util/func";
import { MarkFulfilledMinor, ReportMinor, ExternalMinor } from '@shopify/polaris-icons';
import PersistStore from "../../../../main/PersistStore";
import { Button, Popover, Box, Avatar, Text, HorizontalGrid, HorizontalStack, IndexFiltersMode, VerticalStack, ActionList } from "@shopify/polaris";
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
import useTable from "../../../components/tables/TableContext.js";
import values from "@/util/values";
import SpinnerCentered from "../../../components/progress/SpinnerCentered.jsx";
import TableStore from "../../../components/tables/TableStore.js";
import CriticalFindingsGraph from "./CriticalFindingsGraph.jsx";
import settingFunctions from "../../settings/module.js";
import JiraTicketCreationModal from "../../../components/shared/JiraTicketCreationModal.jsx";
import testingApi from "../../testing/api.js"
import issuesFunctions from '@/apps/dashboard/pages/issues/module';
import { isMCPSecurityCategory, isGenAISecurityCategory, isAgenticSecurityCategory, mapLabel, getDashboardCategory  } from "../../../../main/labelHelper";

const sortOptions = [
    { label: 'Severity', value: 'severity asc', directionLabel: 'Highest', sortKey: 'severity', columnIndex: 2 },
    { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest', sortKey: 'severity', columnIndex: 2 },
    { label: 'Number of endpoints', value: 'numberOfEndpoints asc', directionLabel: 'More', sortKey: 'numberOfEndpoints', columnIndex: 5 },
    { label: 'Number of endpoints', value: 'numberOfEndpoints desc', directionLabel: 'Less', sortKey: 'numberOfEndpoints', columnIndex: 5 },
    { label: 'Discovered time', value: 'creationTime asc', directionLabel: 'Newest', sortKey: 'creationTime', columnIndex: 7 },
    { label: 'Discovered time', value: 'creationTime desc', directionLabel: 'Oldest', sortKey: 'creationTime', columnIndex: 7 },
];

const getCompliances = () => {
    const isDemoAccount = func.isDemoAccount();
    const isMCP = isMCPSecurityCategory();
    const isGenAiSecurity = isGenAISecurityCategory();
    const isAgenticSecurity = isAgenticSecurityCategory();

    if (isDemoAccount && (isMCP || isAgenticSecurity || isGenAiSecurity)) {
        // Different compliances for demo account + MCP + Agentic Security
        return ["OWASP Agentic", "OWASP LLM", "NIST AI Risk Management Framework","MITRE ATLAS","CIS Controls", "CMMC", "CSA CCM", "Cybersecurity Maturity Model Certification (CMMC)", "FISMA", "FedRAMP", "GDPR", "HIPAA", "ISO 27001", "NIST 800-171", "NIST 800-53", "PCI DSS", "SOC 2", "OWASP"];
    }
    
    // Default compliances
    return ["CIS Controls", "CMMC", "CSA CCM", "Cybersecurity Maturity Model Certification (CMMC)", "FISMA", "FedRAMP", "GDPR", "HIPAA", "ISO 27001", "NIST 800-171", "NIST 800-53", "PCI DSS", "SOC 2", "OWASP"];
};

const allCompliances = getCompliances();

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
    }
]

const resourceName = {
    singular: 'issue',
    plural: 'issues',
};

function CompliancePage() {
    const [headers, setHeaders] = useState([
        {
          title: '',
          type: CellType.COLLAPSIBLE
        },
        {
            title: "Severity",
            text: "Severity",
            value: "severity",
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
            value: "domains"
        },
        {
            title: "Compliance",
            text: "Compliance",
            value: "compliance",
            sortActive: true
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


    function calcFilteredTestIds(complianceView) {
        let ret = Object.entries(subCategoryMap).filter(([_, v]) => {return !!v.compliance?.mapComplianceToListClauses[complianceView]}).map(([k, _]) => k)
        
        return ret
    }

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
    const [moreActions, setMoreActions] = useState(false);
    const [complianceView, setComplianceView] = useState('SOC 2');
    const [filteredTestIds, setFilteredTestIds] = useState([]);

    const [boardsModalActive, setBoardsModalActive] = useState(false)
    const [projectToWorkItemsMap, setProjectToWorkItemsMap] = useState({})
    const [projectId, setProjectId] = useState('')
    const [workItemType, setWorkItemType] = useState('')

    const [serviceNowModalActive, setServiceNowModalActive] = useState(false)
    const [serviceNowTables, setServiceNowTables] = useState([])
    const [serviceNowTable, setServiceNowTable] = useState('')


    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5])

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    const hostNameMap = PersistStore.getState().hostNameMap

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

    const handleSaveJiraAction = (issueId, labels) => {
        let jiraMetaData;
        try {
            jiraMetaData = issuesFunctions.prepareAdditionalIssueFieldsJiraMetaData(projId, issueType);
            // Use labels parameter if provided
            if (labels !== undefined && labels && labels.trim()) {
                jiraMetaData.labels = labels.trim();
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

    const createServiceNowTicketBulk = (items) => {
        setSelectedIssuesItems(items)
        settingFunctions.fetchServiceNowIntegration().then((serviceNowIntegration) => {
            if(serviceNowIntegration.tableNames && serviceNowIntegration.tableNames.length > 0){
                setServiceNowTables(serviceNowIntegration.tableNames)
                setServiceNowTable(serviceNowIntegration.tableNames[0])
            }
            setServiceNowModalActive(true)
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

    let promotedBulkActions = (selectedResources) => {
        let items
        if(selectedResources.length > 0 && typeof selectedResources[0][0] === 'string') {
            const flatSelectedResources = selectedResources.flat()
            items = flatSelectedResources.map((item) => JSON.parse(item))
        } else {
            items = selectedResources.map((item) => JSON.parse(item))
        }
        
        function ignoreAction(ignoreReason){
            api.bulkUpdateIssueStatus(items, "IGNORED", ignoreReason, {} ).then((res) => {
                setToast(true, false, `Issue${items.length==1 ? "" : "s"} ignored`)
                resetResourcesSelected()
            })
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
            onAction: () => { createJiraTicketBulk() }
        },
        {
            content: 'Create azure work item',
            onAction: () => { createAzureBoardWorkItemBulk() },
            disabled: (window.AZURE_BOARDS_INTEGRATED === 'false')
        },
        {
            content: 'Create ServiceNow ticket',
            onAction: () => { createServiceNowTicketBulk(items) },
            disabled: (window.SERVICENOW_INTEGRATED === 'false')
        }]
        
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
            case "compliance":
                return func.convertToDisambiguateLabel(value, func.toUpperCase(), 2)
            case "issueCategory":
                return func.convertToDisambiguateLabelObj(value, null, 3)
            case "collectionIds":
            case "apiCollectionId":
                return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 2)
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
        issuesFunctions.fetchIntegrationCustomFieldsMetadata();
    }, [])
  

    const onSelectCompliance = (compliance) => {
        setComplianceView(compliance)
        resetResourcesSelected()
        setMoreActions(false)
    }

    const fetchTableData = async (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) => {
        setTableLoading(true)
        let filterStatus = [selectedTab.toUpperCase()]
        let filterSeverity = filters.severity
        let filterCompliance = filters.compliance
        const activeCollections = (filters?.activeCollections !== undefined && filters?.activeCollections.length > 0) ? filters?.activeCollections[0] : initialValForResponseFilter;
        const apiCollectionId = filters.apiCollectionId || []
        let filterCollectionsId = apiCollectionId.concat(filters.collectionIds)
        let filterSubCategory = calcFilteredTestIds(complianceView)
        
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
                const key = `${item?.id?.testSubCategory}|${item?.severity}|${item?.unread.toString()}`
                if (!uniqueIssuesMap.has(key)) {
                    uniqueIssuesMap.set(key, {
                        id: item?.id,
                        severity: func.toSentenceCase(item?.severity),
                        compliance: Object.keys(subCategoryMap[item?.id?.testSubCategory].compliance?.mapComplianceToListClauses || {}),
                        severityType: item?.severity,
                        issueName: item?.id?.testSubCategory,
                        category: item?.id?.testSubCategory,
                        numberOfEndpoints: 1,
                        creationTime: item?.creationTime,
                        issueStatus: item?.unread.toString(),
                        testRunName: "Test Run",
                        domains: [(hostNameMap[item?.id?.apiInfoKey?.apiCollectionId] !== null ? hostNameMap[item?.id?.apiInfoKey?.apiCollectionId] : apiCollectionMap[item?.id?.apiInfoKey?.apiCollectionId])],
                        urls: [{
                            method: item?.id?.apiInfoKey?.method,
                            url: item?.id?.apiInfoKey?.url,
                            id: JSON.stringify(item?.id),
                            jiraIssueUrl: item?.jiraIssueUrl || "",
                        }],
                    })
                } else {
                    const existingIssue = uniqueIssuesMap.get(key)
                    const domain = (hostNameMap[item?.id?.apiInfoKey?.apiCollectionId] !== null ? hostNameMap[item?.id?.apiInfoKey?.apiCollectionId] : apiCollectionMap[item?.id?.apiInfoKey?.apiCollectionId])
                    if (!existingIssue.domains.includes(domain)) {
                        existingIssue.domains.push(domain)
                    }
                    existingIssue.urls.push({
                        method: item?.id?.apiInfoKey?.method,
                        url: item?.id?.apiInfoKey?.url,
                        id: JSON.stringify(item?.id),
                    })
                    existingIssue.numberOfEndpoints += 1
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

        const issueTableData = await transform.convertToIssueTableData(sortedIssueItem, subCategoryMap, true)
        ret.push(...issueTableData)
        setTableLoading(false)
        setLoading(false)

        return {value: ret, total: total}
    }

    const components = (
        <>
            <HorizontalGrid gap={5} columns={2} key={"critical-issues-graph-detail"}>
                <CriticalFindingsGraph startTimestamp={getTimeEpoch("since")} endTimestamp={getTimeEpoch("until")} linkText={""} linkUrl={""}/>
                <CriticalFindingsGraph startTimestamp={getTimeEpoch("since")} endTimestamp={getTimeEpoch("until")} linkText={""} linkUrl={""} complianceMode={complianceView}/>
            </HorizontalGrid>

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
                        titleText={"Compliance Report"}
                        tooltipContent={"View a detailed compliance report mapping detected issues to industry standards such as OWASP API Security Top 10, NIST, PCI-DSS, SOC 2, and more."}
                    />
                    <Popover
                        active={moreActions}
                        activator={(
                            <Button onClick={() => setMoreActions(!moreActions)} disclosure removeUnderline>
                                <Box>
                                    <HorizontalStack gap={2}>
                                        <Avatar source={func.getComplianceIcon(complianceView)} shape="square"  size="extraSmall"/> 
                                        <Text>{complianceView}</Text>
                                    </HorizontalStack>
                                </Box>
                            </Button>
                        )}
                        autofocusTarget="first-node"
                        onClose={() => { setMoreActions(false) }}
                        preferredAlignment="right"
                    >
                        <Popover.Pane fixed>
                            <Popover.Section>
                            <VerticalStack gap={"2"}>
                                {allCompliances.map(compliance => {return <Button textAlign="left" plain onClick={() => {onSelectCompliance(compliance)}} removeUnderline>
                                    <Box>
                                        <HorizontalStack gap={2}>
                                            <Avatar source={func.getComplianceIcon(compliance)} shape="square"  size="extraSmall"/> 
                                            <Text>{compliance}</Text>
                                        </HorizontalStack>
                                    </Box>
                                </Button>} )}
                            </VerticalStack>
                                
                            </Popover.Section>
                        </Popover.Pane>
                    </Popover>
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
            primaryAction={<Button primary onClick={() => openVulnerabilityReport([], false)} disabled={showEmptyScreen}>Export {complianceView} report</Button>}
            secondaryActions={
                <HorizontalStack gap={2}>
                    <DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />
                    <Popover
                        active={popOverActive}
                        activator={<Button onClick={() => setPopOverActive((prev) => !prev)} disabled={showEmptyScreen} disclosure>More Actions</Button>}
                        autofocusTarget="first-node"
                        onClose={() => setPopOverActive(false)}
                    >
                        <ActionList
                            actionRole="menuitem"
                            items={[
                                {
                                    content: 'Export summary report',
                                    onAction: () => openVulnerabilityReport([], true),
                                },
                            ]}
                        />
                    </Popover>
                </HorizontalStack>
            }
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
        </>
    )
}

export default CompliancePage
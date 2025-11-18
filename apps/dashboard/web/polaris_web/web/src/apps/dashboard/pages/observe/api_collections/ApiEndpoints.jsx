import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, HorizontalStack, Button, Popover, Modal, IndexFiltersMode, VerticalStack, Box, Checkbox, ActionList, Icon, Tooltip } from "@shopify/polaris"
import TitleWithInfo from "../../../components/shared/TitleWithInfo"
import api from "../api"
import { useEffect, useState } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import {useLocation, useNavigate, useParams } from "react-router-dom"
import { saveAs } from 'file-saver'
import {FileMinor, HideMinor, ViewMinor, MagicMinor} from '@shopify/polaris-icons';
import "./api_inventory.css"
import ApiDetails from "./ApiDetails"
import UploadFile from "../../../components/shared/UploadFile"
import RunTest from "./RunTest"
import ObserveStore from "../observeStore"
import WorkflowTests from "./WorkflowTests"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout"
import dashboardFunc from "../../transform"
import settingsRequests from "../../settings/api"
import PersistStore from "../../../../main/PersistStore"
import transform from "../transform"
import { CellType } from "../../../components/tables/rows/GithubRow"
import {ApiGroupModal, Operation} from "./ApiGroupModal"
import TooltipText from "../../../components/shared/TooltipText"
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout"
import { ENDPOINTS_PAGE_DOCS_URL } from "../../../../main/onboardingData"
import {TestrunsBannerComponent} from "../../testing/TestRunsPage/TestrunsBannerComponent"
import GetPrettifyEndpoint from "../GetPrettifyEndpoint"
import SourceLocation from "./component/SourceLocation"
import useTable from "../../../components/tables/TableContext"
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip"
import { SelectSource } from "./SelectSource"
import InlineEditableText from "../../../components/shared/InlineEditableText"
import IssuesApi from "../../issues/api"
import SequencesFlow from "./SequencesFlow"
import { CATEGORY_API_SECURITY, getDashboardCategory, isCategory, mapLabel } from "../../../../main/labelHelper"
import AgentDiscoverGraph from "./AgentDiscoverGraph"
import McpToolsGraph from "./McpToolsGraph"

const headings = [
    {
        text: "Endpoint",
        value: "endpointComp",
        title: `${mapLabel("API endpoints", getDashboardCategory())}`,
        textValue: "endpoint",
        sortActive: true
    },
    {
        text: "Risk score",
        title: <HeadingWithTooltip 
            title={"Risk score"}
            content={"Risk score is calculated based on the amount of sensitive information the API shares and its current status regarding security issues."}
        />,
        value: "riskScoreComp",
        textValue: "riskScore",
        sortActive: true,
        filterKey: 'riskScore',
        showFilter:true
    },{
        text:"Issues",
        title: "Issues",
        value: "issuesComp",
        textValue: "issues",
        showFilter:true,
        filterKey:"severity"
    },
    {
        text: "Hostname",
        value: 'hostName',
        title: "Hostname",
        maxWidth: '100px',
        type: CellType.TEXT,
    },
    {
        text: 'Access Type',
        value: 'access_type', 
        title:"Access Type",
        showFilter: true,
        type: CellType.TEXT,
        tooltipContent: "Access type of the API. It can be public, private, partner"
    },
    {
        text: 'Auth Type',
        title: 'Auth type',
        value: 'auth_type',
        showFilter: true,
        textValue: 'authTypeTag',
        tooltipContent: "Authentication type of the API."
    },
    {
        text: 'Sensitive params in response',
        title: 'Sensitive params',
        value: 'sensitiveTagsComp',
        filterKey: 'sensitiveInResp',
        showFilter: true,
        textValue: "sensitiveDataTags"
    },
    {
        text: 'Last Seen',
        title: <HeadingWithTooltip 
                title={"Last Seen"}
                content={"Time when API was last detected in traffic."}
            />,
        value: 'last_seen',
        isText: true,
        type: CellType.TEXT,
        sortActive: true,
        
    },
    {
        text: 'Discovered At',
        title: <HeadingWithTooltip 
                title={"Discovered At"}
                content={"Time when API was first discovered in traffic."}
            />,
        value: 'added',
        isText: true,
        type: CellType.TEXT,
        sortActive: true,
    },
    {
        text: 'Last ' + mapLabel('Tested', getDashboardCategory()),
        title: <HeadingWithTooltip 
                title={"Last " + mapLabel('Tested', getDashboardCategory())}
                content={"Time when API was last tested successfully."}
            />,
        value: 'lastTestedComp',
        sortActive: true,
        sortKey: 'lastTested',
        textValue: 'lastTested',
    },
    {
        text: "Source location",
        value: "sourceLocationComp",
        textValue: "sourceLocation",
        title: "Source location",
        tooltipContent: "Exact location of the URL in case detected from the source code."    
    },
    {
        text: "Collection",
        value: "apiCollectionName",
        title: "Collection",
        showFilter: true,
        filterKey: "apiCollectionName",
    },
    {
        text: "Tags",
        value: "tagsComp",
        textValue: "tagsString",
        title: "Tags",
        showFilter: true,
        filterKey: "tagsString",
    },
    {
        text: "Description",
        value: "descriptionComp",
        textValue: "description",
        title: "Description",
        filterKey: "description",
        tooltipContent: "Description of the API",
    }
]

let headers = JSON.parse(JSON.stringify(headings))
if(!getDashboardCategory().includes("MCP")){
    headers.push({
        text: 'Method',
        filterKey: 'method',
        showFilter: true,
        textValue: 'method',
        sortActive: true
    })
}

headers.push({
    text: 'Sensitive params in request',
    value: 'sensitiveInReq',
    filterKey: 'sensitiveInReq',
    showFilter: true,
})

headers.push({
    text: 'Response codes',
    value: 'responseCodes',
    filterKey: 'responseCodes',
    showFilter: true,
})


const sortOptions = [
    { label: 'Risk Score', value: 'riskScore asc', directionLabel: 'Highest', sortKey: 'riskScore', columnIndex: 2},
    { label: 'Risk Score', value: 'riskScore desc', directionLabel: 'Lowest', sortKey: 'riskScore', columnIndex: 2},
    { label: 'Endpoint', value: 'endpoint asc', directionLabel: 'A-Z', sortKey: 'endpoint', columnIndex: 1 },
    { label: 'Endpoint', value: 'endpoint desc', directionLabel: 'Z-A', sortKey: 'endpoint', columnIndex: 1 },
    { label: 'Last seen', value: 'lastSeenTs asc', directionLabel: 'Newest', sortKey: 'lastSeenTs', columnIndex: 8 },
    { label: 'Last seen', value: 'lastSeenTs desc', directionLabel: 'Oldest', sortKey: 'lastSeenTs', columnIndex: 8 },
    { label: 'Discovered at', value: 'detectedTs asc', directionLabel: 'Newest', sortKey: 'detectedTs', columnIndex: 9 },
    { label: 'Discovered at', value: 'detectedTs desc', directionLabel: 'Oldest', sortKey: 'detectedTs', columnIndex: 9 },
    { label: 'Last tested', value: 'lastTested asc', directionLabel: 'Newest', sortKey: 'lastTested', columnIndex: 10 },
    { label: 'Last tested', value: 'lastTested desc', directionLabel: 'Oldest', sortKey: 'lastTested', columnIndex: 10 },
];



function ApiEndpoints(props) {
    const { endpointListFromConditions, sensitiveParamsForQuery, isQueryPage } = props
    const params = useParams()
    const location = useLocation()
    const apiCollectionId = params.apiCollectionId
    const navigate = useNavigate()

    const showDetails = ObserveStore(state => state.inventoryFlyout)
    const setShowDetails = ObserveStore(state => state.setInventoryFlyout)
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const allCollections = PersistStore(state => state.allCollections);
    const hostNameMap = PersistStore(state => state.hostNameMap);
    const setCollectionsMap = PersistStore(state => state.setCollectionsMap)
    const setAllCollections = PersistStore(state => state.setAllCollections)

    const [ pageTitle, setPageTitle] = useState(collectionsMap[apiCollectionId] !== undefined ? collectionsMap[apiCollectionId] : "")
    const [apiEndpoints, setApiEndpoints] = useState([])
    const [apiInfoList, setApiInfoList] = useState([])
    const [unusedEndpoints, setUnusedEndpoints] = useState([])
    const [showEmptyScreen, setShowEmptyScreen] = useState(false)
    const [runTests, setRunTests ] = useState(false)
    const [showSourceDialog,  setShowSourceDialog] = useState(false)
    const [openAPIfile, setOpenAPIfile] = useState(null)
    const [sourcesBackfilled, setSourcesBackfilled] = useState(false)
    const [collectionIssuesData, setCollectionIssuesData] = useState([])

    const [endpointData, setEndpointData] = useState({"all":[], 'sensitive': [], 'new': [], 'high_risk': [], 'no_auth': [], 'shadow': [], 'zombie': []})
    const [selectedTab, setSelectedTab] = useState("all")
    const [selected, setSelected] = useState(0)
    const [selectedResourcesForPrimaryAction, setSelectedResourcesForPrimaryAction] = useState([])
    const [loading, setLoading] = useState(true)
    const [apiDetail, setApiDetail] = useState({})
    const [exportOpen, setExportOpen] = useState(false)
    const [showSequencesFlow, setShowSequencesFlow] = useState(false)

    const filteredEndpoints = ObserveStore(state => state.filteredItems)
    const setFilteredEndpoints = ObserveStore(state => state.setFilteredItems)
    const coverageInfo = PersistStore(state => state.coverageMap)

    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const [isGptActive, setIsGptActive] = useState(false)
    const [redacted, setIsRedacted] = useState(false)
    const [showRedactModal, setShowRedactModal] = useState(false)
    const [tableLoading, setTableLoading] = useState(false)

    const queryParams = new URLSearchParams(location.search);
    const selectedUrl = queryParams.get('selected_url')
    const selectedMethod = queryParams.get('selected_method')
    const [isEditing, setIsEditing] = useState(false);
    const [editableTitle, setEditableTitle] = useState(pageTitle);
    const [description, setDescription] = useState("");
    const [isEditingDescription, setIsEditingDescription] = useState(false)
    const [editableDescription, setEditableDescription] = useState(description)
    const [currentKey, setCurrentKey] = useState(Date.now()); // to force remount InlineEditableText component
    const hasAccessToDiscoveryAgent = func.checkForFeatureSaas('STATIC_DISCOVERY_AI_AGENTS')


    // the values used here are defined at the server.
    const definedTableTabs = apiCollectionId === 111111999 ? ['All', 'New', 'High risk', 'No auth', 'Shadow'] : ( apiCollectionId === 111111120 ? ['All', 'New', 'Sensitive', 'High risk', 'Shadow'] : ['All', 'New', 'Sensitive', 'High risk', 'No auth', 'Shadow', 'Zombie'] )

    const { tabsInfo } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, endpointData)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)
    async function fetchData() {
        setLoading(true)
        let apiEndpointsInCollection;
        let apiInfoListInCollection;
        let unusedEndpointsInCollection;
        let sensitiveParamsResp;
        let sourceCodeData = {};
        let apiInfoSeverityMap ;
        if (isQueryPage) {
            let apiCollectionData = endpointListFromConditions
            if (Object.keys(endpointListFromConditions).length === 0) {
                apiCollectionData = {
                    data: {
                        endpoints: [],
                        apiInfoList: []
                    }
                }
            }
            setShowEmptyScreen(apiCollectionData.data.endpoints.length === 0)
            setIsRedacted(apiCollectionData.redacted)
            apiEndpointsInCollection = apiCollectionData.data.endpoints.map(x => { return { ...x._id, startTs: x.startTs, changesCount: x.changesCount, shadow: x.shadow ? x.shadow : false } })
            apiInfoListInCollection = apiCollectionData.data.apiInfoList
            unusedEndpointsInCollection = apiCollectionData.unusedEndpoints
            sensitiveParamsResp = sensitiveParamsForQuery
            if (Object.keys(sensitiveParamsForQuery).length === 0) {
                sensitiveParamsResp = {
                    data: {
                        endpoints: [],
                        apiInfoList: []
                    }
                }
            }
        } else {
            let apiPromises = [
                api.fetchApisFromStis(apiCollectionId),
                api.fetchApiInfosForCollection(apiCollectionId),
                api.fetchAPIsFromSourceCode(apiCollectionId),
                api.getSeveritiesCountPerCollection(apiCollectionId),
                IssuesApi.fetchIssues(0, 1000, ["OPEN"], [apiCollectionId], null, null, null, null, null, null, true, null)
            ];
            
            let results = await Promise.allSettled(apiPromises);
            let stisEndpoints =  results[0].status === 'fulfilled' ? results[0].value : {};
            let apiInfosData = results[1].status === 'fulfilled' ? results[1].value : {};
            sourceCodeData = results[2].status === 'fulfilled' ? results[2].value : {};
            apiInfoSeverityMap = results[3].status === 'fulfilled' ? results[3].value : {};
            let issuesDataResp = results[4].status === 'fulfilled' ? results[4].value : {};
            
            // Initialize with empty sensitive params for fast UI loading
            sensitiveParamsResp = { data: { endpoints: [] } };
            setShowEmptyScreen(stisEndpoints?.list !== undefined && stisEndpoints?.list?.length === 0)
            apiEndpointsInCollection = stisEndpoints?.list !== undefined && stisEndpoints.list.map(x => { return { ...x._id, startTs: x.startTs, changesCount: x.changesCount, shadow: x.shadow ? x.shadow : false } })
            apiInfoListInCollection = apiInfosData.apiInfoList
            unusedEndpointsInCollection = stisEndpoints.unusedEndpoints
            setIsRedacted(apiInfosData.redacted)
            setCollectionIssuesData(issuesDataResp?.issues || []);
        }

        let sensitiveParams = sensitiveParamsResp.data.endpoints

        let sensitiveParamsMap = {}
        sensitiveParams.forEach(p => {
            let position = p.responseCode > -1 ? "response" : "request";
            let key = p.method + " " + p.url + " " + p.apiCollectionId
            if (!sensitiveParamsMap[key]) sensitiveParamsMap[key] = new Set()

            if (!p.subType) {
                p.subType = { name: "CUSTOM" }
            }

            sensitiveParamsMap[key].add({ name: p.subType, position: position})
        })

        apiEndpointsInCollection.forEach(apiEndpoint => {
            const key = apiEndpoint.method + " " + apiEndpoint.url + " " + apiEndpoint.apiCollectionId;
            const allSensitive = new Set(), sensitiveInResp = [], sensitiveInReq = [];

            sensitiveParamsMap[key]?.forEach(({ name, position }) => {
                allSensitive.add(name);
                (position === 'response' ? sensitiveInResp : sensitiveInReq).push(name);
            });

            Object.assign(apiEndpoint, {
                sensitive: allSensitive,
                sensitiveInReq,
                sensitiveInResp
            });
        })
        apiInfoSeverityMap = func.getSeverityCountPerEndpointList(apiInfoSeverityMap)


        let data = {}
        let allEndpoints = func.mergeApiInfoAndApiCollection(apiEndpointsInCollection, apiInfoListInCollection, collectionsMap,apiInfoSeverityMap)

        // handle code analysis endpoints
        const codeAnalysisCollectionInfo = sourceCodeData.codeAnalysisCollectionInfo
        const codeAnalysisApisMap = codeAnalysisCollectionInfo?.codeAnalysisApisMap
        let shadowApis = []
        setLoading(false)
        if (codeAnalysisApisMap) {
            // Don't show empty screen if there are codeanalysis endpoints present
            if (codeAnalysisApisMap && Object.keys(codeAnalysisApisMap).length > 0) {
                setShowEmptyScreen(false)
            }
            shadowApis = { ...codeAnalysisApisMap }

            // Find shadow endpoints and map api endpoint location
            allEndpoints.forEach(api => {
                let apiEndpoint = transform.getTruncatedUrl(api.endpoint)
                // ensure apiEndpoint does not end with a slash
                if (apiEndpoint !== "/" && apiEndpoint.endsWith("/")) 
                    apiEndpoint = apiEndpoint.slice(0, -1)

                const apiKey = api.method + " " + apiEndpoint

                if (Object.hasOwn(codeAnalysisApisMap, apiKey)) {
                    const codeAnalysisApi = codeAnalysisApisMap[apiKey]
                    const location = codeAnalysisApi.location
                    api.sourceLocation = codeAnalysisApi.location.filePath
                    api.sourceLocationComp = <SourceLocation location={location} />

                    delete shadowApis[apiKey]
                }
                else {
                    api.source_location = ""
                }
            })

            shadowApis = Object.entries(shadowApis).map(([ codeAnalysisApiKey, codeAnalysisApi ]) => {
                const {id, lastSeenTs, discoveredTs, location,  } = codeAnalysisApi
                const { method, endpoint} = id

                return {
                    id: codeAnalysisApiKey,
                    endpointComp: <GetPrettifyEndpoint method={method} url={endpoint} isNew={false} />,
                    method: method,
                    endpoint: endpoint,
                    apiCollectionId: apiCollectionId,
                    codeAnalysisEndpoint: true,
                    sourceLocation: location.filePath, 
                    sourceLocationComp: <SourceLocation location={location} />,
                    parameterisedEndpoint: method + " " + endpoint,
                    apiCollectionName: collectionsMap[apiCollectionId],
                    last_seen: func.prettifyEpoch(lastSeenTs),
                    added: func.prettifyEpoch(discoveredTs),
                    descriptionComp: (<Box maxWidth="300px"><TooltipText tooltip={codeAnalysisApi.description} text={codeAnalysisApi.description}/></Box>),
                }
            })
        }

        // MEMORY OPTIMIZATION: Limit rendered items to prevent browser crash with large collections
        const INITIAL_RENDER_LIMIT = 50;
        const shouldOptimizeEndpoints = allEndpoints.length > INITIAL_RENDER_LIMIT;

        // enrich with collection tags (env types) for API group view
        const collectionTagsMap = {}
        ;(allCollections || []).forEach((c) => {
            const envType = c?.envType
            const envTypeList = envType?.map(func.formatCollectionType) || []
            collectionTagsMap[c.id] = {
                list: envTypeList,
                comp: getTagsCompactComponent(envTypeList, 2),
                str: envTypeList.join(" ")
            }
        })

        // Step 1: Create lightweight objects for ALL endpoints (for filtering & counting only)
        const allEndpointsLight = allEndpoints.map((obj) => {
            const t = collectionTagsMap[obj.apiCollectionId];
            return {
                ...obj,
                tagsComp: t?.comp || null,
                tagsString: t?.str || "",
                isNew: transform.isNewEndpoint(obj.lastSeenTs),
                open: obj.auth_type === "UNAUTHENTICATED" || obj.auth_type === undefined,
            };
        });

        // Single pass through endpoints for all filtering and checks
        const result = allEndpointsLight.reduce((acc, obj) => {
            // Check for OpenAPI sources
            if (obj.sources) {
                const hasOpenAPISource = obj.sources.hasOwnProperty("OPEN_API");
                if (hasOpenAPISource) {
                    acc.hasOpenAPI = true;
                }
                if (!sourcesBackfilled) {
                    acc.needsBackfill = true;
                }

                // Zombie endpoints: only OPEN_API source
                if (Object.keys(obj.sources).length === 1 && hasOpenAPISource) {
                    acc.zombieEndpoints.push(obj);
                }

                // Undocumented endpoints: has sources but no OPEN_API
                if (!hasOpenAPISource) {
                    acc.undocumentedEndpoints.push(obj);
                }
            }

            // Filter for other tabs
            if (obj.sensitive && obj.sensitive.size > 0) {
                acc.sensitiveEndpoints.push(obj);
            }
            if (obj.riskScore >= 4) {
                acc.highRiskEndpoints.push(obj);
            }
            if (obj.isNew) {
                acc.newEndpoints.push(obj);
            }
            if (obj.open) {
                acc.noAuthEndpoints.push(obj);
            }

            return acc;
        }, {
            hasOpenAPI: false,
            needsBackfill: false,
            sensitiveEndpoints: [],
            highRiskEndpoints: [],
            newEndpoints: [],
            noAuthEndpoints: [],
            zombieEndpoints: [],
            undocumentedEndpoints: []
        });

        // Set state if backfill is needed
        if (result.needsBackfill) {
            setSourcesBackfilled(true);
        }

        const sensitiveEndpoints = result.sensitiveEndpoints;
        const highRiskEndpoints = result.highRiskEndpoints;
        const newEndpoints = result.newEndpoints;
        const noAuthEndpoints = result.noAuthEndpoints;
        const zombieEndpoints = result.zombieEndpoints;
        const undocumentedEndpoints = result.hasOpenAPI ? result.undocumentedEndpoints : [];

        // Step 2: Store accurate counts (from ALL endpoints)
        data['_counts'] = {
            all: allEndpointsLight.length + shadowApis.length,
            sensitive: sensitiveEndpoints.length,
            high_risk: highRiskEndpoints.length,
            new: newEndpoints.length,
            no_auth: noAuthEndpoints.length,
            shadow: shadowApis.length + undocumentedEndpoints.length,
            zombie: zombieEndpoints.length
        };

        // Step 3: Helper function to prettify a page of data with tags applied
        const prettifyPageWithTags = (pageData) => {
            const prettified = transform.prettifyEndpointsData(pageData);

            // Re-apply tags after prettify
            prettified.forEach((obj) => {
                const t = collectionTagsMap[obj.apiCollectionId];
                if (t) {
                    obj.tagsComp = t.comp;
                    obj.tagsString = t.str;
                } else {
                    obj.tagsString = "";
                }
            });

            return prettified;
        };

        if (shouldOptimizeEndpoints) {
            // Large collection: use lazy prettification - pass raw data and prettify on demand per page

            // For 'all' tab: mix raw allEndpointsLight + already prettified shadowApis
            data['all'] = [...allEndpointsLight, ...shadowApis];
            data['all']._prettifyPageData = (pageData) => {
                // Determine which items are shadowApis (already prettified) vs raw data
                return pageData.map((item) => {
                    // Check if this item is a shadowApi by checking for codeAnalysisEndpoint flag
                    if (item.codeAnalysisEndpoint === true) {
                        // Already prettified, return as-is
                        return item;
                    } else {
                        // Raw data, needs prettification
                        return prettifyPageWithTags([item])[0];
                    }
                });
            };

            data['sensitive'] = sensitiveEndpoints;
            data['sensitive']._prettifyPageData = prettifyPageWithTags;

            data['high_risk'] = highRiskEndpoints;
            data['high_risk']._prettifyPageData = prettifyPageWithTags;

            data['new'] = newEndpoints;
            data['new']._prettifyPageData = prettifyPageWithTags;

            data['no_auth'] = noAuthEndpoints;
            data['no_auth']._prettifyPageData = prettifyPageWithTags;

            // For 'shadow' tab: mix shadowApis (prettified) + undocumentedEndpoints (raw)
            data['shadow'] = [...shadowApis, ...undocumentedEndpoints];
            data['shadow']._prettifyPageData = (pageData) => {
                return pageData.map(item =>
                    item.codeAnalysisEndpoint === true ? item : prettifyPageWithTags([item])[0]
                );
            };

            data['zombie'] = zombieEndpoints;
            data['zombie']._prettifyPageData = prettifyPageWithTags;
        } else {
            // Small collection: render all normally
            const prettifyData = transform.prettifyEndpointsData(allEndpointsLight);

            prettifyData.forEach((obj) => {
                const t = collectionTagsMap[obj.apiCollectionId];
                if (t) {
                    obj.tagsComp = t.comp;
                    obj.tagsString = t.str;
                } else {
                    obj.tagsString = "";
                }
            });

            data['all'] = [...prettifyData, ...shadowApis];
            data['sensitive'] = sensitiveEndpoints.filter(x => x.sensitive && x.sensitive.size > 0);
            data['high_risk'] = prettifyData.filter(x => x.riskScore >= 4);
            data['new'] = prettifyData.filter(x => x.isNew);
            data['no_auth'] = prettifyData.filter(x => x.open);
            data['shadow'] = [...shadowApis, ...undocumentedEndpoints];
            data['zombie'] = zombieEndpoints;
        }
        setEndpointData(data)
        setSelectedTab("all")
        setSelected(0)

        setApiEndpoints(apiEndpointsInCollection)
        setApiInfoList(apiInfoListInCollection)
        setUnusedEndpoints(unusedEndpointsInCollection)
        
        // Load sensitive parameters asynchronously and update the data when ready
        if (!isQueryPage) {
            // Use setTimeout to ensure state is set before we try to update it
            setTimeout(() => {
                api.loadSensitiveParameters(apiCollectionId).then((sensitiveResp) => {
                    // Process the sensitive params
                    let sensitiveParams = sensitiveResp.data.endpoints;
                    let sensitiveParamsMap = {};
                    
                    sensitiveParams.forEach(p => {
                        let position = p.responseCode > -1 ? "response" : "request";
                        let key = p.method + " " + p.url + " " + p.apiCollectionId;
                        if (!sensitiveParamsMap[key]) sensitiveParamsMap[key] = new Set();
                        
                        if (!p.subType) {
                            p.subType = { name: "CUSTOM" };
                        }
                        
                        sensitiveParamsMap[key].add({ name: p.subType, position: position});
                    });

                    // Get current state to check if optimization is enabled
                    const currentEndpointData = endpointData;
                    const hasLazyPrettification = currentEndpointData['all']?._prettifyPageData !== undefined;

                    let currentPrettyData = [...data['all']];
                    currentPrettyData.forEach(apiEndpoint => {
                        const key = apiEndpoint.method + " " + apiEndpoint.endpoint + " " + apiEndpoint.apiCollectionId;
                        const allSensitive = new Set(), sensitiveInResp = [], sensitiveInReq = [];

                        sensitiveParamsMap[key]?.forEach(({ name, position }) => {
                            allSensitive.add(name);
                            (position === 'response' ? sensitiveInResp : sensitiveInReq).push(name);
                        });

                        const sensitiveTags = [...func.convertSensitiveTags(allSensitive)]

                        Object.assign(apiEndpoint, {
                            sensitive: allSensitive,
                            sensitiveTags,
                            sensitiveInReq: [...func.convertSensitiveTags(sensitiveInReq)],
                            sensitiveInResp: [...func.convertSensitiveTags(sensitiveInResp)],
                            // Only create prettified component if NOT using lazy prettification
                            sensitiveTagsComp: hasLazyPrettification ? undefined : transform.prettifySubtypes(sensitiveTags)
                        });
                    })

                    // MEMORY OPTIMIZATION: Preserve _actualTotal and _prettifyPageData properties when updating data
                    const preserveArrayProperties = (oldArray, newArray) => {
                        if (oldArray._actualTotal !== undefined) {
                            newArray._actualTotal = oldArray._actualTotal;
                        }
                        if (oldArray._prettifyPageData !== undefined) {
                            newArray._prettifyPageData = oldArray._prettifyPageData;
                        }
                        return newArray;
                    };

                    data['all'] = preserveArrayProperties(data['all'], currentPrettyData);
                    data['sensitive'] = preserveArrayProperties(data['sensitive'], currentPrettyData.filter(x => x.sensitive && x.sensitive.size > 0));
                    data['high_risk'] = preserveArrayProperties(data['high_risk'], currentPrettyData.filter(x=> x.riskScore >= 4));
                    data['new'] = preserveArrayProperties(data['new'], currentPrettyData.filter(x=> x.isNew));
                    data['no_auth'] = preserveArrayProperties(data['no_auth'], currentPrettyData.filter(x => x.open));
                    setEndpointData(data)
                    setCurrentKey(Date.now()) // force remount InlineEditableText component to reset filters
                }).catch((err) => {
                    console.error("Failed to load sensitive parameters:", err);
                });
            }, 100); // Small delay to ensure state is set
        }
    }

    useEffect(() => {
        if (!endpointData || !endpointData["all"] || !selectedUrl || !selectedMethod) return
        let allData = endpointData["all"]

        const selectedApiDetail = allData.filter((x) => {
            return selectedUrl === x.endpoint && selectedMethod === x.method
        })

        if (!selectedApiDetail || selectedApiDetail.length === 0)  return

        setApiDetail(selectedApiDetail[0])
        setShowDetails(true)

    }, [selectedUrl, selectedMethod, endpointData])

    const checkGptActive = async() => {
        await settingsRequests.fetchAktoGptConfig(apiCollectionId).then((resp) => {
            if(resp.currentState[0].state === "ENABLED"){
                setIsGptActive(true)
            }
        })
    }

    useEffect(() => {
        if (!isQueryPage) {
            checkGptActive()
        }
        fetchData()
    }, [apiCollectionId, endpointListFromConditions])

    useEffect(() => {
        if (pageTitle !== collectionsMap[apiCollectionId]) { 
            setPageTitle(collectionsMap[apiCollectionId])
        }

        setDescription(collectionsObj?.description || "")
        setEditableDescription(collectionsObj?.description || "")
    }, [collectionsMap[apiCollectionId]])

    const resourceName = {
        singular: mapLabel('endpoint', getDashboardCategory()),
        plural: mapLabel('endpoints', getDashboardCategory()),
    };

    const getFilteredItems = (filteredItems) => {
        setFilteredEndpoints(filteredItems)
    }

    function handleRowClick(data) {
        
        let tmp = { ...data, endpointComp: "", sensitiveTagsComp: "" }
        
        const sameRow = func.deepComparison(apiDetail, tmp);
        if (!sameRow) {
            setShowDetails(true)
        } else {
            setShowDetails(!showDetails)
        }
        setApiDetail((prev) => {
            if (sameRow) {
                return prev;
            }
            return { ...tmp }
        })
    }

    

    function handleRefresh() {
        fetchData()
        func.setToast(true, false, "Endpoints refreshed")
    }

    function computeApiGroup() {
        api.computeCustomCollections(pageTitle);
        func.setToast(true, false, "API group is being computed.")
    }

    function redactCheckBoxClicked(){
        if(!redacted){
            setShowRedactModal(true)
        } else {
            setIsRedacted(false)
            redactCollection();
        }
    }

    function redactCollection(){
        setShowRedactModal(false)
        var updatedRedacted = !redacted;
        api.redactCollection(apiCollectionId, updatedRedacted).then(resp => {
            setIsRedacted(updatedRedacted)
            func.setToast(true, false, updatedRedacted ? "Collection redacted" : "Collection unredacted")
        })
    }

    function deleteApisAction() {
        setShowDeleteApiModal(false)
        var apiObjects = apis.map((x) => {
            let tmp = x.split("###");
            return {
                method: tmp[0],
                url: tmp[1],
                apiCollectionId: parseInt(tmp[2])
            }
        }) 
    
    
        api.deleteApis(apiObjects).then(resp => {
            func.setToast(true, false, "APIs deleted successfully. Refresh to see the changes.")
        }).catch (err => {
            func.setToast(true, true, "There was some error deleting the APIs. Please contact support@akto.io for assistance")
        })
    }

    async function exportOpenApi() {
        let lastFetchedUrl = null;
        let lastFetchedMethod = null;
        for (let index = 0; index < 10; index++) {
            let result = await api.downloadOpenApiFile(apiCollectionId, lastFetchedUrl, lastFetchedMethod)
            let openApiString = result["openAPIString"]
            let blob = new Blob([openApiString], {
                type: "application/json",
            });
            const fileName = "open_api_" + collectionsMap[apiCollectionId] + ".json";
            saveAs(blob, fileName);

            lastFetchedUrl = result["lastFetchedUrl"]
            lastFetchedMethod = result["lastFetchedMethod"]

            if (!lastFetchedUrl || !lastFetchedMethod) break;
        }
        func.setToast(true, false, <div data-testid="openapi_spec_download_message">OpenAPI spec downloaded successfully</div>)
    }

    async function exportOpenApiForSelectedApi() {
        const apiInfoKeyList = selectedResourcesForPrimaryAction.map(str => {
            const parts = str.split('###')

            const method = parts[0]
            const url = parts[1]
            const apiCollectionId = parseInt(parts[2], 10)

            return {
                apiCollectionId: apiCollectionId,
                method: method,
                url: url
            }
        })
        let result = await api.downloadOpenApiFileForSelectedApis(apiInfoKeyList, apiCollectionId)
        let openApiString = result["openAPIString"]
        let blob = new Blob([openApiString], {
            type: "application/json",
        });
        const fileName = "open_api_" + collectionsMap[apiCollectionId] + ".json";
        saveAs(blob, fileName);

        func.setToast(true, false, <div data-testid="openapi_spec_download_message">OpenAPI spec downloaded successfully</div>)
    }

    function deleteApis(selectedResources){

            setActionOperation(Operation.REMOVE)
            setApis(selectedResources)
            setShowDeleteApiModal(true);
    }

    function exportCsv(selectedResources = []) {
        const selectedResourcesSet = new Set(selectedResources)
        if (!loading) {
            let headerTextToValueMap = Object.fromEntries(headers.map(x => [x.text, x.type === CellType.TEXT ? x.value : x.textValue]).filter(x => x[0].length > 0));

            let csv = Object.keys(headerTextToValueMap).join(",") + "\r\n"
            const allEndpoints = endpointData['all']
            allEndpoints.forEach(i => {
                if(selectedResources.length === 0 || selectedResourcesSet.has(i.id)){
                    csv += Object.values(headerTextToValueMap).map(h => (i[h] || "-")).join(",") + "\r\n"
                }
            })
            let blob = new Blob([csv], {
                type: "application/csvcharset=UTF-8"
            });
            saveAs(blob, ("All endpoints") + ".csv");
            func.setToast(true, false, <div data-testid="csv_download_message">CSV exported successfully</div>)
        }
    }

    async function exportPostman() {
        let result;
        if(selectedResourcesForPrimaryAction && selectedResourcesForPrimaryAction.length > 0) {
            const apiInfoKeyList = selectedResourcesForPrimaryAction.map(str => {
                const parts = str.split('###')

                const method = parts[0]
                const url = parts[1]
                const apiCollectionId = parseInt(parts[2], 10)

                return {
                    apiCollectionId: apiCollectionId,
                    method: method,
                    url: url
                }
            })

            result = await api.exportToPostmanForSelectedApis(apiInfoKeyList, apiCollectionId)
        } else {
            result = await api.exportToPostman(apiCollectionId)
        }

        if (result)
        func.setToast(true, false, "We have initiated export to Postman, checkout API section on your Postman app in sometime.")
    }

    const [showWorkflowTests, setShowWorkflowTests] = useState(false)

    function toggleWorkflowTests() {
        setShowWorkflowTests(!showWorkflowTests)
    }

    function disambiguateLabel(key, value) {
        if(key.includes("dateRange")){
            return new Date(Date.parse(value.since)).toDateString() + " - " + new Date(Date.parse(value.until)).toDateString();
        }
        switch (key) {
            case "parameterisedEndpoint":
                return func.convertToDisambiguateLabelObj(value, null, 1)
            case "method":
                return func.convertToDisambiguateLabelObj(value, null, 3)
            default:
              return func.convertToDisambiguateLabelObj(value, null, 2);
          }          
    }

    function uploadOpenApiFile(file, isAiAgent = false) {
        setOpenAPIfile(file)
        if(isAiAgent){
            uploadOpenFileWithSource("OPEN_API", file, isAiAgent)
        }
        else if (!isApiGroup && !(collectionsObj?.hostName && collectionsObj?.hostName?.length > 0) && !sourcesBackfilled) {
            setShowSourceDialog(true)
        } else {
            uploadOpenFileWithSource(null, file)
        }
    }

    function uploadOpenFileWithSource(source, file, isAiAgent = false) {
        const reader = new FileReader();
        if (!file) {
            file = openAPIfile
        }
        reader.readAsText(file)

        reader.onload = async () => {
            const formData = new FormData();
            formData.append("openAPIString", reader.result)
            formData.append("apiCollectionId", apiCollectionId);
            formData.append("triggeredWithAIAgent", isAiAgent);
            if (source) {
                formData.append("source", source)
            }
            func.setToast(true, false, "We are uploading your openapi file, please don't refresh the page!")

            api.uploadOpenApiFile(formData).then(resp => {
                if (file.size > 2097152) {
                    func.setToast(true, false, "We have successfully read your file")
                }
                else {
                    func.setToast(true, false, "Your Openapi file has been successfully processed")
                }
                if(isAiAgent) {
                    window.open("/dashboard/observe/" + apiCollectionId + "/open-api-upload", "_blank")
                } else {
                    fetchData()
                }
            }).catch(err => {
                console.log(err);
                if (err.message.includes(404)) {
                    func.setToast(true, true, "Please limit the file size to less than 50 MB")
                } else {
                    let message = err?.response?.data?.actionErrors?.[0] || "Something went wrong while processing the file"
                    func.setToast(true, true, message)
                }
            })
        }
    }
    
    const showOnlyUnique = () => {
        const endpoints = endpointData["all"]
        // unique endpoint is who has method same and url same
        // here endpoint can duplicated as in some cases the url doesn't start with /
        // so matching with removing first letter if slash is first letter
        const uniqueEndpoints = endpoints.filter((endpoint, index, self) =>
            index === self.findIndex((t) => t.method === endpoint.method && t.endpoint.replace(/^\//, '') === endpoint.endpoint.replace(/^\//, ''))
        )

        // MEMORY OPTIMIZATION: Preserve _actualTotal properties
        const preserveActualTotal = (oldArray, newArray) => {
            if (oldArray?._actualTotal !== undefined) {
                newArray._actualTotal = oldArray._actualTotal;
            }
            return newArray;
        };

        const data = {}
        data['all'] = preserveActualTotal(endpointData['all'], uniqueEndpoints);
        data['sensitive'] = preserveActualTotal(endpointData['sensitive'], uniqueEndpoints.filter(x => x.sensitive && x.sensitive.size > 0));
        data['high_risk'] = preserveActualTotal(endpointData['high_risk'], uniqueEndpoints.filter(x=> x.riskScore >= 4));
        data['new'] = preserveActualTotal(endpointData['new'], uniqueEndpoints.filter(x=> x.isNew));
        data['no_auth'] = preserveActualTotal(endpointData['no_auth'], uniqueEndpoints.filter(x => x.open));
        setEndpointData(data)
    }

    function handleFileChange(file) {
        if (file) {
            const reader = new FileReader();

            let isHar = file.name.endsWith(".har")
            if (isHar && file.size >= 52428800) {
                func.setToast(true, true, "Please limit the file size to less than 50 MB")
                return
            }
            let isJson = file.name.endsWith(".json")
            let isYaml = file.name.endsWith(".yaml") || file.name.endsWith(".yml")
            let isPcap = file.name.endsWith(".pcap")
            if (isHar || isJson || isYaml) {
                reader.readAsText(file)
            } else if (isPcap) {
                reader.readAsArrayBuffer(new Blob([file]))
            }
            reader.onload = async () => {
                let skipKafka = false;//window.location.href.indexOf("http://localhost") != -1
                if (isHar) {
                    const formData = new FormData();
                    formData.append("harString", reader.result)
                    formData.append("hsFile", reader.result)
                    formData.append("skipKafka", skipKafka)
                    formData.append("apiCollectionId", apiCollectionId);
                    func.setToast(true, false, "We are uploading your har file, please don't refresh the page!")

                    api.uploadHarFile(formData).then(resp => {
                        if (file.size > 2097152) {
                            func.setToast(true, false, "We have successfully read your file")
                        }
                        else {
                            func.setToast(true, false, "Your Har file has been successfully processed")
                        }
                        fetchData()
                    }).catch(err => {
                        if (err.message.includes(404)) {
                            func.setToast(true, true, "Please limit the file size to less than 50 MB")
                        } else {
                            let message = err?.response?.data?.actionErrors?.[0] || "Something went wrong while processing the file"
                            func.setToast(true, true, message)
                        }
                    })

                } else if (isPcap) {
                    var arrayBuffer = reader.result
                    var bytes = new Uint8Array(arrayBuffer);

                    await api.uploadTcpFile([...bytes], apiCollectionId, skipKafka)
                }
            }
        }
    }

    function displayGPT(){
        setIsGptScreenActive(true)
        let requestObj = {key: "COLLECTION",filteredItems: filteredEndpoints,apiCollectionId: Number(apiCollectionId)}
        const activePrompts = dashboardFunc.getPrompts(requestObj)
        setPrompts(activePrompts)
        
    }

    function getTagsCompactComponent(envTypeList) {
        const list = envTypeList || []
        // Use shared badge renderer to show 1 tag and a +N badge with tooltip inline
        return transform.getCollectionTypeList(list, 1, false)
    }

    function getCollectionTypeListComp(collectionsObj) {
        const envType = collectionsObj?.envType
        const envTypeList = envType?.map(func.formatCollectionType) || []

        return getTagsCompactComponent(envTypeList)
    }

    const collectionsObj = (allCollections && allCollections.length > 0) ? allCollections.filter(x => Number(x.id) === Number(apiCollectionId))[0] : {}
    const isApiGroup = collectionsObj?.type === 'API_GROUP'
    const isHostnameCollection = hostNameMap[collectionsObj?.id] !== null && hostNameMap[collectionsObj?.id] !== undefined 
    const collectionTypeListComp = getCollectionTypeListComp(collectionsObj)
    
    const secondaryActionsComponent = (
        <HorizontalStack gap="2">

            <Popover
                active={exportOpen}
                activator={(
                    <Button onClick={() => setExportOpen(true)} disclosure>
                        <div data-testid="more_actions_button">More Actions</div>
                    </Button>
                )}
                autofocusTarget="first-node"
                onClose={() => { setExportOpen(false) }}
                preferredAlignment="right"
            >
                <div className="inventory-list">
                <ActionList
                    sections={[
                        {
                            title: 'Switch view',
                            items: [
                                {
                                    content: showSequencesFlow ? "Display table view" : "Display graph view",
                                    onAction: () => { 
                                        setShowSequencesFlow(!showSequencesFlow); 
                                        setExportOpen(false); 
                                    },
                                    prefix: <Box width="24px"> <Icon source={showSequencesFlow ? HideMinor: ViewMinor} /></Box>
                                },
                               
                            ]
                        },
                        ...(showSequencesFlow ? [] : [
                            {
                                title:'Re-Compute',
                                items: [
                                    {
                                        content: 'Refresh',
                                        onAction: () => { handleRefresh(); setExportOpen(false) },
                                    },
                                    isApiGroup ? {
                                        content: 'Re-compute API Group',
                                        onAction: () => { computeApiGroup(); setExportOpen(false) },
                                    }: {}
                                ]
                            },
                            {
                                title: 'Upload',
                                items: [
                                    !isApiGroup &&{
                                        content: '',
                                        prefix: (<Box width="160px" >
                                                    <UploadFile
                                                        fileFormat=".json,.yaml,.yml"
                                                        fileChanged={file => {uploadOpenApiFile(file); setExportOpen(false)}}
                                                        tooltipText="Upload openapi file"
                                                        label={(
                                                            <div style={{ display: "flex", gap:'6px' }}>
                                                                <Box>
                                                                    <Icon source={FileMinor} />
                                                                </Box>
                                                                <Text>Upload OpenAPI file</Text>
                                                            </div>
                                                        )}
                                                        primary={false} 
                                                    />
                                                </Box>)
                                    },
                                    !isApiGroup && !(isHostnameCollection)  && {
                                        content: '',
                                        prefix:  (<Box width="160px" >
                                            <UploadFile
                                                fileFormat=".har"
                                                fileChanged={file => {handleFileChange(file); setExportOpen(false)}}
                                                tooltipText="Upload traffic(.har)"
                                                label={(
                                                    <div style={{ display: "flex", gap:'6px' }}>
                                                        <Box>
                                                            <Icon source={FileMinor} />
                                                        </Box>
                                                        <Text>Upload har file</Text>
                                                    </div>
                                                )}
                                                primary={false} 
                                            />
                                        </Box>)
                                    },
                                    !isApiGroup && (!isHostnameCollection && hasAccessToDiscoveryAgent) && {
                                        content: '',
                                        prefix: (<Box width="160px" >
                                            <UploadFile
                                                fileFormat=".json"
                                                fileChanged={file => {uploadOpenApiFile(file, true); setExportOpen(false)}}
                                                tooltipText="Test using AI Agent"
                                                label={(
                                                    <div style={{ display: "flex", gap:'6px' }}>
                                                        <Box>
                                                            <Icon source={MagicMinor} />
                                                        </Box>
                                                        <Text>Upload using AI</Text>
                                                    </div>
                                                )}
                                                primary={false} 
                                            />
                                        </Box>)
                                    }
                                ]
                            },
                            {
                                title: 'Export as',
                                items: [
                                    {
                                        content: 'OpenAPI spec',
                                        onAction: () => { (selectedResourcesForPrimaryAction && selectedResourcesForPrimaryAction.length > 0) ? exportOpenApiForSelectedApi() : exportOpenApi()},
                                    },
                                    {
                                        content: 'Postman',
                                        onAction: () => { exportPostman(); setExportOpen(false) },
                                    },
                                    {
                                        content: 'CSV',
                                        onAction: () => { exportCsv(); setExportOpen(false) },
                                    }
                                ]
                            },
                            {
                                title: 'Others',
                                items: [
                                    {
                                        content: `${showWorkflowTests ? "Hide" : "Show"} workflow tests`,
                                        onAction: () => { toggleWorkflowTests(); setExportOpen(false) },
                                    },
                                    {
                                        content: '',
                                        prefix: <Box paddingInlineStart={"2"}><Checkbox
                                                    label='Redact'
                                                    checked={redacted}
                                                    onChange={() => redactCheckBoxClicked()}
                                                /></Box>,
                                        onAction: () => { redactCheckBoxClicked() },
                                    },
                                    window?.USER_NAME?.includes("@akto.io") && {
                                        content: 'Show only unique endpoints',
                                        onAction: () => { showOnlyUnique() },
                                    }
                                ]
                            }
                        ])
                    ]}
                />
                </div>
            </Popover>

            {isApiGroup &&collectionsObj?.automated !== true ? <Button onClick={() => navigate("/dashboard/observe/query_mode?collectionId=" + apiCollectionId)}>Edit conditions</Button> : null}

            {isGptActive ? <Button onClick={displayGPT} disabled={showEmptyScreen}>Ask AktoGPT</Button>: null}
                    
            <RunTest
                apiCollectionId={apiCollectionId}
                endpoints={filteredEndpoints}
                filtered={loading ? false : filteredEndpoints.length !== endpointData["all"].length}
                runTestFromOutside={runTests}
                closeRunTest={() => setRunTests(false)}
                disabled={showEmptyScreen || window.USER_ROLE === "GUEST" || (collectionsObj?.isOutOfTestingScope || false)}
                selectedResourcesForPrimaryAction={selectedResourcesForPrimaryAction}
                preActivator={false}
            />
            <SelectSource
                show={showSourceDialog}
                setShow={(val) => setShowSourceDialog(val)}
                primaryAction={(val) => uploadOpenFileWithSource(val)}
            />
        </HorizontalStack>
    )

    const handleSelectedTab = (selectedIndex) => {
        setTableLoading(true)
        setSelected(selectedIndex)
        setTimeout(()=>{
            setTableLoading(false)
        },200)
    }
    
    const [showDeleteApiModal, setShowDeleteApiModal] = useState(false)
    const [showApiGroupModal, setShowApiGroupModal] = useState(false)
    const [apis, setApis] = useState([])
    const [actionOperation, setActionOperation] = useState(Operation.ADD)

    function handleApiGroupAction(selectedResources, operation){

        setActionOperation(operation)
        setApis(selectedResources)
        setShowApiGroupModal(true);
    }

    function toggleApiGroupModal(){
        setShowApiGroupModal(false);
    }

    const promotedBulkActions = (selectedResources) => {

        let ret = [
            {
                content: 'Export as CSV',
                onAction: () => exportCsv(selectedResources)
            }
        ]
        if (isApiGroup) {
            ret.push(
                {
                    content: 'Remove from ' + mapLabel('API', getDashboardCategory()) + ' group',
                    onAction: () => handleApiGroupAction(selectedResources, Operation.REMOVE)
                }
            )
        } else {
            ret.push({
                content: <div data-testid="add_to_api_group_button">{'Add to ' + mapLabel('API', getDashboardCategory()) + ' group'}</div>,
                onAction: () => handleApiGroupAction(selectedResources, Operation.ADD)
            })
        }

        if (window.USER_NAME && window.USER_NAME.endsWith("@akto.io")) {
            ret.push({
                content: 'Delete ' + mapLabel('APIs', getDashboardCategory()),
                onAction: () => deleteApis(selectedResources)
            })
        }

        return ret;
    }

    let modal = (
        <Modal
            open={showRedactModal}
            onClose={() => setShowRedactModal(false)}
            title="Note!"
            primaryAction={{
                content: 'Enable',
                onAction: redactCollection
            }}
            key="redact-modal-1"
        >
            <Modal.Section>
                <Text>When enabled, existing sample payload values for this collection will be deleted, and data in all the future payloads for this collection will be redacted. Please note that your API Inventory, Sensitive data etc. will be intact. We will simply be deleting the sample payload values.</Text>
            </Modal.Section>
        </Modal>
    )

    let deleteApiModal = (
        <Modal
            open={showDeleteApiModal}
            onClose={() => setShowApiGroupModal(false)}
            title="Confirm"
            primaryAction={{
                content: 'Yes',
                onAction: deleteApisAction
            }}
            key="redact-modal-1"
        >
            <Modal.Section>
                <Text>Are you sure you want to delete {(apis || []).length} API(s)?</Text>
            </Modal.Section>
        </Modal>
    )

    const canShowTags = () => {
        return isApiGroup || isQueryPage;
    }

    const apiEndpointTable = [<GithubSimpleTable
        key={currentKey}
        pageLimit={50}
        data={endpointData[selectedTab]}
        prettifyPageData={endpointData[selectedTab]?._prettifyPageData}
        sortOptions={sortOptions}
        resourceName={resourceName}
        filters={[]}
        disambiguateLabel={disambiguateLabel}
        headers={headers.filter(x => {
            if (!canShowTags() && (x.text === 'Collection' || x.text === 'Tags')) {
                return false;
            }
            return true;
        })}
        getStatus={() => { return "warning" }}
        selected={selected}
        onRowClick={handleRowClick}
        onSelect={handleSelectedTab}
        getFilteredItems={getFilteredItems}
        mode={IndexFiltersMode.Default}
        headings={headings.filter(x => {
            if (!canShowTags() && (x.text === 'Collection' || x.text === 'Tags')) {
                return false;
            }
            return true;
        })}
        useNewRow={true}
        condensedHeight={true}
        tableTabs={tableTabs}
        selectable={true}
        promotedBulkActions={promotedBulkActions}
        loading={tableLoading || loading}
        setSelectedResourcesForPrimaryAction={setSelectedResourcesForPrimaryAction}
        calendarFilterKeys={{
            "lastTested": "Last Tested",
            "lastSeenTs": "Last Seen",
            "detectedTs": "Discovered timestamp",
        }}
    />,
    <ApiDetails
        key="api-details"
        showDetails={showDetails && apiDetail && Object.keys(apiDetail).length > 0}
        setShowDetails={setShowDetails}
        apiDetail={apiDetail}
        headers={transform.getDetailsHeaders()}
        isGptActive={isGptActive}
        collectionIssuesData={collectionIssuesData}
    />,
    ]


    const components = [
        loading ? [<SpinnerCentered key="loading" />] : (
            showWorkflowTests ? [
                <WorkflowTests
                    key={"workflow-tests"}
                    apiCollectionId={apiCollectionId}
                    endpointsList={loading ? [] : endpointData["all"]}
                />
            ] : showEmptyScreen ? [
                <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/file_plus.svg"}
                    headingText={"Discover APIs to get started"}
                    description={"Your API collection is currently empty. Import APIs from other collections now."}
                    buttonText={"Import from other collections"}
                    redirectUrl={"/dashboard/observe/inventory"}
                    learnText={"inventory"}
                    docsUrl={ENDPOINTS_PAGE_DOCS_URL}
                />] : showSequencesFlow ? [
                <SequencesFlow key="sequences-flow" apiCollectionId={apiCollectionId}  />
            ] : [
                func.isDemoAccount() ? <AgentDiscoverGraph key="agent-discover-graph" apiCollectionId={apiCollectionId} /> : <></>,
                (!isCategory(CATEGORY_API_SECURITY)) && <McpToolsGraph key="mcp-tools-graph" apiCollectionId={apiCollectionId} />,
                (coverageInfo[apiCollectionId] === 0 || !(coverageInfo.hasOwnProperty(apiCollectionId)) ? <TestrunsBannerComponent key={"testrunsBanner"} onButtonClick={() => setRunTests(true)} isInventory={true}  disabled={collectionsObj?.isOutOfTestingScope || false}/> : null),
                <div className="apiEndpointsTable" key="table">
                    {apiEndpointTable}
                      <Modal large open={isGptScreenActive} onClose={() => setIsGptScreenActive(false)} title="Akto GPT">
                          <Modal.Section flush>
                              <AktoGptLayout prompts={prompts} closeModal={() => setIsGptScreenActive(false)} />
                          </Modal.Section>
                      </Modal>
                  </div>,
                  <ApiGroupModal
                      key="api-group-modal"
                      showApiGroupModal={showApiGroupModal}
                      toggleApiGroupModal={toggleApiGroupModal}
                      apis={apis}
                      operation={actionOperation}
                      currentApiGroupName={pageTitle}
                      fetchData={fetchData}
                  />,
                  modal,
                  deleteApiModal
            ]
        )
      ]

    function updateCollectionName(list, apiCollectionId, newName) {
        list.forEach(item => {
            if (item.id === apiCollectionId) {
                item.displayName = newName;
                item.name = newName;
            }
        });
    }

    function updateCollectionDescription(list, apiCollectionId, newDescription) {
        list.forEach(item => {
            if (item.id === apiCollectionId) {
                item.description = newDescription;
            }
        });
    }

    
      const handleSaveClick = async () => {
        if(editableTitle === pageTitle) {
            setIsEditing(false);
            return;
        }
        api.editCollectionName(apiCollectionId, editableTitle).then((resp) => {
            func.setToast(true, false, 'Collection name updated successfully!')
            setPageTitle(editableTitle)
            collectionsMap[apiCollectionId] = editableTitle
            setCollectionsMap(collectionsMap)
            updateCollectionName(allCollections, parseInt(apiCollectionId, 10), editableTitle)
            setAllCollections(allCollections)
            setIsEditing(false);
        }).catch((err) => {
            setEditableTitle(pageTitle)
            setIsEditing(false);
        })
        
      };
    
      const handleTitleChange = (value) => {
        setEditableTitle(value);
      };

    const handleSaveDescription = () => {
        // Check for special characters
        const specialChars = /[!@#$%^&*()\-_=+\[\]{}\\|;:'",.<>/?~]/;
        if (specialChars.test(editableDescription)) {
            func.setToast(true, true, "Description contains special characters that are not allowed.");
            return;
        }
        
        setIsEditingDescription(false);
        if(editableDescription === description) return;
        api.saveCollectionDescription(apiCollectionId, editableDescription)
            .then(() => {
                updateCollectionDescription(allCollections, parseInt(apiCollectionId, 10), editableDescription);
                setAllCollections(allCollections);
                func.setToast(true, false, "Description saved successfully");
                setDescription(editableDescription);
            })
            .catch((err) => {
                console.error("Failed to save description:", err);
                func.setToast(true, true, "Failed to save description. Please try again.");
            });
    };

    return (
        <div>
            {isQueryPage ? (
                apiEndpointTable
            ) : (
                <PageWithMultipleCards
                        title={(
                            <Box maxWidth="35vw">
                                <VerticalStack gap={2}>
                                    <HorizontalStack gap={2}>
                                        <>
                                            {isEditing ? (
                                                <InlineEditableText textValue={editableTitle} setTextValue={handleTitleChange} handleSaveClick={handleSaveClick} setIsEditing={setIsEditing} maxLength={24} />
                                            ) :
                                                <div style={{ cursor: isApiGroup ? 'pointer' : 'default' }} onClick={isApiGroup ? () => { setIsEditing(true); } : undefined}>
                                                    <TitleWithInfo
                                                        titleComp={<TooltipText tooltip={pageTitle} text={pageTitle} textProps={{ variant: 'headingLg' }} />}
                                                        tooltipContent={isApiGroup ? "This API group is computed periodically" : null}
                                                    />
                                                </div>
                                            }
                                        </>
                                        <>
                                            {collectionTypeListComp}
                                        </>
                                    </HorizontalStack>
                                    <HorizontalStack gap={2}>
                                        {isEditingDescription ? (
                                            <InlineEditableText 
                                                textValue={editableDescription} 
                                                setTextValue={setEditableDescription} 
                                                handleSaveClick={handleSaveDescription} 
                                                setIsEditing={setIsEditingDescription} 
                                                placeholder={"Add a brief description"} 
                                                fitParentWidth={true}
                                            />
                                        ) : (
                                            !description ? (
                                                <Button plain removeUnderline onClick={() => setIsEditingDescription(true)}>
                                                    Add description
                                                </Button>
                                            ) : (
                                                /*
                                                 Setting maxWidth to 100% to override the tooltipSpan class max-width of 63 vw and instead use the max-width of the parent HorizontalStack.
                                                */
                                                <Box maxWidth="100%" onClick={() => setIsEditingDescription(true)}>
                                                    <TooltipText tooltip={description} text={description} textProps={{ variant: 'bodyMd', fontWeight: "medium"}} />
                                                </Box>
                                            )
                                        )}
                                    </HorizontalStack>
                                </VerticalStack>
                            </Box>
                        )
                    }
                    secondaryActions={secondaryActionsComponent}
                    components={[
                        ...components
                    ]}
                />
            )}
        </div>

    )
}

export default ApiEndpoints
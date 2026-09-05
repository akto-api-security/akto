import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, IndexFiltersMode, Box, Badge, Popover, ActionList, Link, Tooltip } from "@shopify/polaris"
import api from "../api"
import { useEffect,useState, useRef } from "react"
import func from "@/util/func"
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import { CircleTickMajor } from '@shopify/polaris-icons';
import ObserveStore from "../observeStore"
import PersistStore from "../../../../main/PersistStore"
import transform from "../transform"
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered"
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow"
import CreateNewCollectionModal from "./CreateNewCollectionModal"
import TooltipText from "@/apps/dashboard/components/shared/TooltipText"
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo"
import collectionApi from "./api"
import CollectionsPageBanner from "./component/CollectionsPageBanner"
import useTable from "@/apps/dashboard/components/tables/TableContext"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip"

const headers = [
    {
        title: "API collection name",
        text: "API collection name",
        value: "displayNameComp",
        filterKey:"displayName",
        showFilter:true
    },
    {
        title: "Total endpoints",
        text: "Total endpoints",
        value: "endpoints",
        isText: CellType.TEXT,
        sortActive: true
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Risk score of collection is maximum risk score of the endpoints inside this collection</Text>} title="Risk score" />,
        value: 'riskScoreComp',
        sortActive: true
    },
    {
        title: 'Test coverage',
        text: 'Test coverage',
        value: 'coverage',
        isText: CellType.TEXT,
        tooltipContent: (<Text variant="bodySm">Percentage of endpoints tested successfully in the collection</Text>)
    },
    {
        title: 'Issues',
        text: 'Issues',
        value: 'issuesArr',
        tooltipContent: (<Text variant="bodySm">Severity and count of issues present in the collection</Text>)
    },
    {   
        title: 'Sensitive data' ,
        text: 'Sensitive data' ,
        value: 'sensitiveSubTypes',
        tooltipContent: (<Text variant="bodySm">Types of data type present in response of endpoint inside the collection</Text>)
    },
    {
        text: 'Collection type',
        title: 'Collection type',
        value: 'envTypeComp',
        filterKey: "envType",
        showFilter: true,
        tooltipContent: (<Text variant="bodySm">Environment type for an API collection, Staging or Production </Text>)
    },
    {   
        title: <HeadingWithTooltip content={<Text variant="bodySm">The most recent time an endpoint within collection was either discovered for the first time or seen again</Text>} title="Last traffic seen" />, 
        text: 'Last traffic seen', 
        value: 'lastTraffic',
        isText: CellType.TEXT,
        sortActive: true
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Time when collection was created</Text>} title="Discovered" />,
        text: 'Discovered',
        value: 'discovered',
        isText: CellType.TEXT,
        sortActive: true,
    }
]

const sortOptions = [
    { label: 'Activity', value: 'deactivatedScore asc', directionLabel: 'Active', sortKey: 'deactivatedRiskScore' },
    { label: 'Activity', value: 'deactivatedScore desc', directionLabel: 'Inactive', sortKey: 'activatedRiskScore' },
    { label: 'Risk Score', value: 'score asc', directionLabel: 'High risk', sortKey: 'riskScore', columnIndex: 3 },
    { label: 'Risk Score', value: 'score desc', directionLabel: 'Low risk', sortKey: 'riskScore' , columnIndex: 3},
    { label: 'Discovered', value: 'discovered asc', directionLabel: 'Recent first', sortKey: 'startTs', columnIndex: 9 },
    { label: 'Discovered', value: 'discovered desc', directionLabel: 'Oldest first', sortKey: 'startTs' , columnIndex: 9},
    { label: 'Endpoints', value: 'endpoints asc', directionLabel: 'More', sortKey: 'endpoints', columnIndex: 2 },
    { label: 'Endpoints', value: 'endpoints desc', directionLabel: 'Less', sortKey: 'endpoints' , columnIndex: 2},
    { label: 'Last traffic seen', value: 'detected asc', directionLabel: 'Recent first', sortKey: 'detected', columnIndex: 8 },
    { label: 'Last traffic seen', value: 'detected desc', directionLabel: 'Oldest first', sortKey: 'detected' , columnIndex: 8},
  ];


const resourceName = {
    singular: 'collection',
    plural: 'collections',
  };

function convertToCollectionData(c) {
    return {
        ...c,
        endpoints: c["urlsCount"] || 0,
        detected: func.prettifyEpoch(c.startTs),
        icon: CircleTickMajor,
        nextUrl: "/dashboard/observe/inventory/"+ c.id
    }
}

const convertToNewData = (collectionsArr, sensitiveInfoMap, severityInfoMap, coverageMap, trafficInfoMap, riskScoreMap) => {

    const newData = collectionsArr.map((c) => {
        if(c.deactivated){
            c.rowStatus = 'critical'
            c.disableClick = true
        }
        return{
            ...c,
            displayNameComp: (<Box maxWidth="20vw"><TooltipText tooltip={c.displayName} text={c.displayName} textProps={{fontWeight: 'medium'}}/></Box>),
            testedEndpoints: coverageMap[c.id] ? coverageMap[c.id] : 0,
            sensitiveInRespTypes: sensitiveInfoMap[c.id] ? sensitiveInfoMap[c.id] : [],
            severityInfo: severityInfoMap[c.id] ? severityInfoMap[c.id] : {},
            detected: func.prettifyEpoch(trafficInfoMap[c.id] || 0),
            detectedTimestamp : trafficInfoMap[c.id] || 0,
            riskScore: riskScoreMap[c.id] ? riskScoreMap[c.id] : 0,
            discovered: func.prettifyEpoch(c.startTs || 0),
        }
    })

    const prettifyData = transform.prettifyCollectionsData(newData)
    return { prettify: prettifyData, normal: newData }
}

function ApiCollections() {

    const [data, setData] = useState({'all':[]})
    const [active, setActive] = useState(false);
    const [loading, setLoading] = useState(false)
    const [selectedTab, setSelectedTab] = useState("all")
    const [selected, setSelected] = useState(0)
    const [summaryData, setSummaryData] = useState({totalEndpoints:0 , totalTestedEndpoints: 0, totalSensitiveEndpoints: 0, totalCriticalEndpoints: 0})
    const [hasUsageEndpoints, setHasUsageEndpoints] = useState(false)
    const [envTypeMap, setEnvTypeMap] = useState({})
    const [refreshData, setRefreshData] = useState(false)
    const [popover,setPopover] = useState(false)

    const definedTableTabs = ['All', 'Hostname', 'Groups', 'Custom']

    const { tabsInfo } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

    const setInventoryFlyout = ObserveStore(state => state.setInventoryFlyout)
    const setFilteredItems = ObserveStore(state => state.setFilteredItems) 
    const setSamples = ObserveStore(state => state.setSamples)
    const setSelectedUrl = ObserveStore(state => state.setSelectedUrl)

    const resetFunc = () => {
        setInventoryFlyout(false)
        setFilteredItems([])
        setSamples("")
        setSelectedUrl({})
    }

    const showCreateNewCollectionPopup = () => {
        setActive(true)
    }

    const allCollections = PersistStore(state => state.allCollections)
    const setAllCollections = PersistStore(state => state.setAllCollections)
    const setCollectionsMap = PersistStore(state => state.setCollectionsMap)
    const setHostNameMap = PersistStore(state => state.setHostNameMap)

    const setCoverageMap = PersistStore(state => state.setCoverageMap)

    async function fetchData() {
        setLoading(true)
        let apiCollectionsResp = await api.getAllCollectionsBasic();
        setLoading(false)
        let tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)
        let envTypeObj = {}
        tmp.forEach((c) => {
            envTypeObj[c.id] = c.envType
        })
        setEnvTypeMap(envTypeObj)
        let dataObj = {}
        dataObj = convertToNewData(tmp, {}, {}, {}, {}, {});
        let res = {}
        res.all = dataObj.prettify
        res.hostname = dataObj.prettify.filter((c) => c.hostName !== null && c.hostName !== undefined)
        res.groups = dataObj.prettify.filter((c) => c.type === "API_GROUP")
        res.custom = res.all.filter(x => !res.hostname.includes(x) && !res.groups.includes(x));
        setData(res);

        let apiPromises = [
            api.getAllCollections(),
            api.getCoverageInfoForCollections(),
            api.getLastTrafficSeen(),
            api.getUserEndpoints(),
        ];

        let results = await Promise.allSettled(apiPromises);

        apiCollectionsResp = results[0].status === 'fulfilled' ? results[0].value : {};
        let coverageInfo = results[1].status === 'fulfilled' ? results[1].value : {};
        let trafficInfo = results[2].status === 'fulfilled' ? results[2].value : {};
        let hasUserEndpoints = results[3].status === 'fulfilled' ? results[3].value : true;
        setHasUsageEndpoints(hasUserEndpoints)
        setCoverageMap(coverageInfo)

        tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)
        envTypeObj = {}
        tmp.forEach((c) => {
            envTypeObj[c.id] = c.envType
        })
        setEnvTypeMap(envTypeObj)

        const issuesObj = await transform.fetchRiskScoreInfo();
        const severityObj = issuesObj.severityObj;
        const riskScoreObj = issuesObj.riskScoreObj;
        const sensitiveInfo = await transform.fetchSensitiveInfo();

        dataObj = convertToNewData(tmp, sensitiveInfo.sensitiveInfoMap, severityObj, coverageInfo, trafficInfo, riskScoreObj?.riskScoreMap);

        const summary = transform.getSummaryData(dataObj.normal)
        summary.totalCriticalEndpoints = riskScoreObj.criticalUrls;
        summary.totalSensitiveEndpoints = sensitiveInfo.sensitiveUrls
        setSummaryData(summary)

        setAllCollections(apiCollectionsResp.apiCollections || [])
        setCollectionsMap(func.mapCollectionIdToName(tmp))
        const allHostNameMap = func.mapCollectionIdToHostName(tmp)
        setHostNameMap(allHostNameMap)

        tmp = {}
        tmp.all = dataObj.prettify
        tmp.hostname = dataObj.prettify.filter((c) => c.hostName !== null && c.hostName !== undefined)
        tmp.groups = dataObj.prettify.filter((c) => c.type === "API_GROUP")
        tmp.custom = tmp.all.filter(x => !tmp.hostname.includes(x) && !tmp.groups.includes(x));

        setData(tmp);
    }

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 2)
    }

    useEffect(() => {
        fetchData()
        resetFunc()
    }, [])
    const createCollectionModalActivatorRef = useRef();

    async function handleCollectionsAction(collectionIdList, apiFunction, toastContent){
        const collectionIdListObj = collectionIdList.map(collectionId => ({ id: collectionId.toString() }))
        await apiFunction(collectionIdListObj)
        fetchData()
        func.setToast(true, false, `${collectionIdList.length} API collection${func.addPlurality(collectionIdList.length)} ${toastContent} successfully`)
    }

    const promotedBulkActions = (selectedResources) => {
        let actions = [
            {
                content: `Remove collection${func.addPlurality(selectedResources.length)}`,
                onAction: () => handleCollectionsAction(selectedResources, api.deleteMultipleCollections, "deleted")
            }
        ];

        const deactivated = allCollections.filter(x => { return x.deactivated }).map(x => x.id);
        const activated = allCollections.filter(x => { return !x.deactivated }).map(x => x.id);
        if (selectedResources.every(v => { return activated.includes(v) })) {
            actions.push(
                {
                    content: `Deactivate collection${func.addPlurality(selectedResources.length)}`,
                    onAction: () => {
                        const message = "Deactivating a collection will stop traffic ingestion and testing for this collection. Please sync the usage data via Settings > billing after deactivating a collection to reflect your updated usage. Are you sure, you want to deactivate this collection ?"
                        func.showConfirmationModal(message, "Deactivate collection", () => handleCollectionsAction(selectedResources, collectionApi.deactivateCollections, "deactivated") )
                    }
                }
            )
        } else if (selectedResources.every(v => { return deactivated.includes(v) })) {
            actions.push(
                {
                    content: `Reactivate collection${func.addPlurality(selectedResources.length)}`,
                    onAction: () =>  {
                        const message = "Please sync the usage data via Settings > billing after reactivating a collection to resume data ingestion and testing."
                        func.showConfirmationModal(message, "Activate collection", () => handleCollectionsAction(selectedResources, collectionApi.activateCollections, "activated"))
                    }
                }
            )
        }

        const toggleTypeContent = (
            <Popover
                activator={<div onClick={() => setPopover(!popover)}>Set ENV type</div>}
                onClose={() => setPopover(false)}
                active={popover}
                autofocusTarget="first-node"
            >
                <Popover.Pane>
                    <ActionList
                        actionRole="menuitem"
                        items={[
                            {content: 'Staging', onAction: () => updateEnvType(selectedResources, "STAGING")},
                            {content: 'Production', onAction: () => updateEnvType(selectedResources, "PRODUCTION")},
                            {content: 'Reset', onAction: () => updateEnvType(selectedResources, null)},
                        ]}
                    />
                </Popover.Pane>
            </Popover>
        )

        const toggleEnvType = {
            content: toggleTypeContent
        }

        return [...actions, toggleEnvType];
    }
    const updateData = (dataMap) => {
        let copyObj = data;
        Object.keys(copyObj).forEach((key) => {
            data[key].length > 0 && data[key].forEach((c) => {
                c['envType'] = dataMap[c.id]
                c['envTypeComp'] = dataMap[c.id] ? <Badge size="small" status="info">{func.toSentenceCase(dataMap[c.id])}</Badge> : null
            })
        })
        setData(copyObj)
        setRefreshData(!refreshData)
    }

    const updateTags = async (apiCollectionIds, tagObj) => {
        let copyObj = await JSON.parse(JSON.stringify(envTypeMap))
        apiCollectionIds.forEach(id => {
            if(!copyObj[id]) {
                copyObj[id] = []
            }

            if(tagObj === null) {
                copyObj[id] = []
            } else {
                if(tagObj?.keyName?.toLowerCase() === 'envtype') {
                    // Replace any existing envType tag (staging, production, QA, DEV, INTEG, UAT, PREPROD, INTERNAL, etc.)
                    const currentEnvIndex = copyObj[id].findIndex(tag =>
                        tag.keyName?.toLowerCase() === 'envtype' || tag.keyName?.toLowerCase() === 'usersetenvtype'
                    )

                    if (currentEnvIndex === -1) {
                        copyObj[id].push(tagObj)
                    } else {
                        const currentValue = copyObj[id][currentEnvIndex].value?.toLowerCase()
                        if (tagObj.value?.toLowerCase() !== currentValue) {
                            copyObj[id][currentEnvIndex] = tagObj
                        } else {
                            copyObj[id].splice(currentEnvIndex, 1)
                        }
                    }
                } else {
                    const index = copyObj[id].findIndex(tag => 
                        tag.keyName === tagObj.keyName && tag.value === tagObj.value
                    )

                    if (index === -1) {
                        copyObj[id].push(tagObj)
                    } else {
                        copyObj[id].splice(index, 1)
                    }
                }
            }
        })


        await api.updateEnvTypeOfCollection(tagObj === null ? tagObj : [tagObj], apiCollectionIds, tagObj === null).then(() => {
            func.setToast(true, false, "Tags updated successfully")
            setEnvTypeMap(copyObj)
            updateData(copyObj)
        })

    }

    const modalComponent = <CreateNewCollectionModal
        key="modal"
        active={active}
        setActive={setActive}
        createCollectionModalActivatorRef={createCollectionModalActivatorRef}
        fetchData={fetchData}
    />

    let coverage = '0%';
    if(summaryData.totalEndpoints !== 0){
        if(summaryData.totalEndpoints < summaryData.totalTestedEndpoints){
            coverage = '100%'
        }else{
            coverage = Math.ceil((summaryData.totalTestedEndpoints * 100) / summaryData.totalEndpoints) + '%'
        }
    }

      const summaryItems = [
        {
            title: "Total APIs",
            data: transform.formatNumberWithCommas(summaryData.totalEndpoints),
        },
        {
            title: "Critical APIs",
            data: transform.formatNumberWithCommas(summaryData.totalCriticalEndpoints),
        },
        {
            title: "Tested APIs (Coverage)",
            data: coverage
        },
        {
            title: "Sensitive in response APIs",
            data: transform.formatNumberWithCommas(summaryData.totalSensitiveEndpoints),
        }
    ]


    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex)
    }

    const tableComponent = (
        <GithubSimpleTable
            key={refreshData}
            pageLimit={100}
            data={data[selectedTab]}
            sortOptions={sortOptions}
            resourceName={resourceName}
            filters={[]}
            disambiguateLabel={disambiguateLabel}
            headers={headers}
            selectable={true}
            promotedBulkActions={promotedBulkActions}
            mode={IndexFiltersMode.Default}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
            tableTabs={tableTabs}
            onSelect={handleSelectedTab}
            selected={selected}
        />
    )

    const components = loading ? [<SpinnerCentered key={"loading"}/>]: [<SummaryCardInfo summaryItems={summaryItems} key="summary"/>, (!hasUsageEndpoints ? <CollectionsPageBanner key="page-banner" /> : null) ,modalComponent, tableComponent]

    return(
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"Akto automatically groups similar APIs into meaningful collections based on their subdomain names. "}
                    titleText={"API collections"}
                    docsUrl={"https://docs.akto.io/api-inventory/concepts"}
                />
            }
            primaryAction={<Button id={"create-new-collection-popup"} primary secondaryActions onClick={showCreateNewCollectionPopup}>Create new collection</Button>}
            isFirstPage={true}
            components={components}
        />
    )
}

export default ApiCollections 
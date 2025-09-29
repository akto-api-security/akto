import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Button, Modal, HorizontalStack, IndexFiltersMode, Text, Badge, Thumbnail, HorizontalGrid, Box } from "@shopify/polaris"
import api from "../api"
import { useEffect,useState } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { useNavigate } from "react-router-dom"
import dashboardFunc from "../../transform"
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import { CellType } from "../../../components/tables/rows/GithubRow"
import useTable from "../../../components/tables/TableContext"
import transform from "../transform"
import ShowListInBadge from "../../../components/shared/ShowListInBadge"
import PersistStore from "../../../../main/PersistStore"
import SummaryCard from "../../dashboard/new_components/SummaryCard"
import InfoCard from "../../dashboard/new_components/InfoCard"
import ChartypeComponent from "../../testing/TestRunsPage/ChartypeComponent"
import BarGraph from "../../../components/charts/BarGraph"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper"

const headers = [
    {
        title: "Priority",
        text: "Priority",
        filterKey: "priorityText",
        value: "priorityComp",
        sortActive: true,
        sortKey: "priorityVal",
        showFilter: true,
    },
    {
        title: "",
        text: "",
        value: "avatarComp",
    },
    {
        title: "Data type",
        text: "Data type",
        value: "nameComp",
        filterKey: "subType",
    },
    {
        title: "Domains",
        text: "Collection name",
        value: "domainsComp",
        filterKey: "domainsArrName",
        showFilter: true,
    },
    {
        title: mapLabel("API response", getDashboardCategory()),
        text: "API response",
        value: "response",
        sortActive: true,
    },
    {
        title: mapLabel("API request", getDashboardCategory()),
        text: "API request",
        value: "request",
        sortActive: true
    },
    {
        title: "Datatype tags",
        text: "Tags",
        value: "categoryComp",
        filterKey: "categoriesArr",
        showFilter: true,
    },
    {
        title: '',
        type: CellType.ACTION,
    }
] 

const sortOptions = [
    { label: 'Sensitive in Response', value: 'response asc', directionLabel: 'Highest', sortKey: 'response', columnIndex: 5},
    { label: 'Sensitive in Response', value: 'response desc', directionLabel: 'Lowest', sortKey: 'response', columnIndex: 5},
    { label: 'Sensitive in Request', value: 'request asc', directionLabel: 'Highest', sortKey: 'request', columnIndex: 6},
    { label: 'Sensitive in Request', value: 'request desc', directionLabel: 'Lowest', sortKey: 'request', columnIndex: 6},
    { label: 'Priority', value: 'priorityVal asc', directionLabel: 'Highest', sortKey: 'priorityVal', columnIndex: 1},
    { label: 'Priority', value: 'priorityVal desc', directionLabel: 'Lowest', sortKey: 'priorityVal', columnIndex: 1},
];

const resourceName = {
    singular: 'sensitive data type',
    plural: 'sensitive data types',
  };

const severityOrder = {'CRITICAL': 4 ,'HIGH': 3, 'MEDIUM': 2, 'LOW': 1 };  
const colorsArr = ["#7F56D9", "#9E77ED", "#B692F6", "#D6BBFB", "#E9D7FE"];

const convertToDataTypesData = (type, collectionsMap, countMap, subtypeToApiCollectionsMap) => {
    const priorityText =  type?.dataTypePriority || "-"
    const categoriesList = type?.categoriesList || []

    const domainsArr = subtypeToApiCollectionsMap[type.name] || []

    const iconSource = (type?.iconString !== undefined && type?.iconString?.length> 0) ? func.getIconFromString(type?.iconString) : func.getSensitiveIcons(type.name)

    return {
        id: type.name,
        nameComp: <Text fontWeight="medium">{type.name}</Text>,
        subType: type.name,
        avatarComp: <Thumbnail source={iconSource} size="small" />,
        priorityVal: priorityText.length > 1 ? severityOrder[priorityText] : 0,
        priorityText: priorityText,
        priorityComp: priorityText.length > 1 ? 
        <div key={type.name} className={`badge-wrapper-${priorityText}`}>
            <Badge status={func.getHexColorForSeverity(priorityText)}>{func.toSentenceCase(priorityText)}</Badge>
        </div> : "-",
        categoriesArr: categoriesList,
        categoryComp: categoriesList.length > 0 ?  (
            <ShowListInBadge 
                itemsArr={categoriesList}
                maxItems={3}
                maxWidth={"280px"}
                status={"new"}
            />) 
        : "-" ,
        domainsArr: domainsArr,
        domainsArrName: domainsArr.map(x => collectionsMap[x]),
        domainsComp: domainsArr.length > 0 ? (
            <ShowListInBadge 
                itemsArr={domainsArr.map(x => collectionsMap[x])}
                maxItems={1}
                maxWidth={"250px"}
                status={"new"}
                itemWidth={"200px"}
            />) 
        : "-" ,
        response: (countMap['RESPONSE'] && countMap['RESPONSE'][type.name]) || 0,
        request: (countMap['REQUEST'] && countMap['REQUEST'][type.name]) || 0,
        active: type?.active !== undefined ?  type.active : true,
        nextUrl: "/dashboard/observe/sensitive/"+type.name,
        isCustomType: type?.creatorId > 0
    }
}

function AllSensitiveData() {

    const [data, setData] = useState({"all":[], "detected": [], "enabled":[], 'disabled': []})
    const [mapData, setMapData] = useState({})
    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const navigate = useNavigate()
    const collectionsMap = PersistStore((state) => state.collectionsMap)
    const [summaryInfo, setSummaryInfo] = useState({
        totalAPIs: 0,
        sensitiveMarked: 0,
        totalActive: 0,
        totalCategories: 0
    })
    const [countMap, setCountMap] = useState({})
    const [severityCountMap, setSeverityCountMap] = useState([])
    const [loading, setLoading] = useState(false)

    const definedTableTabs = ["All", "Detected", "Enabled", "Disabled"]
    const tableSelectedTab = PersistStore.getState().tableSelectedTab[window.location.pathname]
    const initialSelectedTab = tableSelectedTab || "detected"
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab)
    let initialTabIdx = func.getTableTabIndexById(1, definedTableTabs, initialSelectedTab)
    const [selected, setSelected] = useState(initialTabIdx)

    const { tabsInfo } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

    const getActions = (item) => {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => setTimeout(() =>navigate("/dashboard/observe/data-types", {state: {name: item.subType, dataObj: mapData[item.subType], type: item.isCustomType ? 'Custom' : 'Akto'}}),[0]),
            }]
        }]
    }

    function disambiguateLabel(key, value) {
        switch(key){
            case "subType": 
                return func.convertToDisambiguateLabelObj(value, null, 4)
            default:
                return value
        }
    }

    const handleRedirect = () => {
        navigate("/dashboard/observe/data-types")
    }

    const convertToGraphData = (severityMap) => {
        let dataArr = []
        Object.keys(severityMap).forEach((x) => {
            const color = func.getHexColorForSeverity(x)
            let text = func.toSentenceCase(x)
            const value =  severityMap[x]
            dataArr.push({
                text, value, color
            })
        })
        return dataArr
    }

    const fetchData = async() => {
        setLoading(true)
        let temp = {}
        const promises = [
            api.fetchDataTypes(),
            api.fetchSubTypeCountMap(0, func.timeNow()),
            api.fetchCountMapOfApis()
        ]

        const results = await Promise.allSettled(promises)
        const dataTypesRes = results[0].status === 'fulfilled' ? results[0].value : {};
        const subTypeCountRes = results[1].status === 'fulfilled' ? results[1].value : {};
        const dataTypesVsApisCount = results[2].status === 'fulfilled' ? results[2].value : {};

        let dataTypesArr = [...(dataTypesRes?.dataTypes?.aktoDataTypes || []) , ...(dataTypesRes?.dataTypes?.customDataTypes || [])]
        const reqResCountMap = subTypeCountRes?.subTypeCountMap || {REQUEST: {}, RESPONSE: {}}
        let subtypeToNameMap = {}

        let totalSensitive = 0;
        const categoriesSet = new Set()

        let countOfSubtypes = []
        let subTypesSeverityCountMap={
            "CRITICAL": 0,
            "HIGH": 0,
            "MEDIUM": 0,
            "LOW": 0,
        }

        const tempArr = dataTypesArr.map((type) => {
            subtypeToNameMap[type.name] = type
            if(type.sensitiveAlways || (type?.sensitivePosition || []).length > 0){
                totalSensitive++
            }
            (type?.categoriesList || []).forEach(x => {categoriesSet.add(x)})
            if(dataTypesVsApisCount?.countMap?.[type.name] && dataTypesVsApisCount?.countMap?.[type.name] !== undefined){
                countOfSubtypes.push({key: [type?.name] , value: dataTypesVsApisCount?.countMap?.[type.name]})
                if(type?.dataTypePriority !== undefined && type?.dataTypePriority?.length > 0){
                    subTypesSeverityCountMap[type?.dataTypePriority] += dataTypesVsApisCount?.countMap?.[type.name];
                }
                
            }
            return convertToDataTypesData(type, collectionsMap, reqResCountMap, dataTypesVsApisCount?.apiCollectionsMap || {})
        })
        temp.all = tempArr
        temp.detected = tempArr.filter((x) => x.response > 0 || x.request > 0)
        temp.enabled = tempArr.filter((x) => x.active === true)
        temp.disabled = tempArr.filter((x) => x.active === false)
        let finalCountMap = {}
        countOfSubtypes.sort((a,b) =>{
            return b['value'] - a['value']
        }).slice(0,5).forEach((x, index) => {
            finalCountMap[x.key]= {
                color: colorsArr[index],
                text: x['value']
            }
        })
        setLoading(false)
        setCountMap(finalCountMap)
        setData(temp)
        setMapData(subtypeToNameMap)
        setSeverityCountMap(convertToGraphData(subTypesSeverityCountMap))
        const summaryObj = {
            totalAPIs: dataTypesVsApisCount?.totalApisCount || 0,
            totalActive: temp.enabled.length,
            totalCategories: categoriesSet.size,
            sensitiveMarked: totalSensitive
        }
        setSummaryInfo(summaryObj)
    }

    const summaryInfoData = [
        {
            title: mapLabel("APIs Affected", getDashboardCategory()),
            data: transform.formatNumberWithCommas(summaryInfo.totalAPIs),
            variant: 'heading2xl',
            color: "critical"
        },
        {
            title: 'Sensitive Datatypes',
            data: transform.formatNumberWithCommas(summaryInfo.sensitiveMarked),
            variant: 'heading2xl',
        },
        {
            title: 'Datatype Enabled',
            data: transform.formatNumberWithCommas(summaryInfo.totalActive),
            variant: 'heading2xl',
        },
        {
            title: 'Total Datatype Tags',
            data: transform.formatNumberWithCommas(summaryInfo.totalCategories),
            variant: 'heading2xl',
        }
    ]
    
    useEffect(() => {
        fetchData();
    }, [])

    function displayGPT(){
        setIsGptScreenActive(true)
        let requestObj = {key: "DATA_TYPES"}
        const activePrompts = dashboardFunc.getPrompts(requestObj)
        setPrompts(activePrompts)
    }

    function resetSampleData(){
        api.resetSampleData();
    }

    function fillSensitiveDataTypes(){
        api.fillSensitiveDataTypes();
    }

    const secondaryActionsComp = (
        <HorizontalStack gap={"2"}>
            { (func.checkOnPrem() && window?.USER_NAME !== undefined && window.USER_NAME.includes("razorpay")) ? <Button onClick={resetSampleData}>Reset Sample Data</Button> : <></>}
            <Button onClick={displayGPT}>Ask AktoGPT</Button>
            <Button onClick={fillSensitiveDataTypes}>Fill Data Types</Button>
        </HorizontalStack>
    )

    const graphComponents = (
        <HorizontalGrid key={"graphs"} gap={"5"} columns={2}>
            <InfoCard
                title={`${mapLabel("APIs", getDashboardCategory())} by Sensitive data severity`}
                titleToolTip={`Number of ${mapLabel("APIs", getDashboardCategory())} per each category`}
                component={
                    <BarGraph
                        data={severityCountMap}
                        areaFillHex="true"
                        height={"280px"}
                        defaultChartOptions={{
                            "legend": {
                                enabled: false
                            },
                        }}
                        showYAxis={true}
                        yAxisTitle={`Number of ${mapLabel("APIs", getDashboardCategory())}`}
                        barWidth={100}
                        barGap={12}
                        showGridLines={true}
                    />
                }
            />
            <InfoCard
                title={"Top 5 sensitive datatype"}
                titleToolTip={"Numbers of APIs having the corresponding data type in request or response"}
                component={
                    <Box paddingBlockStart={"6"}>
                        <ChartypeComponent
                            data={countMap}
                            isNormal={true} 
                            boxHeight={'280px'} 
                            chartOnLeft={true} 
                            dataTableWidth="270px" 
                            boxPadding={0}
                            pieInnerSize="50%"
                        />
                    </Box>
                }
            />
        </HorizontalGrid>
    )

    const componentsArr = [
        <SummaryCard
            key={"summaryInfo"}
            summaryItems={summaryInfoData}
         />,
         graphComponents,
        <GithubSimpleTable
            key="table"
            data={data[selectedTab]} 
            sortOptions={sortOptions} 
            resourceName={resourceName} 
            filters={[]}
            disambiguateLabel={disambiguateLabel} 
            headers={headers}
            hasRowActions={true}
            getActions={getActions}
            mode={IndexFiltersMode.Default}
            headings={headers}
            useNewRow={true}
            // condensedHeight={true}
            tableTabs={tableTabs}
            onSelect={(val) => setSelected(val)}
            selected={selected}
            lastColumnSticky={true}
        />,
        <Modal key="modal" large open={isGptScreenActive} onClose={()=> setIsGptScreenActive(false)} title="Akto GPT">
            <Modal.Section flush>
                <AktoGptLayout prompts={prompts} closeModal={()=> setIsGptScreenActive(false)} />
            </Modal.Section>
        </Modal>
    ]
    
    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo 
                    titleText={"Sensitive data exposure"} 
                    tooltipContent={"Akto allows you to identify which sensitive data an API contains in request or response."}
                    docsUrl="https://docs.akto.io/api-inventory/concepts/sensitive-data" 
                />
            }
            primaryAction={<Button id={"all-data-types"} primary onClick={handleRedirect}>Create custom data types</Button>}
            secondaryActions={secondaryActionsComp}
            isFirstPage={true}
            components={
                loading ? [<SpinnerCentered key={"spinner"}/>] : componentsArr
            }
        />

    )
}

export default AllSensitiveData
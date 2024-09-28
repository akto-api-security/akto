import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Button, Modal, HorizontalStack, IndexFiltersMode, Text, Badge, Thumbnail } from "@shopify/polaris"
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

const headers = [
    {
        title: "Priority",
        text: "Priority",
        filterKey: "priorityText",
        value: "priorityComp",
        sortActive: true,
        sortKey: "priorityVal"
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
        title: "Category",
        text: "Category",
        value: "categoryComp",
        filterKey: "categoriesArr"
    },
    {
        title: "Domains",
        text: "Domains",
        value: "domainsComp",
        filterKey: "domainsArr"
    },
    {
        title: "API response",
        text: "API response",
        value: "response",
        sortActive: true,
    },
    {
        title: "API request",
        text: "API request",
        value: "request",
        sortActive: true
    },
    {
        title: '',
        type: CellType.ACTION,
    }
] 

const sortOptions = [
    { label: 'Sensitive in Response', value: 'response asc', directionLabel: 'Highest', sortKey: 'response', columnIndex: 6},
    { label: 'Sensitive in Response', value: 'response desc', directionLabel: 'Lowest', sortKey: 'response', columnIndex: 6},
    { label: 'Sensitive in Request', value: 'request asc', directionLabel: 'Highest', sortKey: 'request', columnIndex: 7},
    { label: 'Sensitive in Request', value: 'request desc', directionLabel: 'Lowest', sortKey: 'request', columnIndex: 7},
    { label: 'Priority', value: 'priorityVal asc', directionLabel: 'Highest', sortKey: 'priorityVal', columnIndex: 1},
    { label: 'Priority', value: 'priorityVal desc', directionLabel: 'Lowest', sortKey: 'priorityVal', columnIndex: 1},
];

const resourceName = {
    singular: 'sensitive data type',
    plural: 'sensitive data types',
  };

const severityOrder = {'CRITICAL': 4 ,'HIGH': 3, 'MEDIUM': 2, 'LOW': 1 };  

const convertToDataTypesData = (type, collectionsMap, countMap, subtypeToApiCollectionsMap) => {
    const priorityText =  type?.dataTypePriority || "-"
    const categoriesList = type?.categoriesList || []

    const domainsArr = subtypeToApiCollectionsMap[type.name] || []

    return {
        id: type.name,
        nameComp: <Text fontWeight="medium">{type.name}</Text>,
        subType: type.name,
        avatarComp: <Thumbnail source={func.getSensitiveIcons(type.name)} size="extraSmall"/>,
        priorityVal: priorityText.length > 1 ? severityOrder[priorityText] : 0,
        priorityText: priorityText,
        priorityComp: priorityText.length > 1 ? <Badge status={transform.getColor(priorityText)}>{priorityText}</Badge> : "-",
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
        domainsComp: domainsArr.length > 0 ? (
            <ShowListInBadge 
                itemsArr={domainsArr.map(x => collectionsMap[x])}
                maxItems={1}
                maxWidth={"250px"}
                status={"new"}
            />) 
        : "-" ,
        response: (countMap['RESPONSE'] && countMap['RESPONSE'][type.name]) || 0,
        request: (countMap['REQUEST'] && countMap['REQUEST'][type.name]) || 0,
        active: type?.active !== undefined ?  type.active : true
    }
}

function AllSensitiveData() {

    const [data, setData] = useState({"all":[]})
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

    const [selectedTab, setSelectedTab] = useState("all")
    const [selected, setSelected] = useState(0)

    const definedTableTabs = ["All", "Enabled", "Disabled"]

    const { tabsInfo } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

    const getActions = (item) => {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => navigate("/dashboard/observe/data-types", {state: {name: item.subType, dataObj: mapData[item.subType], type: item.isCustomType ? 'Custom' : 'Akto'}}),
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

    const fetchData = async() => {
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
        const reqResCountMap = subTypeCountRes?.response?.subTypeCountMap || {REQUEST: {}, RESPONSE: {}}
        let subtypeToNameMap = {}

        let totalSensitive = 0;
        const categoriesSet = new Set()

        const tempArr = dataTypesArr.map((type) => {
            subtypeToNameMap[type.name] = type
            if(type.sensitiveAlways || (type?.sensitivePosition || []).length > 0){
                totalSensitive++
            }
            (type?.categoriesList || []).forEach(x => {categoriesSet.add(x)})
            return convertToDataTypesData(type, collectionsMap, reqResCountMap, dataTypesVsApisCount?.apiCollectionsMap || {})
        })
        temp.all = tempArr
        temp.enabled = tempArr.filter((x) => x.active === true)
        temp.disabled = tempArr.filter((x) => x.active === false)
        setData(temp)
        setMapData(subtypeToNameMap)
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
            title: 'APIs Affected',
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
            title: 'Total Categories',
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

    const secondaryActionsComp = (
        <HorizontalStack gap={"2"}>
            { (func.checkOnPrem() && window?.USER_NAME !== undefined && window.USER_NAME.includes("razorpay")) ? <Button onClick={resetSampleData}>Reset Sample Data</Button> : <></>}
            <Button onClick={displayGPT}>Ask AktoGPT</Button>
        </HorizontalStack>
    )

    const componentsArr = [
        <SummaryCard
            key={"summaryInfo"}
            summaryItems={summaryInfoData}
         />,
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
            condensedHeight={true}
            tableTabs={tableTabs}
            onSelect={(val) => setSelected(val)}
            selected={selected}
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
            components={componentsArr}
        />

    )
}

export default AllSensitiveData
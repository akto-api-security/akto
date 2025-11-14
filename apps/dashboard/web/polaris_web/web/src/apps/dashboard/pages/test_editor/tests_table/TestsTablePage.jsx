import { Avatar, Badge, Box, IndexFiltersMode, List, Text } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../../components/shared/TitleWithInfo";

import transform from "../../testing/transform"
import func from "../../../../../util/func"
import convertFunc from ".././transform"
import { useEffect, useState } from "react";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import useTable from "../../../components/tables/TableContext";
import TestsFlyLayout from "./TestsFlyLayout";
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip";
import TooltipText from "../../../components/shared/TooltipText";
import LocalStore from "../../../../main/LocalStorageStore";
import ShowListInBadge from "../../../components/shared/ShowListInBadge";
import { getDashboardCategory } from "../../../../main/labelHelper";

const sortOptions = [
    { label: 'Severity', value: 'severity asc', directionLabel: 'Highest', sortKey: 'severityVal', columnIndex: 2 },
    { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest', sortKey: 'severityVal', columnIndex: 2 },
    { label: 'Test', value: 'test asc', directionLabel: 'A-Z', sortKey: 'name', columnIndex: 1 },
    { label: 'Test', value: 'test desc', directionLabel: 'Z-A', sortKey: 'name', columnIndex: 1 },
];

const llmCategories = [
    "LLM",
    "LLM01",
    "PROMPT_INJECTION",
    "SENSITIVE_INFORMATION_DISCLOSURE",
    "SUPPLY_CHAIN",
    "DATA_AND_MODEL_POISONING",
    "IMPROPER_OUTPUT_HANDLING",
    "EXCESSIVE_AGENCY",
    "SYSTEM_PROMPT_LEAKAGE",
    "VECTOR_AND_EMBEDDING_WEAKNESSES",
    "MISINFORMATION",
    "UNBOUNDED_CONSUMPTION",
    "AGENTIC_BUSINESS_ALIGNMENT",
    "AGENTIC_HALLUCINATION_AND_TRUSTWORTHINESS",
    "AGENTIC_SAFETY",
    "AGENTIC_SECURITY",
]

const mcpCategories = [
    "MCP_AUTH",
    "MCP_INPUT_VALIDATION",
    "MCP_DOS",
    "MCP_SENSITIVE_DATA_LEAKAGE",
    "MCP_TOOL_POISONING",
    "MCP_PROMPT_INJECTION",
    "MCP_PRIVILEGE_ABUSE",
    "MCP_INDIRECT_PROMPT_INJECTION",
    "MCP_MALICIOUS_CODE_EXECUTION",
    "MCP_FUNCTION_MANIPULATION",
    "MCP_SECURITY",
    "MCP"
]

const severityObj = {
    "Critical": "Immediate action; exploitable with severe impact",
    "High": "Urgent action; significant security risk",
    "Medium": "Moderate risk; potential for exploitation",
    "Low": "Minor concerns; limited impact",
    "Dynamic Severity": "Severity changes based on API context"
}

const headings = [
    {
        title: "Test",
        text: "Tests",
        value: "tests",
        textValue: "tests",
        sortActive: true,
        boxWidth: "465px",
    },
    {
        title: (
            <HeadingWithTooltip
                title={"Severity"}
                content={
                    <List>
                        {Object.entries(severityObj).map(([key, value]) => (
                            <List.Item key={key}><Text as="span" fontWeight="medium">{key}</Text> - <Text as="span" color="subdued">{value}</Text></List.Item>
                        ))}
                    </List>
                }
            />
        ),
        text: "Severity",
        value: "severity",
        textValue: "severity",
        sortKey: "severityVal",
        sortActive: true,
        showFilter: true,
        filterKey: "severityText",
    },
    {
        title: "Category",
        text: "Category",
        value: "category",
        showFilter: true,
        filterKey: "category",
    },
    {
        title: "Testing Methods",
        text: "Testing Methods",
        value: "testingMethods",
        showFilter: true,
        filterKey: 'testingMethods'
    },
    {
        title: "Compliance",
        value: "complianceComp",
        text: "Compliance",
        showFilter: true,
        filterKey: 'compliance'
    },
    {
        title: "Author",
        text: "Author",
        value: "author",
        showFilter: true,
        filterKey: "author",
    },
    {
        title: "Duration",
        text: "Duration",
        value: "duration",
        showFilter: true,
        filterKey: "duration",
    }
];


let headers = JSON.parse(JSON.stringify(headings))

function TestsTablePage() {
    const [selectedTest, setSelectedTest] = useState({})
    const [data, setData] = useState({ 'all': [], 'by_akto': [], 'custom': [], 'inactive': [] })
    const localSubCategoryMap = LocalStore.getState().subCategoryMap
    const categoryMap = LocalStore.getState().categoryMap;
    const dashboardCategory = getDashboardCategory();

    const severityOrder = { CRITICAL: 5, HIGH: 4, MEDIUM: 3, LOW: 2, dynamic_severity: 1 };

    const mapTestData = (obj) => {
        const allData = [], customData = [], aktoData = [], deactivatedData = [];
        Object.entries(obj.mapTestToData).map(([key, value]) => {
            const data = {
                name: key,
                tests: <Box maxWidth="480px"><TooltipText text={key} tooltip={key} textProps={{fontWeight: 'medium'}} />
              </Box>,
                severityText: value.severity.replace(/_/g, " ").toUpperCase(),
                severity: value.severity.length > 1 ? (
                    <div className={`badge-wrapper-${value.severity}`}>
                        <Badge status={func.getHexColorForSeverity(value.severity)}>
                            {func.toSentenceCase(value.severity.replace(/_/g, " "))}
                        </Badge>
                    </div>
                ) : "",
                category: value.category,
                author: func.toSentenceCase(value.author),
                testingMethods: value.nature.length ? func.toSentenceCase(value.nature.replace(/_/g, " ")) : "-",
                severityVal: severityOrder[value.severity] || 0,
                value: value.value,  
                complianceComp: value?.compliance?.length > 0 ? (
                    <ShowListInBadge
                        itemsArr={value?.compliance.map(x => <Avatar source={func.getComplianceIcon(x)} shape="square"  size="extraSmall"/>)}
                        maxItems={2}
                        maxWidth={"250px"}
                        status={"new"}
                        itemWidth={"200px"}
                        useBadge={false}
                    />) 
                : "-",
                compliance: value?.compliance,
                duration: value?.duration || "-",
            }
            if (value.isCustom) {
                if (value?.inactive === true) {
                    deactivatedData.push(data)
                } else {
                    customData.push(data)
                }
            } else {
                aktoData.push(data)
            }
            allData.push(data)
        });
        return [allData, aktoData, customData, deactivatedData]
    };

    const fetchAllTests = async () => {
        try {
            let categoriesName = Object.keys(categoryMap);
            if(dashboardCategory === "MCP Security"){
                categoriesName = mcpCategories;
            } else if (dashboardCategory === "Agentic Security") {
                categoriesName = [...llmCategories, ...mcpCategories];
            }
             else {
                categoriesName = Object.keys(categoryMap);
            }
            let metaDataObj = {
                subCategories: [],
                categories: []
            }

            if ((localSubCategoryMap && Object.keys(localSubCategoryMap).length > 0 ) && categoriesName.length > 0) {
                metaDataObj = {
                    subCategories: Object.values(localSubCategoryMap),
                    categories: Object.keys(categoryMap)
                }
            } else { 
                metaDataObj = await transform.getAllSubcategoriesData(false, "testEditor")
                categoriesName = metaDataObj?.categories.map(x => x.name)
            }
            if (!metaDataObj?.subCategories?.length) return;
            try {
                metaDataObj.subCategories = metaDataObj.subCategories.filter(
                    (subCategory) => categoriesName.includes(subCategory.superCategory.name)
                )
            } catch (error) {
            }

            const obj = convertFunc.mapCategoryToSubcategory(metaDataObj.subCategories);
            const [allData, aktoData, customData, deactivatedData] = mapTestData(obj);
            setData({
                all: allData,
                by_akto: aktoData,
                custom: customData,
                inactive: deactivatedData
            });
        } catch (error) {
            console.error("Error fetching tests:", error);
        }
    };

    useEffect(() => {
        fetchAllTests()
    }, [dashboardCategory])


    const resourceName = {
        singular: 'test',
        plural: 'tests',
    };

    const [showDetails, setShowDetails] = useState(false)

    const handleRowClick = (data) => {
        setShowDetails(true)
        setSelectedTest(data);
    };

    const [selected, setSelected] = useState(1)
    const [selectedTab, setSelectedTab] = useState('by_akto')

    const { tabsInfo } = useTable()
    const definedTableTabs = ['All', 'By Akto', 'Custom', 'Inactive'];
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)


    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex)
    }

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabel(value, func.toSentenceCase, 2)
    }


    const components = [
        <GithubSimpleTable
            tableTabs={tableTabs}
            selected={selected}
            mode={IndexFiltersMode.Default}
            onSelect={handleSelectedTab}
            disambiguateLabel={disambiguateLabel}
            sortOptions={sortOptions}
            onRowClick={handleRowClick}
            resourceName={resourceName}
            useNewRow={true}
            headers={headers}
            headings={headings}
            data={data[selectedTab]}
            filters={[]}
        />,
        <TestsFlyLayout data={selectedTest} setShowDetails={setShowDetails} showDetails={showDetails} ></TestsFlyLayout>
    ]


    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    titleText={"Tests"}
                />
            }
            isFirstPage={true}
            components={components}
        />

    )
}


export default TestsTablePage;
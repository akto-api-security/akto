import { Avatar, Badge, Box, HorizontalStack, IndexFiltersMode, List, Text, Tooltip } from "@shopify/polaris";
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

const sortOptions = [
    { label: 'Severity', value: 'severity asc', directionLabel: 'Highest', sortKey: 'severityVal', columnIndex: 2 },
    { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest', sortKey: 'severityVal', columnIndex: 2 },
    { label: 'Test', value: 'test asc', directionLabel: 'A-Z', sortKey: 'name', columnIndex: 1 },
    { label: 'Test', value: 'test desc', directionLabel: 'Z-A', sortKey: 'name', columnIndex: 1 },
];

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
                        <List.Item><Text as="span" fontWeight="medium">Critical</Text> -  <Text as="span" color="subdued">Immediate action; exploitable with severe impact</Text></List.Item>
                        <List.Item><Text as="span" fontWeight="medium">High</Text> - <Text as="span" color="subdued">Urgent action; significant security risk</Text></List.Item>
                        <List.Item><Text as="span" fontWeight="medium">Medium</Text> -  <Text as="span" color="subdued">Moderate risk; potential for exploitation</Text></List.Item>
                        <List.Item><Text as="span" fontWeight="medium">Low</Text> -  <Text as="span" color="subdued">Minor concerns; limited impact</Text></List.Item>
                        <List.Item><Text as="span" fontWeight="medium">Dynamic Severity</Text> -  <Text as="span" color="subdued">Severity changes based on API context</Text></List.Item>
                    </List>
                }
            />
        ),
        text: "Severity",
        value: "severity",
        textValue: "severity",
        sortKey: "severityVal",
        sortActive: true,
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
    },
    {
        title: "Author",
        text: "Author",
        value: "author",
        showFilter: true,
        filterKey: "author",
    }
];
const filterOptions = [
    {
        key: 'testingMethods',
        label: "Testing Methods",
        title: "Testing Methods",
        choices: [
            {label: 'Intrusive', value: "Intrusive"},
            {label: 'Non intrusive', value: "Non intrusive"},
        ]
    },
    {
        key: 'severityText',
        label: "Severity",
        title: "Severity",
        choices: [
            {label: 'Critical', value: "CRITICAL"},
            {label: 'High', value: "HIGH"},
            {label: 'Medium', value: "MEDIUM"},
            {label: 'Low', value: "LOW"},
            {label: 'Dynamic Severity', value: "DYNAMIC SEVERITY"},
        ]
    }
]

let headers = JSON.parse(JSON.stringify(headings))

function TestsTablePage() {
    const [selectedTest, setSelectedTest] = useState({})
    const [data, setData] = useState({ 'all': [], 'by_akto': [], 'custom': [] })
    const localSubCategoryMap = LocalStore.getState().subCategoryMap

    const severityOrder = { CRITICAL: 5, HIGH: 4, MEDIUM: 3, LOW: 2, dynamic_severity: 1 };

    const mapTestData = (obj) => {
        const allData = [], customData = [], aktoData = [];
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
            }
            if (value.isCustom) {
                customData.push(data)
            }
            else {
                aktoData.push(data)
            }
            allData.push(data)
        });
        return [allData, aktoData, customData]
    };

    const fetchAllTests = async () => {
        try {
            let metaDataObj = {
                subCategories: [],
            }
            if ((localSubCategoryMap && Object.keys(localSubCategoryMap).length > 0)) {
                metaDataObj = {
                    subCategories: Object.values(localSubCategoryMap),
                }
                
            } else { 
                metaDataObj = await transform.getAllSubcategoriesData(true, "runTests")
            }
            if (!metaDataObj?.subCategories?.length) return;

            const obj = convertFunc.mapCategoryToSubcategory(metaDataObj.subCategories);
            const [allData, aktoData, customData] = mapTestData(obj);
            setData({
                all: allData,
                by_akto: aktoData,
                custom: customData
            });
        } catch (error) {
            console.error("Error fetching tests:", error);
        }
    };

    useEffect(() => {
        fetchAllTests()
    }, [])


    const resourceName = {
        singular: 'test',
        plural: 'tests',
    };

    const [showDetails, setShowDetails] = useState(false)

    const handleRowClick = (data) => {
        setShowDetails(true)
        setSelectedTest(data);
    };

    const [selected, setSelected] = useState(0)
    const [selectedTab, setSelectedTab] = useState('all')

    const { tabsInfo } = useTable()
    const definedTableTabs = ['All', 'By Akto', 'Custom'];
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
            filters={filterOptions}
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
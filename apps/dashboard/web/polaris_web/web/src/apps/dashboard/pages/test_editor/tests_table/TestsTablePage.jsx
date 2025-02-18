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
        maxWidth: "460px",
        type: "TEXT"
    },
    {
        title: (
            <HeadingWithTooltip
                title={"Severity"}
                content={
                    <List>
                        <List.Item><span style={{ fontWeight: "550" }}>Critical</span> - Immediate action; exploitable with severe impact</List.Item>
                        <List.Item><span style={{ fontWeight: "550" }}>High</span> - Urgent action; significant security risk</List.Item>
                        <List.Item><span style={{ fontWeight: "550" }}>Medium</span> - Moderate risk; potential for exploitation</List.Item>
                        <List.Item><span style={{ fontWeight: "550" }}>Low</span> - Minor concerns; limited impact</List.Item>
                        <List.Item><span style={{ fontWeight: "550" }}>Dynamic Severity</span> - Severity changes based on API context</List.Item>
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
    },
    {
        title: "Compliance",
        text: "Compliance",
        value: "compliance",
    },
    {
        title: "Author",
        text: "Author",
        value: "author",
        showFilter: true,
        filterKey: "author",
    }
];

let headers = JSON.parse(JSON.stringify(headings))

function TestsTablePage() {
    const [selectedTest, setSelectedTest] = useState({})
    const [data, setData] = useState({ 'all': [], 'by_akto': [], 'custom': [] })

    const severityOrder = { CRITICAL: 5, HIGH: 4, MEDIUM: 3, LOW: 2, dynamic_severity: 1 };

    const mapTestData = (obj) => {
        const allData = [], customData = [], aktoData = [];
        Object.entries(obj.mapTestToData).map(([key, value]) => {
            const data = {
                name: key,
                tests: key,
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
                testingMethods: value.nature.length ? func.toSentenceCase(value.nature.replace(/_/g, " ")) : "",
                severityVal: severityOrder[value.severity] || 0,
                content: value.content,
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
            const metaDataObj = await transform.getAllSubcategoriesData(false, "testEditor");
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
        setTimeout(() => {
            setTableLoading(false)
        }, 200)
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
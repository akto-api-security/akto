import { Badge, Button, IndexFiltersMode, Text } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../../components/shared/TitleWithInfo";

import transform from "../../testing/transform"
import func from "../../../../../util/func"
import convertFunc from ".././transform"
import { useEffect, useState } from "react";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import useTable from "../../../components/tables/TableContext";
import JsonComponent from "../../quick_start/components/shared/JsonComponent";
import TestsFlyLayout from "./TestsFlyLayout";

const sortOptions = [
    { label: 'Severity', value: 'severity asc', directionLabel: 'Highest', sortKey: 'severityVal', columnIndex: 2 },
    { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest', sortKey: 'severityVal', columnIndex: 2 },
];

function TestsTable() {
    const [selectedTest, setSelectedTest] = useState({})
    const [data, setData] = useState({ 'all': [], 'by_akto': [], 'custom': [] })

    const fetchAllTests = async () => {
        const metaDataObj = await transform.getAllSubcategoriesData(false, "testEditor")
        const subCategories = metaDataObj.subCategories;
        console.log(subCategories)
        

        if (subCategories && subCategories.length > 0) {
            try {
                const obj = convertFunc.mapCategoryToSubcategory(subCategories)
                const severityOrder = {'CRITICAL': 4 ,'HIGH': 3, 'MEDIUM': 2, 'LOW': 1 }; 
                console.log("test-editor", obj)
                const allData = [], customData = [], aktoData = [];
                Object.entries(obj.mapTestToData).forEach(([key, value]) => {
                    const data = {
                        tests: (<Text fontWeight="medium" variant="headingSm" as="h5" truncate>{key}</Text>),
                        severityText: value.severity,
                        severity: value.severity.length > 1 ?
                            <div className={`badge-wrapper-${value.severity}`}>
                                <Badge status={func.getHexColorForSeverity(value.severity)}>{func.toSentenceCase(value.severity)}</Badge>
                            </div> : "",
                        category: value.category,
                        author: func.toSentenceCase(value.author),
                        testingMethods: value.nature.length? func.toSentenceCase(value.nature.replace(/_/g, " ")):"",
                        severityVal: severityOrder[value.severity],
                        content: value.content,
                        value:value.value
                    }
                    if(value.isCustom){
                        customData.push(data)
                    }
                    else{
                        aktoData.push(data)
                    }
                    allData.push(data)
                })
                setData(prevData => ({
                    all: [...allData],
                    by_akto: [...aktoData],
                    custom: [...customData]
                }));
                console.log("data", data)
            } catch (error) {
                console.log("Error in fetching tests", error)
            }

        }
    }
    useEffect(() => {
        fetchAllTests()
    }, [])


    const headings = [
        {
            title: "Test",
            text: "tests",
            value: "tests",
            textValue: "tests",
            sortActive: true,

        },
        {
            title: "Severity",
            text: "severity",
            value: "severity",
            textValue: "severity",
            sortKey: "severityVal",
            sortActive: true
        },
        {
            title: "Category",
            text: "category",
            value: "category",
        },
        {
            title: "Testing Methods",
            text: "testingMethods",
            value: "testingMethods",
        },
        {
            title: "Compliance",
            text: "compliance",
            value: "compliance",
        },
        {
            title: "Author",
            text: "author",
            value: "author",
        }
    ]

    let headers = JSON.parse(JSON.stringify(headings))

    const resourceName = {
        singular: 'test',
        plural: 'tests',
    };

    const handleRowClick = (data) => {
        setShowDetails(true)
        setSelectedTest(data);
    };

    const [selected, setSelected] = useState(0)
    const [tableLoading, setTableLoading] = useState(false)
    const [selectedTab, setSelectedTab] = useState('all')

    const { tabsInfo } = useTable()
    const definedTableTabs = ['All', 'By Akto', 'Custom'];
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)


    const handleSelectedTab = (selectedIndex) => {
        setTableLoading(true)
        setSelected(selectedIndex)
        setTimeout(() => {
            setTableLoading(false)
        }, 200)
    }
    const [showDetails, setShowDetails] = useState(false)


    const components = [
        <GithubSimpleTable
            tableTabs={tableTabs}
            loading={tableLoading}
            selected={selected}
            mode={IndexFiltersMode.Default}
            onSelect={handleSelectedTab}
            sortOptions={sortOptions}
            onRowClick={handleRowClick}
            resourceName={resourceName}
            useNewRow={true}
            headers={headers}
            headings={headings}
            data={data[selectedTab]}
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


export default TestsTable;
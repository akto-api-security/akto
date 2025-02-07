import { Button, HorizontalStack, Icon, IndexFiltersMode, Text, TextField } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import TitleWithInfo from "../../../components/shared/TitleWithInfo"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import { useEffect, useState } from "react"
import FlyLayoutSuite from "./FlyLayoutSuite"
import LocalStore from "../../../../main/LocalStorageStore";
import useTable from "../../../components/tables/TableContext"
import func from "../../../../../util/func"
import ShowListInBadge from "../../../components/shared/ShowListInBadge"
import transform from "../../testing/transform";



const sortOptions = [
    { label: 'Template Name', value: 'template asc', directionLabel: 'A-Z', sortKey: 'name', columnIndex: 1 },
    { label: 'Template Name', value: 'template desc', directionLabel: 'Z-A', sortKey: 'name', columnIndex: 1 },
];


const owaspTop10List = {
    "Broken Object Level Authorization": ["BOLA"],
    "Broken Authentication": ["NO_AUTH"],
    "Broken Object Property Level Authorization": ["EDE", "MA"],
    "Unrestricted Resource Consumption": ["RL"],
    "Broken Function Level Authorization": ["BFLA"],
    "Unrestricted Access to Sensitive Business Flows": ["INPUT"],
    "Server Side Request Forgery": ['SSRF'],
    "Security Misconfiguration": ["SM", "UHM", "VEM", "MHH", "SVD", "CORS", "ILM"],
    "Improper Inventory Management": ["IAM", "IIM"],
    "Unsafe Consumption of APIs": ["COMMAND_INJECTION", "INJ", "CRLF", "SSTI", "LFI", "XSS", "INJECT"]
}

function TestSuite() {
    const [show, setShow] = useState(false)
    const [data, setData] = useState({ 'all_templates': []})
    const [selectedTab, setSelectedTab] = useState('all_templates')
    const customTestSuiteData = [...LocalStore.getState().customTestSuites];
    const [selectedTestSuite, setSelectedTestSuite] = useState({})

    const { tabsInfo } = useTable()
    const definedTableTabs = ['All templates'];
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)



    const headings = [
        {
            title: "Template name",
            text: "template_name",
            value: "name",
            textValue: "name",
            sortActive: true,
        },
        {
            title: "Total tests",
            text: "testCount",
            value: "testCount",
            textValue: "testCount",
        },
        {
            title: "Categories covered",
            text: "categoriesCovered",
            value: "categoriesCovered",
        },
    ]

    let headers = JSON.parse(JSON.stringify(headings))

    const resourceName = {
        singular: 'testSuite',
        plural: 'testSuites',
    };

    const handleRowClick = (data) => {
        setShow(true)
        setSelectedTestSuite(data);
    };

    const fetchData = async () => {
        const listData = owaspTop10List;
        const metaDataObj = await transform.getAllSubcategoriesData(true, "runTests")
        const subCategoryMap = {};
        metaDataObj.subCategories.forEach(subCategory => {
            if (!subCategoryMap[subCategory?.superCategory?.name]) {
                subCategoryMap[subCategory.superCategory?.name] = [];
            }
            let obj = {
                label: subCategory.testName,
                value: subCategory.name,
                author: subCategory.author,
            }
            subCategoryMap[subCategory.superCategory?.name].push(obj);
        });
        const updatedData = [];
        let id = 1;
        Object.entries(listData).forEach(([key, value]) => { 
            const categoryTests = [];
            value.forEach(cat => {
                subCategoryMap[cat]?.forEach(test => {
                    categoryTests.push(test.value);
                });
            });
            updatedData.push({
                testSuiteName: key,
                name: (<Text fontWeight="semibold">{key}</Text>),
                id: id++,
                testCount: categoryTests.length,
                tests: categoryTests,
                categoriesCovered: (
                    <ShowListInBadge
                        itemsArr={[...value]}
                        maxItems={4}
                        maxWidth={"250px"}
                        status={"new"}
                        itemWidth={"200px"}
                    />
                )
            });
        });
        const newData = {
            "all_templates": [...updatedData],
        }
        if (JSON.stringify(data) !== JSON.stringify(newData)) {
            setData(prevData => ({
                ...prevData,
                all_templates: [...updatedData], 
            }));
        }
        if (selectedTab !== "all_templates") { 
            setSelectedTab("all_templates"); 
        }

    };

    useEffect(() => {
        fetchData();
    }, [])


    const [selected, setSelected] = useState(0)
    const [tableLoading, setTableLoading] = useState(false)
    const handleSelectedTab = (selectedIndex) => {
        setTableLoading(true)
        setSelected(selectedIndex)
        setTimeout(() => {
            setTableLoading(false)
        }, 200)
    }

    const components = [
        <GithubSimpleTable
            sortOptions={sortOptions}
            tableTabs={tableTabs}
            loading={tableLoading}
            selected={selected}
            mode={IndexFiltersMode.Default}
            onSelect={handleSelectedTab}
            onRowClick={handleRowClick}
            resourceName={resourceName}
            useNewRow={true}
            headers={headers}
            headings={headings}
            data={data[selectedTab]}
        />,
        <FlyLayoutSuite
            selectedTestSuite={selectedTestSuite}
            setSelectedTestSuite={setSelectedTestSuite}
            customTestSuiteData={customTestSuiteData}
            show={show}
            setShow={setShow}
        />
    ]

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"Akto automatically groups similar APIs into meaningful collections based on their subdomain names. "}
                    titleText={"Test Suites"}
                    docsUrl={"https://docs.akto.io/api-inventory/concepts"}
                />
            }
            components={components}
        >

        </PageWithMultipleCards>
    )

}

export default TestSuite
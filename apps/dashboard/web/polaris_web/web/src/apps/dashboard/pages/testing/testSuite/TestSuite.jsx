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
import transform from "./transform";


const sortOptions = [
    { label: 'Template Name', value: 'template asc', directionLabel: 'A-Z', sortKey: 'testSuiteName', columnIndex: 1 },
    { label: 'Template Name', value: 'template desc', directionLabel: 'Z-A', sortKey: 'testSuiteName', columnIndex: 1 },
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
    const [data, setData] = useState({ 'all_templates': [] })
    const [selectedTab, setSelectedTab] = useState('all_templates')
    const [selectedTestSuite, setSelectedTestSuite] = useState({})

    const localCategoryMap = LocalStore.getState().categoryMap
    const localSubCategoryMap = LocalStore.getState().subCategoryMap

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
        const subCategoryMap = await transform.getSubCategoryMap(localCategoryMap);
        const updatedData = [];
        let id = 1;
        Object.entries(listData).forEach(([key, value]) => {
            const testSuiteSubCategoryMap = [];
            let count = 0;
            value.forEach(cat => {
                if (!subCategoryMap[cat] || !Array.isArray(subCategoryMap[cat]) || subCategoryMap[cat].length === 0) return;
                subCategoryMap[cat].forEach(test => { testSuiteSubCategoryMap.push(test.value) });
            });

            updatedData.push({
                tests: testSuiteSubCategoryMap,
                testSuiteName: key,
                name: (<Text variant="headingSm" fontWeight="medium" as="h2">{key}</Text>),
                id: id++,
                testCount: testSuiteSubCategoryMap.length,
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
        
        setData(prevData => ({
            ...prevData,
            all_templates: [...updatedData],
        }));
        if (selectedTab !== "all_templates") {
            setSelectedTab("all_templates");
        }

    };

    useEffect(() => {
       fetchData()
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
            show={show}
            setShow={setShow}
            localSubCategoryMap={localSubCategoryMap}
        />

    ]

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"Akto categorizes tests by OWASP Top 10 vulnerabilities, offering insights into common security issues and facilitating efficient resolution. "}
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
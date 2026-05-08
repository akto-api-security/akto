import { Button, IndexFiltersMode } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import TitleWithInfo from "../../../components/shared/TitleWithInfo"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import { useEffect, useState } from "react"
import FlyLayoutSuite from "./FlyLayoutSuite"
import LocalStore from "../../../../main/LocalStorageStore";
import useTable from "../../../components/tables/TableContext"
import func from "../../../../../util/func"
import transform from "./transform";
import api from "../api"
import { CellType } from "../../../components/tables/rows/GithubRow"
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper"


function TestSuite() {
    const [show, setShow] = useState(false)
    const [data, setData] = useState({ 'all': [], 'by_akto': [], 'custom': [] })
    const [selectedTab, setSelectedTab] = useState('by_akto')
    const [selectedTestSuite, setSelectedTestSuite] = useState({})
    const [createNewMode, setCreateNewMode] = useState(false)
    const localSubCategoryMap = LocalStore.getState().subCategoryMap

    const { tabsInfo } = useTable()
    const [selected, setSelected] = useState(1)
    const definedTableTabs = ['All', 'By Akto', 'Custom'];
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)



    const headings = [
        {
            title: "Template name",
            text: "template_name",
            value: "name",
            textValue: "name",
            sortActive: true,
            boxWidth: "465px",
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
            boxWidth: "320px",
        },
        {
            title: "Author",
            text: "Author",
            value: "author",
            showFilter: true,
            filterKey: "author",
        },
        {
            title: '',
            type: CellType.ACTION,
        }
    ]

    const resourceName = {
        singular: mapLabel('test', getDashboardCategory()) + ' suite',
        plural: mapLabel('test', getDashboardCategory()) + ' suites',
    };

    const handleRowClick = (data) => {
        setShow(true)
        setSelectedTestSuite(data);
    };

    const fetchData = async () => {
        const subCategoryMap = await transform.getSubCategoryMap(LocalStore);
        let all = [], by_akto = [], custom = [];

        // Get dashboard category using the existing helper function
        const dashboardCategory = getDashboardCategory();

        const fetchedData = await api.fetchAllTestSuites();

        // Process default test suites from backend and filter based on account type
        fetchedData?.defaultTestSuites?.forEach((testSuiteItem) => {
            const categoriesCoveredList = [];
            const testSet = new Set(testSuiteItem?.subCategoryList||[]);
            Object.entries(subCategoryMap).forEach(([key, value]) => {
                if (value.some(test => testSet.has(test.value))) {
                    categoriesCoveredList.push(key);
                }
            });

            // Filter based on dashboard category using suiteType
            let shouldInclude = false;

            if (dashboardCategory === 'MCP Security') {
                // For MCP Security, only show test suites with MCP_SECURITY suiteType
                shouldInclude = testSuiteItem.suiteType === 'MCP_SECURITY';
            } else if (dashboardCategory === 'Agentic Security') {
                // For Agentic Security, show test suites with both MCP_SECURITY and AI_AGENT_SECURITY suiteTypes
                shouldInclude = testSuiteItem.suiteType === 'MCP_SECURITY' || testSuiteItem.suiteType === 'AI_AGENT_SECURITY';
            } else {
                // For API Security, show test suites with OWASP suiteType (default API security)
                shouldInclude = testSuiteItem.suiteType === 'OWASP';
            }

            if (shouldInclude) {
                const aktoTestSuite = transform.getPrettifiedObj(testSuiteItem, categoriesCoveredList, true);
                all.push(aktoTestSuite);
                by_akto.push(aktoTestSuite);
            }
        });


        fetchedData?.testSuiteList?.forEach((testSuiteItem) => {
            const categoriesCoveredList = [];
            const testSet = new Set(testSuiteItem?.subCategoryList||[]);
            Object.entries(subCategoryMap).forEach(([key, value]) => {
                if (value.some(test => testSet.has(test.value))) {
                    categoriesCoveredList.push(key);
                }
            });
            const customTestSuite = transform.getPrettifiedObj(testSuiteItem, categoriesCoveredList);
            all.push(customTestSuite);
            custom.push(customTestSuite);
        });

        // sort here by category priority
        all = func.sortByCategoryPriority(all, 'testSuiteName');
        by_akto = func.sortByCategoryPriority(by_akto, 'testSuiteName');
        custom = func.sortByCategoryPriority(custom, 'testSuiteName');

        setData({
            all,
            by_akto,
            custom,
        });

    };

    useEffect(() => {
       fetchData()
    }, [])


    const [tableLoading, setTableLoading] = useState(false)
    const handleSelectedTab = (selectedIndex) => {
        setTableLoading(true)
        setSelected(selectedIndex)
        setTimeout(() => {
            setTableLoading(false)
        }, 200)
    }

    const deleteTestSuite = async (e,item) => {
        if(e.stopPropagation){
            e.stopPropagation();
        }
        api.deleteTestSuite(item.id).then(() => {
            func.setToast(true, false, "Test suite deleted successfully")
            fetchData();
        }
        ).catch((error) => {
            func.setToast(true, true, "Failed to delete test suite. Please try again.");
        });
    }

    const getActions = (item) => {
        return [{
            items: [
                {
                    content: <Button disabled={item?.isAutoGenerated} destructive plain removeUnderline onClick={(e)=>deleteTestSuite(e,item)}>Delete</Button>,
                    disabled: item?.isAutoGenerated,
                }]
        }]
    }

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabel(value, func.toSentenceCase, 2)
    }
    

    const components = [
        <GithubSimpleTable
            key={"test-suite-table"}
            tableTabs={tableTabs}
            loading={tableLoading}
            selected={selected}
            onSelect={handleSelectedTab}
            onRowClick={handleRowClick}
            disambiguateLabel={disambiguateLabel}
            resourceName={resourceName}
            useNewRow={true}
            headers={headings}
            headings={headings}
            data={data[selectedTab]}
            getActions={getActions}
            hasRowActions={true}
            mode={IndexFiltersMode.Default}
        />,

        <FlyLayoutSuite
            key={"fly-layout-suite"}
            selectedTestSuite={selectedTestSuite}
            setSelectedTestSuite={setSelectedTestSuite}
            show={show}
            setShow={setShow}
            localSubCategoryMap={localSubCategoryMap}
            fetchTableData={fetchData}
            createNewMode={createNewMode}
            setCreateNewMode={setCreateNewMode}
        />

    ]

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"Create or manage custom test suites by combining tests across categories for simplified test execution and reusability."}
                    titleText={mapLabel("Test", getDashboardCategory()) + " Suites"}
                    docsUrl={"https://docs.akto.io/api-security-testing/concepts/test"}
                />
            }
            components={components}
            primaryAction={<Button onClick={()=>{setShow(true); setSelectedTestSuite(null); setCreateNewMode(true)}} primary>Create New</Button>}
        />
    )

}

export default TestSuite
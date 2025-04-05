import { useEffect, useState } from "react"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import api from "../api"
import { Box, Button, IndexFiltersMode } from "@shopify/polaris"
import { useNavigate } from "react-router-dom"
import func from "@/util/func"
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout"
import { ROLES_PAGE_DOCS_URL } from "../../../../main/onboardingData"
import { CellType } from "../../../components/tables/rows/GithubRow"
import TitleWithInfo from "../../../components/shared/TitleWithInfo"
import useTable from "../../../components/tables/TableContext"
import TooltipText from "../../../components/shared/TooltipText"


const sortOptions = [
    { label: 'Created at', value: 'created asc', directionLabel: 'Highest', sortKey: 'createdTs', columnIndex: 2 },
    { label: 'Created at', value: 'created desc', directionLabel: 'Lowest', sortKey: 'createdTs', columnIndex: 2 },
];

const headers = [
    {
        title:"Test Role",
        text:"Name",
        value:"nameComp",
    },
    {
        title:"Created",
        text:"Created at",
        value:"createdAt",
        sortKey:"createdTs",
        sortActive:true,
    },
    {
        title:"Author",
        text:"Created by",
        value:"createdBy",
        showFilter:true,
        filterKey:"createdBy",
    },
    {
        title: "No. of Auths",
        value : "numAuths",
    },
    {
        title: "Type of Auth",
        value : "authType",
    },    
    {
        title:"No. of tests",
        value:"testCountForRole",
    },
    {
        title: '',
        type: CellType.ACTION,
    }
]

const resourceName = {
    singular: 'test role',
    plural: 'test roles',
};

function TestRolesPage(){

    const [loading, setLoading] = useState(false);
    const [showEmptyScreen, setShowEmptyScreen] = useState(false)
    const navigate = useNavigate()

    const [data, setData] = useState({ 'all': [], 'system': [], 'custom': []})

    const handleRedirect = () => {
        navigate("details")
    }

    const deleteTestRole = async (item, e) => {
        if (e.stopPropagation) e.stopPropagation()
        const message = `This will permanently delete the ${item?.name||""} role. Do you want to continue?`
        func.showConfirmationModal(message, "Delete test role", async () => { await api.deleteTestRole(item.name); setLoading(true); fetchData(); func.setToast(true, false, "Test role has been deleted successfully.") })
    }

    const handleAccessMatrix = (item) => {
        navigate("access-matrix", {state: {
            name: item.name,
            endpoints: item.endpointLogicalGroup.testingEndpoints,
            authWithCondList: item.authWithCondList
        }})
    }

    const getActions = (item) => {

        const actionItems = [{
            items: [
                {
                    content:<Button monochrome plain onClick={()=>handleAccessMatrix(item)} removeUnderline>Access matrix</Button>,
                }
            ]
        }]

        // if(item.name !== 'ATTACKER_TOKEN_ALL') {
        if(item.createdBy !== 'System') {
            const removeActionItem = {
                content: <Button plain onClick={(e)=>deleteTestRole(item,e)} destructive removeUnderline>Delete</Button>,
            }
            actionItems[0].items.push(removeActionItem)
        }

        return actionItems
    }

    function checkAuthType(item){
        let authSet = new Set();
        item.authWithCondList?.forEach((auth) => {
            let type = auth?.authMechanism?.type.toLowerCase();
            if(type === 'login_request') type = 'automated'
            authSet.add(func.capitalizeFirstLetter(type))
        })
        let authType = Array.from(authSet).join(" & ")
        return authType.length === 0? '-' : authType
    }

    async function fetchData(){
        await api.fetchTestRoles().then((res) => {
            console.log(res)
            setShowEmptyScreen(res.testRoles.length === 0)
            const all = [], system = [], custom = []
            res.testRoles.forEach((testRole) => {
                testRole.timestamp = func.prettifyEpoch(testRole.lastUpdatedTs)
                testRole.id=testRole.name;
                testRole.createdAt = func.prettifyEpoch(testRole.createdTs)
                testRole.nameComp = (<Box maxWidth="40vw"><TooltipText tooltip={testRole.name} text={testRole.name} textProps={{fontWeight: 'medium'}}/></Box>)
                testRole.numAuths = testRole?.authWithCondList?.length||0
                testRole.authType = checkAuthType(testRole)
                all.push(testRole)
                if(testRole.createdBy === 'System') {
                    system.push(testRole)
                } else {
                    custom.push(testRole)
                }
            })
            setData({ 'all': all, 'system': system, 'custom': custom})
            setLoading(false);
        })
    }
    const [selected, setSelected] = useState(0)
    const [selectedTab, setSelectedTab] = useState('all')
    const { tabsInfo } = useTable()
    const definedTableTabs = ['All', 'System', 'Custom'];
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)


    useEffect(() => {
        setLoading(true);
        fetchData();
    }, [])

    const onTestRoleClick = (item) => navigate("details", {state: {
        name: item.name,
        endpoints: item?.endpointLogicalGroup?.testingEndpoints || [],
        authWithCondList: item?.authWithCondList || []
    }})

    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex)
    }

    return (
        <PageWithMultipleCards
            title={<TitleWithInfo
                titleText={"Test roles"}
                tooltipContent={"Test roles define specific access permissions and authentication methods for API security testing scenarios."}
            />}
        primaryAction = {<Button primary onClick={handleRedirect}><div data-testid="new_test_role_button">Create new test role</div></Button>}
        isFirstPage={true}
        components={[
            showEmptyScreen ? 
                <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/file_check.svg"}
                    headingText={"Define your Test Roles"}
                    description={"No test role to show yet. Create one now to test for role specific vulnerabilities such as BOLA or privilege escalation."}
                    buttonText={"Create test role"}
                    redirectUrl={"/dashboard/testing/roles/details"}
                    learnText={"Creating test roles"}
                    docsUrl={ROLES_PAGE_DOCS_URL}
                />

            
            :    <GithubSimpleTable
                    key="table"
                    selected={selected}
                    data={data[selectedTab]}
                    disambiguateLabel={(key,value) => func.convertToDisambiguateLabelObj(value, null, 2)}
                    onSelect={handleSelectedTab}
                    mode={IndexFiltersMode.Default}
                    tableTabs={tableTabs}
                    resourceName={resourceName} 
                    headers={headers}
                    headings={headers}
                    loading={loading}
                    onRowClick={onTestRoleClick}
                    getActions={getActions}
                    hasRowActions={true}
                    useNewRow={true}
                    sortOptions={sortOptions}
                />
        ]}
        />
    )
}

export default TestRolesPage
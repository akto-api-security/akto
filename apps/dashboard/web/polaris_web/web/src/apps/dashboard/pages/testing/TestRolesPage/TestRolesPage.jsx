import { useEffect, useState } from "react"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import api from "../api"
import { Button } from "@shopify/polaris"
import { useNavigate } from "react-router-dom"
import func from "@/util/func"
import {
    ProfileMinor,
    CalendarMinor
  } from '@shopify/polaris-icons';
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout"
import { ROLES_PAGE_DOCS_URL } from "../../../../main/onboardingData"

const headers = [
    {
        text:"Name",
        value:"name",
        itemOrder:1
    },
    {
        text:"Last updated",
        value:"timestamp",
        itemOrder: 3,
        icon:CalendarMinor
    },
    {
        text:"Created by",
        value:"createdBy",
        itemOrder: 3,
        icon:ProfileMinor
    }
]

const resourceName = {
    singular: 'test role',
    plural: 'test roles',
};

function TestRolesPage(){

    const [testRoles, setTestRoles] = useState([]);
    const [loading, setLoading] = useState(false);
    const [showEmptyScreen, setShowEmptyScreen] = useState(false)
    const navigate = useNavigate()

    const handleRedirect = () => {
        navigate("details")
    }


    const getActions = (item) => {

        return [{
            items: [
                {
                    content: 'Edit',
                    onAction: () => navigate("details", {state: {
                        name: item.name,
                        endpoints: item.endpointLogicalGroup.testingEndpoints,
                        authWithCondList: item.authWithCondList
                    }})
                },
                {
                    content: 'Access matrix',
                    onAction: () => navigate("access-matrix", {state: {
                        name: item.name,
                        endpoints: item.endpointLogicalGroup.testingEndpoints,
                        authWithCondList: item.authWithCondList
                    }})
                }
            ]
        }]
    }

    useEffect(() => {
        setLoading(true);
        
        async function fetchData(){
            await api.fetchTestRoles().then((res) => {
                setShowEmptyScreen(res.testRoles.length === 0)
                setTestRoles(res.testRoles.map((testRole) => {
                    testRole.timestamp = func.prettifyEpoch(testRole.lastUpdatedTs)
                    testRole.id=testRole.name;
                    return testRole;
                }));
                setLoading(false);
            })
        }
        fetchData();
    }, [])

    return (
        <PageWithMultipleCards
        title={"Test roles"}
        primaryAction = {<Button primary onClick={handleRedirect}>Create new test role</Button>}
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
                    data={testRoles} 
                    resourceName={resourceName} 
                    headers={headers}
                    loading={loading}
                    getActions={getActions}
                    hasRowActions={true}
                />
        ]}
        />
    )
}

export default TestRolesPage
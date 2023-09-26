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
    const navigate = useNavigate()

    const handleRedirect = () => {
        navigate("details")
    }

    const getActions = (item) => {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => navigate("details", {state: {name: item.name ,endpoints: item.endpointLogicalGroup.testingEndpoints}}),
            },
            {
                content: 'Access matrix',
                onAction: () => console.log("access matrix")
            },
            {
                content: 'Validate',
                onAction: () => console.log("validate")
            }]
        }]
    }

    useEffect(() => {
        setLoading(true);
        
        async function fetchData(){
            await api.fetchTestRoles().then((res) => {
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
            <GithubSimpleTable
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
import { useEffect, useState } from "react"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import api from "../api"

const headers = [
    {
        text:"Name",
        value:"name",
        itemOrder:1
    },
]

const resourceName = {
    singular: 'test role',
    plural: 'test roles',
};

function TestRolesPage(){

    const [testRoles, setTestRoles] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        setLoading(true);
        
        async function fetchData(){
            await api.fetchTestRoles().then((res) => {
                setTestRoles(res.testRoles.map((testRole) => {
                    testRole.id=testRole.name;
                    testRole.nextUrl="settings"
                    return testRole;
                }));
            })
        }
        fetchData();
        setLoading(false);
    }, [])

    return (
        <PageWithMultipleCards
        title={"Test roles"}
        components={[
            <GithubSimpleTable
            key="table"
            data={testRoles} 
            resourceName={resourceName} 
            headers={headers}
            loading={loading}
            />
        ]}
        />
    )
}

export default TestRolesPage
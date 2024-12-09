import { Box, Button, HorizontalGrid, IndexFiltersMode, LegacyCard, ResourceItem, ResourceList, Text, TextField } from '@shopify/polaris'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import { useLocation } from 'react-router-dom'
import api from '../api'
import func from "@/util/func";
import { useEffect } from 'react';
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable';
import { useState } from 'react';

function TestRoleAccessMatrix() {
    const location = useLocation()
    const [name, setName] = useState(location?.state?.name || null);

    const [roleToUrls, setRoleToUrls] = useState([])

    const fetchRoleToUrls = async () => {
        if (name !== null) {
            const response = await api.fetchAccessMatrixUrlToRoles()
            const accessMatrixRolesToUrls = response.accessMatrixRoleToUrls
            const accessMatrixRoleToUrls = accessMatrixRolesToUrls[name] ? accessMatrixRolesToUrls[name] : [] 
            setRoleToUrls(accessMatrixRoleToUrls)
        }
    }

    useEffect(() => {
        fetchRoleToUrls()
    }, [])

    const handleCreateAccessMatrix = async () => {
        if (name !== null) {
            await api.createMultipleAccessMatrixTasks(name)
            func.setToast(true, false, "Access matrix for test role created successfully")
        }
    }

    const handleDeleteAccessMatrix = async () => {
        if (name !== null) {
            await api.deleteAccessMatrix(name)
            func.setToast(true, false, "Access matrix for test role deleted successfully")
        }
    }

    const headers = [
        {
            title: 'Method',
            value: 'method',
        },
        {
            title: 'Url',
            value: 'url'
        }
    ]

    const resourceName = {
        singular: 'apiEndpoint',
        plural: 'apiEndpoints',
    };

    const accessMatrixComponent = (
        <LegacyCard>
            {roleToUrls.length === 0 ?
                <LegacyCard.Section>
                    <Text>Access matrix empty or not created.</Text>
                </LegacyCard.Section> :
                <GithubSimpleTable
                    headers={headers}
                    pageLimit={10}
                    data={roleToUrls}
                    resourceName={resourceName}
                    headings={headers}
                    useNewRow={true}
                    condensedHeight={true}
                    hideQueryField={true}
                />
            }
        </LegacyCard>
    )

    const components = [
        <div key="access-matrix">{accessMatrixComponent}</div>
    ]

    return (
        <PageWithMultipleCards
            backUrl='/dashboard/testing/roles'
            title={`Access Matrix - ${name}`}
            components={components}
            divider={true}
            secondaryActions={
                [
                    <Button key="deleteAccessMatrix" onClick={handleDeleteAccessMatrix}>Delete access matrix</Button>,
                    <Button key="createAccessMatrix" primary onClick={handleCreateAccessMatrix}>Create access matrix</Button>
                ]}
        />
    )
}

export default TestRoleAccessMatrix
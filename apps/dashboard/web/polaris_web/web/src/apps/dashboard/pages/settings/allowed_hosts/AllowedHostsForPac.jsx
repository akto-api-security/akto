import { Button, Text, TextField, VerticalStack, Card, Form } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import settingRequests from '../api'
import func from '@/util/func'
import { CellType } from '../../../components/tables/rows/GithubRow'

function validateHost(host) {
    if (!host || host.trim() === '') {
        return 'Host cannot be empty'
    }
    return null
}

const headers = [
    {
        text: 'Host',
        title: 'Host',
        value: 'hostValue',
        type: CellType.TEXT,
    },
    {
        text: 'Created By',
        title: 'Created By',
        value: 'addedBy',
        type: CellType.TEXT,
    },
    {
        text: 'Last Updated',
        title: 'Last Updated',
        value: 'updatedTsFormatted',
        type: CellType.TEXT,
    },
]

const resourceName = {
    singular: 'host',
    plural: 'hosts',
}

function AllowedHostsForPac() {
    const [host, setHost] = useState('')
    const [hostError, setHostError] = useState('')
    const [loading, setLoading] = useState(false)
    const [tableData, setTableData] = useState([])
    const [fetchingData, setFetchingData] = useState(false)

    function buildTableData(allowedHostsForPac) {
        if (!allowedHostsForPac) return []
        const data = Object.entries(allowedHostsForPac).map(([key, info]) => ({
            hostValue: info.pattern || key,
            addedBy: info.addedBy || '-',
            updatedTsFormatted: info.updatedTs ? func.prettifyEpoch(info.updatedTs) : '-',
            updatedTs: info.updatedTs,
        }))
        data.sort((a, b) => b.updatedTs - a.updatedTs)
        return data
    }

    async function fetchData() {
        setFetchingData(true)
        try {
            const response = await settingRequests.fetchAdminSettings()
            setTableData(buildTableData(response?.accountSettings?.allowedHostsForPac))
        } catch (e) {
            func.setToast(true, true, 'Failed to load allowed hosts')
        } finally {
            setFetchingData(false)
        }
    }

    useEffect(() => {
        fetchData()
    }, [])

    function handleHostChange(value) {
        setHost(value)
        if (hostError) setHostError('')
    }

    async function handleAdd(value) {
        const error = validateHost(value)
        if (error) {
            setHostError(error)
            return
        }
        setLoading(true)
        try {
            const resp = await settingRequests.addAllowedHostForTac(value)
            setTableData(buildTableData(resp))
            setHost('')
            func.setToast(true, false, 'Host added successfully')
        } catch (e) {
            func.setToast(true, true, 'Failed to add host')
        } finally {
            setLoading(false)
        }
    }

    const inputCard = (
        <Card>
            <VerticalStack gap="3">
                <Text variant="headingSm" as="h3">Add Allowed Host</Text>
                <Text variant="bodyMd" color="subdued">
                    Add host patterns to ignore during traffic capture. Matching hosts will be excluded from TAC processing.
                </Text>
                <Form onSubmit={() => handleAdd(host)}>
                    <TextField
                        label="Host"
                        value={host}
                        onChange={handleHostChange}
                        placeholder="e.g. internal.example.com"
                        error={hostError || undefined}
                        autoComplete="off"
                        connectedRight={
                            <Button
                                primary
                                onClick={() => handleAdd(host)}
                                loading={loading}
                                disabled={!host.trim()}
                            >
                                Add
                            </Button>
                        }
                    />
                </Form>
            </VerticalStack>
        </Card>
    )

    const tableCard = (
        <GithubSimpleTable
            key="allowed-hosts-table"
            data={tableData}
            resourceName={resourceName}
            headers={headers}
            loading={fetchingData}
            hasRowActions={false}
            useNewRow={true}
            condensedHeight={true}
            headings={headers}
            pageLimit={15}
        />
    )

    return (
        <PageWithMultipleCards
            title="Allowed Hosts"
            isFirstPage={true}
            components={[inputCard, tableCard]}
        />
    )
}

export default AllowedHostsForPac

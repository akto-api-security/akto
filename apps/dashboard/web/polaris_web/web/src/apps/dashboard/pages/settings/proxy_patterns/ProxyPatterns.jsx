import { Button, Text, TextField, Banner, VerticalStack, Card, Form } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import settingRequests from '../api'
import settingFunctions from '../module'
import func from '@/util/func'
import { CellType } from '../../../components/tables/rows/GithubRow'
import { ToggleComponent } from '../about/About'

function validateRegex(pattern) {
    if (!pattern || pattern.trim() === '') {
        return 'Pattern cannot be empty'
    }
    return null
}

const headers = [
    {
        text: 'Value',
        title: 'Value',
        value: 'patternValue',
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
    singular: 'pattern',
    plural: 'patterns',
}

function ProxyPatterns() {
    const [pattern, setPattern] = useState('')
    const [patternError, setPatternError] = useState('')
    const [loading, setLoading] = useState(false)
    const [tableData, setTableData] = useState([])
    const [fetchingData, setFetchingData] = useState(false)
    const [switchProxyMode, setSwitchProxyMode] = useState(false)

    function buildTableData(matchingPatternsForProxy) {
        if (!matchingPatternsForProxy) return []
        const data =  Object.entries(matchingPatternsForProxy).map(([key, info]) => ({
            patternValue: info.pattern || key,
            addedBy: info.addedBy || '-',
            updatedTsFormatted: info.updatedTs
                ? func.prettifyEpoch(info.updatedTs)
                : '-',
            updatedTs: info.updatedTs
        }))
        data.sort((a, b) => b.updatedTs - a.updatedTs)
        return data
    }

    async function fetchData() {
        setFetchingData(true)
        try {
            const { resp } = await settingFunctions.fetchAdminInfo()
            setTableData(buildTableData(resp?.matchingPatternsForProxy))
            setSwitchProxyMode(resp?.switchProxyMode || false)
        } catch (e) {
            func.setToast(true, true, 'Failed to load proxy patterns')
        } finally {
            setFetchingData(false)
        }
    }

    useEffect(() => {
        fetchData()
    }, [])

    function handlePatternChange(value) {
        setPattern(value)
        if (patternError) setPatternError('')
    }

    async function handleToggleProxyMode(value) {
        setSwitchProxyMode(value)
        try {
            await settingRequests.addMatchingPatternForProxy("",value)
            func.setToast(true, false, `Proxy mode ${value ? 'enabled' : 'disabled'}`)
        } catch (e) {
            setSwitchProxyMode(!value)
            func.setToast(true, true, 'Failed to update proxy mode')
        }
    }

    async function handleAdd(value) {
        if (!switchProxyMode) {
            func.setToast(true, true, 'Enable proxy mode before adding patterns')
            return
        }
        const error = validateRegex(value)
        if (error) {
            setPatternError(error)
            return
        }
        setLoading(true)
        try {
            const resp = await settingRequests.addMatchingPatternForProxy(value, switchProxyMode)
            setTableData(buildTableData(resp?.matchingPatternsForProxy || resp))
            setPattern('')
            func.setToast(true, false, 'Pattern added successfully')
        } catch (e) {
            func.setToast(true, true, 'Failed to add pattern')
        } finally {
            setLoading(false)
        }
    }

    const inputCard = (
        <Card>
            <VerticalStack gap="3">
                <Text variant="headingSm" as="h3">Add Proxy Pattern</Text>
                <Text variant="bodyMd" color="subdued">
                    Add patterns to match proxy traffic. These are used to identify requests routed through a proxy.
                </Text>
                <ToggleComponent
                    text="Proxy Mode"
                    onToggle={handleToggleProxyMode}
                    initial={switchProxyMode}
                />
                {!switchProxyMode && (
                    <Banner status="warning">Enable proxy mode to add patterns.</Banner>
                )}
                {patternError && (
                    <Banner status="critical">{patternError}</Banner>
                )}
                <Form onSubmit={() => handleAdd(pattern)}>
                    <TextField
                        label="Pattern"
                        value={pattern}
                        onChange={handlePatternChange}
                        placeholder="e.g. .internal.example.com."
                        error={patternError ? true : undefined}
                        autoComplete="off"
                        disabled={!switchProxyMode}
                        connectedRight={
                            <Button
                                primary
                                onClick={() => handleAdd(pattern)}
                                loading={loading}
                                disabled={!pattern.trim() || !switchProxyMode}
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
            key="proxy-patterns-table"
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
            title="Proxy Patterns"
            isFirstPage={true}
            components={[inputCard, tableCard]}
        />
    )
}

export default ProxyPatterns

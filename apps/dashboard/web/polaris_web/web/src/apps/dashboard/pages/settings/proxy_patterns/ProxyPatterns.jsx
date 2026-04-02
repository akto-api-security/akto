import { Box, Button, Text, TextField, Banner, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import settingRequests from '../api'
import settingFunctions from '../module'
import func from '@/util/func'

function validateRegex(pattern) {
    if (!pattern || pattern.trim() === '') {
        return 'Pattern cannot be empty'
    }
    return null
}

const headers = [
    {
        text: 'Value',
        value: 'patternValue',
        itemOrder: 1,
    },
    {
        text: 'Created By',
        value: 'addedBy',
        itemOrder: 2,
    },
    {
        text: 'Last Updated',
        value: 'updatedTsFormatted',
        itemOrder: 3,
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

    function buildTableData(matchingPatternsForProxy) {
        if (!matchingPatternsForProxy) return []
        return Object.entries(matchingPatternsForProxy).map(([key, info]) => ({
            patternValue: key,
            addedBy: info.addedBy || '-',
            updatedTsFormatted: info.updatedTs
                ? func.epochToDateTime(info.updatedTs)
                : '-',
        }))
    }

    async function fetchData() {
        setFetchingData(true)
        try {
            const { resp } = await settingFunctions.fetchAdminInfo()
            setTableData(buildTableData(resp?.matchingPatternsForProxy))
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

    async function handleAdd() {
        const error = validateRegex(pattern)
        if (error) {
            setPatternError(error)
            return
        }
        setLoading(true)
        try {
            const resp = await settingRequests.addMatchingPatternForProxy(pattern.trim())
            setTableData(buildTableData(resp))
            setPattern('')
            func.setToast(true, false, 'Pattern added successfully')
        } catch (e) {
            func.setToast(true, true, 'Failed to add pattern')
        } finally {
            setLoading(false)
        }
    }

    const inputCard = (
        <Box padding="4">
            <VerticalStack gap="3">
                <Text variant="headingSm" as="h3">Add Proxy Pattern</Text>
                <Text variant="bodyMd" color="subdued">
                    Add patterns to match proxy traffic. These are used to identify requests routed through a proxy.
                </Text>
                {patternError && (
                    <Banner status="critical">{patternError}</Banner>
                )}
                <TextField
                    label="Pattern"
                    value={pattern}
                    onChange={handlePatternChange}
                    placeholder="e.g. .internal.example.com."
                    error={patternError ? true : undefined}
                    autoComplete="off"
                    connectedRight={
                        <Button
                            primary
                            onClick={handleAdd}
                            loading={loading}
                            disabled={!pattern.trim()}
                        >
                            Add
                        </Button>
                    }
                />
            </VerticalStack>
        </Box>
    )

    const tableCard = (
        <GithubSimpleTable
            key="proxy-patterns-table"
            data={tableData}
            resourceName={resourceName}
            headers={headers}
            loading={fetchingData}
            hasRowActions={false}
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

import { Button, Text, TextField, VerticalStack, Card, Form } from '@shopify/polaris'
import { DeleteMajor } from '@shopify/polaris-icons'
import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import func from '@/util/func'
import { CellType } from '../../../components/tables/rows/GithubRow'

const HEADERS = [
    { text: 'Value', title: 'Value', value: 'patternValue', type: CellType.TEXT },
    { text: 'Created By', title: 'Created By', value: 'addedBy', type: CellType.TEXT },
    { text: 'Last Updated', title: 'Last Updated', value: 'updatedTsFormatted', type: CellType.TEXT },
    { text: '', title: '', value: 'actions', type: CellType.ACTION },
]

// patternKey: the field name on each info object that holds the display value (e.g. "pattern" or "host")
export function buildPatternTableData(map, patternKey) {
    if (!map) return []
    const data = Object.entries(map).map(([key, info]) => ({
        patternValue: info[patternKey] || key,
        addedBy: info.addedBy || '-',
        updatedTsFormatted: info.updatedTs ? func.prettifyEpoch(info.updatedTs) : '-',
        updatedTs: info.updatedTs,
    }))
    data.sort((a, b) => b.updatedTs - a.updatedTs)
    return data
}

/**
 * Reusable page for "add a pattern to a list" settings screens.
 *
 * Props:
 *   title         - page title
 *   cardTitle     - heading inside the input card
 *   description   - subtext inside the input card
 *   extraContent  - optional ReactNode rendered in the card between description and the input (e.g. a toggle)
 *   inputLabel    - TextField label
 *   placeholder   - TextField placeholder
 *   inputDisabled - whether the input and button are disabled
 *   tableKey      - unique key for GithubSimpleTable
 *   resourceName  - { singular, plural } for the table
 *   onFetch       - async () => Map<string, info> — called on mount to load data
 *   onAdd         - async (value) => Map<string, info> — called when user submits
 *   onDelete      - optional async (patternValue) => Map<string, info> — called when user deletes a row
 *   patternKey    - field name on the info object holding the display value
 */
function PatternSettingsPage({
    title,
    cardTitle,
    description,
    extraContent,
    inputLabel,
    placeholder,
    inputDisabled,
    tableKey,
    resourceName,
    onFetch,
    onAdd,
    onDelete,
    patternKey,
}) {
    const [value, setValue] = useState('')
    const [inputError, setInputError] = useState('')
    const [loading, setLoading] = useState(false)
    const [tableData, setTableData] = useState([])
    const [fetchingData, setFetchingData] = useState(false)

    async function fetchData() {
        setFetchingData(true)
        try {
            const map = await onFetch()
            setTableData(buildPatternTableData(map, patternKey))
        } catch (e) {
            func.setToast(true, true, `Failed to load ${resourceName.plural}`)
        } finally {
            setFetchingData(false)
        }
    }

    useEffect(() => { fetchData() }, [])

    function handleChange(v) {
        setValue(v)
        if (inputError) setInputError('')
    }

    async function handleAdd() {
        if (!value.trim()) {
            setInputError(`${inputLabel} cannot be empty`)
            return
        }
        setLoading(true)
        try {
            const map = await onAdd(value.trim())
            setTableData(buildPatternTableData(map, patternKey))
            setValue('')
            func.setToast(true, false, `${resourceName.singular.charAt(0).toUpperCase() + resourceName.singular.slice(1)} added successfully`)
        } catch (e) {
            func.setToast(true, true, `Failed to add ${resourceName.singular}`)
        } finally {
            setLoading(false)
        }
    }

    function handleDelete(item) {
        func.showConfirmationModal(
            `Delete "${item.patternValue}"?`,
            'Delete',
            async () => {
                try {
                    const map = await onDelete(item.patternValue)
                    setTableData(buildPatternTableData(map, patternKey))
                    func.setToast(true, false, `${resourceName.singular.charAt(0).toUpperCase() + resourceName.singular.slice(1)} deleted successfully`)
                } catch (e) {
                    func.setToast(true, true, `Failed to delete ${resourceName.singular}`)
                }
            }
        )
    }

    function getRowActions(item) {
        return [{
            items: [{
                content: 'Delete',
                icon: DeleteMajor,
                destructive: true,
                onAction: () => handleDelete(item),
            }],
        }]
    }

    const inputCard = (
        <Card>
            <VerticalStack gap="3">
                <Text variant="headingSm" as="h3">{cardTitle}</Text>
                <Text variant="bodyMd" color="subdued">{description}</Text>
                {extraContent}
                <Form onSubmit={handleAdd}>
                    <TextField
                        label={inputLabel}
                        value={value}
                        onChange={handleChange}
                        placeholder={placeholder}
                        error={inputError || undefined}
                        autoComplete="off"
                        disabled={inputDisabled}
                        connectedRight={
                            <Button
                                primary
                                onClick={handleAdd}
                                loading={loading}
                                disabled={inputDisabled || !value.trim()}
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
            key={tableKey}
            data={tableData}
            resourceName={resourceName}
            headers={HEADERS}
            loading={fetchingData}
            hasRowActions={!!onDelete}
            getActions={onDelete ? getRowActions : undefined}
            useNewRow={true}
            condensedHeight={true}
            headings={HEADERS}
            pageLimit={15}
        />
    )

    return (
        <PageWithMultipleCards
            title={title}
            isFirstPage={true}
            components={[inputCard, tableCard]}
        />
    )
}

export default PatternSettingsPage

import { Button, Modal, Text, TextField, VerticalStack, Form } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import func from '@/util/func'
import { CellType } from '../../../components/tables/rows/GithubRow'

const DEFAULT_HEADERS = [
    { text: 'Value', title: 'Value', value: 'patternValue', type: CellType.TEXT },
    { text: 'Created By', title: 'Created By', value: 'addedBy', type: CellType.TEXT },
    { text: 'Last Updated', title: 'Last Updated', value: 'updatedTsFormatted', type: CellType.TEXT },
]

export function buildPatternTableData(map, patternKey, buildRow) {
    if (!map) return []
    const data = Object.entries(map).map(([key, info]) => {
        const base = {
            id: key,
            patternValue: info[patternKey] || key,
            addedBy: info.addedBy || '-',
            updatedTsFormatted: info.updatedTs ? func.prettifyEpoch(info.updatedTs) : '-',
            updatedTs: info.updatedTs,
        }
        return buildRow ? { ...base, ...buildRow(key, info) } : base
    })
    data.sort((a, b) => b.updatedTs - a.updatedTs)
    return data
}

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
    headers,
    buildRow,
    additionalCards = [],
    onRowClick,
    getRowActions,
    secondaryActions,
    initialValue
}) {
    const tableHeaders = headers || DEFAULT_HEADERS
    const [value, setValue] = useState('')
    const [inputError, setInputError] = useState('')
    const [loading, setLoading] = useState(false)
    const [tableData, setTableData] = useState([])
    const [fetchingData, setFetchingData] = useState(false)
    const [addModalOpen, setAddModalOpen] = useState(false)

    async function fetchData() {
        setFetchingData(true)
        try {
            const map = await onFetch()
            setTableData(buildPatternTableData(map, patternKey, buildRow))
        } catch (e) {
            func.setToast(true, true, `Failed to load ${resourceName.plural}`)
        } finally {
            setFetchingData(false)
        }
    }

    useEffect(() => { fetchData() }, [])
    useEffect(() => {
        if (initialValue?.value) {
            setValue(initialValue.value)
            setAddModalOpen(true)
        }
    }, [initialValue])

    function handleChange(v) {
        setValue(v)
        if (inputError) setInputError('')
    }

    function handleClose() {
        setAddModalOpen(false)
        setValue('')
        setInputError('')
    }

    async function handleAdd() {
        if (!value.trim()) {
            setInputError(`${inputLabel} cannot be empty`)
            return
        }
        setLoading(true)
        try {
            const map = await onAdd(value.trim())
            setTableData(buildPatternTableData(map, patternKey, buildRow))
            handleClose()
            func.setToast(true, false, `${resourceName.singular.charAt(0).toUpperCase() + resourceName.singular.slice(1)} added successfully`)
        } catch (e) {
            func.setToast(true, true, `Failed to add ${resourceName.singular}`)
        } finally {
            setLoading(false)
        }
    }

    function promotedBulkActions(selectedResources) {
        if (!onDelete) return []
        return [{
            content: `Delete ${resourceName.singular}${selectedResources.length > 1 ? 's' : ''}`,
            destructive: true,
            onAction: () => {
                func.showConfirmationModal(
                    `Delete ${selectedResources.length} ${resourceName.singular}${selectedResources.length > 1 ? 's' : ''}?`,
                    'Delete',
                    async () => {
                        try {
                            let map
                            for (const id of selectedResources) {
                                const item = tableData.find(r => r.id === id)
                                if (item) map = await onDelete(item.patternValue)
                            }
                            if (map !== undefined) setTableData(buildPatternTableData(map, patternKey, buildRow))
                            func.setToast(true, false, `${selectedResources.length} ${resourceName.singular}${selectedResources.length > 1 ? 's' : ''} deleted successfully`)
                        } catch (e) {
                            func.setToast(true, true, `Failed to delete ${resourceName.singular}`)
                        }
                    }
                )
            }
        }]
    }

    const addModal = (
        <Modal
            open={addModalOpen}
            onClose={handleClose}
            title={cardTitle}
            primaryAction={{ content: 'Add', onAction: handleAdd, loading, disabled: inputDisabled || !value.trim() }}
            secondaryActions={[{ content: 'Cancel', onAction: handleClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    {!!description && <Text variant="bodyMd" color="subdued">{description}</Text>}
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
                        />
                    </Form>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )

    const tableCard = (
        <GithubSimpleTable
            key={tableKey}
            data={tableData}
            resourceName={resourceName}
            headers={tableHeaders}
            loading={fetchingData}
            selectable={true}
            promotedBulkActions={promotedBulkActions}
            useNewRow={true}
            condensedHeight={true}
            headings={tableHeaders}
            pageLimit={15}
            onRowClick={onRowClick}
            rowClickable={!!onRowClick}
            getActions={getRowActions}
            hasRowActions={!!getRowActions}
            preventRowClickOnActions={true}
        />
    )

    return (
        <>
            <PageWithMultipleCards
                title={title}
                isFirstPage={true}
                primaryAction={{ content: `Add ${resourceName.singular}`, onAction: () => setAddModalOpen(true) }}
                secondaryActions={secondaryActions}
                components={[addModal, tableCard, ...(additionalCards || [])]}
            />
        </>
    )
}

export default PatternSettingsPage

import React, { useState, useCallback, useMemo } from 'react'
import useTable from '../../tables/TableContext'
import { Badge, Box, Checkbox, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import { ChevronDownMinor, ChevronRightMinor } from '@shopify/polaris-icons'
import TableStore from '../../tables/TableStore'
import GetPrettifyEndpoint from '../../../pages/observe/GetPrettifyEndpoint'

// Fixed width for the endpoint/name column — must match groupObj.boxWidth in GraphqlTreeView
const NAME_COL_WIDTH = '400px'

function GraphqlPrettifyChildren({ data, headers, dataColumns, onTerminalClick }) {
    const [refresh, setRefresh] = useState(false)
    const { openedRows, selectItems, modifyOpenedLevels } = useTable()

    const handleNodeClick = useCallback((isTerminal, level, node, e) => {
        if (e.target.type === 'checkbox') return
        if (isTerminal) {
            if (onTerminalClick) onTerminalClick(node)
        } else {
            setRefresh(r => !r)
            const newItems = openedRows.includes(level)
                ? openedRows.filter(x => x !== level)
                : [...new Set([...openedRows, level])]
            TableStore.getState().setOpenedLevels(newItems)
            modifyOpenedLevels(newItems)
        }
    }, [openedRows, modifyOpenedLevels, onTerminalClick])

    const isLevelVisible = useCallback((level) => {
        const inputSegments = level.split('#')
        const currentDepth = inputSegments.length - 1
        if (currentDepth < 2) return true
        if (openedRows.length === 0) return false
        for (let i = 0; i < openedRows.length; i++) {
            const arraySegments = openedRows[i].split('#')
            if (
                arraySegments.length === inputSegments.length ||
                arraySegments.length === inputSegments.length - 1
            ) {
                let match = true
                for (let j = 0; j < arraySegments.length; j++) {
                    if (arraySegments[j] !== inputSegments[j]) { match = false; break }
                }
                if (match) return true
            }
        }
        return false
    }, [openedRows])

    // Header row — mirrors the data row layout exactly so columns align
    const headerRow = useMemo(() => {
        if (!dataColumns || dataColumns.length === 0) return null
        return (
            <Box
                paddingBlockStart="2"
                paddingBlockEnd="2"
                paddingInlineStart="2"
                background="surface-subdued"
                borderColor="border-subdued"
                borderBlockEndWidth="1"
            >
                <HorizontalStack wrap={false} blockAlign="center">
                    {/* Checkbox placeholder */}
                    <Box minWidth="24px" />
                    {/* Name column */}
                    <Box minWidth={NAME_COL_WIDTH}>
                        <Text variant="headingSm" color="subdued">Endpoint</Text>
                    </Box>
                    {dataColumns.map(h => (
                        <Box key={h.value} minWidth={h.boxWidth || '120px'} maxWidth={h.boxWidth || '150px'}>
                            <Text variant="headingSm" color="subdued">{h.title || h.text}</Text>
                        </Box>
                    ))}
                </HorizontalStack>
            </Box>
        )
    }, [dataColumns])

    const traverseAndMakeRows = useCallback((nodes) => {
        let rows = []
        if (!nodes || nodes.length === 0) return rows

        nodes.forEach(node => {
            const ids = node.isTerminal ? [node.id] : (node.apiCollectionIds || [node.id])
            if (!isLevelVisible(node.level)) return

            const depth = node.level.split('#').length - 1
            const isOpen = openedRows.includes(node.level)
            const icon = isOpen ? ChevronDownMinor : ChevronRightMinor
            const indentSteps = depth - 1
            const indent = indentSteps > 0 ? `${indentSteps * 16}px` : undefined

            const selectedItems = TableStore.getState().selectedItems.flat()
            const checkedVal = ids.every(id => selectedItems.includes(id))
            const handleCheck = () => {
                const newItems = checkedVal
                    ? selectedItems.filter(x => !ids.includes(x))
                    : [...new Set([...selectedItems, ...ids])]
                TableStore.getState().setSelectedItems(newItems)
                selectItems(newItems)
            }

            // Data cells — populated for terminal rows, blank for group rows
            const dataCells = dataColumns?.map(h => {
                const colWidth = h.boxWidth || '120px'
                const val = node[h.value]
                const content = node.isTerminal
                    ? (val != null
                        ? (typeof val === 'string' || typeof val === 'number'
                            ? <Text variant="bodySm">{val}</Text>
                            : val)
                        : <Text variant="bodySm" color="subdued">-</Text>)
                    : null
                return (
                    <div
                        key={h.value}
                        style={{ minWidth: colWidth, maxWidth: colWidth, cursor: node.isTerminal ? 'pointer' : 'default' }}
                        onClick={node.isTerminal ? e => handleNodeClick(true, node.level, node, e) : undefined}
                    >
                        {content}
                    </div>
                )
            })

            rows.push(
                <Box
                    key={node.level}
                    paddingBlockStart="2"
                    paddingBlockEnd="2"
                    paddingInlineStart="2"
                    borderColor="border-subdued"
                    borderBlockEndWidth="1"
                >
                    <HorizontalStack wrap={false} blockAlign="center">
                        {/* Checkbox */}
                        <Box minWidth="24px">
                            <Checkbox checked={checkedVal} onChange={handleCheck} />
                        </Box>

                        {/* Name / endpoint cell */}
                        <div
                            style={{ cursor: 'pointer', minWidth: NAME_COL_WIDTH, maxWidth: NAME_COL_WIDTH }}
                            onClick={e => handleNodeClick(node.isTerminal, node.level, node, e)}
                        >
                            <HorizontalStack gap="2" wrap={false} blockAlign="center">
                                {indent ? <Box minWidth={indent} /> : null}
                                {!node.isTerminal
                                    ? <Box><Icon source={icon} /></Box>
                                    : <Box minWidth="20px" />}
                                {node.isTerminal ? (
                                    <Box maxWidth="340px">
                                        {node.endpointComp || (
                                            <GetPrettifyEndpoint method={node.method} url={node.endpoint} isNew={false} />
                                        )}
                                    </Box>
                                ) : (
                                    <HorizontalStack gap="2" wrap={false} blockAlign="center">
                                        <Tooltip content={node.displayName} dismissOnMouseOut>
                                            <Text variant="headingSm">{node.displayName}</Text>
                                        </Tooltip>
                                        {node.children?.length > 0
                                            ? <Badge size="small" status="new">{node.children.length}</Badge>
                                            : null}
                                    </HorizontalStack>
                                )}
                            </HorizontalStack>
                        </div>

                        {/* Data columns */}
                        {dataCells}
                    </HorizontalStack>
                </Box>
            )

            if (!node.isTerminal && node.children?.length > 0) {
                rows = [...rows, ...traverseAndMakeRows(node.children)]
            }
        })
        return rows
    }, [isLevelVisible, openedRows, selectItems, handleNodeClick, dataColumns])

    const rowElements = useMemo(() => {
        return traverseAndMakeRows(data)
    }, [refresh, data, traverseAndMakeRows])

    // Must return a <td> — makeTree result is a sibling of IndexTable.Row inside tbody
    return (
        <td colSpan={20}>
            <VerticalStack gap="0">
                {headerRow}
                {rowElements}
            </VerticalStack>
        </td>
    )
}

export default GraphqlPrettifyChildren

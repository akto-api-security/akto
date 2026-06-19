import React, { useState, useCallback, useMemo } from 'react'
import {
    IndexTable,
    LegacyCard,
    Text,
    Badge,
    Box,
    HorizontalStack,
    Icon,
    Tooltip,
} from '@shopify/polaris'
import { ChevronDownMinor, ChevronRightMinor } from '@shopify/polaris-icons'

function GraphqlTreeTable({ treeData, endpointHeaders, onRowClick }) {
    const [openNodes, setOpenNodes] = useState(new Set())

    const toggleNode = useCallback((level) => {
        setOpenNodes(prev => {
            const next = new Set(prev)
            if (next.has(level)) next.delete(level)
            else next.add(level)
            return next
        })
    }, [])

    const visibleRows = useMemo(() => {
        const rows = []
        const walk = (nodes, depth) => {
            for (const node of nodes) {
                rows.push({ node, depth })
                if (!node.isTerminal && openNodes.has(node.level) && node.children?.length > 0) {
                    walk(node.children, depth + 1)
                }
            }
        }
        walk(treeData || [], 0)
        return rows
    }, [treeData, openNodes])

    const allLeaves = useMemo(() => {
        const leaves = []
        const collect = (nodes) => {
            for (const n of nodes) {
                if (n.isTerminal) leaves.push(n)
                else if (n.children) collect(n.children)
            }
        }
        collect(treeData || [])
        return leaves
    }, [treeData])

    const headings = useMemo(() => {
        const nameHeading = { title: 'Endpoint' }
        const rest = (endpointHeaders || [])
            .filter(h => h.value !== 'endpointComp')
            .map(h => ({ title: typeof h.title === 'string' ? h.title : (h.text || h.value || '') }))
        return [nameHeading, ...rest]
    }, [endpointHeaders])

    const dataColumns = useMemo(() => {
        return (endpointHeaders || []).filter(h => h.value !== 'endpointComp')
    }, [endpointHeaders])

    return (
        <LegacyCard>
            <IndexTable
                resourceName={{ singular: 'endpoint', plural: 'endpoints' }}
                itemCount={allLeaves.length}
                headings={headings}
                selectable={true}
                onSelectionChange={() => {}}
            >
                {visibleRows.map(({ node, depth }, idx) => {
                    const indentPx = depth * 20
                    const isOpen = openNodes.has(node.level)

                    if (!node.isTerminal) {
                        return (
                            <IndexTable.Row
                                key={node.level}
                                id={node.level}
                                position={idx}
                                selected={false}
                                onClick={() => toggleNode(node.level)}
                            >
                                <IndexTable.Cell>
                                    <Box width="100%">
                                        <HorizontalStack align="start" wrap={false} blockAlign="center" gap="2">
                                            {indentPx > 0 && <Box minWidth={`${indentPx}px`} />}
                                            <Icon source={isOpen ? ChevronDownMinor : ChevronRightMinor} />
                                            <Tooltip content={node.displayName} dismissOnMouseOut>
                                                <Text variant="headingSm">{node.displayName}</Text>
                                            </Tooltip>
                                            <Badge size="small" status="new">
                                                {node.children?.length ?? 0}
                                            </Badge>
                                        </HorizontalStack>
                                    </Box>
                                </IndexTable.Cell>
                                {dataColumns.map(h => <IndexTable.Cell key={h.value} />)}
                            </IndexTable.Row>
                        )
                    }

                    return (
                        <IndexTable.Row
                            key={node.id || node.level}
                            id={node.id || node.level}
                            position={idx}
                            selected={false}
                            onClick={() => onRowClick && onRowClick(node)}
                        >
                            <IndexTable.Cell>
                                <HorizontalStack wrap={false} blockAlign="center" gap="2">
                                    {indentPx > 0 && <Box minWidth={`${indentPx}px`} />}
                                    <Box minWidth="20px" />
                                    {node.endpointComp || <Text variant="bodySm">{node.endpoint}</Text>}
                                </HorizontalStack>
                            </IndexTable.Cell>
                            {dataColumns.map(h => {
                                const val = node[h.value]
                                const content = val != null
                                    ? (typeof val === 'string' || typeof val === 'number'
                                        ? <Text variant="bodySm">{val}</Text>
                                        : val)
                                    : <Text variant="bodySm" color="subdued">-</Text>
                                return <IndexTable.Cell key={h.value}>{content}</IndexTable.Cell>
                            })}
                        </IndexTable.Row>
                    )
                })}
            </IndexTable>
        </LegacyCard>
    )
}

export default GraphqlTreeTable

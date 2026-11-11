import { Badge, Box, Button, Card, HorizontalStack, IndexTable, Text, VerticalStack } from '@shopify/polaris'

// Polaris has no heatmap, so a count's weight is shown with the Badge tones instead.
// `ratio` is the count over the busiest cell.
const BUCKETS = [
    { upTo: 0.1, status: undefined, label: 'Low' },
    { upTo: 0.3, status: 'attention', label: 'Medium' },
    { upTo: 0.6, status: 'warning', label: 'High' },
    { upTo: 1, status: 'critical', label: 'Very high' },
]
const OTHER = 'Other'

function CountBadge({ value, max }) {
    if (!value) return <Text as="span" color="subdued">–</Text>
    const bucket = BUCKETS.find((b) => value / max <= b.upTo)
    return <Badge status={bucket.status}>{value.toLocaleString()}</Badge>
}

// heatmap: { columns: [policy], otherPolicies: [policy], rows: [{ agent, where, hosts, cells: [count per column] }], total: agents in all }
// onOpen(hosts, policies) opens those violations; policies is empty for a whole agent.
function ViolationHeatmap({ heatmap, onOpen, onLoadMore, loadingMore }) {
    const { columns, otherPolicies, rows, total } = heatmap
    const max = Math.max(1, ...rows.flatMap((r) => r.cells))
    const headings = [{ title: 'Agent' }, ...columns.map((c) => ({ title: c, alignment: 'center' }))]
    // The Other column stands for every policy that has no column of its own.
    const policiesOf = (column) => (column === OTHER && otherPolicies.length > 0 ? otherPolicies : [column])
    // An agent with no host to filter by has nothing to open.
    const openProps = (row, policies) => (row.hosts.length > 0
        ? { className: 'cursor-pointer', onClick: () => onOpen(row.hosts, policies) }
        : {})

    return (
        <Card padding="0">
            <Box padding="4">
                <VerticalStack gap="1">
                    <Text variant="headingMd" as="h2">Where violations happen</Text>
                    <Text variant="bodySm" color="subdued">Violations per agent for your busiest policies. Select a cell to see those violations.</Text>
                </VerticalStack>
            </Box>
            {rows.length === 0 ? (
                <Box padding="4">
                    <Text variant="bodyMd" color="subdued" alignment="center">No violations in this period.</Text>
                </Box>
            ) : (
            <IndexTable
                resourceName={{ singular: 'agent', plural: 'agents' }}
                itemCount={rows.length}
                headings={headings}
                selectable={false}
            >
                {rows.map((row, index) => (
                    <IndexTable.Row id={`${row.agent}-${index}`} key={`${row.agent}-${index}`} position={index}>
                        <IndexTable.Cell>
                            <Box {...openProps(row, [])}>
                                <VerticalStack gap="0">
                                    <Text variant="bodyMd" fontWeight="semibold">{row.agent}</Text>
                                    <Text variant="bodySm" color="subdued">{row.where}</Text>
                                </VerticalStack>
                            </Box>
                        </IndexTable.Cell>
                        {row.cells.map((count, i) => (
                            <IndexTable.Cell key={columns[i]}>
                                <Box {...openProps(row, policiesOf(columns[i]))}>
                                    <HorizontalStack align="center">
                                        <CountBadge value={count} max={max} />
                                    </HorizontalStack>
                                </Box>
                            </IndexTable.Cell>
                        ))}
                    </IndexTable.Row>
                ))}
            </IndexTable>
            )}
            <Box padding="4">
                <HorizontalStack align="space-between" blockAlign="center" gap="2">
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="bodySm" color="subdued">Fewer</Text>
                        {BUCKETS.map((b) => <Badge key={b.label} status={b.status}>{b.label}</Badge>)}
                        <Text variant="bodySm" color="subdued">More</Text>
                    </HorizontalStack>
                    {rows.length < total && (
                        <Button plain loading={loadingMore} onClick={onLoadMore}>{`Show more agents (${total - rows.length} more)`}</Button>
                    )}
                </HorizontalStack>
            </Box>
        </Card>
    )
}

export default ViolationHeatmap

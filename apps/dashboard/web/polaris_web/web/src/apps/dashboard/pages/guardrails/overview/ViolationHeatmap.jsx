import { Box, Button, Card, HorizontalGrid, HorizontalStack, Link, Text, Tooltip, VerticalStack } from '@shopify/polaris'

const OTHER = 'Other'
const LABEL_WIDTH = 240

// The design's colour scheme: a pale pink that deepens to a strong red as the count grows. Polaris's strong critical
// red laid over the cell, from 0.1 to full opacity, follows it closely.
const MIN_OPACITY = 0.1
// As in the design, light cells have dark text and dark cells white. Past this opacity white is the more readable.
const LIGHT_TEXT_FROM = 0.78

// Counts are very uneven (one load-tested agent can hold 100x the rest), so strength is read on a log scale:
// on a plain count / max, every cell but the top few would look the same.
// It runs from 0 to 1, where 1 is the busiest cell.
const strengthOf = (value, max) => Math.log(1 + value) / Math.log(1 + max)

// A layer that covers its cell.
function Fill({ background, opacity }) {
    return (
        <Box
            position="absolute"
            insetBlockStart="0"
            insetBlockEnd="0"
            insetInlineStart="0"
            insetInlineEnd="0"
            borderRadius="2"
            background={background}
            opacity={opacity.toFixed(2)}
        />
    )
}

// A cell shaded by its count. onOpen is missing when there is nothing to open.
function Cell({ count, strength, tip, onOpen }) {
    const filled = count > 0
    const opacity = MIN_OPACITY + (1 - MIN_OPACITY) * strength
    const text = <Text as="span" variant="bodyMd" fontWeight="semibold">{filled ? count.toLocaleString() : '–'}</Text>
    let color = 'text-disabled'
    if (filled) color = opacity > LIGHT_TEXT_FROM ? 'text-on-color' : 'text'
    return (
        // The tooltip wraps its child in a flex container, so the child takes the full width to fill the cell.
        <Tooltip content={tip} activatorWrapper="div" preferredPosition="above">
            <Box width="100%" position="relative" borderRadius="2" background="bg-subdued" color={color}>
                {filled && <Fill background="bg-critical-strong" opacity={opacity} />}
                {/* Same padding either way, so a button and plain text sit at the same height. Top and bottom only,
                    so the button spans the full width of the cell. (A large plain button is pulled 20px left by
                    Polaris's negative margins, which puts its number off centre.) */}
                <Box position="relative" paddingBlockStart="2" paddingBlockEnd="2">
                    {onOpen ? (
                        <Button plain monochrome removeUnderline fullWidth accessibilityLabel={tip} onClick={onOpen}>{text}</Button>
                    ) : (
                        <Text as="p" alignment="center">{text}</Text>
                    )}
                </Box>
            </Box>
        </Tooltip>
    )
}

// One agent: its label, then a cell per policy column. An agent with no host to filter by has nothing to open.
function AgentRow({ row, columns, max, onOpenAgent, onOpenCell }) {
    const openable = row.hosts.length > 0
    return (
        <>
            <VerticalStack gap="0" align="center">
                <Text as="span" variant="bodyMd" fontWeight="semibold" breakWord>
                    {openable ? <Link monochrome removeUnderline onClick={onOpenAgent}>{row.agent}</Link> : row.agent}
                </Text>
                {row.where && <Text as="span" variant="bodySm" color="subdued" breakWord>{row.where}</Text>}
            </VerticalStack>
            {row.cells.map((count, index) => (
                <Cell
                    key={columns[index]}
                    count={count}
                    strength={count > 0 ? strengthOf(count, max) : 0}
                    tip={`${row.agent} · ${columns[index]}: ${count.toLocaleString()} violation${count === 1 ? '' : 's'}`}
                    onOpen={openable && count > 0 ? () => onOpenCell(index) : undefined}
                />
            ))}
        </>
    )
}

// heatmap: { columns: [policy], otherPolicies: [policy], rows: [{ agent, where, hosts, cells: [count per column] }], total: agents in all }
// onOpen(hosts, policies) opens those violations; policies is empty for a whole agent.
function ViolationHeatmap({ heatmap, onOpen, onLoadMore, loadingMore }) {
    const { columns, otherPolicies, rows, total } = heatmap
    const max = Math.max(1, ...rows.flatMap((r) => r.cells))
    // The Other column stands for every policy that has no column of its own.
    const policiesOf = (column) => (column === OTHER && otherPolicies.length > 0 ? otherPolicies : [column])

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
                <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
                    <HorizontalGrid columns={`${LABEL_WIDTH}px repeat(${columns.length}, minmax(0, 1fr))`} gap="1">
                        <Box />
                        {columns.map((column) => (
                            <Box key={column} paddingBlockEnd="1">
                                <Text as="p" variant="bodySm" color="subdued" alignment="center" breakWord>{column}</Text>
                            </Box>
                        ))}
                        {rows.map((row, index) => (
                            <AgentRow
                                key={`${row.agent}-${index}`}
                                row={row}
                                columns={columns}
                                max={max}
                                onOpenAgent={() => onOpen(row.hosts, [])}
                                onOpenCell={(columnIndex) => onOpen(row.hosts, policiesOf(columns[columnIndex]))}
                            />
                        ))}
                    </HorizontalGrid>
                </Box>
            )}
            {rows.length < total && (
                <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
                    <HorizontalStack align="end">
                        <Button plain loading={loadingMore} onClick={onLoadMore}>{`Show more agents (${total - rows.length} more)`}</Button>
                    </HorizontalStack>
                </Box>
            )}
        </Card>
    )
}

export default ViolationHeatmap

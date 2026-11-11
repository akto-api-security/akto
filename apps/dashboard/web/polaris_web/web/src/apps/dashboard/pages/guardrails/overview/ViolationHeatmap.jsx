import { Badge, Box, Button, Card, HorizontalStack, IndexTable, Text, VerticalStack } from '@shopify/polaris'
import { SEVERITY } from '../utils'

// A cell has the hue of its policy's severity, and gets stronger as its count grows.
const SEVERITY_LEVELS = [SEVERITY.LOW, SEVERITY.MEDIUM, SEVERITY.HIGH, SEVERITY.CRITICAL]
// The full-strength end of each severity's colour, as r, g, b.
const HEAT_RGB = { LOW: [140, 145, 150], MEDIUM: [255, 158, 44], HIGH: [240, 100, 79], CRITICAL: [185, 28, 12] }
// The faintest cell stays readable.
const MIN_ALPHA = 0.2
const OTHER = 'Other'

const severityRank = (severity) => SEVERITY_LEVELS.findIndex((level) => level.value === severity)

// Counts are very uneven (one load-tested agent can hold 100x the rest), so strength is read on a log scale.
// It runs from 0 to 1, where 1 is the busiest cell.
const strengthOf = (value, max) => Math.log(1 + value) / Math.log(1 + max)

// The .heat-badge rule in dashboard.css applies these two variables to the Polaris badge.
function heatStyle(severity, strength) {
    const [r, g, b] = HEAT_RGB[severity]
    const alpha = MIN_ALPHA + (1 - MIN_ALPHA) * strength
    return {
        '--heat-bg': `rgba(${r}, ${g}, ${b}, ${alpha.toFixed(2)})`,
        // Strong critical cells are dark red and need light text.
        '--heat-text': severity === SEVERITY.CRITICAL.value && alpha > 0.6 ? '#FFFBFB' : '#202223',
    }
}

// `severity` is missing for a policy that no longer exists, which stays a plain badge.
function HeatBadge({ severity, strength, children }) {
    return severity
        ? <div className="heat-badge" style={heatStyle(severity, strength)}><Badge size="small">{children}</Badge></div>
        : <Badge size="small">{children}</Badge>
}

function CountBadge({ value, severity, strength }) {
    if (!value) return <Text as="span" color="subdued">–</Text>
    return <HeatBadge severity={severity} strength={strength}>{value.toLocaleString()}</HeatBadge>
}

// heatmap: { columns: [policy], otherPolicies: [policy], rows: [{ agent, where, hosts, cells: [count per column] }], total: agents in all }
// policySeverity: { policy name: severity }
// onOpen(hosts, policies) opens those violations; policies is empty for a whole agent.
function ViolationHeatmap({ heatmap, policySeverity = {}, onOpen, onLoadMore, loadingMore }) {
    const { columns, otherPolicies, rows, total } = heatmap
    const max = Math.max(1, ...rows.flatMap((r) => r.cells))
    const headings = [{ title: 'Agent' }, ...columns.map((c) => ({ title: c, alignment: 'center' }))]
    // The Other column stands for every policy that has no column of its own.
    const policiesOf = (column) => (column === OTHER && otherPolicies.length > 0 ? otherPolicies : [column])
    // Other mixes several policies, so it shows the highest severity among them.
    const severityOf = (column) => policiesOf(column)
        .map((policy) => policySeverity[policy])
        .filter((severity) => severityRank(severity) >= 0)
        .reduce((highest, severity) => (severityRank(severity) > severityRank(highest) ? severity : highest), undefined)
    const columnSeverity = columns.map(severityOf)
    // An agent with no host to filter by has nothing to open.
    const openProps = (row, policies) => (row.hosts.length > 0
        ? { className: 'cursor-pointer', onClick: () => onOpen(row.hosts, policies) }
        : {})

    return (
        <Card padding="0">
            <Box padding="4">
                <VerticalStack gap="1">
                    <Text variant="headingMd" as="h2">Where violations happen</Text>
                    <Text variant="bodySm" color="subdued">Violations per agent for your busiest policies. The colour is the policy's severity and gets stronger with more violations. Select a cell to see those violations.</Text>
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
                                        <CountBadge value={count} severity={columnSeverity[i]} strength={strengthOf(count, max)} />
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
                        <Text variant="bodySm" color="subdued">Policy severity</Text>
                        {SEVERITY_LEVELS.map((level) => <HeatBadge key={level.value} severity={level.value} strength={1}>{level.label}</HeatBadge>)}
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

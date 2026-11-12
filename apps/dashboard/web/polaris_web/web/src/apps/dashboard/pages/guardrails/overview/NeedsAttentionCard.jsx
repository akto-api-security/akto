import { Badge, Box, Button, Card, Divider, HorizontalStack, Link, Text, VerticalStack } from '@shopify/polaris'
import func from '@/util/func'
import { usePermissions } from '@/util/permissions'
import AllowedAction from '../../../components/shared/AllowedAction'
import { SeverityBadge } from '../../observe/agentic/AgenticCellRenderers'
import useCaseOwners from '../../threat_detection/utils/useCaseOwners'
import { ownerLabel } from '../../threat_detection/utils/caseManagement'

// rows: the loaded open issues, worst severity first; total counts all of them, so the rest load on demand.
// collectionsMap names the agent when the event has one.
function NeedsAttentionCard({ rows, total, collectionsMap, onViewAll, onReview, onOpenPolicy, onAssign, onLoadMore, loadingMore }) {
    const { canCall } = usePermissions()
    const { owners } = useCaseOwners()

    return (
        <Card padding="0">
            <Box padding="4">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingMd" as="h2">Needs attention</Text>
                    <Button plain onClick={onViewAll}>View all open issues</Button>
                </HorizontalStack>
            </Box>
            {rows.length === 0 ? (
                <Box padding="4">
                    <Text variant="bodyMd" color="subdued" alignment="center">Nothing needs attention right now.</Text>
                </Box>
            ) : rows.map((row) => {
                const agent = collectionsMap?.[row.collectionId] || row.host || '-'
                return (
                    <Box key={row.id}>
                        <Divider />
                        <Box padding="4">
                            <HorizontalStack gap="4" blockAlign="center" wrap={false}>
                                <Box minWidth="80px">
                                    <SeverityBadge severity={row.severity} />
                                </Box>
                                <Box minWidth="0" width="100%">
                                    <VerticalStack gap="0">
                                        <Text variant="bodyMd" fontWeight="semibold">
                                            <Link removeUnderline onClick={() => onOpenPolicy(row)}>{row.policy}</Link>
                                        </Text>
                                        <Text variant="bodySm" color="subdued" truncate>
                                            {`${agent} · ${row.count.toLocaleString()} occurrence${row.count === 1 ? '' : 's'} · last ${func.prettifyEpoch(row.ts)}`}
                                        </Text>
                                    </VerticalStack>
                                </Box>
                                <Box minWidth="120px">
                                    {row.owner
                                        ? <Text variant="bodySm">{ownerLabel(row.owner, owners)}</Text>
                                        : <Badge>Unassigned</Badge>}
                                </Box>
                                <HorizontalStack gap="2" wrap={false}>
                                    <AllowedAction allowed={canCall('api/updateMaliciousEventStatus')}>
                                        <Button size="slim" onClick={() => onAssign(row, agent)}>Assign</Button>
                                    </AllowedAction>
                                    <Button size="slim" primary onClick={() => onReview(row)}>Review</Button>
                                </HorizontalStack>
                            </HorizontalStack>
                        </Box>
                    </Box>
                )
            })}
            {rows.length < total && (
                <Box>
                    <Divider />
                    <Box padding="4">
                        <HorizontalStack align="center">
                            <Button plain loading={loadingMore} onClick={onLoadMore}>{`Show more (${total - rows.length} more)`}</Button>
                        </HorizontalStack>
                    </Box>
                </Box>
            )}
        </Card>
    )
}

export default NeedsAttentionCard

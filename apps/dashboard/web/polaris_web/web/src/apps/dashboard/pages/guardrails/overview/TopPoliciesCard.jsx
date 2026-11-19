import { Box, Button, HorizontalStack, ProgressBar, Text, VerticalStack } from '@shopify/polaris'
import CardWithHeader from '../../dashboard/new_components/CardWithHeader'

// policies: the loaded rows, busiest first. total counts every policy, so the rest can be loaded on demand.
function TopPoliciesCard({ policies, total, onOpen, onLoadMore, loadingMore }) {
    const max = policies[0]?.count || 1
    return (
        <CardWithHeader title="Top policies triggered" hasData={policies.length > 0} emptyMessage="No policies triggered in this period." minHeight="240px">
            <VerticalStack gap="3">
                {policies.map((p) => (
                    <Box key={p.name} className="cursor-pointer" onClick={() => onOpen(p.name)}>
                        <VerticalStack gap="1">
                            <HorizontalStack align="space-between" gap="2" wrap={false}>
                                <Text variant="bodyMd" truncate>{p.name}</Text>
                                <Text variant="bodyMd" fontWeight="semibold">{p.count.toLocaleString()}</Text>
                            </HorizontalStack>
                            <ProgressBar progress={Math.round((p.count * 100) / max)} size="small" animated={false} />
                        </VerticalStack>
                    </Box>
                ))}
                {policies.length < total && (
                    <Button plain loading={loadingMore} onClick={onLoadMore}>{`Show more (${total - policies.length} more)`}</Button>
                )}
            </VerticalStack>
        </CardWithHeader>
    )
}

export default TopPoliciesCard

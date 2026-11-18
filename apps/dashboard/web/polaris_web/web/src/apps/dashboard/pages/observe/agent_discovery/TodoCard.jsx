import { Badge, Box, Button, Card, Divider, HorizontalStack, Link, Text, VerticalStack } from '@shopify/polaris'
import { SeverityBadge } from '../agentic/AgenticCellRenderers'

const KIND_ACTION = { coverage: 'Apply a policy' }

// todos: most severe first. Each names its riskiest assets (up to a few of the count) so there is somewhere to start.
function TodoCard({ todos, snoozedCount, onReview, onOpenAsset, onAsk, onSnooze, onShowSnoozed }) {
    return (
        <Card padding="0">
            <Box padding="4">
                <VerticalStack gap="1">
                    <Text variant="headingMd" as="h2">What to do first</Text>
                    <Text variant="bodySm" color="subdued">Most severe first, from what Argus has seen in your traffic.</Text>
                </VerticalStack>
            </Box>
            {todos.length === 0 && (
                <Box padding="4">
                    <Text variant="bodyMd" color="subdued" alignment="center">Nothing needs doing right now.</Text>
                </Box>
            )}
            {todos.map((todo) => (
                <Box key={todo.id}>
                    <Divider />
                    <Box padding="4">
                        <VerticalStack gap="2">
                            <HorizontalStack gap="2" blockAlign="center">
                                <SeverityBadge severity={todo.severity} />
                                <Text variant="bodySm" color="subdued">{todo.kind}</Text>
                            </HorizontalStack>
                            <Text variant="bodyMd" fontWeight="semibold">{todo.title}</Text>
                            <Text variant="bodyMd" color="subdued">{todo.why}</Text>
                            {todo.chips.length > 0 && (
                                <HorizontalStack gap="2">
                                    {todo.chips.map((chip) => <Badge key={chip}>{chip}</Badge>)}
                                </HorizontalStack>
                            )}
                            <HorizontalStack gap="2" blockAlign="center">
                                <Text variant="bodySm" color="subdued">Most at risk:</Text>
                                {todo.assets.map((asset) => (
                                    <Link key={asset.id} removeUnderline onClick={() => onOpenAsset(asset)}>{asset.name}</Link>
                                ))}
                                {todo.count > todo.assets.length && (
                                    <Text variant="bodySm" color="subdued">{`+${todo.count - todo.assets.length} more`}</Text>
                                )}
                            </HorizontalStack>
                            <HorizontalStack gap="2">
                                <Button primary onClick={() => onReview(todo)}>{KIND_ACTION[todo.id] || 'Review'}</Button>
                                <Button onClick={() => onAsk(todo)}>Ask Argus</Button>
                                <Button plain onClick={() => onSnooze(todo)}>Snooze</Button>
                            </HorizontalStack>
                        </VerticalStack>
                    </Box>
                </Box>
            ))}
            {snoozedCount > 0 && (
                <Box>
                    <Divider />
                    <Box padding="4">
                        <HorizontalStack align="center">
                            <Button plain onClick={onShowSnoozed}>{`Show ${snoozedCount} snoozed`}</Button>
                        </HorizontalStack>
                    </Box>
                </Box>
            )}
        </Card>
    )
}

export default TodoCard

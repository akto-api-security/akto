import { Box, Button, Card, HorizontalStack, Spinner, Text, VerticalStack } from '@shopify/polaris'

// One stat within a card — a number/name plus its subdued label.
function Stat({ label, value }) {
    if (value === null || value === undefined || value === '') return null
    return (
        <VerticalStack gap="1">
            <Text variant="bodySm" color="subdued">{label}</Text>
            <Text variant="bodyMd" fontWeight="semibold">{value}</Text>
        </VerticalStack>
    )
}

// Card-shape-specific stat rows — each of the 5 fixed cards (see ArgusPostureService#buildInsightCards)
// has its own fields, so this is an explicit lookup by id rather than a generic renderer guessing
// at unknown shapes.
function statsFor(card) {
    switch (card.id) {
        case 'RED_TEAM_BREAKDOWN':
            return [
                <Stat key="total" label="Open issues" value={card.totalOpenIssues} />,
                card.topFinding && (
                    <Stat key="top" label="Most common issue"
                        value={`${card.topFinding.vulnType} on ${card.topFinding.agentName} (${card.topFinding.count})`} />
                ),
            ]
        case 'ATTACK_FLOW_ANALYSIS':
            return (card.issuePreview || []).map((issue, i) => (
                <Stat key={`issue-${i}`} label={`Analyzing (${issue.severity})`} value={`${issue.vulnType} on ${issue.agentName}`} />
            ))
        case 'GUARDRAIL_BREAKDOWN':
            return [
                <Stat key="total" label="Guardrail events" value={card.totalEvents} />,
                card.byAgent?.[0] && <Stat key="agent" label="Top agent" value={`${card.byAgent[0].agentName} (${card.byAgent[0].count})`} />,
                card.byPolicy?.[0] && <Stat key="policy" label="Top policy" value={`${card.byPolicy[0].policy} (${card.byPolicy[0].count})`} />,
            ]
        case 'GUARDRAIL_HOTSPOT':
            return [
                card.hottestAgent && <Stat key="agent" label="Most active agent" value={`${card.hottestAgent.agentName} (${card.hottestAgent.count})`} />,
                card.hottestPolicy && <Stat key="policy" label="Most-triggered policy" value={`${card.hottestPolicy.policy} (${card.hottestPolicy.count})`} />,
            ]
        case 'OBSERVABILITY':
            return [
                <Stat key="tokens" label="Tokens used" value={card.totalTokens} />,
                card.hottestAgent && <Stat key="agent" label="Top agent by tokens" value={`${card.hottestAgent.agentName} (${card.hottestAgent.tokens})`} />,
                card.topTopics?.length > 0 && <Stat key="topics" label="Top topics" value={card.topTopics.map((t) => t.topic).join(', ')} />,
            ]
        default:
            return []
    }
}

// summary/impact/recommendation — the shape every card except ATTACK_FLOW_ANALYSIS gets back.
function CardSummary({ summary }) {
    return (
        <VerticalStack gap="2">
            <Text variant="bodySm">{summary.summary}</Text>
            {summary.impact && (
                <Text variant="bodySm" color="subdued">
                    <Text as="span" fontWeight="semibold">Impact — </Text>{summary.impact}
                </Text>
            )}
            {summary.recommendation && (
                <Text variant="bodySm" color="subdued">
                    <Text as="span" fontWeight="semibold">Recommended — </Text>{summary.recommendation}
                </Text>
            )}
        </VerticalStack>
    )
}

// One agent's attack flow — an ordered list of steps (attempt -> agent behavior -> outcome),
// grounded in a real validated red-team conversation, not a raw request/response transcript.
function AttackFlow({ flow }) {
    return (
        <VerticalStack gap="2">
            <Text variant="bodyMd" fontWeight="semibold">{flow.agentName}</Text>
            <VerticalStack gap="1">
                {(flow.steps || []).map((step, i) => (
                    <HorizontalStack key={i} gap="2" blockAlign="start" wrap={false}>
                        <Box minWidth="20px"><Text variant="bodySm" color="subdued">{i + 1}.</Text></Box>
                        <Text variant="bodySm">{step}</Text>
                    </HorizontalStack>
                ))}
            </VerticalStack>
            {flow.impact && (
                <Text variant="bodySm" color="subdued">
                    <Text as="span" fontWeight="semibold">Impact — </Text>{flow.impact}
                </Text>
            )}
            {flow.recommendation && (
                <Text variant="bodySm" color="subdued">
                    <Text as="span" fontWeight="semibold">Recommended — </Text>{flow.recommendation}
                </Text>
            )}
        </VerticalStack>
    )
}

function CardBody({ card, summary, summaryLoading }) {
    if (summaryLoading) {
        return (
            <HorizontalStack gap="2" blockAlign="center">
                <Spinner size="small" />
                <Text variant="bodySm" color="subdued">Generating AI summary…</Text>
            </HorizontalStack>
        )
    }
    if (!summary) return null
    if (card.id === 'ATTACK_FLOW_ANALYSIS') {
        const flows = summary.flows || []
        if (flows.length === 0) return null
        return (
            <VerticalStack gap="4">
                {flows.map((flow, i) => <AttackFlow key={i} flow={flow} />)}
            </VerticalStack>
        )
    }
    return <CardSummary summary={summary} />
}

function InsightCard({ card, summary, summaryLoading, onOpenCta }) {
    const stats = statsFor(card).filter(Boolean)
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="3">
                    <HorizontalStack align="space-between" blockAlign="start">
                        <Text variant="headingSm">{card.title}</Text>
                        {card.cta && (
                            <Button plain onClick={() => onOpenCta(card.cta)}>{card.cta.label}</Button>
                        )}
                    </HorizontalStack>
                    {stats.length > 0 && <HorizontalStack gap="6" wrap>{stats}</HorizontalStack>}
                    {(summaryLoading || summary) && (
                        <Box paddingBlockStart="2" borderBlockStartWidth="1" borderColor="border">
                            <Box paddingBlockStart="3">
                                <CardBody card={card} summary={summary} summaryLoading={summaryLoading} />
                            </Box>
                        </Box>
                    )}
                </VerticalStack>
            </Box>
        </Card>
    )
}

function InsightCardsSection({ cards, summaries, summariesLoading, onOpenRoute }) {
    const rows = cards || []
    if (rows.length === 0) {
        return (
            <Card>
                <Box padding="4">
                    <Text variant="bodyMd" color="subdued" alignment="center">No insights in this window.</Text>
                </Box>
            </Card>
        )
    }
    const openCta = (cta) => { if (cta?.route && onOpenRoute) onOpenRoute(cta.route) }
    return (
        <VerticalStack gap="3">
            {rows.map((card) => (
                <InsightCard key={card.id} card={card} summary={summaries?.[card.id]} summaryLoading={summariesLoading} onOpenCta={openCta} />
            ))}
        </VerticalStack>
    )
}

export default InsightCardsSection

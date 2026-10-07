import { useNavigate } from 'react-router-dom'
import { Box, Button, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import { LegendDot } from '../../../observe/agentic/AgenticStatsCard'
import { ctaHref } from './cta'
import SimpleIndexTable from '../../../../components/tables/SimpleIndexTable'
import func from '@/util/func'
import './AgentDetail.css'

const HEADINGS = [
    { title: 'When' },
    { title: 'Guardrail' },
    { title: 'Event' },
    { title: 'Action' },
]

function ActionBadge({ action }) {
    const blocked = action === 'Blocked'
    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            <LegendDot color={blocked ? 'var(--color-success-new)' : 'var(--p-color-icon-warning)'} />
            <Text as="span" variant="bodyMd" fontWeight="medium" color={blocked ? 'success' : 'warning'}>{action}</Text>
        </HorizontalStack>
    )
}

function GuardrailActivitySection({ activity }) {
    const navigate = useNavigate()
    if (!activity) return null

    if (activity.available === false) {
        return (
            <Card padding="5">
                <Text variant="bodyMd" color="subdued" alignment="center">
                    Guardrail activity is unavailable.
                </Text>
            </Card>
        )
    }

    const events = activity.events || []
    const viewAllCta = (activity.ctas || []).find((c) => c.id === 'view_violations')

    if (events.length === 0) {
        return (
            <Card padding="5">
                <Text variant="bodyMd" color="subdued" alignment="center">
                    No guardrail activity in the last 90 days.
                </Text>
            </Card>
        )
    }

    return (
        <Card padding="0">
            <Box padding="5" paddingBlockEnd="3">
                <Text variant="bodySm" color="subdued">Last 90 days, newest first.</Text>
            </Box>
            <SimpleIndexTable
                resourceName={{ singular: 'event', plural: 'events' }}
                headings={HEADINGS}
                rows={events.map((event) => [
                    <Text variant="bodyMd" color="subdued">{func.prettifyEpoch(event.timestamp)}</Text>,
                    <Text variant="bodyMd" fontWeight="medium">{event.guardrail || '-'}</Text>,
                    <Text variant="bodyMd" color="subdued">{event.event || '-'}</Text>,
                    <ActionBadge action={event.action} />,
                ])}
                getRowId={(cells, index) => `${events[index].timestamp}-${index}`}
            />
            <Box padding="3" borderBlockStartWidth="1" borderColor="border-subdued">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="bodySm" color="subdued">Showing {events.length} of {activity.total}.</Text>
                    {viewAllCta && (
                        <Button plain onClick={() => navigate(ctaHref(viewAllCta))}>{viewAllCta.label}</Button>
                    )}
                </HorizontalStack>
            </Box>
        </Card>
    )
}

export default GuardrailActivitySection

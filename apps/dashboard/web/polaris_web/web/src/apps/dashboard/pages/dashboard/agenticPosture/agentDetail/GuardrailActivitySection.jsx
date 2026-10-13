import { useNavigate } from 'react-router-dom'
import { Box, Button, Card, Text } from '@shopify/polaris'
import ProfileTimeline from '@/apps/dashboard/components/shared/ProfileTimeline'
import { ctaHref } from './cta'
import './AgentDetail.css'

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
    const rows = events.map((event) => ({
        timestamp: event.timestamp,
        title: event.event || '-',
        detail: event.url,
        severity: event.severity,
    }))

    return (
        <Card padding="5">
            <ProfileTimeline subtitle="Last 90 days, newest first." rows={rows} total={activity.total} />
            {viewAllCta && events.length > 0 && (
                <Box paddingBlockStart="4">
                    <Button plain onClick={() => navigate(ctaHref(viewAllCta))}>{viewAllCta.label}</Button>
                </Box>
            )}
        </Card>
    )
}

export default GuardrailActivitySection

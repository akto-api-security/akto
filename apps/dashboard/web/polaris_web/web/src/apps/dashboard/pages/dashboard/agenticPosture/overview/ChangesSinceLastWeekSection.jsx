import { Box, Card, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { CircleAlertMajor, CirclePlusMinor, CircleTickMajor } from '@shopify/polaris-icons'
import { TONE_TEXT_COLOR } from '../../agenticPostureShared'

const ICONS = { plus: CirclePlusMinor, tick: CircleTickMajor, alert: CircleAlertMajor }

function ChangeRow({ row }) {
    const Icon_ = ICONS[row.icon] || CirclePlusMinor
    return (
        <HorizontalStack gap="3" blockAlign="center">
            {/* Icon must not be a direct flex child here — Polaris's own .Polaris-Icon rule
                sets margin:auto (meant for centering inside a fixed-size box), which a flex
                container with spare width reinterprets as "consume all remaining space,"
                shoving the icon away from its siblings. Wrapping in a Box (as AgentDetails.jsx's
                MetadataField already does elsewhere in this app) makes the Box the flex child
                instead, sized to its content, so there's no free space left for margin:auto to
                eat. */}
            <Box>
                <Icon source={Icon_} color={TONE_TEXT_COLOR[row.tone] || 'subdued'} />
            </Box>
            <Text variant="bodyMd" fontWeight="semibold">{row.count}</Text>
            <Text variant="bodyMd" color="subdued">{row.label}</Text>
        </HorizontalStack>
    )
}

function ChangesSinceLastWeekSection({ changesThisWeek }) {
    const rows = changesThisWeek || []
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="3">
                    {rows.map((row) => {
                        const display = row.status === 'COMING_SOON'
                            ? { icon: 'plus', count: row.illustrative?.count ?? 0, label: row.label }
                            : row
                        return <ChangeRow key={row.id} row={display} />
                    })}
                </VerticalStack>
            </Box>
        </Card>
    )
}

export default ChangesSinceLastWeekSection

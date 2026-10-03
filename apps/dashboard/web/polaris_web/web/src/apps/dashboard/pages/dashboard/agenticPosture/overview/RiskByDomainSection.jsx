import { Box, Card, HorizontalGrid, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { ArrowDownMinor, ArrowUpMinor, IdentityCardMajor, KeyMajor, NoteMajor, SecureMajor, ToolsMajor } from '@shopify/polaris-icons'
import { TONE_TEXT_COLOR } from '../../agenticPostureShared'

const ICONS = {
    identity: IdentityCardMajor,
    permissions: KeyMajor,
    toolsMcp: ToolsMajor,
    dataExposure: NoteMajor,
    protectionControls: SecureMajor,
}

// Five domain cards — count + agent count + a real week-over-week delta. No trend sparkline: no
// historical snapshot exists yet to back a smooth curve (see the posture plan).
function DomainCard({ domain }) {
    const positive = domain.delta > 0
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="2">
                    <HorizontalStack gap="2" blockAlign="center">
                        {ICONS[domain.id] && <Box><Icon source={ICONS[domain.id]} color="subdued" /></Box>}
                        <Text variant="bodySm" fontWeight="semibold">{domain.label}</Text>
                    </HorizontalStack>
                    <Text variant="heading2xl">{domain.count}</Text>
                    <Text variant="bodySm" color="subdued">findings · {domain.agentCount} agents</Text>
                    {domain.delta !== null && domain.delta !== undefined && (
                        <HorizontalStack gap="1" blockAlign="center">
                            <Box><Icon source={positive ? ArrowUpMinor : ArrowDownMinor} color={TONE_TEXT_COLOR[domain.deltaTone] || 'subdued'} /></Box>
                            <Text variant="bodySm" fontWeight="semibold" color={TONE_TEXT_COLOR[domain.deltaTone] || 'subdued'}>
                                {positive ? '+' : ''}{domain.delta} wk
                            </Text>
                        </HorizontalStack>
                    )}
                </VerticalStack>
            </Box>
        </Card>
    )
}

function RiskByDomainSection({ riskByDomain }) {
    return (
        <HorizontalGrid columns={5} gap="3">
            {(riskByDomain || []).map((domain) => <DomainCard key={domain.id} domain={domain} />)}
        </HorizontalGrid>
    )
}

export default RiskByDomainSection

import { Box, Card, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { CircleAlertMajor, CircleTickMajor } from '@shopify/polaris-icons'
import DetailGrid from '../../../observe/agentic/DetailGrid'

// Orphaned/owner is real — the same owner-tag signal used elsewhere on this page. Identity
// type/credential/shared-credential have no backing data anywhere (verified: no credential-type
// field on any collection/tag, no structured credential metadata in the agent-import flow, no
// auth-header fingerprinting in traffic capture) — sample data for now, rendered plainly like the
// rest of the page (see the posture plan for what closing this gap for real would take).
function IdentitySection({ identity }) {
    if (!identity) return null
    return (
        <VerticalStack gap="3">
            <Card>
                <Box padding="4">
                    {identity.orphaned ? (
                        <TextWithIcon icon={CircleAlertMajor} tone="critical" text="No owner mapped for this agent" />
                    ) : (
                        <TextWithIcon icon={CircleTickMajor} tone="success" text={`Owned by ${identity.ownerName}`} />
                    )}
                </Box>
            </Card>
            {identity.illustrative && (
                <Card>
                    <Box padding="4">
                        <DetailGrid
                            columns={3}
                            items={[
                                { label: 'Identity type', value: identity.illustrative.type },
                                { label: 'Credential', value: identity.illustrative.credential },
                                { label: 'Shared credential', value: identity.illustrative.shared ? 'Yes' : 'No' },
                            ]}
                        />
                    </Box>
                </Card>
            )}
        </VerticalStack>
    )
}

function TextWithIcon({ icon, tone, text }) {
    return (
        <HorizontalStack gap="1" blockAlign="center">
            {/* Box wrapper works around Polaris's own .Polaris-Icon{margin:auto} rule, which a
                wide flex row reinterprets as "consume all remaining space" — see ChangesSinceLastWeekSection. */}
            <Box><Icon source={icon} color={tone} /></Box>
            <Text as="span" variant="bodySm" color={tone}>{text}</Text>
        </HorizontalStack>
    )
}

export default IdentitySection

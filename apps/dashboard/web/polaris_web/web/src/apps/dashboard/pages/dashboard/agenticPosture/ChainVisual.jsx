import { Badge, Box, HorizontalStack, Icon } from '@shopify/polaris'
import { ArrowRightMinor, CustomersMinor, GlobeMinor, KeyMajor, SecureMajor } from '@shopify/polaris-icons'

const TYPE_ICONS = {
    source: GlobeMinor,
    agent: CustomersMinor,
    tool: KeyMajor,
    resource: SecureMajor,
}

// The mockup color-codes each node type (muted/dashed source, blue agent, purple tool, red
// resource). Polaris Badge has no "purple" status, so this maps to the closest built-in
// progression instead: grey (unknown/external) -> blue (known agent) -> amber (tool/actor) ->
// red (the sensitive resource actually at risk) — default (undefined) renders Polaris's neutral
// grey, which is what "source" wants anyway.
const TYPE_BADGE_STATUS = {
    agent: 'info',
    tool: 'attention',
    resource: 'critical',
}

// One "source -> agent -> tool -> resource" chain, rendered as plain Polaris Badges (each with a
// type icon) joined by an arrow icon — no custom SVG connectors, unlike the original mockup's
// hand-drawn node chips. Shared by the Overview page's Dangerous Paths cards and the Agent Detail
// page's callout. `chain` is [{label, type}], `type` in source|agent|tool|resource.
function ChainVisual({ chain }) {
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap>
            {(chain || []).map((node, i) => (
                <HorizontalStack key={`${node.label}-${i}`} gap="2" blockAlign="center" wrap={false}>
                    <Badge status={TYPE_BADGE_STATUS[node.type]}>
                        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                            {TYPE_ICONS[node.type] && <Box><Icon source={TYPE_ICONS[node.type]} /></Box>}
                            <span>{node.label}</span>
                        </HorizontalStack>
                    </Badge>
                    {i < chain.length - 1 && <Box><Icon source={ArrowRightMinor} color="subdued" /></Box>}
                </HorizontalStack>
            ))}
        </HorizontalStack>
    )
}

export default ChainVisual

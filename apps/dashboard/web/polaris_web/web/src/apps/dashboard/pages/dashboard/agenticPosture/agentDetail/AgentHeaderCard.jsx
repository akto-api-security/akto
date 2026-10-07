import { Badge, Box, Card, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import { AutomationMajor } from '@shopify/polaris-icons'
import func from '@/util/func'
import { severityTone } from './tones'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'
import './AgentDetail.css'

const DESCRIPTION_LIMIT = 255

function Description({ text }) {
    const truncated = text.length > DESCRIPTION_LIMIT
    const shown = truncated ? `${text.slice(0, DESCRIPTION_LIMIT).trimEnd()}...` : text
    const body = (
        <Box maxWidth="540px">
            <Text variant="bodyMd" color="subdued">{shown}</Text>
        </Box>
    )
    return truncated ? <Tooltip content={text} preferredPosition="below">{body}</Tooltip> : body
}

function AgentHeaderCard({ header }) {
    if (!header) return null

    const tone = severityTone(header.severity)

    const metadata = [
        header.createdAt ? `Created · ${func.prettifyEpoch(header.createdAt)}` : null,
        header.lastActive ? `Last active · ${func.prettifyEpoch(header.lastActive)}` : null,
    ].filter(Boolean)

    return (
        <Card padding="6">
            <VerticalStack gap="4">
                <HorizontalStack align="space-between" blockAlign="start" gap="5">
                    <HorizontalStack align="start" gap="4" blockAlign="center" wrap={false}>
                        <Box background={tone.background} borderRadius="3" padding="4">
                            <Icon source={AutomationMajor} color={tone.icon} />
                        </Box>
                        <VerticalStack gap="2">
                            <HorizontalStack align="start" gap="2" blockAlign="center">
                                <Text variant="headingLg" as="h1">{header.name}</Text>
                                {header.severity && <SeverityBadge severity={header.severity} />}
                                {header.environment && <Badge>{header.environment.toLowerCase()}</Badge>}
                            </HorizontalStack>
                            {header.description && <Description text={header.description} />}
                        </VerticalStack>
                    </HorizontalStack>
                    <VerticalStack gap="0" inlineAlign="end">
                        <Text variant="headingXl" as="p" fontWeight="bold">
                            {header.riskScore}
                            <Text variant="bodyMd" as="span" color="subdued" fontWeight="medium">/100</Text>
                        </Text>
                        <Text variant="bodySm" color="subdued">risk score</Text>
                    </VerticalStack>
                </HorizontalStack>
                {metadata.length > 0 && (
                    <Box borderBlockStartWidth="1" borderColor="border-subdued" paddingBlockStart="4">
                        <HorizontalStack gap="5">
                            {metadata.map((item) => (
                                <Text key={item} variant="bodySm" color="subdued">{item}</Text>
                            ))}
                        </HorizontalStack>
                    </Box>
                )}
            </VerticalStack>
        </Card>
    )
}

export default AgentHeaderCard

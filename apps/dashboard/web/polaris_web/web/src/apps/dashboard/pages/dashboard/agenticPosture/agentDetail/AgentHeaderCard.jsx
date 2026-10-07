import { useNavigate } from 'react-router-dom'
import { Badge, Box, Button, Card, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { AutomationMajor } from '@shopify/polaris-icons'
import func from '@/util/func'
import { severityTone } from './tones'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'
import DetailGrid from '../../../observe/agentic/DetailGrid'
import { NumberCardsRow } from '../../new_components/PostureShared'
import { ctaHref } from './cta'
import TooltipText from '../../../../components/shared/TooltipText'
import './AgentDetail.css'

function redTeamScanSummary(redTeam, hasMaliciousActivity) {
    if (!redTeam) return { text: '-', isWarning: false }
    if (!redTeam.scanned) {
        return {
            text: hasMaliciousActivity ? 'Not run for this agent, and it has malicious activity' : 'Not run for this agent',
            isWarning: hasMaliciousActivity,
        }
    }
    const when = redTeam.lastScannedAt ? func.prettifyEpoch(redTeam.lastScannedAt) : 'unknown date'
    return { text: `Last run ${when} · ${redTeam.openFindings || 0} open findings`, isWarning: false }
}

function AgentHeaderCard({ header, redTeam, guardrailActivity, tools }) {
    const navigate = useNavigate()
    if (!header) return null

    const tone = severityTone(header.severity)

    const metadata = [
        header.createdAt ? `Created · ${func.prettifyEpoch(header.createdAt)}` : null,
        header.lastActive ? `Last active · ${func.prettifyEpoch(header.lastActive)}` : null,
    ].filter(Boolean)

    const maliciousEvents = guardrailActivity && guardrailActivity.available !== false ? (guardrailActivity.total || 0) : null
    const privilegedToolCount = (tools || []).filter((t) => t.privileged).length
    const scanSummary = redTeamScanSummary(redTeam, (maliciousEvents || 0) > 0)

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
                            {header.description && (
                                <Box maxWidth="540px">
                                    <TooltipText
                                        text={header.description}
                                        tooltip={header.description}
                                        textProps={{ variant: 'bodyMd', color: 'subdued' }}
                                    />
                                </Box>
                            )}
                        </VerticalStack>
                    </HorizontalStack>
                    <VerticalStack gap="3" inlineAlign="end">
                        <VerticalStack gap="0" inlineAlign="end">
                            <Text variant="headingXl" as="p" fontWeight="bold">
                                {header.riskScore}
                                <Text variant="bodyMd" as="span" color="subdued" fontWeight="medium">/100</Text>
                            </Text>
                            <Text variant="bodySm" color="subdued">risk score</Text>
                        </VerticalStack>
                        {(header.ctas || []).length > 0 && (
                            <HorizontalStack gap="2" wrap>
                                {header.ctas.map((cta) => (
                                    <Button key={cta.id} primary={cta.primary} onClick={() => navigate(ctaHref(cta))}>
                                        {cta.label}
                                    </Button>
                                ))}
                            </HorizontalStack>
                        )}
                    </VerticalStack>
                </HorizontalStack>

                <NumberCardsRow metrics={[
                    { key: 'openFindings', label: 'Open red-team findings', value: redTeam ? redTeam.openFindings || 0 : '-' },
                    { key: 'maliciousEvents', label: 'Guardrail Violations, 90d', value: maliciousEvents === null ? '-' : maliciousEvents },
                    { key: 'privilegedTools', label: 'Privileged tools', value: privilegedToolCount },
                ]} />

                <DetailGrid columns={4} items={[
                    { label: 'Host', value: header.host },
                    { label: 'Top issue', value: header.topIssue },
                    { label: 'Guardrail coverage', value: header.guardrailCoverage, isWarning: header.guardrailCoverage === 'Not covered' },
                    { label: 'Red-team scan', value: scanSummary.text, isWarning: scanSummary.isWarning },
                ]} />

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

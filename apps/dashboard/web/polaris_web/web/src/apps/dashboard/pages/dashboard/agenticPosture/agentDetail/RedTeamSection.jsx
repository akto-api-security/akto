import { useNavigate } from 'react-router-dom'
import { Box, Button, Card, DataTable, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import { RefreshMajor } from '@shopify/polaris-icons'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'
import func from '@/util/func'
import './AgentDetail.css'

function Heading({ children }) {
    return (
        <span className="ad-label">
            <Text as="span" variant="bodySm" fontWeight="semibold" color="subdued">{children}</Text>
        </span>
    )
}

// Neither DetailGrid (a compact label/value pair) nor RuntimeActivitySection's own StatTile (a
// card-wrapped single value) carries a muted subtitle under the number — this section's stat row
// needs both a big number and a breakdown line beneath it, side by side in one shared card.
function Stat({ label, value, secondary }) {
    return (
        <VerticalStack gap="1">
            <span className="ad-label">
                <Text variant="bodySm" color="subdued">{label}</Text>
            </span>
            <Text variant="headingXl" fontWeight="bold">{value}</Text>
            {secondary && <Text variant="bodySm" color="subdued">{secondary}</Text>}
        </VerticalStack>
    )
}

// Same shape InsightResult.Cta already carries everywhere else a drill builds one — route plus a
// flat params map turned into a query string, never a second URL-building convention.
function ctaHref(cta) {
    if (!cta.params) return cta.route
    const qs = new URLSearchParams(cta.params).toString()
    if (!qs) return cta.route
    return cta.route + (cta.route.includes('?') ? '&' : '?') + qs
}

function absoluteDate(epoch) {
    if (!epoch) return null
    return new Date(epoch * 1000).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function RedTeamSection({ redTeam }) {
    const navigate = useNavigate()
    if (!redTeam) return null

    const runScanCta = (redTeam.ctas || []).find((c) => c.id === 'run_red_team')
    const viewAllCta = (redTeam.ctas || []).find((c) => c.id === 'view_findings')

    return (
        <Box id="redTeam" paddingBlockStart="2">
            <VerticalStack gap="4">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingMd">Red Teaming</Text>
                    {runScanCta && (
                        <Button icon={RefreshMajor} onClick={() => navigate(ctaHref(runScanCta))}>
                            {runScanCta.label}
                        </Button>
                    )}
                </HorizontalStack>

                {!redTeam.scanned ? (
                    <Card padding="5">
                        <Text variant="bodyMd" color="subdued" alignment="center">
                            This agent hasn't been red-teamed yet.
                        </Text>
                    </Card>
                ) : (
                    <Card padding="0">
                        <Box padding="5">
                            <HorizontalGrid columns={2} gap="8">
                                <Stat
                                    label="Open findings"
                                    value={String(redTeam.openFindings || 0)}
                                    secondary={redTeam.severityBreakdown}
                                />
                                <Stat
                                    label="Last scanned"
                                    value={redTeam.lastScannedAt ? func.prettifyEpoch(redTeam.lastScannedAt) : '-'}
                                    secondary={absoluteDate(redTeam.lastScannedAt)}
                                />
                            </HorizontalGrid>
                        </Box>
                        {(redTeam.findings || []).length > 0 && (
                            <DataTable
                                columnContentTypes={['text', 'text', 'text']}
                                headings={[
                                    <Heading key="test">Test</Heading>,
                                    <Heading key="severity">Severity</Heading>,
                                    <Heading key="lastSeen">Last seen</Heading>,
                                ]}
                                rows={redTeam.findings.map((finding) => [
                                    <Text as="span" variant="bodyMd" fontWeight="medium">{finding.test}</Text>,
                                    <SeverityBadge severity={finding.severity} />,
                                    <Text as="span" variant="bodyMd" color="subdued">{func.prettifyEpoch(finding.lastSeen)}</Text>,
                                ])}
                                verticalAlign="middle"
                                increasedTableDensity
                                hideScrollIndicator
                            />
                        )}
                        {viewAllCta && (
                            <Box padding="3" borderBlockStartWidth="1" borderColor="border-subdued">
                                <Button plain onClick={() => navigate(ctaHref(viewAllCta))}>
                                    {viewAllCta.label}
                                </Button>
                            </Box>
                        )}
                    </Card>
                )}
            </VerticalStack>
        </Box>
    )
}

export default RedTeamSection

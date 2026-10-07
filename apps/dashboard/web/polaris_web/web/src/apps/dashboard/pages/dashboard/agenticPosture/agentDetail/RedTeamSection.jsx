import { useNavigate } from 'react-router-dom'
import { Box, Button, Card, HorizontalGrid, Text, VerticalStack } from '@shopify/polaris'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'
import { ctaHref } from './cta'
import SimpleIndexTable from '../../../../components/tables/SimpleIndexTable'
import func from '@/util/func'
import './AgentDetail.css'

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

function absoluteDate(epoch) {
    if (!epoch) return null
    return new Date(epoch * 1000).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

const FINDINGS_HEADINGS = [
    { title: 'Test' },
    { title: 'Severity' },
    { title: 'Last seen' },
]

function RedTeamSection({ redTeam }) {
    const navigate = useNavigate()
    if (!redTeam) return null

    const viewAllCta = (redTeam.ctas || []).find((c) => c.id === 'view_findings')

    return (
        <Box id="redTeam" paddingBlockStart="2">
            <VerticalStack gap="4">
                <Text variant="headingMd">Red Teaming</Text>

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
                            <SimpleIndexTable
                                resourceName={{ singular: 'finding', plural: 'findings' }}
                                headings={FINDINGS_HEADINGS}
                                rows={redTeam.findings.map((finding) => [
                                    <Text variant="bodyMd" fontWeight="medium">{finding.test}</Text>,
                                    <SeverityBadge severity={finding.severity} />,
                                    <Text variant="bodyMd" color="subdued">{func.prettifyEpoch(finding.lastSeen)}</Text>,
                                ])}
                                getRowId={(cells, index) => `${redTeam.findings[index].test}-${index}`}
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

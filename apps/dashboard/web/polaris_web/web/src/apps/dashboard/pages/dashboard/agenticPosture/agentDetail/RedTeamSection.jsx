import { useNavigate } from 'react-router-dom'
import { Box, Button, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'
import { ctaHref } from './cta'
import SimpleIndexTable from '../../../../components/tables/SimpleIndexTable'
import func from '@/util/func'
import './AgentDetail.css'

function SeverityCounts({ counts }) {
    if (!counts || counts.length === 0) return null
    return (
        <HorizontalStack gap="3">
            {counts.map((sc) => (
                <HorizontalStack key={sc.severity} gap="1" blockAlign="center">
                    <SeverityBadge severity={sc.severity} />
                    <Text variant="bodyMd" color="subdued">{sc.count}</Text>
                </HorizontalStack>
            ))}
        </HorizontalStack>
    )
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
                            <VerticalStack gap="2">
                                <span className="ad-label">
                                    <Text variant="bodySm" color="subdued">Open findings</Text>
                                </span>
                                <Text variant="headingXl" fontWeight="bold">{redTeam.openFindings || 0}</Text>
                                <SeverityCounts counts={redTeam.severityCounts} />
                            </VerticalStack>
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

import { Badge, Box, Card, HorizontalStack, IndexTable, Link, Text } from '@shopify/polaris'
import { riskBand } from '../../agenticPostureShared'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'
import CustomProgressBar from '../../new_components/CustomProgressBar'

const BAR_COLOR_FOR_TONE = {
    critical: 'var(--p-color-bg-critical-strong)',
    warning: 'var(--p-color-bg-warning-strong-experimental)',
    success: 'var(--p-color-bg-success-strong)',
}

const HEADINGS = [
    { title: 'Rank' },
    { title: 'Agent' },
    { title: 'Environment' },
    { title: 'Score' },
    { title: 'Issue' },
    { title: 'Severity' },
]

// Ranked by blast radius — real Polaris IndexTable, not the app's AgGrid/GithubServerTable
// wrappers (neither is Polaris-pure/CSS-free, see the posture plan's component audit).
function HighestRiskAgentsTable({ agents, onOpenAgent }) {
    const rows = agents || []
    if (rows.length === 0) {
        return (
            <Card>
                <Box padding="4">
                    <Text variant="bodyMd" color="subdued" alignment="center">No agents scored in this environment.</Text>
                </Box>
            </Card>
        )
    }
    return (
        <IndexTable
            resourceName={{ singular: 'agent', plural: 'agents' }}
            itemCount={rows.length}
            headings={HEADINGS}
            selectable={false}
        >
            {rows.map((agent, index) => {
                const band = riskBand(agent.score)
                return (
                    <IndexTable.Row id={agent.groupKey} key={agent.groupKey} position={index}>
                        <IndexTable.Cell>
                            <Text variant="bodySm" color="subdued">{agent.rank}</Text>
                        </IndexTable.Cell>
                        <IndexTable.Cell>
                            <Link onClick={() => onOpenAgent(agent.groupKey)} removeUnderline>
                                <Text variant="bodyMd" fontWeight="semibold">{agent.name}</Text>
                            </Link>
                        </IndexTable.Cell>
                        <IndexTable.Cell>
                            <Badge>{agent.environment}</Badge>
                        </IndexTable.Cell>
                        <IndexTable.Cell>
                            <Box minWidth="140px">
                                <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                                    <Box width="90px">
                                        <CustomProgressBar progress={agent.score} height="8px" borderRadius="var(--p-border-radius-1)"
                                            backgroundColor="var(--p-color-bg-strong)"
                                            topColor={band ? BAR_COLOR_FOR_TONE[band.tone] : 'var(--p-color-bg-primary)'} />
                                    </Box>
                                    <Text variant="bodySm" fontWeight="semibold">{agent.score}</Text>
                                </HorizontalStack>
                            </Box>
                        </IndexTable.Cell>
                        <IndexTable.Cell>
                            <Text variant="bodySm" color="subdued">{agent.issue}</Text>
                        </IndexTable.Cell>
                        <IndexTable.Cell>
                            <SeverityBadge severity={agent.severity} />
                        </IndexTable.Cell>
                    </IndexTable.Row>
                )
            })}
        </IndexTable>
    )
}

export default HighestRiskAgentsTable

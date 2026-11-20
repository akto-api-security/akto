import { Card, HorizontalGrid, Text, VerticalStack } from '@shopify/polaris'
import { KpiCard } from '../../guardrails/overview/OverviewKpiRow'

const count = (n) => n.toLocaleString()

// Shadow AI is unreviewed AI that also shows a risk signal, so the note says which signal matters most.
function shadowNote({ value, sensitive }) {
    if (value === 0) return { note: 'None found', color: 'success' }
    if (sensitive > 0) return { note: `${count(sensitive)} with sensitive data`, color: 'critical' }
    return { note: 'Unreviewed, with risk signals', color: 'warning' }
}

function DiscoveryKpiRow({ kpis, onOpenInventory }) {
    const { agents, mcpServers, tools, shadowAi } = kpis
    const shadow = shadowNote(shadowAi)
    return (
        <HorizontalGrid columns={{ xs: 1, sm: 2, lg: 4 }} gap="4">
            <KpiCard
                title="Agents"
                value={count(agents.value)}
                note={agents.newThisWeek > 0 ? `+${count(agents.newThisWeek)} this week` : 'None new this week'}
                onClick={onOpenInventory}
            />
            <KpiCard
                title="MCP servers"
                value={count(mcpServers.value)}
                note={mcpServers.unreviewed > 0 ? `${count(mcpServers.unreviewed)} unreviewed` : 'All reviewed'}
                noteColor={mcpServers.unreviewed > 0 ? 'warning' : 'subdued'}
                onClick={onOpenInventory}
            />
            <KpiCard title="Tools" value={count(tools.value)} note={`${count(tools.privileged)} privileged`} />
            <KpiCard title="Shadow AI" value={count(shadowAi.value)} note={shadow.note} noteColor={shadow.color} />
        </HorizontalGrid>
    )
}

// How many things were found, and how few of them need action.
export function DiscoveryFunnel({ funnel }) {
    const steps = [
        [funnel.assets, 'assets discovered'],
        [funnel.agents, 'AI agents'],
        [funnel.highRisk, 'assets with a high risk score'],
        [funnel.todos, 'things to do first'],
    ]
    return (
        <Card padding="4">
            <HorizontalGrid columns={{ xs: 2, md: 4 }} gap="4">
                {steps.map(([value, label]) => (
                    <VerticalStack key={label} gap="0">
                        <Text variant="heading2xl" as="p">{count(value)}</Text>
                        <Text variant="bodySm" color="subdued">{label}</Text>
                    </VerticalStack>
                ))}
            </HorizontalGrid>
        </Card>
    )
}

export default DiscoveryKpiRow

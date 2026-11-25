import { useMemo } from 'react'
import { Badge, Box, Button, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import BubblePlot from '../../../../components/shared/BubblePlot'
import BarGraph from '../../../../components/charts/BarGraph'
import { KpiCard } from '../../../guardrails/overview/OverviewKpiRow'
import Dot from './Dot'
import MiniBar from './MiniBar'
import Panel from './Panel'
import { POSTURE_COLORS, QUAD_LABELS, USED_LABELS, percent, plural } from './inventoryUtils'

const DAY_SECONDS = 86400
const PLOT_HEIGHT = 300
const RISK_BANDS_TOP_DOWN = ['Critical', 'High', 'Medium', 'Low']
const TIME_TICKS = ['Used today', '7 days', '30 days', '90 days', 'Never']
// Shades the two top quadrants: risk 50 or more, split at 30 days without traffic (the 48% mark below).
const QUADRANTS = { splitX: 48, heightPct: 50, labels: ['Fix first', 'Revoke access', 'Monitor', 'Retire'] }
const NEVER_X = 96
const MAX_DAYS = 120
const SOURCE_CARDS = 3
const PART_ALPHAS = ['', 'B3', '80', '59']
const NO_LEGEND = { legend: { enabled: false }, xAxis: { labels: { enabled: false }, tickLength: 0 } }
const WEEK_COLORS = { now: '#6D3BEF', before: '#E1E3E5' }
const STALE_COLOR = '#B98900'
const ACTIVE_COLOR = '#6D3BEF'
const HIGH_COLOR = POSTURE_COLORS.atRisk
const REST_COLOR = '#C9CCCF'
const BOX_TOKENS = {
    hiA: { background: 'bg-critical-subdued', borderColor: 'border-critical', action: 'Fix first →', color: 'critical' },
    hiD: { background: 'bg-caution-subdued', borderColor: 'border-caution', action: 'Revoke access →', color: 'warning' },
    loA: { background: 'bg', borderColor: 'border-subdued', action: 'Monitor →', color: 'subdued' },
    loD: { background: 'bg', borderColor: 'border-subdued', action: 'Retire →', color: 'subdued' },
}

// Left is used today, right is never. The square root spreads the recent days, so 7 and 30 days land near the 25% and 50% ticks.
function xOf(asset, generatedAt) {
    if (!asset.seenTs) return NEVER_X
    const days = Math.min(Math.max((generatedAt - asset.seenTs) / DAY_SECONDS, 0), MAX_DAYS)
    return 2 + Math.sqrt(days / MAX_DAYS) * 90
}

// The same asset always lands on the same spot, so assets seen at the same time do not sit on one line.
const spread = (id, steps, size) => ((Math.abs(id) % steps) - (steps - 1) / 2) * size

function bubblesOf(points, generatedAt, colors, onOpen) {
    return points.map((asset) => ({
        key: asset.id,
        x: Math.min(98, Math.max(2, xOf(asset, generatedAt) + spread(asset.id, 7, 0.8))),
        y: Math.min(98, Math.max(2, asset.risk + spread(asset.id, 5, 0.8))),
        radius: 3 + Math.min(asset.tools, 9) / 2,
        fill: colors[asset.platform],
        stroke: '#FFFFFF',
        tip: `${asset.name} · risk ${asset.risk}`,
        label: null,
        priority: asset.risk,
        onClick: () => onOpen(asset.id),
    }))
}

// Text on a tinted box with the Polaris tokens, used for the quadrant counts and the insight lines.
function Insight({ text, button, onClick }) {
    return (
        <Box background="bg-magic-subdued" padding="3" borderRadius="2">
            <VerticalStack gap="3">
                <Text variant="bodyMd" as="p">{text}</Text>
                <Box><Button onClick={onClick}>{button}</Button></Box>
            </VerticalStack>
        </Box>
    )
}

function Kpis({ counts, onFilter }) {
    return (
        <HorizontalGrid columns={{ xs: 1, sm: 2, md: 3, lg: 5 }} gap="4">
            <KpiCard title="Agents" value={counts.total} note={`+${counts.newThisWeek} this week`} onClick={() => onFilter({})} />
            <KpiCard title="Risk score 50+" value={counts.high} note={`${counts.critical} critical (75+)`} noteColor="critical" onClick={() => onFilter({ flag: 'high' })} />
            <KpiCard title="No owning team" value={counts.noTeam} note={`${counts.noTeamHigh} of them high risk`} noteColor="critical" onClick={() => onFilter({ flag: 'noTeam' })} />
            <KpiCard title="No guardrail policy" value={counts.noPolicy} note={`${percent(counts.noPolicy, counts.total)}% of agents`} noteColor="warning" onClick={() => onFilter({ flag: 'noPolicy' })} />
            <KpiCard title="Dormant with access" value={counts.dormant} note="Unused 30+ days" noteColor="warning" onClick={() => onFilter({ flag: 'dormant' })} />
        </HorizontalGrid>
    )
}

function RiskVsLastUsed({ data, colors, onFilter, onOpen }) {
    const bubbles = useMemo(() => bubblesOf(data.points, data.generatedAt, colors, onOpen), [data, colors, onOpen])
    const legend = (
        <HorizontalStack gap="3" blockAlign="center">
            {data.platforms.map((p) => (
                <HorizontalStack key={p.name} gap="1" blockAlign="center">
                    <Dot color={colors[p.name]} size={10} />
                    <Text as="span" variant="bodySm" color="subdued">{p.name}</Text>
                </HorizontalStack>
            ))}
            <Text as="span" variant="bodySm" color="subdued">Size = tools</Text>
        </HorizontalStack>
    )
    return (
        <Panel title="Risk vs. last used" aside={legend} empty={data.points.length === 0} emptyText="No agents to plot yet.">
            <VerticalStack gap="3">
                <BubblePlot bubbles={bubbles} height={PLOT_HEIGHT} bands={RISK_BANDS_TOP_DOWN.length} bandLabels={RISK_BANDS_TOP_DOWN} tickLabels={TIME_TICKS} quadrants={QUADRANTS} />
                <HorizontalGrid columns={{ xs: 1, sm: 2, lg: 4 }} gap="2">
                    {Object.keys(BOX_TOKENS).map((key) => {
                        const { background, borderColor, action, color } = BOX_TOKENS[key]
                        return (
                            // A Box given a className drops its own styling, so the clickable wrapper is a plain Box around the styled one.
                            <Box key={key} className="cursor-pointer" onClick={() => onFilter({ quad: key })}>
                                <Box background={background} borderColor={borderColor} borderWidth="1" borderRadius="2" padding="3">
                                    <VerticalStack gap="05">
                                        <Text variant="bodySm" color="subdued">{QUAD_LABELS[key]}</Text>
                                        <Text variant="headingLg" as="p">{data.quads[key]}</Text>
                                        <Text variant="bodySm" fontWeight="semibold" color={color}>{action}</Text>
                                    </VerticalStack>
                                </Box>
                            </Box>
                        )
                    })}
                </HorizontalGrid>
            </VerticalStack>
        </Panel>
    )
}

// The three biggest platforms, with their types, the counts that matter and one line on what stands out.
function SourceCard({ platform, color, onFilter }) {
    const { name, total, high, noPolicy, dormant, types } = platform
    const stats = [
        { count: high, label: 'risk 50+', flag: 'high', tone: 'critical' },
        { count: noPolicy, label: 'no policy', flag: 'noPolicy' },
        { count: dormant, label: 'dormant', flag: 'dormant' },
    ]
    const [top] = [
        [noPolicy, 'have no guardrail policy'],
        [dormant, 'are dormant but still have access'],
        [high, 'have a risk score of 50 or more'],
    ].sort((a, b) => b[0] - a[0])
    return (
        <Panel title={<HorizontalStack gap="2" blockAlign="center"><Dot color={color} size={10} /><Text variant="headingMd" as="h2">{name}</Text></HorizontalStack>} aside={<Text variant="headingLg" as="p">{total}</Text>}>
            <VerticalStack gap="3">
                <MiniBar max={total} height="8px" title={name} parts={types.map((t, i) => ({ label: t.name, count: t.count, color: `${color}${PART_ALPHAS[i % PART_ALPHAS.length]}`, tooltip: `${t.name}: ${t.count}` }))} />
                <HorizontalStack gap="3">
                    {types.map((t) => (
                        <Box key={t.name} className="cursor-pointer" onClick={() => onFilter({ platform: name, type: t.name })}>
                            <Text as="span" variant="bodySm" color="subdued">{t.name} <Text as="span" fontWeight="bold">{t.count}</Text></Text>
                        </Box>
                    ))}
                </HorizontalStack>
                <HorizontalGrid columns={3} gap="2">
                    {stats.map((stat) => (
                        <Box key={stat.label} className="cursor-pointer" onClick={() => onFilter({ platform: name, flag: stat.flag })}>
                            <Box background="bg-subdued" borderRadius="2" padding="2">
                                <VerticalStack gap="0">
                                    <Text as="span" fontWeight="bold" color={stat.count > 0 ? stat.tone : undefined}>{stat.count}</Text>
                                    <Text as="span" variant="bodySm" color="subdued">{stat.label}</Text>
                                </VerticalStack>
                            </Box>
                        </Box>
                    ))}
                </HorizontalGrid>
                <Text variant="bodySm" as="p">{top[0] > 0 ? `${percent(top[0], total)}% of ${name} agents ${top[1]}.` : `No open flags on ${name} agents.`}</Text>
            </VerticalStack>
        </Panel>
    )
}

// Agents per team, the high-risk ones in red. "No owning team" leads the list.
function Ownership({ teams, counts, onFilter }) {
    const most = Math.max(1, ...teams.map((t) => t.total))
    return (
        <Panel title="Ownership" aside={<Text variant="bodySm" color="subdued">Agents · high risk</Text>} empty={teams.length === 0} emptyText="No teams yet.">
            <VerticalStack gap="2">
                {teams.map((team) => (
                    <Box key={team.name} className="cursor-pointer" onClick={() => onFilter({ team: team.none ? 'none' : team.name })}>
                        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                            <Box width="100%"><Text as="span" fontWeight={team.none ? 'semibold' : 'regular'} color={team.none ? 'critical' : undefined}>{team.name}</Text></Box>
                            <Box minWidth="64px">
                                <MiniBar max={most} title={`${team.name}: ${team.total} agents, ${team.high} high risk`} parts={[{ label: 'high', count: team.high, color: HIGH_COLOR }, { label: 'other', count: team.total - team.high, color: REST_COLOR }]} />
                            </Box>
                            <Box minWidth="28px"><Text as="p" alignment="end">{team.total}</Text></Box>
                            <Box minWidth="24px"><Text as="p" alignment="end" color="critical">{team.high}</Text></Box>
                        </HorizontalStack>
                    </Box>
                ))}
                <Insight
                    text={`${plural(counts.noTeam, 'agent')} ${counts.noTeam === 1 ? 'has' : 'have'} no owning team, ${counts.noTeamHigh} of them high risk.${counts.sharedByThree > 0 ? ` ${counts.sharedByThree} more ${counts.sharedByThree === 1 ? 'is' : 'are'} shared by 3 teams, with no single accountable owner.` : ''}`}
                    button="View unowned"
                    onClick={() => onFilter({ flag: 'noTeam' })}
                />
            </VerticalStack>
        </Panel>
    )
}

function Tools({ tools, onFilter }) {
    const most = Math.max(1, ...tools.map((t) => t.assets))
    const top = tools.find((t) => t.privileged)
    return (
        <Panel title="Tools across agents" aside={<Text variant="bodySm" color="subdued">Agents · high risk</Text>} empty={tools.length === 0} emptyText="No tools discovered yet.">
            <VerticalStack gap="2">
                {tools.map((tool) => (
                    <Box key={tool.name} className="cursor-pointer" onClick={() => onFilter({ tool: tool.name })}>
                        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                            <Box width="100%">
                                <HorizontalStack gap="2" blockAlign="center">
                                    <Text as="span">{tool.name}</Text>
                                    {tool.privileged && <Badge status="critical">privileged</Badge>}
                                </HorizontalStack>
                            </Box>
                            <Box minWidth="64px">
                                <MiniBar max={most} title={`${tool.name}: ${tool.assets} agents, ${tool.high} high risk`} parts={[{ label: 'high', count: tool.high, color: HIGH_COLOR }, { label: 'other', count: tool.assets - tool.high, color: REST_COLOR }]} />
                            </Box>
                            <Box minWidth="28px"><Text as="p" alignment="end">{tool.assets}</Text></Box>
                            <Box minWidth="24px"><Text as="p" alignment="end" color="critical">{tool.high}</Text></Box>
                        </HorizontalStack>
                    </Box>
                ))}
                {top && (
                    <Insight
                        text={`${top.name} is on ${plural(top.assets, 'agent')}, ${top.high} of them with a risk score of 50 or more.`}
                        button="View agents"
                        onClick={() => onFilter({ tool: top.name })}
                    />
                )}
            </VerticalStack>
        </Panel>
    )
}

function AgeAndActivity({ data, onFilter }) {
    const { createdPerWeek, lastUsed, counts } = data
    const weeks = createdPerWeek.length
    const bars = createdPerWeek.map((value, index) => {
        const ago = weeks - 1 - index
        return { text: ago === 0 ? 'This week' : `${ago} weeks ago`, value, id: ago, color: ago === 0 ? WEEK_COLORS.now : WEEK_COLORS.before }
    })
    const most = Math.max(1, ...lastUsed.map((bucket) => bucket.count))
    return (
        <Panel title="Age and activity" empty={counts.total === 0} emptyText="No agents yet.">
            <VerticalStack gap="4">
                <VerticalStack gap="1">
                    <Text variant="bodySm" color="subdued">Created per week, last 12 weeks</Text>
                    <BarGraph data={bars} height={90} barWidth={16} barGap={4} showYAxis={false} backgroundColor="transparent" extraChartOptions={NO_LEGEND} onBarClick={(name, custom) => onFilter({ week: custom.id })} />
                    <HorizontalStack align="space-between">
                        <Text variant="bodySm" color="subdued">12 weeks ago</Text>
                        <Text variant="bodySm" color="subdued">This week</Text>
                    </HorizontalStack>
                </VerticalStack>
                <VerticalStack gap="2">
                    <Text variant="bodySm" color="subdued">Last used</Text>
                    {lastUsed.map((bucket) => (
                        <Box key={bucket.key} className="cursor-pointer" onClick={() => onFilter({ used: bucket.key })}>
                            <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                                <Box minWidth="80px"><Text as="span">{USED_LABELS[bucket.key]}</Text></Box>
                                <Box width="100%">
                                    <MiniBar max={most} title={`${USED_LABELS[bucket.key]}: ${bucket.count}`} parts={[{ label: bucket.key, count: bucket.count, color: ['q', 'old', 'never'].includes(bucket.key) ? STALE_COLOR : ACTIVE_COLOR }]} />
                                </Box>
                                <Box minWidth="32px"><Text as="p" alignment="end">{bucket.count}</Text></Box>
                            </HorizontalStack>
                        </Box>
                    ))}
                </VerticalStack>
                <Insight
                    text={`${plural(counts.newThisWeek, 'agent')} created this week. ${counts.dormant} have had no traffic in 30 days but still have access.`}
                    button="View dormant"
                    onClick={() => onFilter({ flag: 'dormant' })}
                />
            </VerticalStack>
        </Panel>
    )
}

function InventoryAnalytics({ data, colors, onFilter, onOpen }) {
    const biggest = [...data.platforms].sort((a, b) => b.total - a.total).slice(0, SOURCE_CARDS)
    return (
        <VerticalStack gap="4">
            <Kpis counts={data.counts} onFilter={onFilter} />
            <HorizontalGrid columns={{ xs: 1, lg: ['twoThirds', 'oneThird'] }} gap="4">
                <RiskVsLastUsed data={data} colors={colors} onFilter={onFilter} onOpen={onOpen} />
                <VerticalStack gap="4">
                    {biggest.map((platform) => <SourceCard key={platform.name} platform={platform} color={colors[platform.name]} onFilter={onFilter} />)}
                </VerticalStack>
            </HorizontalGrid>
            <HorizontalGrid columns={{ xs: 1, md: 2, lg: 3 }} gap="4">
                <Ownership teams={data.teams} counts={data.counts} onFilter={onFilter} />
                <Tools tools={data.tools} onFilter={onFilter} />
                <AgeAndActivity data={data} onFilter={onFilter} />
            </HorizontalGrid>
        </VerticalStack>
    )
}

export default InventoryAnalytics

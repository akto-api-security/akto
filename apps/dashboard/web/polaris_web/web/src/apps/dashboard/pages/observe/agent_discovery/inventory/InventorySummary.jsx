import { Badge, Box, Button, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import func from '@/util/func'
import DonutChart from '../../../../components/shared/DonutChart'
import SegmentBar from '../../../../components/shared/SegmentBar'
import LineChart from '../../../../components/charts/LineChart'
import Dot from './Dot'
import MiniBar from './MiniBar'
import { revokeIdentity } from './inventoryApi'
import { POSTURE_COLORS, ago, lastUsedText, percent, plural } from './inventoryUtils'

const DONUT_SIZE = 168
const PLATFORM_DONUT_SIZE = 76
const TREND_COLORS = { total: '#6D3BEF', atRisk: POSTURE_COLORS.atRisk }
const TREND_OPTIONS = {
    xAxis: { type: 'datetime', title: { text: undefined }, gridLineWidth: 0, dateTimeLabelFormats: { day: '%b %e', week: '%b %e' } },
    yAxis: { title: { text: undefined }, min: 0, gridLineColor: '#F3F4F6' },
    legend: { enabled: true, align: 'right', verticalAlign: 'top', symbolRadius: 6, itemStyle: { fontWeight: 'normal' } },
    tooltip: { shared: true, xDateFormat: '%b %e, %Y' },
}
const WEEKS_BACK = 4
const CARD_ACCENT = { critical: 'border-critical', caution: 'border-caution' }
const HEALTHY_FADED = `${POSTURE_COLORS.healthy}8C`

const donutOf = (posture, counts) => Object.fromEntries(posture.map((group) => [group.label, { text: counts[group.key], color: POSTURE_COLORS[group.key] }]))

// A row of the hero legend: opens the agents in that group.
function PostureRow({ group, total, onOpen }) {
    return (
        <Box className="cursor-pointer" onClick={onOpen}>
            <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                <Dot color={POSTURE_COLORS[group.key]} size={12} square />
                <Box width="100%"><Text as="span">{group.label}</Text></Box>
                <Text as="span" fontWeight="semibold">{group.count}</Text>
                <Box minWidth="40px"><Text as="p" color="subdued" alignment="end">{percent(group.count, total)}%</Text></Box>
            </HorizontalStack>
        </Box>
    )
}

function Hero({ data, onFilter }) {
    const { counts, posture, trend } = data
    const byLabel = Object.fromEntries(posture.map((group) => [group.label, group.key]))
    const donut = donutOf(posture, Object.fromEntries(posture.map((group) => [group.key, group.count])))
    const [atRisk, attention] = posture
    const series = [
        { name: 'All agents', color: TREND_COLORS.total, data: trend.map((p) => [p.ts * 1000, p.total]) },
        { name: 'At risk', color: TREND_COLORS.atRisk, data: trend.map((p) => [p.ts * 1000, p.atRisk]) },
    ]
    const last = trend[trend.length - 1]
    const before = trend[trend.length - 1 - WEEKS_BACK]
    return (
        <Card padding="5">
            <HorizontalGrid columns={{ xs: 1, md: 2 }} gap="6">
                <HorizontalStack gap="6" blockAlign="center" wrap={false}>
                    <Box minWidth={`${DONUT_SIZE + 20}px`}>
                        <DonutChart
                            data={donut}
                            size={DONUT_SIZE}
                            pieInnerSize="72%"
                            title={String(counts.total)}
                            subtitle="agents"
                            centered
                            titleFontSize={32}
                            titleFontWeight="700"
                            sliceBorderWidth={0}
                            onSegmentClick={(label) => onFilter({ flag: byLabel[label] })}
                        />
                    </Box>
                    <Box width="100%">
                        <VerticalStack gap="3">
                            <Text variant="headingLg" as="h2">{atRisk.count} agents are at risk and {attention.count} need attention.</Text>
                            <VerticalStack gap="2">
                                {posture.map((group) => <PostureRow key={group.key} group={group} total={counts.total} onOpen={() => onFilter({ flag: group.key })} />)}
                            </VerticalStack>
                        </VerticalStack>
                    </Box>
                </HorizontalStack>
                <VerticalStack gap="2">
                    <Text variant="headingSm" as="h3">Agents over 12 weeks</Text>
                    <LineChart type="spline" height={170} backgroundColor="transparent" data={series} text={true} yAxisTitle="" showGridLines={true} exportingDisabled={true} defaultChartOptions={TREND_OPTIONS} />
                    {/* Past scores are not stored, so the At risk line counts the agents that are at risk now, from when each was created. */}
                    <Text variant="bodyMd" color="subdued">
                        {`${last.total - before.total} agents were added in the last ${WEEKS_BACK} weeks. At-risk agents went from ${before.atRisk} to ${last.atRisk}, by today's scores.`}
                    </Text>
                </VerticalStack>
            </HorizontalGrid>
        </Card>
    )
}

function Combos({ combos, colors, onFilter }) {
    if (combos.length === 0) return null
    return (
        <VerticalStack gap="3">
            <HorizontalStack gap="3" blockAlign="baseline">
                <Text variant="headingMd" as="h2">Risky combinations</Text>
                <Text variant="bodyMd" color="subdued">One risk factor is common. These agents have several at once.</Text>
            </HorizontalStack>
            <HorizontalGrid columns={{ xs: 1, sm: 2, lg: 4 }} gap="4">
                {combos.map((combo) => (
                    <Box key={combo.id} background="bg" borderRadius="2" shadow="md" borderBlockStartWidth="3" borderColor={CARD_ACCENT[combo.tone] || CARD_ACCENT.caution} padding="4">
                        <VerticalStack gap="3">
                            <HorizontalStack gap="2" blockAlign="end">
                                <Text variant="heading2xl" as="p">{combo.count}</Text>
                                <Text variant="bodyMd" color="subdued">{combo.count === 1 ? 'agent' : 'agents'}</Text>
                            </HorizontalStack>
                            <HorizontalStack gap="1">
                                {combo.parts.map((part) => <Badge key={part}>{part}</Badge>)}
                            </HorizontalStack>
                            <Text variant="bodyMd" color="subdued">{combo.why}</Text>
                            <VerticalStack gap="1">
                                <SegmentBar height="6px" segments={combo.platforms.map((p) => ({ label: p.name, count: p.count, color: colors[p.name] }))} />
                                <Text variant="bodySm" color="subdued">{combo.platforms.map((p) => `${p.name} ${p.count}`).join(' · ')}</Text>
                            </VerticalStack>
                            <Box>
                                <Button onClick={() => onFilter({ flag: combo.id, flagLabel: combo.parts.join(' + ') })}>{`Review ${plural(combo.count, 'agent')}`}</Button>
                            </Box>
                        </VerticalStack>
                    </Box>
                ))}
            </HorizontalGrid>
        </VerticalStack>
    )
}

// One card per platform: its risk donut, and how many agents of each type it has.
function Platforms({ platforms, colors, onFilter }) {
    return (
        <HorizontalGrid columns={{ xs: 1, sm: 2, lg: 3 }} gap="4">
            {platforms.map((platform) => {
                const { name, total, types } = platform
                const most = Math.max(...types.map((t) => t.count))
                const donut = donutOf(
                    [{ key: 'atRisk', label: 'At risk' }, { key: 'attention', label: 'Needs attention' }, { key: 'healthy', label: 'Healthy' }],
                    platform,
                )
                return (
                    <Card key={name} padding="4">
                        <Box className="cursor-pointer" onClick={() => onFilter({ platform: name })}>
                            <HorizontalStack gap="4" blockAlign="center" wrap={false}>
                                <Box minWidth={`${PLATFORM_DONUT_SIZE}px`}>
                                    <DonutChart data={donut} size={PLATFORM_DONUT_SIZE} pieInnerSize="70%" title={String(total)} centered titleFontSize={18} titleFontWeight="700" sliceBorderWidth={0} />
                                </Box>
                                <Box width="100%">
                                    <VerticalStack gap="1">
                                        <HorizontalStack gap="2" blockAlign="center">
                                            <Dot color={colors[name]} />
                                            <Text as="span" fontWeight="semibold">{name}</Text>
                                        </HorizontalStack>
                                        {types.map((type) => (
                                            <HorizontalStack key={type.name} gap="2" blockAlign="center" wrap={false}>
                                                <Box width="100%"><Text as="span" variant="bodySm" color="subdued">{type.name}</Text></Box>
                                                <Box minWidth="60px"><MiniBar parts={[{ label: type.name, count: type.count, color: colors[name] }]} max={most} height="4px" title={`${type.name}: ${type.count}`} /></Box>
                                                <Box minWidth="20px"><Text as="p" variant="bodySm" alignment="end">{type.count}</Text></Box>
                                            </HorizontalStack>
                                        ))}
                                    </VerticalStack>
                                </Box>
                            </HorizontalStack>
                        </Box>
                    </Card>
                )
            })}
        </HorizontalGrid>
    )
}

// "No owning team" first, then the teams with the most agents at risk. Each row opens that team's agents.
function RiskByTeam({ teams, onFilter, onAnalytics }) {
    const most = Math.max(1, ...teams.map((t) => t.total))
    return (
        <Card padding="4">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingMd" as="h2">Risk by team</Text>
                    <Button plain onClick={onAnalytics}>Analytics</Button>
                </HorizontalStack>
                {teams.map((team) => (
                    <Box key={team.name} className="cursor-pointer" onClick={() => onFilter({ team: team.none ? 'none' : team.name })}>
                        <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                            <Box minWidth="110px"><Text as="span" truncate fontWeight={team.none ? 'semibold' : 'regular'} color={team.none ? 'critical' : undefined}>{team.name}</Text></Box>
                            <Box width="100%">
                                <MiniBar
                                    max={most}
                                    height="10px"
                                    title={`${team.name}: ${team.atRisk} at risk, ${team.attention} need attention, ${team.healthy} healthy`}
                                    parts={[
                                        { label: 'atRisk', count: team.atRisk, color: POSTURE_COLORS.atRisk },
                                        { label: 'attention', count: team.attention, color: POSTURE_COLORS.attention },
                                        { label: 'healthy', count: team.healthy, color: HEALTHY_FADED },
                                    ]}
                                />
                            </Box>
                            <Box minWidth="28px"><Text as="p" alignment="end">{team.total}</Text></Box>
                        </HorizontalStack>
                    </Box>
                ))}
            </VerticalStack>
        </Card>
    )
}

function Recent({ recent, generatedAt, colors, onOpen, onFilter }) {
    return (
        <Card padding="4">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingMd" as="h2">Recently discovered</Text>
                    <Button plain onClick={() => onFilter({ flag: 'new' })}>View all</Button>
                </HorizontalStack>
                {recent.map((asset) => (
                    <HorizontalStack key={asset.id} gap="3" blockAlign="start" wrap={false}>
                        <Box paddingBlockStart="2"><Dot color={colors[asset.platform]} size={10} /></Box>
                        <Box width="100%">
                            <VerticalStack gap="0">
                                <Box><Button plain monochrome removeUnderline onClick={() => onOpen(asset.id)}><Text as="span" fontWeight="semibold">{asset.name}</Text></Button></Box>
                                <Text variant="bodySm" color="subdued">{`${ago(asset.createdTs, generatedAt)} · ${asset.platform} · ${asset.type}`}</Text>
                            </VerticalStack>
                        </Box>
                        <Box minWidth="96px">
                            {asset.policies > 0
                                ? <Text as="p" variant="bodySm" color="success" alignment="end">Policy attached</Text>
                                : <Text as="p" variant="bodySm" color="critical" alignment="end">No policy</Text>}
                        </Box>
                    </HorizontalStack>
                ))}
            </VerticalStack>
        </Card>
    )
}

// Identities unused for 30 days that still hold access; Revoke disables the identity after a confirm.
function Quiet({ quiet, generatedAt, onIdentities, onRevoked }) {
    const revoke = (identity) => func.showConfirmationModal(`Revoke ${identity.name}? It will lose its access.`, 'Revoke', async () => {
        try {
            if (!await revokeIdentity(identity.id)) throw new Error('not revoked')
            func.setToast(true, false, 'Identity revoked')
            onRevoked()
        } catch (error) {
            func.setToast(true, true, 'Failed to revoke the identity')
        }
    })
    return (
        <Card padding="4">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingMd" as="h2">Gone quiet, still has access</Text>
                    {quiet.total > 0 && <Button plain onClick={onIdentities}>{`View all ${quiet.total}`}</Button>}
                </HorizontalStack>
                {quiet.items.length === 0 && <Text variant="bodyMd" color="subdued">No identities have gone quiet in the last 30 days.</Text>}
                {quiet.items.map((identity) => (
                    <HorizontalStack key={identity.id} gap="3" blockAlign="center" wrap={false}>
                        <Box width="100%">
                            <VerticalStack gap="0">
                                <Text as="span" fontWeight="semibold" truncate>{identity.name}</Text>
                                <Text variant="bodySm" color="subdued">{`Last used ${lastUsedText(identity.lastUsedTs, generatedAt)} · ${identity.agentName || identity.accessLevel}`}</Text>
                            </VerticalStack>
                        </Box>
                        <Button size="slim" outline destructive onClick={() => revoke(identity)}>Revoke</Button>
                    </HorizontalStack>
                ))}
            </VerticalStack>
        </Card>
    )
}

function InventorySummary({ data, colors, onFilter, onOpen, onAnalytics, onIdentities, onRevoked }) {
    return (
        <VerticalStack gap="4">
            <Hero data={data} onFilter={onFilter} />
            <Combos combos={data.combos} colors={colors} onFilter={onFilter} />
            <Platforms platforms={data.platforms} colors={colors} onFilter={onFilter} />
            <HorizontalGrid columns={{ xs: 1, md: 2, lg: 3 }} gap="4">
                <RiskByTeam teams={data.teams} onFilter={onFilter} onAnalytics={onAnalytics} />
                <Recent recent={data.recent} generatedAt={data.generatedAt} colors={colors} onOpen={onOpen} onFilter={onFilter} />
                <Quiet quiet={data.quiet} generatedAt={data.generatedAt} onIdentities={onIdentities} onRevoked={onRevoked} />
            </HorizontalGrid>
        </VerticalStack>
    )
}

export default InventorySummary

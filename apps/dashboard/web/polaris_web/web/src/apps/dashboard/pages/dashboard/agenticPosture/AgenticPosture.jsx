import { useCallback, useEffect, useMemo, useReducer, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Box, Button, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import { RefreshMajor } from '@shopify/polaris-icons'
import { produce } from 'immer'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import DateRangeFilter from '../../../components/layouts/DateRangeFilter'
import SpinnerCentered from '../../../components/progress/SpinnerCentered'
import PostureDrillFlyout from '../PostureDrillFlyout'
import func from '@/util/func'
import values from '@/util/values'
import postureDataSource from './postureDataSource'
import EnvironmentTabs from './overview/EnvironmentTabs'
import PostureScoreCard from './overview/PostureScoreCard'
import KpiGrid from './overview/KpiGrid'
import DangerousPathsSection from './overview/DangerousPathsSection'
import HighestRiskAgentsTable from './overview/HighestRiskAgentsTable'
import RiskByDomainSection from './overview/RiskByDomainSection'
import InsightCardsSection from './overview/InsightCardsSection'
import CoverageGovernanceSection from './overview/CoverageGovernanceSection'
import TopFindingsSection from './overview/TopFindingsSection'
import ChangesSinceLastWeekSection from './overview/ChangesSinceLastWeekSection'
import dashboardApi from '../api'

function SectionHeading({ title, description, action }) {
    return (
        <HorizontalStack align="space-between" blockAlign="end">
            <VerticalStack gap="1">
                <Text variant="headingMd">{title}</Text>
                {description && <Text variant="bodySm" color="subdued">{description}</Text>}
            </VerticalStack>
            {action}
        </HorizontalStack>
    )
}

function Section({ title, description, action, children }) {
    return (
        <VerticalStack gap="4">
            <SectionHeading title={title} description={description} action={action} />
            {children}
        </VerticalStack>
    )
}

// Drills that page through agents show 10 rows per page; the rest keep the flyout's default.
const DRILL_PAGE_SIZE = { highRiskAgents: 10, postureScore: 10 }
const REGENERATE_POLL_MS = 5000

const DEFAULT_DATE_RANGE = values.ranges[3] // "Last 30 days" — same default the other posture pages use

function dateRangeFromSearchParams(searchParams) {
    const sinceParam = searchParams.get('since')
    const untilParam = searchParams.get('until')
    if (sinceParam == null || untilParam == null) return null
    const sinceTs = parseInt(sinceParam, 10)
    const untilTs = parseInt(untilParam, 10)
    if (Number.isNaN(sinceTs) || Number.isNaN(untilTs)) return null
    return { title: 'Custom', alias: 'custom', period: { since: new Date(sinceTs * 1000), until: new Date(untilTs * 1000) } }
}

function AgenticPosture() {
    const navigate = useNavigate()
    const [searchParams, setSearchParams] = useSearchParams()
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        searchParams,
        (sp) => dateRangeFromSearchParams(sp) || DEFAULT_DATE_RANGE
    )
    const [pageData, setPageData] = useState({})
    const [loading, setLoading] = useState(true)
    const [selectedEnv, setSelectedEnv] = useState(() => searchParams.get('env') || 'all')
    const [regenerating, setRegenerating] = useState(false)
    const [refreshKey, setRefreshKey] = useState(0)

    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000)

    const drillState = useMemo(() => {
        const drillId = searchParams.get('drill')
        if (!drillId) return null
        return { drillId, path: searchParams.get('path') || '' }
    }, [searchParams])

    useEffect(() => {
        const since = String(getTimeEpoch('since'))
        const until = String(getTimeEpoch('until'))
        if (searchParams.get('since') === since && searchParams.get('until') === until
            && searchParams.get('env') === selectedEnv) return
        const next = new URLSearchParams(searchParams)
        next.set('since', since)
        next.set('until', until)
        next.set('env', selectedEnv)
        setSearchParams(next, { replace: true })
    }, [currDateRange, selectedEnv]) // eslint-disable-line react-hooks/exhaustive-deps

    const openDrill = (drillId, path = '') => {
        const next = new URLSearchParams(searchParams)
        next.set('drill', drillId)
        if (path) next.set('path', path); else next.delete('path')
        setSearchParams(next, { replace: true })
    }
    const navigateDrill = (state) => openDrill(state.drillId, state.path)
    const closeDrill = () => {
        const next = new URLSearchParams(searchParams)
        next.delete('drill')
        next.delete('path')
        setSearchParams(next, { replace: true })
    }

    const fetchArgusDrill = useCallback(
        (drillId, path, startTimestamp, endTimestamp, skip, limit) =>
            dashboardApi.fetchArgusPostureDrill(drillId, path, startTimestamp, endTimestamp, selectedEnv, skip, limit),
        [selectedEnv]
    )

    useEffect(() => {
        let cancelled = false
        async function load() {
            setLoading(true)
            try {
                const startTimestamp = getTimeEpoch('since')
                const endTimestamp = getTimeEpoch('until')
                const [summaryResp, cards] = await Promise.all([
                    postureDataSource.fetchPostureSummary(startTimestamp, endTimestamp, selectedEnv),
                    postureDataSource.fetchInsightCards(startTimestamp, endTimestamp),
                ])
                if (!cancelled) {
                    setPageData(summaryResp || {})
                    setInsightCards(cards)
                }
            } catch (error) {
                console.error('Error fetching posture data:', error)
                if (!cancelled) {
                    setPageData({})
                    setInsightCards([])
                }
            } finally {
                if (!cancelled) setLoading(false)
            }
        }
        load()
        return () => { cancelled = true }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [currDateRange, selectedEnv, refreshKey])

    // Resume polling if a regeneration was already running when the page opened.
    useEffect(() => {
        dashboardApi.fetchArgusPostureRegenerateStatus()
            .then((status) => { if (status && status.running) setRegenerating(true) })
            .catch(() => {})
    }, [])

    useEffect(() => {
        if (!regenerating) return
        const timer = setInterval(async () => {
            try {
                const status = await dashboardApi.fetchArgusPostureRegenerateStatus()
                if (!status || status.running) return
                setRegenerating(false)
                if (status.error) {
                    func.setToast(true, true, 'Posture regeneration failed')
                } else {
                    func.setToast(true, false, 'Posture dashboard regenerated')
                    setRefreshKey((k) => k + 1)
                }
            } catch (error) {
                console.error('Error polling posture regeneration:', error)
            }
        }, REGENERATE_POLL_MS)
        return () => clearInterval(timer)
    }, [regenerating])

    const regenerate = async () => {
        try {
            const resp = await dashboardApi.triggerArgusPostureRegenerate()
            setRegenerating(true)
            func.setToast(true, false, resp && resp.status === 'ALREADY_RUNNING'
                ? 'Regeneration already in progress'
                : 'Regenerating posture dashboard, this can take a few minutes')
        } catch (error) {
            func.setToast(true, true, 'Could not start regeneration')
        }
    }

    // Deliberately its own effect, not folded into the cards load above: each card's AI summary
    // can take a few seconds on a cache miss and must never hold up the (fast, Java-only) card
    // data itself.
    useEffect(() => {
        let cancelled = false
        async function loadSummaries() {
            setInsightSummariesLoading(true)
            setInsightSummaries({})
            try {
                const startTimestamp = getTimeEpoch('since')
                const endTimestamp = getTimeEpoch('until')
                const summaries = await postureDataSource.fetchInsightCardSummaries(startTimestamp, endTimestamp)
                if (!cancelled) setInsightSummaries(summaries)
            } finally {
                if (!cancelled) setInsightSummariesLoading(false)
            }
        }
        loadSummaries()
        return () => { cancelled = true }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [currDateRange])

    const openAgent = (groupKey) => navigate(`/dashboard/agentic-posture/agents/${encodeURIComponent(groupKey)}`)
    const openKpiLink = (kpi) => { if (kpi.linkGroupKey) openAgent(kpi.linkGroupKey) }

    const term = searchTerm.trim().toLowerCase()
    const highestRiskAgents = useMemo(() => {
        const rows = pageData.highestRiskAgents || []
        const filtered = selectedEnv === 'all' ? rows : rows.filter((r) => r.environment === selectedEnv)
        if (!term) return filtered
        return filtered.filter((r) => r.name.toLowerCase().includes(term) || (r.issue || '').toLowerCase().includes(term))
    }, [pageData.highestRiskAgents, selectedEnv, term])
    // Highest-risk rows carry a real ApiCollection id as groupKey, so they open the collection page.
    const openCollection = (collectionId) => navigate(`/dashboard/observe/inventory/${encodeURIComponent(collectionId)}`)

    const topbar = (
        <VerticalStack gap="4">
            <Box maxWidth="560px">
                <Text variant="bodyMd" color="subdued">
                    What agents exist, which ones are risky, why, and what changed — across every AI agent Argus has discovered.
                </Text>
            </Box>
            <EnvironmentTabs environments={pageData.environments} selected={selectedEnv} onSelect={setSelectedEnv} />
        </VerticalStack>
    )

    const pageBody = (
        <VerticalStack gap="6">
            {topbar}

            <Section title="Posture Summary" description="What Argus has found across your AI agent fleet this week.">
                {/* gap matches KpiGrid's own gap="3" (12px) so the space between the Posture
                    Score card and the KPI grid reads the same as the space between KPI tiles
                    themselves — was hardcoded to 16px before, which didn't match. */}
                <div style={{ display: 'flex', gap: '12px', alignItems: 'stretch', flexWrap: 'wrap' }}>
                    {/* display:grid (not flex) on these single-child wrappers: CSS Grid's
                        default alignment is "stretch" on BOTH axes, so the child (Card / the KPI
                        grid) fills the wrapper's full width and height. Flexbox only stretches
                        the cross axis by default — the Card was rendering 4px narrower than its
                        own wrapper because Card doesn't accept a style/width prop to fix that
                        directly, so a plain "display:flex" wrapper wasn't enough. */}
                    <div style={{ flex: '1 1 280px', minWidth: '280px', display: 'grid' }}>
                        <PostureScoreCard postureScore={pageData.postureScore} onOpenBreakdown={() => openDrill('postureScore')} />
                    </div>
                    <div style={{ flex: '2.4 1 560px', minWidth: '320px', display: 'grid' }}>
                        <KpiGrid kpis={pageData.kpis} onOpenLink={openKpiLink} onOpenDrill={openDrill}
                            onOpenRoute={(route) => navigate(route)} />
                    </div>
                </div>
            </Section>

            <Section
                title="Dangerous Execution Paths"
                description="End-to-end chains where untrusted input reaches a privileged action against a sensitive resource with a control missing in between."
            >
                <DangerousPathsSection dangerousPaths={pageData.dangerousPaths} />
            </Section>

            <Section
                title="Highest-Risk Agents"
                description="Ranked by blast radius — privilege held, data reached, and controls missing."
                action={<Button onClick={() => openDrill('highRiskAgents')}>View all agents</Button>}
            >
                <HighestRiskAgentsTable agents={pageData.highestRiskAgents} onOpenAgent={openCollection} />
            </Section>

            <Section title="Risk by Domain" description="Where posture gaps are concentrated, and whether each domain is getting better or worse.">
                <RiskByDomainSection riskByDomain={pageData.riskByDomain} />
            </Section>

            <Section title="Insights" description="Red-team, guardrail activity, and observability — the account-wide picture, each with an AI summary.">
                <InsightCardsSection cards={insightCards} summaries={insightSummaries} summariesLoading={insightSummariesLoading} onOpenRoute={navigate} onOpenDrill={openDrill} />
            </Section>

            <Section title="Coverage & Governance" description="Posture is only as reliable as what Argus can see.">
                <CoverageGovernanceSection coverageGovernance={pageData.coverageGovernance} />
            </Section>

            <Section title="Changes Since Last Week" description="What's new in the environment — this is what keeps posture operational, not a static snapshot.">
                <ChangesSinceLastWeekSection changesThisWeek={pageData.changesThisWeek} />
            </Section>
        </VerticalStack>
    )

    return (
        <Box>
            {loading ? (
                <Box padding="8">
                    <SpinnerCentered />
                </Box>
            ) : (
                <PageWithMultipleCards
                    title={<Text variant="headingLg">Posture Overview</Text>}
                    isFirstPage={true}
                    components={[<Box key="body">{pageBody}</Box>]}
                    secondaryActions={
                        <Button icon={RefreshMajor} onClick={regenerate} loading={regenerating} disabled={regenerating}>Regenerate</Button>
                    }
                    primaryAction={
                        <DateRangeFilter
                            initialDispatch={currDateRange}
                            dispatch={(dateObj) => dispatchCurrDateRange({
                                type: 'update', period: dateObj.period, title: dateObj.title, alias: dateObj.alias,
                            })}
                        />
                    }
                />
            )}
            <PostureDrillFlyout
                drillState={drillState}
                onNavigate={navigateDrill}
                onClose={closeDrill}
                startTimestamp={getTimeEpoch('since')}
                endTimestamp={getTimeEpoch('until')}
                rootLabel="Posture overview"
                filterStatePrefix="agentic-posture-drill"
                fetchDrill={fetchArgusDrill}
                ctaInFooter={true}
                pageSize={DRILL_PAGE_SIZE[drillState?.drillId] || 20}
                hideTotalBadge={drillState?.drillId === 'highRiskAgents' && !drillState?.path}
            />
        </Box>
    )
}

export default AgenticPosture

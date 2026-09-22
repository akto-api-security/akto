import { useEffect, useMemo, useReducer, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Box, Icon, Text, TextField, VerticalStack } from '@shopify/polaris'
import { SearchMinor } from '@shopify/polaris-icons'
import { produce } from 'immer'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import DateRangeFilter from '../../../components/layouts/DateRangeFilter'
import SpinnerCentered from '../../../components/progress/SpinnerCentered'
import func from '@/util/func'
import values from '@/util/values'
import postureDataSource from './postureDataSource'
import EnvironmentTabs from './overview/EnvironmentTabs'
import PostureScoreCard from './overview/PostureScoreCard'
import KpiGrid from './overview/KpiGrid'
import DangerousPathsSection from './overview/DangerousPathsSection'
import HighestRiskAgentsTable from './overview/HighestRiskAgentsTable'
import RiskByDomainSection from './overview/RiskByDomainSection'
import TopFindingsSection from './overview/TopFindingsSection'
import CoverageGovernanceSection from './overview/CoverageGovernanceSection'
import ChangesSinceLastWeekSection from './overview/ChangesSinceLastWeekSection'

function SectionHeading({ title, description }) {
    return (
        <VerticalStack gap="1">
            <Text variant="headingMd">{title}</Text>
            {description && <Text variant="bodySm" color="subdued">{description}</Text>}
        </VerticalStack>
    )
}

function Section({ title, description, children }) {
    return (
        <VerticalStack gap="4">
            <SectionHeading title={title} description={description} />
            {children}
        </VerticalStack>
    )
}

function AgenticPosture() {
    const navigate = useNavigate()
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[3] // "Last 30 days" — same default the other posture pages use
    )
    const [pageData, setPageData] = useState({})
    const [loading, setLoading] = useState(true)
    const [selectedEnv, setSelectedEnv] = useState('all')
    const [searchTerm, setSearchTerm] = useState('')

    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000)

    useEffect(() => {
        let cancelled = false
        async function load() {
            setLoading(true)
            try {
                const startTimestamp = getTimeEpoch('since')
                const endTimestamp = getTimeEpoch('until')
                const resp = await postureDataSource.fetchPostureSummary(startTimestamp, endTimestamp)
                if (!cancelled) setPageData(resp || {})
            } catch (error) {
                console.error('Error fetching posture summary:', error)
                if (!cancelled) setPageData({})
            } finally {
                if (!cancelled) setLoading(false)
            }
        }
        load()
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

    const topFindings = useMemo(() => {
        const rows = pageData.topFindings || []
        const filtered = selectedEnv === 'all' ? rows : rows.filter((r) => r.environment === selectedEnv)
        if (!term) return filtered
        return filtered.filter((r) => (r.title || '').toLowerCase().includes(term))
    }, [pageData.topFindings, selectedEnv, term])

    const topbar = (
        <VerticalStack gap="4">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '16px', flexWrap: 'wrap' }}>
                <Box maxWidth="560px">
                    <Text variant="bodyMd" color="subdued">
                        What agents exist, which ones are risky, why, and what changed — across every AI agent Argus has discovered.
                    </Text>
                </Box>
                <Box width="260px">
                    <TextField
                        value={searchTerm}
                        onChange={setSearchTerm}
                        placeholder="Search agents, findings…"
                        prefix={<Icon source={SearchMinor} color="subdued" />}
                        autoComplete="off"
                        clearButton
                        onClearButtonClick={() => setSearchTerm('')}
                    />
                </Box>
            </div>
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
                        <PostureScoreCard postureScore={pageData.postureScore} />
                    </div>
                    <div style={{ flex: '2.4 1 560px', minWidth: '320px', display: 'grid' }}>
                        <KpiGrid kpis={pageData.kpis} onOpenLink={openKpiLink} />
                    </div>
                </div>
            </Section>

            <Section
                title="Dangerous Execution Paths"
                description="End-to-end chains where untrusted input reaches a privileged action against a sensitive resource with a control missing in between."
            >
                <DangerousPathsSection dangerousPaths={pageData.dangerousPaths} />
            </Section>

            <Section title="Highest-Risk Agents" description="Ranked by blast radius — privilege held, data reached, and controls missing.">
                <HighestRiskAgentsTable agents={highestRiskAgents} onOpenAgent={openAgent} />
            </Section>

            <Section title="Risk by Domain" description="Where posture gaps are concentrated, and whether each domain is getting better or worse.">
                <RiskByDomainSection riskByDomain={pageData.riskByDomain} />
            </Section>

            <Section title="Top Posture Findings" description="The highest-impact gaps, with exactly what's affected and how to close them.">
                <TopFindingsSection topFindings={topFindings} onOpenAgent={openAgent} />
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
        </Box>
    )
}

export default AgenticPosture

import { useEffect, useReducer, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Badge, Banner, Box, Button, HorizontalGrid, HorizontalStack, VerticalStack } from '@shopify/polaris'
import { produce } from 'immer'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import DateRangeFilter from '../../../components/layouts/DateRangeFilter'
import SpinnerCentered from '../../../components/progress/SpinnerCentered'
import TitleWithInfo from '../../../components/shared/TitleWithInfo'
import PersistStore from '../../../../main/PersistStore'
import func from '@/util/func'
import values from '@/util/values'
import threatApi from '../../threat_detection/api'
import { goToGuardrailActivity } from '../../threat_detection/utils/threatDashboardUtils'
import { downloadMaliciousEventsAsCsv } from '../../threat_detection/utils/exportEvents'
import InsightsFlyout from '../../observe/agentic/insights/InsightsFlyout'
import InsightsEntryButton from '../../observe/agentic/insights/InsightsEntryButton'
import useInsightsEntryPoint from '../../observe/agentic/insights/useInsightsEntryPoint'
import { INSIGHT_GROUP } from '../../observe/agentic/insights/insightsHelpers'
import ReviewCaseModal from '../components/ReviewCaseModal'
import { fetchOverview, fetchEvents, loadPage } from './overviewApi'
import OverviewKpiRow from './OverviewKpiRow'
import ViolationsTrendCard from './ViolationsTrendCard'
import TopPoliciesCard from './TopPoliciesCard'
import ViolationHeatmap from './ViolationHeatmap'
import NeedsAttentionCard from './NeedsAttentionCard'

const EXPORT_LIMIT = 2000

// The Policies page opens the policy named in ?policy=, or just lists the policies when it no longer exists.
export const policyLink = (name) => `/dashboard/guardrails/policies${name ? `?policy=${encodeURIComponent(name)}` : ''}`

// "Last 1 year". Found by alias, so reordering the picker's ranges cannot change the default.
const DEFAULT_DATE_RANGE = values.ranges.find((range) => range.alias === 'lastYear')

// A shared link only reproduces the page if the date filter comes back with it.
function dateRangeFromSearchParams(searchParams) {
    const sinceParam = searchParams.get('since')
    const untilParam = searchParams.get('until')
    if (sinceParam == null || untilParam == null) return null
    const sinceTs = parseInt(sinceParam, 10)
    const untilTs = parseInt(untilParam, 10)
    if (Number.isNaN(sinceTs) || Number.isNaN(untilTs)) return null
    return { title: 'Custom', alias: 'custom', period: { since: new Date(sinceTs * 1000), until: new Date(untilTs * 1000) } }
}

function GuardrailOverview() {
    const navigate = useNavigate()
    const insights = useInsightsEntryPoint()
    const collectionsMap = PersistStore((state) => state.collectionsMap)
    const [searchParams, setSearchParams] = useSearchParams()
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        searchParams,
        (sp) => dateRangeFromSearchParams(sp) || DEFAULT_DATE_RANGE
    )
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [reloadKey, setReloadKey] = useState(0)
    const [assigning, setAssigning] = useState(null) // { row, agent } being marked for review
    const [assignLoading, setAssignLoading] = useState(false)
    const [loadingMore, setLoadingMore] = useState(null) // the list panel fetching its next page

    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    const startTimestamp = getTimeEpoch('since')
    const endTimestamp = getTimeEpoch('until')

    useEffect(() => {
        if (searchParams.get('since') === String(startTimestamp) && searchParams.get('until') === String(endTimestamp)) return
        const next = new URLSearchParams(searchParams)
        next.set('since', String(startTimestamp))
        next.set('until', String(endTimestamp))
        setSearchParams(next, { replace: true })
    }, [currDateRange]) // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        let cancelled = false
        setLoading(true)
        fetchOverview(startTimestamp, endTimestamp)
            .then((overview) => { if (!cancelled) setData(overview) })
            .catch(() => { if (!cancelled) setData(null) })
            .finally(() => { if (!cancelled) setLoading(false) })
        return () => { cancelled = true }
    }, [startTimestamp, endTimestamp, reloadKey])

    // Appends the next page of a list panel; the backend pages, so this asks for the rows after the loaded ones.
    const loadMore = async (panel) => {
        setLoadingMore(panel)
        try {
            const next = await loadPage(panel, startTimestamp, endTimestamp, data[panel].rows.length)
            setData((prev) => ({ ...prev, [panel]: { ...prev[panel], ...next, rows: [...prev[panel].rows, ...next.rows] } }))
        } catch {
            func.setToast(true, true, 'Failed to load more')
        } finally {
            setLoadingMore(null)
        }
    }

    // The activity page opens in this window with the same date range and these filters applied, and Back returns
    // here with the same range. It lands on its Active tab, the closest match to the design's Open and All tabs;
    // the page does not read a tab from the link yet.
    const openActivity = (filters = {}) => goToGuardrailActivity(navigate, { startTimestamp, endTimestamp, ...filters })

    const exportCsv = async () => {
        try {
            const resp = await fetchEvents(startTimestamp, endTimestamp, { status: 'ACTIVE', limit: EXPORT_LIMIT })
            downloadMaliciousEventsAsCsv(resp?.maliciousEvents || [], 'guardrail_open_violations')
        } catch {
            func.setToast(true, true, 'Failed to export violations')
        }
    }

    // caseFields: { caseOwner?, caseNotes? } from the review dialog.
    const markForReview = async (caseFields) => {
        setAssignLoading(true)
        try {
            const resp = await threatApi.updateMaliciousEventStatus({ eventIds: assigning.row.eventIds, status: 'UNDER_REVIEW', ...caseFields })
            if (resp?.updateSuccess) {
                func.setToast(true, false, 'Marked for review')
                setAssigning(null)
                setReloadKey((k) => k + 1)
            } else {
                func.setToast(true, true, 'Failed to mark for review')
            }
        } catch {
            func.setToast(true, true, 'Failed to mark for review')
        } finally {
            setAssignLoading(false)
        }
    }

    let body
    if (loading) {
        body = <SpinnerCentered />
    } else if (!data) {
        body = <Banner status="critical" title="Couldn't load the guardrail overview">Try again in a moment.</Banner>
    } else {
        const { critUnassigned, kpis, trend, topPolicies, heatmap, policySeverity, attention } = data
        body = (
            <VerticalStack gap="4">
                {critUnassigned > 0 && (
                    <Banner
                        status="warning"
                        title={`${critUnassigned.toLocaleString()} critical issue${critUnassigned === 1 ? ' has' : 's have'} no owner.`}
                        action={{ content: 'Review critical issues', onAction: () => openActivity({ severity: 'CRITICAL' }) }}
                    >
                        Assign an owner so they get triaged.
                    </Banner>
                )}
                <OverviewKpiRow
                    kpis={kpis}
                    onOpenIssues={() => openActivity()}
                    onOpenPolicies={() => navigate('/dashboard/guardrails/policies')}
                />
                <HorizontalGrid columns={{ xs: 1, lg: ['twoThirds', 'oneThird'] }} gap="4">
                    <ViolationsTrendCard points={trend} onOpenRange={(start, end) => openActivity({ startTimestamp: start, endTimestamp: end })} />
                    <TopPoliciesCard
                        policies={topPolicies.rows}
                        total={topPolicies.total}
                        onOpen={(policy) => openActivity({ latestAttack: policy })}
                        onLoadMore={() => loadMore('topPolicies')}
                        loadingMore={loadingMore === 'topPolicies'}
                    />
                </HorizontalGrid>
                <ViolationHeatmap
                    heatmap={heatmap}
                    policySeverity={policySeverity}
                    onOpen={(hosts, policies) => openActivity({ host: hosts.join(','), latestAttack: policies.join(',') })}
                    onLoadMore={() => loadMore('heatmap')}
                    loadingMore={loadingMore === 'heatmap'}
                />
                <NeedsAttentionCard
                    rows={attention.rows}
                    total={attention.total}
                    onLoadMore={() => loadMore('attention')}
                    loadingMore={loadingMore === 'attention'}
                    collectionsMap={collectionsMap}
                    onViewAll={() => openActivity()}
                    onReview={(row) => openActivity({ latestAttack: row.policy, host: row.host })}
                    onOpenPolicy={(row) => navigate(policyLink(row.policy))}
                    onAssign={(row, agent) => setAssigning({ row, agent })}
                />
            </VerticalStack>
        )
    }

    const components = [
        <Box key="body">{body}</Box>,
        <ReviewCaseModal
            key="review-case"
            open={assigning !== null}
            onClose={() => setAssigning(null)}
            onConfirm={markForReview}
            loading={assignLoading}
            summary={assigning ? `${assigning.row.policy} on ${assigning.agent} will be marked for review.` : ''}
        />,
        insights.granted ? (
            <InsightsFlyout
                key="insights"
                show={insights.open}
                onClose={insights.handleClose}
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
                initialInsightId={insights.initialInsightId}
                group={INSIGHT_GROUP.GUARDRAIL_VIOLATIONS}
            />
        ) : null,
    ]

    return (
        <PageWithMultipleCards
            title={
                <HorizontalStack gap="2" blockAlign="center">
                    <TitleWithInfo
                        titleText="Guardrail overview"
                        tooltipContent="Violations caught by your guardrail policies across your agents, and what needs attention first."
                    />
                    <Badge status="info">Beta</Badge>
                </HorizontalStack>
            }
            isFirstPage
            secondaryActions={
                <HorizontalStack gap="2" blockAlign="center">
                    <InsightsEntryButton granted={insights.granted} onClick={insights.handleOpen} label="Guardrail Insights" />
                    <Button onClick={exportCsv}>Export</Button>
                </HorizontalStack>
            }
            primaryAction={
                <DateRangeFilter
                    initialDispatch={currDateRange}
                    dispatch={(dateObj) =>
                        dispatchCurrDateRange({ type: 'update', period: dateObj.period, title: dateObj.title, alias: dateObj.alias })
                    }
                />
            }
            components={components}
        />
    )
}

export default GuardrailOverview

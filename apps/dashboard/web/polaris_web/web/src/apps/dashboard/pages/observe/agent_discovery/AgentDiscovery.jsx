import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge, Banner, Box, Button, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import SpinnerCentered from '../../../components/progress/SpinnerCentered'
import TitleWithInfo from '../../../components/shared/TitleWithInfo'
import AskBar from '../../../components/shared/AskBar'
import ChangesSinceLastWeekSection from '../../dashboard/agenticPosture/overview/ChangesSinceLastWeekSection'
import { ARGUS_CHAT_SCOPE, ASK_ARGUS_PLACEHOLDER } from '../atlas/devices_employees/constants'
import { fetchDiscovery, useSnoozed } from './discoveryApi'
import DiscoveryKpiRow, { DiscoveryFunnel } from './DiscoveryKpiRow'
import TodoCard from './TodoCard'
import { CoverageCard, PlatformsCard } from './CoverageCards'

const INVENTORY_PATH = '/dashboard/observe/inventory'
const POLICIES_PATH = '/dashboard/guardrails/policies'
const CONNECT_PATH = '/dashboard/quick-start'
const agentPath = (id) => `/dashboard/agentic-posture/agent/${id}`

// Answers scroll inside this height, so the page below the bar does not move as the conversation grows.
const ASK_MAX_HEIGHT = '420px'

// Questions this page's data can answer. There is no model or ownership data yet, so none asks about those.
const ASK_SUGGESTIONS = [
    'Which MCP servers are exposed to the internet?',
    'Which agents have no guardrail policy?',
    'Which unreviewed AI agents handle sensitive data?',
    'Which agents can delete data or read credentials?',
    "What's new since last week?",
]

function AgentDiscovery() {
    const navigate = useNavigate()
    const snoozed = useSnoozed()
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [askRequest, setAskRequest] = useState(null)
    const askRef = useRef(null)

    // The bar answers a question another part of the page sends it, and scrolls into view for the answer.
    const ask = useCallback((question) => {
        setAskRequest((current) => ({ id: (current?.id || 0) + 1, question }))
        askRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, [])

    useEffect(() => {
        let cancelled = false
        fetchDiscovery()
            .then((overview) => { if (!cancelled) setData(overview) })
            .catch(() => { if (!cancelled) setData(null) })
            .finally(() => { if (!cancelled) setLoading(false) })
        return () => { cancelled = true }
    }, [])

    // Where the fix is made: policies for coverage, the asset itself when there is one, else the list of assets.
    const review = (todo) => {
        if (todo.id === 'coverage') navigate(POLICIES_PATH)
        else navigate(todo.count === 1 ? agentPath(todo.assets[0].id) : INVENTORY_PATH)
    }

    let body
    if (loading) {
        body = <SpinnerCentered />
    } else if (!data) {
        body = <Banner status="critical" title="Couldn't load agent discovery">Try again in a moment.</Banner>
    } else {
        const { kpis, funnel, todos, platforms, coverage, changes } = data
        const open = todos.filter((todo) => !snoozed.isSnoozed(todo.id))
        body = (
            <VerticalStack gap="4">
                {funnel.assets === 0 && (
                    <Banner
                        status="info"
                        title="No agents found yet"
                        action={{ content: 'Connect an agent platform', onAction: () => navigate(CONNECT_PATH) }}
                    >
                        Argus finds agents, MCP servers and tools from the traffic it sees.
                    </Banner>
                )}
                {/* The chat gets this page's own data, so its answers match what is on screen. */}
                <div ref={askRef}>
                    <AskBar label="Ask Argus" placeholder={ASK_ARGUS_PLACEHOLDER} suggestions={ASK_SUGGESTIONS} chatScope={ARGUS_CHAT_SCOPE} chatData={data} request={askRequest} maxResultsHeight={ASK_MAX_HEIGHT} suggestionsLabel="" />
                </div>
                <DiscoveryKpiRow kpis={kpis} onOpenInventory={() => navigate(INVENTORY_PATH)} />
                <DiscoveryFunnel funnel={funnel} />
                <HorizontalGrid columns={{ xs: 1, lg: ['twoThirds', 'oneThird'] }} gap="4">
                    <TodoCard
                        todos={open}
                        snoozedCount={todos.length - open.length}
                        onReview={review}
                        onOpenAsset={(asset) => navigate(agentPath(asset.id))}
                        onAsk={(todo) => ask(`Explain this and how to fix it: ${todo.title}`)}
                        onSnooze={(todo) => snoozed.snooze(todo.id)}
                        onShowSnoozed={snoozed.clear}
                    />
                    <VerticalStack gap="4">
                        <VerticalStack gap="2">
                            <Text variant="headingMd" as="h2">Since last week</Text>
                            {changes
                                ? <ChangesSinceLastWeekSection changesThisWeek={changes} />
                                : <Card><Text variant="bodyMd" color="subdued">Couldn't load last week's changes.</Text></Card>}
                        </VerticalStack>
                        <PlatformsCard platforms={platforms} />
                        <CoverageCard coverage={coverage} />
                    </VerticalStack>
                </HorizontalGrid>
            </VerticalStack>
        )
    }

    return (
        <PageWithMultipleCards
            title={
                <HorizontalStack gap="2" blockAlign="center">
                    <TitleWithInfo
                        titleText="Agent discovery"
                        tooltipContent="The agents, MCP servers and tools Argus found in your traffic, what nobody has reviewed, and what to do first."
                    />
                    <Badge status="info">Beta</Badge>
                </HorizontalStack>
            }
            isFirstPage
            primaryAction={<Button primary onClick={() => navigate(CONNECT_PATH)}>Connect agent platform</Button>}
            components={[<Box key="body">{body}</Box>]}
        />
    )
}

export default AgentDiscovery

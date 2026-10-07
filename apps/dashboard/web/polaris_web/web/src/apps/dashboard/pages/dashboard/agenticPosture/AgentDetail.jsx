import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { Banner, Box, Text, VerticalStack } from '@shopify/polaris'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import SpinnerCentered from '../../../components/progress/SpinnerCentered'
import postureDataSource from './postureDataSource'
import AgentHeaderCard from './agentDetail/AgentHeaderCard'
import ToolsSection from './agentDetail/ToolsSection'
import RedTeamSection from './agentDetail/RedTeamSection'
import DataSection from './agentDetail/DataSection'
import ProtectionSection from './agentDetail/ProtectionSection'

// StepNav, OwnerSection, IdentitySection and DangerousPathCallout are intentionally not rendered.
// The sections have no data source in the Argus context; the step nav is hidden for now. All are
// kept in the codebase.

function Section({ id, title, children }) {
    return (
        <Box id={id} paddingBlockStart="2">
            <VerticalStack gap="4">
                <Text variant="headingMd">{title}</Text>
                {children}
            </VerticalStack>
        </Box>
    )
}

function AgentDetail() {
    const { collectionId } = useParams()
    const [searchParams] = useSearchParams()
    const finding = searchParams.get('finding')
    const [detail, setDetail] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        let cancelled = false
        async function load() {
            setLoading(true)
            try {
                const resp = await postureDataSource.fetchAgentDetail(collectionId, finding)
                if (!cancelled) setDetail(resp)
            } catch (error) {
                console.error('Error fetching agent detail:', error)
                if (!cancelled) setDetail(null)
            } finally {
                if (!cancelled) setLoading(false)
            }
        }
        load()
        return () => { cancelled = true }
    }, [collectionId, finding])

    if (loading) {
        return (
            <Box padding="8">
                <SpinnerCentered />
            </Box>
        )
    }

    if (!detail) {
        return (
            <Box padding="8">
                <Text variant="bodyMd" color="subdued" alignment="center">
                    This agent isn't available.
                </Text>
            </Box>
        )
    }

    const pageBody = (
        <VerticalStack gap="6">
            {detail.openedFromFinding && (
                <Banner status="critical">
                    Opened from finding: <Text as="span" fontWeight="semibold">{detail.openedFromFinding.title}</Text>
                </Banner>
            )}

            <Box id="agent">
                <AgentHeaderCard header={detail.header} />
            </Box>

            <Section id="tools" title="Tools & Capabilities">
                <ToolsSection tools={detail.tools} />
            </Section>
            <Section id="data" title="Data">
                <DataSection data={detail.data} />
            </Section>

            <Section id="protection" title="Protection">
                <ProtectionSection protection={detail.protection} />
            </Section>
            <RedTeamSection redTeam={detail.redTeam} />
        </VerticalStack>
    )

    return (
        <PageWithMultipleCards
            title={<Text variant="headingLg">{detail.header?.name}</Text>}
            backUrl="/dashboard/agentic-posture"
            components={[<Box key="body">{pageBody}</Box>]}
        />
    )
}

export default AgentDetail

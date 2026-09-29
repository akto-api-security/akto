import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Banner, Box, Text, VerticalStack } from '@shopify/polaris'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import SpinnerCentered from '../../../components/progress/SpinnerCentered'
import postureDataSource from './postureDataSource'
import AgentHeaderCard from './agentDetail/AgentHeaderCard'
import StepNav from './agentDetail/StepNav'
import DangerousPathCallout from './agentDetail/DangerousPathCallout'
import OwnerSection from './agentDetail/OwnerSection'
import IdentitySection from './agentDetail/IdentitySection'
import PermissionsSection from './agentDetail/PermissionsSection'
import ToolsSection from './agentDetail/ToolsSection'
import DataSection from './agentDetail/DataSection'
import ProtectionSection from './agentDetail/ProtectionSection'
import RuntimeActivitySection from './agentDetail/RuntimeActivitySection'

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
    const { groupKey } = useParams()
    const [detail, setDetail] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        let cancelled = false
        async function load() {
            setLoading(true)
            try {
                const resp = await postureDataSource.fetchAgentDetail(groupKey, 0, 0)
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
    }, [groupKey])

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
                <Text variant="bodyMd" color="subdued" alignment="center">No posture data found for "{groupKey}".</Text>
            </Box>
        )
    }

    const pageBody = (
        <VerticalStack gap="6">
            {detail.openedFromFinding && (
                <Banner status="critical">
                    Opened from finding: <Text as="span" fontWeight="semibold">{detail.openedFromFinding}</Text>
                </Banner>
            )}

            <Section id="agent" title="Agent">
                <AgentHeaderCard header={detail.header} />
            </Section>

            <StepNav />

            <DangerousPathCallout dangerousPath={detail.dangerousPath} />

            <Section id="owner" title="Owner">
                <OwnerSection owner={detail.owner} />
            </Section>

            <Section id="identity" title="Identity">
                <IdentitySection identity={detail.identity} />
            </Section>

            <Section id="permissions" title="Permissions">
                <PermissionsSection permissions={detail.permissions} />
            </Section>

            <Section id="tools" title="Tools">
                <ToolsSection tools={detail.tools} />
            </Section>

            <Section id="data" title="Data">
                <DataSection data={detail.data} />
            </Section>

            <Section id="protection" title="Protection">
                <ProtectionSection protection={detail.protection} />
            </Section>

            <Section id="runtime" title="Runtime Activity">
                <RuntimeActivitySection runtimeActivity={detail.runtimeActivity} />
            </Section>
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

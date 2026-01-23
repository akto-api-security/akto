import React from 'react'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import { HorizontalGrid, HorizontalStack, Box, Text } from '@shopify/polaris'
import TitleWithInfo from '../../components/shared/TitleWithInfo'
import SummaryCard from './new_components/SummaryCard'
import { commonMcpServers, summaryInfoData, commonLlmsInBrowsers, commonAiAgents, attackRequests, complianceData, dataProtectionTrendsData, dataProtectionTrendsLabels, guardrailPoliciesData } from './atlusPosture/dummyData'
import InfoCard from './new_components/InfoCard'
import ServersLayout from './atlusPosture/ServersLayout'
import AttackWorldMap from './atlusPosture/AttackWorldMap'
import ComplianceAtRisksCard from './new_components/ComplianceAtRisksCard'
import ThreatCategoryStackedChartWrapper from './atlusPosture/ThreatCategoryStackedChartWrapper'
import CustomLineChart from './new_components/CustomLineChart'
import ChartypeComponent from '../testing/TestRunsPage/ChartypeComponent'

function EndpointPosture() {

    const summaryHeader = (
        <SummaryCard 
            summaryItems={summaryInfoData}
        />
    )

    const coverageComponent = (
        <HorizontalGrid columns={3} gap={4}>
            <InfoCard
                title="Common MCP Servers"
                component={<ServersLayout items={commonMcpServers} boxHeight="200px" />}
                tooltipContent="The most common MCP servers detected in your environment."
            />
            <InfoCard
                title="Common LLMs in browsers"
                component={<ServersLayout items={commonLlmsInBrowsers} boxHeight="200px" />}
                tooltipContent="The most common LLMs agents detected in your environment."
            />
            <InfoCard
                title="Common AI Agents"
                component={<ServersLayout items={commonAiAgents} boxHeight="200px" />}
                tooltipContent="The most common AI agents detected in your environment."
            />
        </HorizontalGrid>
    )

    const worldMapComponent = (
        <AttackWorldMap
            attackRequests={attackRequests}
            style={{
                width: "100%",
                height: "300px",
                marginRight: "auto",
            }}
        />
    )

    const complianceComponent = (
        <HorizontalGrid columns={2} gap={4}>
            <ComplianceAtRisksCard
                complianceData={complianceData}
                tooltipContent="Overview of compliance risks across different security standards"
            />
            <ThreatCategoryStackedChartWrapper />
        </HorizontalGrid>
    )

    const hasGuardrailData = guardrailPoliciesData && typeof guardrailPoliciesData === 'object' && Object.keys(guardrailPoliciesData).length > 0 && Object.values(guardrailPoliciesData).some(item => item?.text > 0);

    const chartsComponent = (
        <HorizontalGrid columns={2} gap={4}>
            <CustomLineChart
                title="Data Protection Trends"
                chartData={dataProtectionTrendsData}
                labels={dataProtectionTrendsLabels}
                chartHeight={290}
                tooltipContent="Trends showing how data protection mechanisms are being triggered over time"
            />
            <InfoCard
                component={
                    <div style={{ marginTop: "20px" }}>
                        {hasGuardrailData ? (
                            <ChartypeComponent
                                data={guardrailPoliciesData}
                                title="Top Triggered Guardrail Policies"
                                isNormal={true}
                                boxHeight={'250px'}
                                chartOnLeft={true}
                                dataTableWidth="250px"
                                pieInnerSize="50%"
                            />
                        ) : (
                            <Box minHeight="250px">
                                <Text alignment='center' color='subdued'>No guardrail policy data available</Text>
                            </Box>
                        )}
                    </div>
                }
                title="Top Triggered Guardrail Policies"
                titleToolTip="Distribution of the most frequently triggered guardrail policies"
            />
        </HorizontalGrid>
    )

    return (
        <PageWithMultipleCards
            isFirstPage={true}
            title={
                <HorizontalStack gap={3}>
                    <TitleWithInfo
                        titleText="Endpoint Security Dashboard"
                        tooltipContent="Monitor and manage your endpoint security from this centralized dashboard."
                        docsUrl="https://docs.akto.io/endpoint-security"
                    />
                </HorizontalStack>
            }
            components={[summaryHeader, coverageComponent, worldMapComponent, complianceComponent, chartsComponent]}
        />
    )
}

export default EndpointPosture
import { Badge, Box, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'
import BarGraph from '@/apps/dashboard/components/charts/BarGraph'
import ChartypeComponent from '../../testing/TestRunsPage/ChartypeComponent'
import GetPrettifyEndpoint from '../../observe/GetPrettifyEndpoint'
import { flags } from '../components/flags/index.mjs'
import ThreatReportSummaryInfoCard from './ThreatReportSummaryInfoCard'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import { CellType } from '../../../components/tables/rows/GithubRow'

const ThreatReportSummary = ({
    totalThreats,
    severityCount,
    threatsByActor,
    threatsByCategory,
    dateRange,
    organizationName,
    topAttackedApis
}) => {

    // Format date range for display - concise format
    const formatDate = (date) => {
        if (!date) return '-'
        const day = date.getDate()
        const month = date.toLocaleString('en-US', { month: 'short' })
        const year = date.getFullYear()
        return `${day} ${month} ${year}`
    }

    // Generate data for top attacked APIs table
    const generateApiTableData = (apis) => {
        return apis.map((api) => ({
            endpointComp: (
                <Box maxWidth='400px'>
                    <GetPrettifyEndpoint method={api.method} url={api.endpoint} isNew={false} />
                </Box>
            ),
            endpoint: `${api.method} ${api.endpoint}`,
            attacks: api.attacks,
            severityBadge: (
                <div className={`badge-wrapper-${api.severity?.toUpperCase() || 'MEDIUM'}`}>
                    <Badge>{api.severity}</Badge>
                </div>
            ),
            severity: api.severity
        }))
    }

    const isAllTime = !dateRange.start || dateRange.start.getTime() === 0 || dateRange.start.getTime() < 86400000
    const dateRangeText = isAllTime
        ? 'All time'
        : `${formatDate(dateRange.start)} to ${formatDate(dateRange.end)}`

    // Prepare summary items for info cards
    const summaryItems = [
        {
            title: 'Threats Detected',
            data: totalThreats,
            visible: true
        },
        {
            title: 'Unique Threat Actors',
            data: threatsByActor.length,
            visible: true
        },
        {
            title: 'Date Range',
            data: dateRangeText,
            visible: true
        }
    ]

    // Prepare severity map for pie chart in the format ChartypeComponent expects
    const severityMap = {
        "CRITICAL": {
            text: severityCount.CRITICAL || 0,
            color: '#d72c0d'
        },
        "HIGH": {
            text: severityCount.HIGH || 0,
            color: '#f49342'
        },
        "MEDIUM": {
            text: severityCount.MEDIUM || 0,
            color: '#ffc453'
        },
        "LOW": {
            text: severityCount.LOW || 0,
            color: '#47c1bf'
        }
    }

    // Color palette for categories (same as used in TopThreatTypeChart)
    const categoryColors = ['#7FB3D5', '#85C1E9', '#F7DC6F', '#F8C471', '#F5B7B1']

    // Prepare top 5 categories chart data for BarGraph
    const topCategoriesChartData = threatsByCategory
        .slice(0, 5) // Top 5
        .map((cat, idx) => ({
            text: cat.category.replaceAll("_", " "),
            value: cat.count,
            color: categoryColors[idx % categoryColors.length]
        }))

    return (
        <Box id="threat-report-summary" paddingBlockStart={6} paddingBlockEnd={6} paddingInlineStart={5} paddingInlineEnd={5}>
            <VerticalStack gap="4">
                <Text variant="headingLg">1. Report summary</Text>
                <VerticalStack gap="3">
                    <Text variant="bodyMd" color='subdued'>
                        The {mapLabel("threat", getDashboardCategory()).toLowerCase()} detection assessment {isAllTime ? 'covering all available data' : `conducted from ${formatDate(dateRange.start)} to ${formatDate(dateRange.end)}`} focused on identifying and analyzing malicious activities targeting <span id='organization-name'>{organizationName}</span>'s {mapLabel("API endpoints", getDashboardCategory())}. The analysis examined successful attack attempts, {mapLabel("threat", getDashboardCategory()).toLowerCase()} actor patterns, and attack categories to provide actionable insights for strengthening security posture.
                    </Text>
                    <ThreatReportSummaryInfoCard summaryItems={summaryItems} />

                    <Box width='100%' paddingBlockStart={4} paddingBlockEnd={2}>
                        <VerticalStack gap={3}>
                            <Text variant="headingSm">Threats by Severity</Text>

                            <ChartypeComponent
                                data={severityMap}
                                navUrl={""}
                                title={""}
                                isNormal={true}
                                boxHeight={'300px'}
                                chartOnLeft={true}
                                dataTableWidth="450px"
                                boxPadding={0}
                                pieInnerSize="50%"
                                chartSize={250}
                                spaceBetween={'space-evenly'}
                            />
                        </VerticalStack>
                    </Box>

                    {topCategoriesChartData.length > 0 && (
                        <Box width='100%' paddingBlockStart={4} paddingBlockEnd={2}>
                            <VerticalStack gap={3}>
                                <Text variant="headingSm">Top 5 Attack Categories</Text>
                                <BarGraph
                                    data={topCategoriesChartData}
                                    areaFillHex="true"
                                    height="300px"
                                    defaultChartOptions={{
                                        legend: {
                                            enabled: false
                                        }
                                    }}
                                    showYAxis={true}
                                    yAxisTitle="Number of Threats"
                                    barWidth={100 - (topCategoriesChartData.length * 6)}
                                    barGap={12}
                                    showGridLines={true}
                                />
                            </VerticalStack>
                        </Box>
                    )}

                    {threatsByActor.length > 0 && (
                        <Box width='100%' paddingBlockStart={4} paddingBlockEnd={2}>
                            <VerticalStack gap={3}>
                                <Text variant="headingSm">Top Threat Actors</Text>
                                <GithubSimpleTable
                                    key="threat-actors-table"
                                    data={threatsByActor.slice(0, 10).map((actor) => {
                                        const countryName = actor.country || 'Unknown'
                                        const flagSrc = (countryName !== 'Unknown' && flags[countryName]) || flags["earth"]
                                        return {
                                            actor: actor.actor,
                                            countryComp: (
                                                <HorizontalStack gap="2" align="center">
                                                    <img src={flagSrc} alt={countryName} style={{ width: '20px', height: '20px' }} />
                                                    <Text variant="bodyMd">{countryName}</Text>
                                                </HorizontalStack>
                                            ),
                                            country: countryName,
                                            count: actor.count
                                        }
                                    })}
                                    resourceName={{ singular: 'threat actor', plural: 'threat actors' }}
                                    headers={[
                                        { title: 'Threat Actor (IP)', value: 'actor', type: CellType.TEXT },
                                        { title: 'Country', value: 'countryComp', textValue: 'country' },
                                        { title: 'Threats', value: 'count', type: CellType.TEXT }
                                    ]}
                                    useNewRow={true}
                                    condensedHeight={true}
                                    hideQueryField={true}
                                    headings={[
                                        { title: 'Threat Actor (IP)', value: 'actor', type: CellType.TEXT },
                                        { title: 'Country', value: 'countryComp', textValue: 'country' },
                                        { title: 'Threats', value: 'count', type: CellType.TEXT }
                                    ]}
                                    hidePagination={true}
                                    showFooter={false}
                                />
                            </VerticalStack>
                        </Box>
                    )}

                    {topAttackedApis && topAttackedApis.length > 0 && (
                        <Box width='100%' paddingBlockStart={4} paddingBlockEnd={2}>
                            <VerticalStack gap={3}>
                                <Text variant="headingSm">Top Attacked {mapLabel("APIs", getDashboardCategory())}</Text>
                                <GithubSimpleTable
                                    key="top-apis-table"
                                    data={generateApiTableData(topAttackedApis)}
                                    resourceName={{ singular: 'API', plural: 'APIs' }}
                                    headers={[
                                        { title: `${mapLabel("API", getDashboardCategory())} Endpoint`, value: 'endpointComp', textValue: 'endpoint' },
                                        { title: 'Attacks', value: 'attacks', type: CellType.TEXT },
                                        { title: 'Severity', value: 'severityBadge', textValue: 'severity' }
                                    ]}
                                    useNewRow={true}
                                    condensedHeight={true}
                                    hideQueryField={true}
                                    headings={[
                                        { title: `${mapLabel("API", getDashboardCategory())} Endpoint`, value: 'endpointComp', textValue: 'endpoint' },
                                        { title: 'Attacks', value: 'attacks', type: CellType.TEXT },
                                        { title: 'Severity', value: 'severityBadge', textValue: 'severity' }
                                    ]}
                                    hidePagination={true}
                                    showFooter={false}
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </VerticalStack>
            </VerticalStack>
        </Box>
    )
}

export default ThreatReportSummary

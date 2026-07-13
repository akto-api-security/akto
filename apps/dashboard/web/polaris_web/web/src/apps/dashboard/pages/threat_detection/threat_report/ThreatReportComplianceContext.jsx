import { Avatar, Badge, Box, Text, VerticalStack, HorizontalStack } from '@shopify/polaris'
import BarGraph from '@/apps/dashboard/components/charts/BarGraph'
import func from '@/util/func'

const ThreatReportComplianceContext = ({ activeComplianceFilters, groupedThreatsData, threatsTableData }) => {
    if (!activeComplianceFilters?.length || !groupedThreatsData?.length) return null

    const activeUpper = activeComplianceFilters.map(f => f.toUpperCase())
    const matchesActiveFilter = (framework) => {
        const frameworkUpper = framework.toUpperCase()
        return activeUpper.some(f => f === frameworkUpper || f.includes(frameworkUpper) || frameworkUpper.includes(f))
    }

    // Unique article set per framework — from grouped rows (deduplicated)
    const filteredFrameworks = {}
    groupedThreatsData.forEach(row => {
        const clauses = row.complianceWithClauses || {}
        Object.entries(clauses).forEach(([framework, articles]) => {
            if (!matchesActiveFilter(framework)) return
            if (!filteredFrameworks[framework]) filteredFrameworks[framework] = new Set()
            ;(articles || []).forEach(a => filteredFrameworks[framework].add(a))
        })
    })

    // Clause violation counts from raw individual events (not grouped) — shows real threat volume
    const clauseViolationCounts = {}
    ;(threatsTableData || []).forEach(row => {
        const clauses = row.complianceWithClauses || {}
        Object.entries(clauses).forEach(([framework, articles]) => {
            if (!matchesActiveFilter(framework)) return
            ;(articles || []).forEach(a => {
                clauseViolationCounts[a] = (clauseViolationCounts[a] || 0) + 1
            })
        })
    })

    const barData = Object.entries(clauseViolationCounts)
        .sort((a, b) => b[1] - a[1])
        .map(([clause, count]) => ({ text: clause, value: count, color: '#9642FC' }))

    const complianceName = activeComplianceFilters[0] || ''

    return (
        <Box paddingBlockStart={4} paddingBlockEnd={2} paddingInlineStart={5} paddingInlineEnd={5}>
            <VerticalStack gap="4">
                {barData.length > 0 && (
                    <Box width='100%'>
                        <VerticalStack gap="2">
                            <Text variant="headingSm">{complianceName} Clauses</Text>
                            <BarGraph
                                data={barData}
                                areaFillHex="true"
                                height="280px"
                                defaultChartOptions={{ legend: { enabled: false } }}
                                showYAxis={true}
                                yAxisTitle="Number of Threats"
                                barWidth={Math.max(20, 60 - barData.length * 3)}
                                barGap={12}
                                showGridLines={true}
                            />
                        </VerticalStack>
                    </Box>
                )}

                {Object.entries(filteredFrameworks).map(([name, articles]) => (
                    <VerticalStack key={name} gap="2">
                        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                            <Avatar source={func.getComplianceIcon(name)} shape="square" size="small" />
                            <Text variant="headingSm">{name}</Text>
                        </HorizontalStack>
                        <HorizontalStack gap="2" wrap={true}>
                            {Array.from(articles).map((article, i) => (
                                <Badge key={i} size="small" status="info">{article}</Badge>
                            ))}
                        </HorizontalStack>
                    </VerticalStack>
                ))}
            </VerticalStack>
        </Box>
    )
}

export default ThreatReportComplianceContext

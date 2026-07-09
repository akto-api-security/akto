import { Avatar, Badge, Box, Text, VerticalStack, HorizontalStack, Link } from '@shopify/polaris'
import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import { CellType } from '../../../components/tables/rows/GithubRow'
import func from '@/util/func'

const ComplianceCell = ({ complianceWithClauses, activeComplianceFilters }) => {
    if (!complianceWithClauses || Object.keys(complianceWithClauses).length === 0) {
        return <Text color="subdued">-</Text>
    }

    // When a filter is active show only matching frameworks; otherwise show all
    const frameworksToShow = activeComplianceFilters?.length > 0
        ? Object.entries(complianceWithClauses).filter(([name]) =>
            activeComplianceFilters.some(f => f.toUpperCase() === name.toUpperCase())
        )
        : Object.entries(complianceWithClauses)

    if (frameworksToShow.length === 0) return <Text color="subdued">-</Text>

    return (
        <VerticalStack gap="1">
            {frameworksToShow.map(([framework, clauses]) => (
                <HorizontalStack key={framework} gap="1" blockAlign="center" wrap={true}>
                    <Avatar source={func.getComplianceIcon(framework)} shape="square" size="extraSmall" />
                    <Text variant="bodySm" fontWeight="semibold" breakWord>{framework}</Text>
                    {Array.isArray(clauses) && clauses.map((clause, i) => (
                        <Badge key={i} size="small" status="info">{clause}</Badge>
                    ))}
                </HorizontalStack>
            ))}
        </VerticalStack>
    )
}

const ThreatReportFindings = ({ threatsTableData, severityCount, organizationName, activeComplianceFilters = [], sectionNumber = 2 }) => {
    const dashboardCategory = getDashboardCategory()

    const handleThreatClick = (threat) => {
        const params = new URLSearchParams({
            refId: threat.refId,
            eventType: 'SINGLE',
            actor: threat.actor,
            filterId: threat.filterId || '',
            eventStatus: 'ACTIVE'
        });

        const navigateUrl = `${window.location.origin}/dashboard/protection/threat-activity?${params.toString()}#active`;
        window.open(navigateUrl, "_blank");
    }


    const threatHeaders = [
        {
            title: `${mapLabel("Threat", dashboardCategory)} Actor`,
            value: "actor",
            type: CellType.TEXT
        },
        {
            title: "Time",
            value: "time",
            type: CellType.TEXT
        },
        {
            title: "Attack Category",
            value: "category",
            type: CellType.TEXT
        },
        {
            title: `Targeted ${mapLabel("API", dashboardCategory)}`,
            value: "targetedApi",
            type: CellType.TEXT
        },
        {
            title: "Severity",
            value: "severityBadge",
            type: CellType.TEXT
        },
        {
            title: "Compliance",
            value: "complianceIcons",
            type: CellType.TEXT
        }
    ]

    const threatResourceName = {
        singular: 'threat',
        plural: 'threats'
    }

    return (
        <Box id="threat-report-findings" paddingBlockStart={6} paddingBlockEnd={6} paddingInlineStart={5} paddingInlineEnd={5}>
            <VerticalStack gap="4">
                <Text variant="headingLg">{sectionNumber}. {mapLabel("Threat", getDashboardCategory())} Detection Findings for {organizationName}</Text>
                <VerticalStack gap="3">
                    <Text variant="bodyMd" color='subdued'>
                        The following section details each {mapLabel("Threat", getDashboardCategory()).toLowerCase()} detected during the assessment period. Each entry includes the {mapLabel("Threat", getDashboardCategory()).toLowerCase()} actor, timestamp, attack category, targeted {mapLabel("API endpoint", getDashboardCategory())}, and severity level.
                    </Text>

                    {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(severity => {
                        if (severityCount[severity] === 0) return null

                        const filteredThreats = threatsTableData
                            .filter(t => t.severity === severity)
                            .map(threat => ({
                                ...threat,
                                targetedApi: (
                                    <HorizontalStack gap="2" align="center">
                                        <Link onClick={() => handleThreatClick(threat)}>
                                            <Box maxWidth="90%">
                                                <Text breakWord>{threat.targetedApi}</Text>
                                            </Box>
                                        </Link>
                                    </HorizontalStack>
                                ),
                                severityBadge: (
                                    <div className={`badge-wrapper-${threat.severity}`}>
                                        <Badge>{threat.severity}</Badge>
                                    </div>
                                ),
                                complianceIcons: (
                                    <ComplianceCell
                                        complianceWithClauses={threat.complianceWithClauses}
                                        activeComplianceFilters={activeComplianceFilters}
                                    />
                                )
                            }))

                        return (
                            <Box key={severity} id={`threat-severity-${severity.toLowerCase()}`}>
                                <Box paddingBlockStart={3} paddingBlockEnd={2}>
                                    <Text variant="headingSm">
                                        {severity.charAt(0) + severity.slice(1).toLowerCase()} Severity {mapLabel("Threat", dashboardCategory)}s ({severityCount[severity]})
                                    </Text>
                                </Box>
                                <GithubSimpleTable
                                    key={`table-${severity}`}
                                    data={filteredThreats}
                                    resourceName={threatResourceName}
                                    headers={threatHeaders}
                                    useNewRow={true}
                                    condensedHeight={true}
                                    hideQueryField={true}
                                    headings={threatHeaders}
                                    hidePagination={true}
                                    showFooter={false}
                                    pageLimit={filteredThreats.length}
                                />
                            </Box>
                        )
                    })}

                    {threatsTableData.length === 0 && (
                        <Box paddingBlockStart={4}>
                            <Text variant="bodyMd" color="subdued" alignment="center">
                                No threats detected during this period.
                            </Text>
                        </Box>
                    )}
                </VerticalStack>
            </VerticalStack>
        </Box>
    )
}

export default ThreatReportFindings

import { Badge, Box, Text, VerticalStack, HorizontalStack, Link } from '@shopify/polaris'
import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import { CellType } from '../../../components/tables/rows/GithubRow'

const ThreatReportFindings = ({ threatsTableData, severityCount, organizationName }) => {
    const dashboardCategory = getDashboardCategory()

    const handleThreatClick = (threat) => {
        const params = new URLSearchParams({
            refId: threat.id,
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
        }
    ]

    const threatResourceName = {
        singular: 'threat',
        plural: 'threats'
    }

    return (
        <Box id="threat-report-findings" paddingBlockStart={6} paddingBlockEnd={6} paddingInlineStart={5} paddingInlineEnd={5}>
            <VerticalStack gap="4">
                <Text variant="headingLg">2. {mapLabel("Threat", getDashboardCategory())} Detection Findings for {organizationName}</Text>
                <VerticalStack gap="3">
                    <Text variant="bodyMd" color='subdued'>
                        The following section details each {mapLabel("threat", getDashboardCategory()).toLowerCase()} detected during the assessment period. Each entry includes the {mapLabel("threat", getDashboardCategory()).toLowerCase()} actor, timestamp, attack category, targeted {mapLabel("API endpoint", getDashboardCategory())}, and severity level.
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
                                )
                            }))

                        return (
                            <Box key={severity} id={`threat-severity-${severity.toLowerCase()}`}>
                                <Box paddingBlockStart={3} paddingBlockEnd={2}>
                                    <Text variant="headingSm">
                                        {severity.charAt(0) + severity.slice(1).toLowerCase()} Severity Threats ({severityCount[severity]})
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

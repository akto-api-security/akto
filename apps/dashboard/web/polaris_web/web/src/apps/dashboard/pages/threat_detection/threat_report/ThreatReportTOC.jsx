import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'
import BaseReportTOC from '@/apps/dashboard/components/shared/reports/BaseReportTOC'

const ThreatReportTOC = ({ organizationName, severityCount }) => {
    const dashboardCategory = getDashboardCategory()

    // Build severity subsections dynamically based on which severities have threats
    const severityChildren = []
    const severities = [
        { key: 'CRITICAL', label: 'Critical' },
        { key: 'HIGH', label: 'High' },
        { key: 'MEDIUM', label: 'Medium' },
        { key: 'LOW', label: 'Low' }
    ]

    severities.forEach(({ key, label }) => {
        if (severityCount && severityCount[key] > 0) {
            severityChildren.push({
                text: `${label} Severity Threats`,
                link: `threat-severity-${key.toLowerCase()}`
            })
        }
    })

    const tocList = [
        {
            text: "Report summary",
            link: "threat-report-summary",
            children: null
        },
        {
            text: `${mapLabel("Threat", dashboardCategory)} Detection Findings for ${organizationName}`,
            link: "threat-report-findings",
            children: severityChildren.length > 0 ? severityChildren : null
        },
        {
            text: "Conclusion and Next Steps",
            link: "threat-report-conclusion",
            children: null
        }
    ]

    return <BaseReportTOC tocList={tocList} numberingStyle="hierarchical" />
}

export default ThreatReportTOC

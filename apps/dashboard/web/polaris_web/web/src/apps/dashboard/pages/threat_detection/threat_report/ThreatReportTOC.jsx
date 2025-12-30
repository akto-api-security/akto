import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'
import BaseReportTOC from '@/apps/dashboard/components/shared/reports/BaseReportTOC'
import func from '@/util/func'

const ThreatReportTOC = ({ organizationName, severityCount }) => {
    const dashboardCategory = getDashboardCategory()

    const severityChildren = []

    func.getAktoSeverities().forEach((severity) => {
        if (severityCount && severityCount[severity] > 0) {
            severityChildren.push({
                text: `${func.toSentenceCase(severity)} Severity Threats`,
                link: `threat-severity-${severity.toLowerCase()}`
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

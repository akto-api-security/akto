import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'
import BaseReportFooter from '@/apps/dashboard/components/shared/reports/BaseReportFooter'

const ThreatReportFooter = () => {
    const dashboardCategory = getDashboardCategory()
    const apiLabel = mapLabel("API", dashboardCategory).toUpperCase()

    return (
        <BaseReportFooter
            assessmentType={`${mapLabel("Threat", dashboardCategory)} Detection`}
            leftBadgeLabel="DAST"
            rightBadgeLabel={`${apiLabel} SECURITY`}
        />
    )
}

export default ThreatReportFooter;

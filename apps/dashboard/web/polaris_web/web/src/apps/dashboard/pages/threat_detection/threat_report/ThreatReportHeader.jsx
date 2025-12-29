import React from 'react'
import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'
import BaseReportHeader from '@/apps/dashboard/components/shared/reports/BaseReportHeader'

const ThreatReportHeader = (props) => {
    const dashboardCategory = getDashboardCategory()
    const reportTitle = `${mapLabel("Threat", dashboardCategory)} Detection Report`

    return (
        <BaseReportHeader
            reportTitle={reportTitle}
            subtitleText={reportTitle}
            showDateInfo={true}
            {...props}
        />
    )
}

export default ThreatReportHeader

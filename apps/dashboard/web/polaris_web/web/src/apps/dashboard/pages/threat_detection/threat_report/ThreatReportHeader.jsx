import React from 'react'
import { getDashboardCategory, mapLabel, isAgenticSecurityCategory, isEndpointSecurityCategory } from '@/apps/main/labelHelper'
import BaseReportHeader from '@/apps/dashboard/components/shared/reports/BaseReportHeader'

const ThreatReportHeader = ({ activeComplianceFilters, ...props }) => {
    const dashboardCategory = getDashboardCategory()
    const isGuardrail = isAgenticSecurityCategory() || isEndpointSecurityCategory()
    const complianceName = isGuardrail && activeComplianceFilters?.length > 0 ? activeComplianceFilters[0] : null

    const reportTitle = complianceName
        ? `Akto ${complianceName} Guardrail Violations Report`
        : `Akto ${mapLabel("Threat", dashboardCategory)} Detection Report`
    const subtitleText = complianceName
        ? `${complianceName} Guardrail Violations Report`
        : `${mapLabel("Threat", dashboardCategory)} Detection Report`

    return (
        <BaseReportHeader
            reportTitle={reportTitle}
            subtitleText={subtitleText}
            showDateInfo={true}
            {...props}
        />
    )
}

export default ThreatReportHeader

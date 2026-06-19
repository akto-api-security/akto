import { useCallback } from 'react'
import api from '@/apps/dashboard/pages/threat_detection/api'
import func from '@/util/func'
import { getDashboardCategory, categoryToShortName } from '@/apps/main/labelHelper'

function useThreatReportDownload({ startTimestamp, endTimestamp, additionalFilters, onComplete }) {
    const downloadThreatReport = useCallback(async () => {
        try {
            const filtersForReport = {
                startTimestamp: [startTimestamp.toString()],
                endTimestamp: [endTimestamp.toString()],
                severity: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'],
                label: ['THREAT'],
                ...additionalFilters
            }

            const response = await api.generateThreatReport(filtersForReport, null)
            const reportId = response.generatedReportId

            // Pass category so the PDF header can render an isolated title (API/Agentic/Endpoint)
            const categoryShortName = categoryToShortName[getDashboardCategory()] || 'API'
            window.open(`/dashboard/threat-detection/report/${reportId}?category=${categoryShortName}`, '_blank')

            func.setToast(true, false, "Opening threat report...")

            if (onComplete) {
                onComplete()
            }
        } catch (error) {
            func.setToast(true, true, "Failed to generate threat report")
        }
    }, [startTimestamp, endTimestamp, additionalFilters, onComplete])

    return { downloadThreatReport }
}

export default useThreatReportDownload

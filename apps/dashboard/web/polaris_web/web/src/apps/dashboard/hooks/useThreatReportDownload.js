import { useCallback } from 'react'
import api from '@/apps/dashboard/pages/threat_detection/api'
import func from '@/util/func'

function useThreatReportDownload({ startTimestamp, endTimestamp, onComplete }) {
    const downloadThreatReport = useCallback(async () => {
        try {
            const filtersForReport = {
                startTimestamp: [startTimestamp.toString()],
                endTimestamp: [endTimestamp.toString()],
                severity: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'],
                label: ['THREAT']
            }

            const response = await api.generateThreatReport(filtersForReport, null)
            const reportId = response.generatedReportId

            window.open(`/dashboard/threat-detection/report/${reportId}`, '_blank')

            func.setToast(true, false, "Opening threat report...")

            if (onComplete) {
                onComplete()
            }
        } catch (error) {
            func.setToast(true, true, "Failed to generate threat report")
        }
    }, [startTimestamp, endTimestamp, onComplete])

    return { downloadThreatReport }
}

export default useThreatReportDownload

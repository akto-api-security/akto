import { useCallback } from 'react'
import api from '@/apps/dashboard/pages/threat_detection/api'
import func from '@/util/func'

/**
 * Custom hook for generating and opening threat reports
 *
 * @param {Object} config - Configuration object
 * @param {number} config.startTimestamp - Start timestamp for the report
 * @param {number} config.endTimestamp - End timestamp for the report
 * @param {Function} config.onComplete - Optional callback to run after successful report generation
 * @returns {Object} { downloadThreatReport } - Function to trigger report download
 */
function useThreatReportDownload({ startTimestamp, endTimestamp, onComplete }) {
    const downloadThreatReport = useCallback(async () => {
        try {
            // Collect filters for report generation
            const filtersForReport = {
                startTimestamp: [startTimestamp.toString()],
                endTimestamp: [endTimestamp.toString()],
                severity: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'],
                label: ['THREAT']
            }

            // Generate report document
            const response = await api.generateThreatReport(filtersForReport, null)
            const reportId = response.generatedReportId

            // Navigate to report page in new tab
            window.open(`/dashboard/threat-detection/report/${reportId}`, '_blank')

            func.setToast(true, false, "Opening threat report...")

            // Call optional completion callback
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

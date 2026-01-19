import { useState } from 'react'
import func from '@/util/func'

function useReportPDFDownload({ downloadFunction, organizationName, currentDate, userName, filename }) {
    const [pdfDownloadEnabled, setPdfDownloadEnabled] = useState(true)

    const handleDownloadPDF = async () => {
        const WAIT_DURATION = 5000
        const MAX_RETRIES = 60
        const reportUrl = window.location.href

        let pdfError = ""
        let status
        let pdf

        setPdfDownloadEnabled(false)

        let reportToastInterval = setInterval(() => {
            func.setToast(true, false, "Preparing your report. This might take a moment...")
        }, 1000)

        let generationStarted = false
        setTimeout(() => {
            clearInterval(reportToastInterval)
            generationStarted = true
            if (status === "IN_PROGRESS") {
                reportToastInterval = setInterval(() => {
                    func.setToast(true, false, "Report PDF generation in progress. Please wait...")
                }, 1000)
            }
        }, 6000)

        try {
            const startDownloadResponse = await downloadFunction(null, organizationName, currentDate, reportUrl, userName, true)
            const reportId = startDownloadResponse?.reportId
            status = startDownloadResponse?.status
            pdf = startDownloadResponse?.pdf

            if (reportId !== null && status === "IN_PROGRESS") {
                for (let i = 0; i < MAX_RETRIES; i++) {
                    const pdfPollResponse = await downloadFunction(reportId, organizationName, currentDate, reportUrl, userName, false)
                    status = pdfPollResponse?.status

                    if (status === "COMPLETED") {
                        pdf = pdfPollResponse?.pdf
                        break
                    } else if (status === "ERROR") {
                        pdfError = "Failed to download PDF"
                        break
                    }

                    await func.sleep(WAIT_DURATION)

                    func.setToast(generationStarted, false, "Report PDF generation in progress. Please wait...")

                    if (i === MAX_RETRIES - 1) {
                        pdfError = "Failed to download PDF. The size might be too large. Filter out unnecessary issues and try again."
                    }
                }
            } else {
                if (status !== "COMPLETED") {
                    pdfError = "Failed to start PDF download"
                }
            }
        } catch (err) {
            pdfError = err?.response?.data?.actionErrors?.[0] || err.message
        }

        clearInterval(reportToastInterval)

        if (status === "COMPLETED") {
            if (pdf === undefined) {
                pdfError = "Failed to download PDF"
            } else {
                try {
                    const byteCharacters = atob(pdf)
                    const byteNumbers = new Array(byteCharacters.length)
                    for (let i = 0; i < byteCharacters.length; i++) {
                        byteNumbers[i] = byteCharacters.charCodeAt(i)
                    }
                    const byteArray = new Uint8Array(byteNumbers)
                    const blob = new Blob([byteArray], { type: "application/pdf" })
                    const link = document.createElement("a")
                    link.href = window.URL.createObjectURL(blob)
                    link.setAttribute("download", filename)
                    document.body.appendChild(link)
                    link.click()
                    func.setToast(true, false, "Report PDF downloaded.")
                } catch (err) {
                    pdfError = err?.response?.data?.actionErrors?.[0] || err.message
                }
            }
        }

        if (pdfError !== "") {
            func.setToast(true, true, `Error: ${pdfError}`)
        }

        setPdfDownloadEnabled(true)
    }

    return { handleDownloadPDF, pdfDownloadEnabled }
}

export default useReportPDFDownload

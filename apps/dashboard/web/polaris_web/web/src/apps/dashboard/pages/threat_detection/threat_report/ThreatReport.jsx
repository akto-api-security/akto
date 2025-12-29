import { Box, Button, Frame, HorizontalStack, Text, TopBar, VerticalStack } from '@shopify/polaris'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import api from '../api'
import func from '@/util/func'
import { isApiSecurityCategory } from '@/apps/main/labelHelper'
import ThreatReportHeader from './ThreatReportHeader'
import ThreatReportTOC from './ThreatReportTOC'
import ThreatReportSummary from './ThreatReportSummary'
import ThreatReportFindings from './ThreatReportFindings'
import ThreatReportConclusion from './ThreatReportConclusion'
import ThreatReportFooter from './ThreatReportFooter'
import transform from './transform'
import useReportPDFDownload from '@/apps/dashboard/components/shared/reports/useReportPDFDownload'
import "./styles.css"

const ThreatReport = () => {
    const { reportId } = useParams()

    const [loading, setLoading] = useState(true)
    const [totalThreats, setTotalThreats] = useState(0)
    const [severityCount, setSeverityCount] = useState({ CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 })
    const [threatsByActor, setThreatsByActor] = useState([])
    const [threatsByCategory, setThreatsByCategory] = useState([])
    const [threatsTableData, setThreatsTableData] = useState([])
    const [topAttackedApis, setTopAttackedApis] = useState([])
    const [dateRange, setDateRange] = useState({ start: null, end: null })
    const [organizationName, setOrganizationName] = useState(func.capitalizeFirstLetter(window.ACCOUNT_NAME || ""))
    const [currentDate, setCurrentDate] = useState('-')
    const [userName, setUserName] = useState(func.capitalizeFirstLetter(window.USER_NAME.split('@')[0] || ""))

    const { handleDownloadPDF: handleDownloadPF, pdfDownloadEnabled } = useReportPDFDownload({
        downloadFunction: api.downloadThreatReportPDF,
        organizationName,
        currentDate,
        userName,
        filename: "akto_threat_findings.pdf"
    })

    const fetchThreatData = async () => {
        try {
            setLoading(true)

            const templatesResponse = await api.fetchFilterYamlTemplate()
            const templates = Array.isArray(templatesResponse?.templates) ? templatesResponse.templates : []

            const localThreatFiltersMap = {}
            templates.forEach((x) => {
                const trimmed = { ...x, content: '', ...x.info }
                delete trimmed['info']
                localThreatFiltersMap[x.id] = trimmed
            })

            const filterResponse = await api.getThreatReportFilters(reportId)
            const filters = filterResponse?.response?.filtersForReport || {}

            if (filters?.startTimestamp && filters?.endTimestamp) {
                setDateRange({
                    start: new Date(parseInt(filters.startTimestamp[0]) * 1000),
                    end: new Date(parseInt(filters.endTimestamp[0]) * 1000)
                })
            }

            const startTs = filters?.startTimestamp ? parseInt(filters.startTimestamp[0]) : undefined
            const endTs = filters?.endTimestamp ? parseInt(filters.endTimestamp[0]) : undefined

            const threatResponse = await api.fetchSuspectSampleData(
                0,
                [],
                [],
                [],
                [],
                { detectedAt: -1 },
                startTs,
                endTs,
                [],
                2000,
                undefined,
                undefined,
                'THREAT',
                undefined,
                undefined
            )

            const allThreats = threatResponse.maliciousEvents || []

            // For API Security: Filter by successfulExploit === true
            // For Agentic Security/MCP: Include all threats (successfulExploit field may be false for blocked attacks)
            let filteredThreats = isApiSecurityCategory()
                ? allThreats.filter(threat => threat.successfulExploit === true)
                : allThreats

            // Limit to 200 threats maximum for report (roughly 30 pages, ~6-7 threats per page)
            const THREAT_LIMIT = 200
            const totalFilteredThreats = filteredThreats.length
            const hasMoreThreats = totalFilteredThreats > THREAT_LIMIT

            if (hasMoreThreats) {
                filteredThreats = filteredThreats.slice(0, THREAT_LIMIT)
                func.setToast(true, false, `Report limited to ${THREAT_LIMIT} most recent threats out of ${totalFilteredThreats} total. Please use filters to narrow down results.`)
            }

            const threats = filteredThreats.map(threat => ({
                ...threat,
                detectedAt: threat.timestamp,
                latestApiMethod: threat.method,
                latestApiEndpoint: threat.url,
                latestApiOrig: threat.payload,
                severity: localThreatFiltersMap[threat?.filterId]?.severity || 'HIGH'
            }))

            const transformedData = transform.processThreatsForReport(threats)

            setTotalThreats(transformedData.totalThreats)
            setSeverityCount(transformedData.severityCount)
            setThreatsByActor(transformedData.threatsByActor)
            setThreatsByCategory(transformedData.threatsByCategory)
            setThreatsTableData(transformedData.threatsTableData)

            try {
                const topResponse = await api.fetchThreatTopNData(startTs, endTs, [], 5)
                if (topResponse?.topApis && Array.isArray(topResponse.topApis)) {
                    setTopAttackedApis(topResponse.topApis)
                }
            } catch (error) {
                console.error('Error fetching top attacked APIs:', error)
            }

            if (currentDate === '-' && threats.length > 0) {
                setCurrentDate(func.getFormattedDate(Date.now() / 1000))
            }

            setLoading(false)
        } catch (error) {
            console.error('Error fetching threat data:', error)
            func.setToast(true, true, 'Error loading threat report')
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchThreatData()
    }, [reportId])

    const reportSecondaryMenu = (
        <div className="report-header-css header-css" id="report-secondary-menu-container">
            <Box width="100%">
                <HorizontalStack align="space-between">
                    <VerticalStack>
                        <Text variant="headingXs">{organizationName} Threat Detection Report</Text>
                        <Text variant="bodySm">{currentDate}</Text>
                    </VerticalStack>
                    <HorizontalStack align="center" gap="4">
                        {(window.USER_NAME?.toLowerCase()?.includes("@akto.io") || window.ACCOUNT_NAME?.toLowerCase()?.includes("advanced bank")) &&
                            <Button primary onClick={() => handleDownloadPF()} disabled={!pdfDownloadEnabled}>Download</Button>
                        }
                        <img src='/public/white_logo.svg' alt="Logo" className='top-bar-logo' />
                    </HorizontalStack>
                </HorizontalStack>
            </Box>
        </div>
    )

    const reportTopBar = <TopBar secondaryMenu={reportSecondaryMenu} />

    if (loading) {
        return (
            <Frame topBar={reportTopBar}>
                <Box padding="5">
                    <Text alignment="center">Loading threat report...</Text>
                </Box>
            </Frame>
        )
    }

    return (
        <Frame topBar={reportTopBar}>
            <div id="report-container">
                <Box background="bg">
                    <ThreatReportHeader
                        organizationName={organizationName}
                        setOrganizationName={setOrganizationName}
                        currentDate={currentDate}
                        userName={userName}
                        setUserName={setUserName}
                    />
                    <div className="report-page-break" />
                    <ThreatReportTOC
                        organizationName={organizationName}
                        severityCount={severityCount}
                    />
                    <div className="report-page-break" />
                    <ThreatReportSummary
                        totalThreats={totalThreats}
                        severityCount={severityCount}
                        threatsByActor={threatsByActor}
                        threatsByCategory={threatsByCategory}
                        dateRange={dateRange}
                        organizationName={organizationName}
                        topAttackedApis={topAttackedApis}
                    />
                    <div className="report-page-break" />
                    <ThreatReportFindings
                        threatsTableData={threatsTableData}
                        severityCount={severityCount}
                        organizationName={organizationName}
                    />
                    <div className="report-page-break" />
                    <ThreatReportConclusion />
                    <div className="report-page-break" />
                    <ThreatReportFooter />
                </Box>
            </div>
        </Frame>
    )
}

export default ThreatReport

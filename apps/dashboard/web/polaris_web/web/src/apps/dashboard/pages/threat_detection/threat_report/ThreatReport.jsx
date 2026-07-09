import { Box, Button, Frame, HorizontalStack, Text, TopBar, VerticalStack } from '@shopify/polaris'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import api from '../api'
import func from '@/util/func'
import { isApiSecurityCategory, isAgenticSecurityCategory, isEndpointSecurityCategory, shortNameToCategory, getReportCategoryShortName } from '@/apps/main/labelHelper'
import PersistStore from '@/apps/main/PersistStore'
import { resolveComplianceClauseMap, mergePolicyComplianceMap, extractRuleViolated } from '../utils/formatUtils'
import guardrailApi from '../../guardrails/api'
import ThreatReportHeader from './ThreatReportHeader'
import ThreatReportTOC from './ThreatReportTOC'
import ThreatReportSummary from './ThreatReportSummary'
import ThreatReportFindings from './ThreatReportFindings'
import ThreatReportConclusion from './ThreatReportConclusion'
import ThreatReportFooter from './ThreatReportFooter'
import ThreatReportComplianceContext from './ThreatReportComplianceContext'
import transform from './transform'
import useReportPDFDownload from '@/apps/dashboard/components/shared/reports/useReportPDFDownload'
import "./styles.css"

// Apply ?category= override synchronously before first render so all
// isAgenticSecurityCategory()/isEndpointSecurityCategory() calls in the data
// fetch see the correct value. Puppeteer opens this page in a fresh session
// where window.DASHBOARD_CATEGORY defaults to API_SECURITY.
const categoryShortName = getReportCategoryShortName()
const categoryOverride = shortNameToCategory[categoryShortName]
if (categoryOverride) {
    PersistStore.getState().setDashboardCategory(categoryOverride)
}

const ThreatReport = () => {
    const { reportId } = useParams()

    const [loading, setLoading] = useState(true)
    const [totalThreats, setTotalThreats] = useState(0)
    const [severityCount, setSeverityCount] = useState({ CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 })
    const [threatsByActor, setThreatsByActor] = useState([])
    const [threatsByCategory, setThreatsByCategory] = useState([])
    const [threatsTableData, setThreatsTableData] = useState([])
    const [groupedThreatsData, setGroupedThreatsData] = useState([])
    const [topAttackedApis, setTopAttackedApis] = useState([])
    const [dateRange, setDateRange] = useState({ start: null, end: null })
    const [activeComplianceFilters, setActiveComplianceFilters] = useState([])
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

            // guardrailComplianceMap: keyed by capability name (e.g. "banCodeDetection")
            // used by transform.processThreatsForReport for Agentic/Endpoint rows
            let localGuardrailComplianceMap = {}
            try {
                // API Security: threat_compliance/{filterId}.conf
                const complianceResp = await api.fetchThreatComplianceInfos()
                if (complianceResp?.threatComplianceInfos && Array.isArray(complianceResp.threatComplianceInfos)) {
                    const threatComplianceMap = {}
                    complianceResp.threatComplianceInfos.forEach((compliance) => {
                        threatComplianceMap[compliance._id] = compliance
                    })
                    Object.keys(localThreatFiltersMap).forEach((filterId) => {
                        const compliance = threatComplianceMap[`threat_compliance/${filterId}.conf`]
                        if (compliance) {
                            localThreatFiltersMap[filterId] = {
                                ...localThreatFiltersMap[filterId],
                                compliance: { mapComplianceToListClauses: compliance.mapComplianceToListClauses }
                            }
                        }
                    })
                }

                // Agentic/Endpoint Security: capability-keyed map for transform.js
                if (isAgenticSecurityCategory() || isEndpointSecurityCategory()) {
                    const guardrailComplianceResp = await api.fetchGuardrailComplianceInfos()
                    if (guardrailComplianceResp?.guardrailComplianceInfos && Array.isArray(guardrailComplianceResp.guardrailComplianceInfos)) {
                        guardrailComplianceResp.guardrailComplianceInfos.forEach((entry) => {
                            const capability = (entry._id || '').replace('guardrails/', '').replace('.conf', '')
                            if (capability) localGuardrailComplianceMap[capability] = entry.mapComplianceToListClauses
                        })
                    }
                    const policiesResp = await guardrailApi.fetchGuardrailPolicies()
                    mergePolicyComplianceMap(localGuardrailComplianceMap, policiesResp?.guardrailPolicies)
                }
            } catch (e) {
                console.error(`Failed to fetch and merge threat compliance: ${e?.message}`)
            }

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

            // Restore saved filters — compliance is client-side only, rest go to the backend call
            const savedIps = filters?.actor || []
            const savedUrls = filters?.url || []
            const savedHosts = filters?.host || []
            const savedTypes = filters?.type || []
            const savedCompliance = filters?.compliance || []
            setActiveComplianceFilters(savedCompliance)

            const isApiSecurity = isApiSecurityCategory()
            const isGuardrail = isAgenticSecurityCategory() || isEndpointSecurityCategory()

            const threatResponse = await api.fetchSuspectSampleData(
                0,
                savedIps,
                [],
                savedUrls,
                savedTypes,
                { detectedAt: -1 },
                startTs,
                endTs,
                [],
                100000,
                undefined,
                isGuardrail ? undefined : true,
                'THREAT',
                savedHosts,
                undefined
            )

            const allThreats = threatResponse.maliciousEvents || []

            // API Security: include only successful exploits. Guardrail (Agentic/Endpoint):
            // include all events — the backend skips the successfulExploit filter for these.
            let filteredThreats = isApiSecurity
                ? allThreats.filter(threat => threat.successfulExploit === true)
                : allThreats

            // Client-side compliance filter — backend has no compliance param
            if (savedCompliance.length > 0) {
                filteredThreats = filteredThreats.filter(threat => {
                    const available = Object.keys(resolveComplianceClauseMap(threat, isGuardrail, localThreatFiltersMap, localGuardrailComplianceMap))
                    return savedCompliance.some(selected =>
                        available.some(c => c.toUpperCase() === selected.toUpperCase())
                    )
                })
            }

            if (isApiSecurity && allThreats.length > filteredThreats.length) {
                func.setToast(true, false, `Report shows ${filteredThreats.length} successful threats out of ${allThreats.length} total threats detected. Only successful exploits are included in this report.`)
            }

            // Grouped rows: for compliance context bar graph (clause counts per group)
            const { groupedRows } =
                transform.groupRawEventsForReport(filteredThreats, localThreatFiltersMap, localGuardrailComplianceMap)

            // Findings table: individual events (each raw event = one row), compliance-filtered
            const findingsRows = filteredThreats.map(threat => {
                const complianceMap = resolveComplianceClauseMap(threat, isGuardrail, localThreatFiltersMap, localGuardrailComplianceMap)
                const effectiveSeverity = isGuardrail
                    ? (threat.severity || 'HIGH').toUpperCase()
                    : (localThreatFiltersMap[threat?.filterId]?.severity || 'HIGH').toUpperCase()
                const normalizedSeverity = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].includes(effectiveSeverity) ? effectiveSeverity : 'HIGH'

                return {
                    id: threat.id || threat._id,
                    refId: threat.refId || '',
                    eventType: threat.eventType || 'SINGLE',
                    actor: threat.actor || 'Unknown',
                    time: threat.timestamp ? new Date(threat.timestamp * 1000).toLocaleString() : '-',
                    timestamp: threat.timestamp || 0,
                    category: threat.filterId || 'Unknown',
                    targetedApi: `${threat.method || ''} ${threat.url || ''}`.trim(),
                    endpoint: threat.url || '',
                    method: threat.method || '',
                    severity: normalizedSeverity,
                    filterId: threat.filterId || '',
                    ruleViolated: extractRuleViolated(threat?.metadata),
                    compliance: Object.keys(complianceMap),
                    complianceWithClauses: complianceMap,
                }
            }).sort((a, b) => {
                const order = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
                const diff = order.indexOf(a.severity) - order.indexOf(b.severity)
                return diff !== 0 ? diff : b.timestamp - a.timestamp
            })

            // All per-event counts from raw individual events
            const actorMap = {}
            const categoryMap = {}
            filteredThreats.forEach(t => {
                const actor = t.actor || 'Unknown'
                if (!actorMap[actor]) actorMap[actor] = { actor, country: t.country || 'Unknown', count: 0 }
                actorMap[actor].count++

                const cat = t.filterId || 'Unknown'
                categoryMap[cat] = (categoryMap[cat] || 0) + 1
            })
            const sortedActors = Object.values(actorMap).sort((a, b) => b.count - a.count)
            const sortedCategories = Object.entries(categoryMap)
                .map(([category, count]) => ({ category, count }))
                .sort((a, b) => b.count - a.count)

            // Severity counts from individual rows (matches findings table row counts)
            const findingsSeverityCount = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
            findingsRows.forEach(r => { if (findingsSeverityCount[r.severity] !== undefined) findingsSeverityCount[r.severity]++ })

            // totalThreats = individual event count (rows shown in findings table)
            setTotalThreats(findingsRows.length)
            setSeverityCount(findingsSeverityCount)
            setThreatsByActor(sortedActors)
            setThreatsByCategory(sortedCategories)
            setThreatsTableData(findingsRows)
            setGroupedThreatsData(groupedRows)

            try {
                const topResponse = await api.fetchThreatTopNData(startTs, endTs, [], 5)
                if (topResponse?.topApis && Array.isArray(topResponse.topApis)) {
                    setTopAttackedApis(topResponse.topApis)
                }
            } catch (error) {
                console.error('Error fetching top attacked APIs:', error)
            }

            if (currentDate === '-' && filteredThreats.length > 0) {
                setCurrentDate(func.getFormattedDate(Date.now() / 1000))
            }

            setLoading(false)
            window.__AKTO_REPORT_READY = true
        } catch (error) {
            console.error('Error fetching threat data:', error)
            func.setToast(true, true, 'Error loading threat report')
            setLoading(false)
            window.__AKTO_REPORT_READY = true
        }
    }

    useEffect(() => {
        window.__AKTO_REPORT_READY = false
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
                        <Button primary onClick={() => handleDownloadPF()} loading={!pdfDownloadEnabled} disabled={!pdfDownloadEnabled}>Download</Button>
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
                        activeComplianceFilters={activeComplianceFilters}
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
                        activeComplianceFilters={activeComplianceFilters}
                        complianceContextSlot={
                            (isAgenticSecurityCategory() || isEndpointSecurityCategory()) && activeComplianceFilters.length > 0
                                ? <ThreatReportComplianceContext
                                    activeComplianceFilters={activeComplianceFilters}
                                    groupedThreatsData={groupedThreatsData}
                                    threatsTableData={threatsTableData}
                                  />
                                : null
                        }
                    />
                    <div className="report-page-break" />
                    <ThreatReportFindings
                        threatsTableData={threatsTableData}
                        severityCount={severityCount}
                        organizationName={organizationName}
                        activeComplianceFilters={activeComplianceFilters}
                        sectionNumber={2}
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

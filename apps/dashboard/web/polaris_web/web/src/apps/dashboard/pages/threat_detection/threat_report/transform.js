import func from '@/util/func'
import { resolveComplianceClauseMap } from '../utils/formatUtils'
import { isAgenticSecurityCategory, isEndpointSecurityCategory } from '@/apps/main/labelHelper'

const transform = {
    
    groupRawEventsForReport(rawEvents, localThreatFiltersMap = {}, localGuardrailComplianceMap = {}, collectionsMap = {}) {
        const isGuardrail = isAgenticSecurityCategory() || isEndpointSecurityCategory()
        const uniqueThreatsMap = new Map()
        const severityCount = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
        const threatsByCategoryMap = {}

        rawEvents.forEach(item => {
            const effectiveSeverity = isGuardrail
                ? ((item.severity || 'HIGH').toUpperCase())
                : ((localThreatFiltersMap[item?.filterId]?.severity || 'HIGH').toUpperCase())
            const normalizedSeverity = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].includes(effectiveSeverity) ? effectiveSeverity : 'HIGH'

            const complianceMap = resolveComplianceClauseMap(item, isGuardrail, localThreatFiltersMap, localGuardrailComplianceMap)

            const key = `${item?.filterId}|${normalizedSeverity}`
            const getDomain = (i) => {
                if (i?.apiCollectionId && collectionsMap[i.apiCollectionId]) return collectionsMap[i.apiCollectionId]
                if (i?.host) return i.host
                return '-'
            }

            if (!uniqueThreatsMap.has(key)) {
                severityCount[normalizedSeverity]++
                const category = item.filterId || 'Unknown'
                if (!threatsByCategoryMap[category]) {
                    threatsByCategoryMap[category] = { category, count: 0 }
                }
                threatsByCategoryMap[category].count++

                uniqueThreatsMap.set(key, {
                    id: item.filterId,
                    refId: item.refId || '',
                    actor: item.actor || 'Unknown',
                    time: item.timestamp ? new Date(item.timestamp * 1000).toLocaleString() : '-',
                    timestamp: item.timestamp || 0,
                    category: item.filterId || 'Unknown',
                    targetedApi: `${item.method || ''} ${item.url || ''}`.trim(),
                    endpoint: item.url || '',
                    method: item.method || '',
                    severity: normalizedSeverity,
                    filterId: item.filterId || '',
                    compliance: Object.keys(complianceMap),
                    complianceWithClauses: complianceMap,
                    numberOfEndpoints: 1,
                    domains: [getDomain(item)],
                    country: item.country || '',
                })
            } else {
                const existing = uniqueThreatsMap.get(key)
                existing.numberOfEndpoints++
                const domain = getDomain(item)
                if (!existing.domains.includes(domain)) existing.domains.push(domain)
                if (item.timestamp && item.timestamp > existing.timestamp) {
                    existing.timestamp = item.timestamp
                    existing.time = new Date(item.timestamp * 1000).toLocaleString()
                }
            }
        })

        const groupedRows = Array.from(uniqueThreatsMap.values())
            .sort((a, b) => {
                const order = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
                const diff = order.indexOf(a.severity) - order.indexOf(b.severity)
                return diff !== 0 ? diff : b.timestamp - a.timestamp
            })

        const threatsByCategory = Object.values(threatsByCategoryMap)
            .sort((a, b) => b.count - a.count)

        return { groupedRows, severityCount, threatsByCategory }
    },

    processThreatsForReport(threats, threatFiltersMap = {}, guardrailComplianceMap = {}) {
        const isGuardrail = isAgenticSecurityCategory() || isEndpointSecurityCategory()
        const severityCount = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
        const threatsByActor = {}
        const threatsByCategory = {}
        const threatsTableData = []

        threats.forEach((threat) => {
            // Count by severity - normalize to uppercase and default to MEDIUM
            let severity = (threat.severity || 'MEDIUM').toUpperCase()
            // Ensure it's one of the valid severities
            if (!['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].includes(severity)) {
                severity = 'MEDIUM'
            }

            severityCount[severity]++

            // Group by actor
            const actor = threat.actor || 'Unknown'
            if (!threatsByActor[actor]) {
                threatsByActor[actor] = {
                    actor: actor,
                    country: threat.country || 'Unknown',
                    count: 0,
                    threats: []
                }
            }
            threatsByActor[actor].count++
            threatsByActor[actor].threats.push(threat)

            // Group by category
            const category = threat.category || 'Unknown'
            if (!threatsByCategory[category]) {
                threatsByCategory[category] = {
                    category: category,
                    subCategory: threat.subCategory || '',
                    count: 0,
                    severityCounts: { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 },
                    threats: []
                }
            }
            threatsByCategory[category].count++
            threatsByCategory[category].severityCounts[severity]++
            threatsByCategory[category].threats.push(threat)

            // Prepare table data
            const complianceMap = resolveComplianceClauseMap(threat, isGuardrail, threatFiltersMap, guardrailComplianceMap);
            threatsTableData.push({
                id: threat.id || threat._id,
                refId: threat.refId || '',
                actor: actor,
                time: threat.detectedAt ? new Date(threat.detectedAt * 1000).toLocaleString() : '-',
                timestamp: threat.detectedAt || 0,
                category: category,
                subCategory: threat.subCategory || '',
                targetedApi: `${threat.latestApiMethod || ''} ${threat.latestApiEndpoint || ''}`.trim(),
                endpoint: threat.latestApiEndpoint || '',
                method: threat.latestApiMethod || '',
                severity: severity,
                country: threat.country || '',
                payload: threat.latestApiOrig || '',
                filterId: threat.filterId || '',
                compliance: Object.keys(complianceMap),
                complianceWithClauses: complianceMap
            })
        })

        // Sort table data by timestamp (most recent first)
        threatsTableData.sort((a, b) => b.timestamp - a.timestamp)

        // Sort actors and categories by count (descending)
        const sortedActors = Object.values(threatsByActor).sort((a, b) => b.count - a.count)
        const sortedCategories = Object.values(threatsByCategory).sort((a, b) => b.count - a.count)

        return {
            totalThreats: threats.length,
            severityCount,
            threatsByActor: sortedActors,
            threatsByCategory: sortedCategories,
            threatsTableData
        }
    },

    /**
     * Get top N threat actors by threat count
     * @param {Array} threatsByActor - Array of threat actor objects
     * @param {Number} n - Number of top actors to return
     * @returns {Array} Top N actors
     */
    getTopActors(threatsByActor, n = 10) {
        return threatsByActor
            .sort((a, b) => b.count - a.count)
            .slice(0, n)
    },

    /**
     * Get top N threat categories by threat count
     * @param {Array} threatsByCategory - Array of threat category objects
     * @param {Number} n - Number of top categories to return
     * @returns {Array} Top N categories
     */
    getTopCategories(threatsByCategory, n = 10) {
        return threatsByCategory
            .sort((a, b) => b.count - a.count)
            .slice(0, n)
    },

    /**
     * Format severity count for charts
     * @param {Object} severityCount - Object with severity counts
     * @returns {Array} Array of chart data objects
     */
    formatSeverityForChart(severityCount) {
        return Object.entries(severityCount)
            .filter(([severity, count]) => count > 0)
            .map(([severity, count]) => ({
                name: func.toSentenceCase(severity),
                value: count,
                color: func.getHexColorForSeverity(severity)
            }))
    }
}

export default transform

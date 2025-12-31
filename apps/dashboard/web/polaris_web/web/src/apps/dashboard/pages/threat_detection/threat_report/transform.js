import func from '@/util/func'

const transform = {
    /**
     * Process threats data for report display
     * @param {Array} threats - Array of malicious event objects
     * @param {Object} threatFiltersMap - Map of threat filter IDs to compliance info
     * @returns {Object} Transformed data for report
     */
    processThreatsForReport(threats, threatFiltersMap = {}) {
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

            // Get compliance information for this threat
            const filterTemplate = threatFiltersMap[threat.filterId]
            const complianceMap = filterTemplate?.compliance?.mapComplianceToListClauses || {}
            const complianceList = Object.keys(complianceMap)

            // Prepare table data
            threatsTableData.push({
                id: threat.id || threat._id,
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
                filterId: threat.filterId,
                compliance: complianceList,
                complianceMap: complianceMap
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

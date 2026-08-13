import request from "../../../../util/request"


export default {
    async fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, startTimestamp, endTimestamp, sensitive, isRequest, queryValue) {
        const resp = await request({
            url: '/api/fetchChanges',
            method: 'post',
            data: {
                sortKey,
                sortOrder,
                limit,
                skip,
                filters: Object.entries(filters).reduce((z, e) => {
                    z[e[0]] = [...e[1]]
                    return z
                }, {}),
                filterOperators,
                startTimestamp,
                endTimestamp,
                sensitive: sensitive,
                request: isRequest,
                searchString:queryValue
            }
        })
        return resp?.data
    },
    fetchRecentParams(startTimestamp, endTimestamp){
        return request({
            url: '/api/fetchRecentParams',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
    },
    // Distinct filter-dropdown values (markedBy) — replaces pulling 1000 full audit rows just to derive them.
    async fetchAuditDataFilterOptions() {
        return await request({
            url: '/api/fetchAuditDataFilterOptions',
            method: 'post',
            data: {}
        });
    },
    async fetchAuditData(sortKey, sortOrder, skip, limit, filters, filterOperators, searchString, mergeMcpServers = false, aiAgentName = null, mcpServerName = null) {
        const data = { sortKey, sortOrder, skip, limit, filters, filterOperators, searchString, mergeMcpServers };
        if (typeof aiAgentName === 'string' && aiAgentName.length > 0) data.aiAgentName = aiAgentName;
        if (typeof mcpServerName === 'string' && mcpServerName.length > 0) data.mcpServerName = mcpServerName;
        const resp = await request({
            url: '/api/fetchAuditData',
            method: 'post',
            data
        });
        return resp;
    },
    async updateAuditData(hexId, remarks, approvalData = null, hexIds = null, cascadeHostCollectionIds = null, mcpServerForAllAgents = null) {
        const data = { hexId };
        if (Array.isArray(hexIds) && hexIds.length > 0) {
            data.hexIds = hexIds;
        }
        if (Array.isArray(cascadeHostCollectionIds) && cascadeHostCollectionIds.length > 0) {
            data.cascadeHostCollectionIds = cascadeHostCollectionIds;
        }
        if (typeof mcpServerForAllAgents === 'string' && mcpServerForAllAgents.length > 0) {
            data.mcpServerForAllAgents = mcpServerForAllAgents;
        }
        if (approvalData) {
            data.approvalData = approvalData;
        } else {
            data.remarks = remarks;
        }

        const resp = await request({
            url: '/api/updateAuditData',
            method: 'post',
            data: data
        });
        return resp;
    },
    async addMcpAllowlistUrls(mcpServerUrls) {
        const list = Array.isArray(mcpServerUrls) ? mcpServerUrls : [mcpServerUrls];
        const urls = [...new Set(list.map((u) => String(u ?? '').trim()).filter(Boolean))];
        if (!urls.length) return null;
        return request({
            url: '/api/addMcpAllowlistEntry',
            method: 'post',
            data: { mcpServerUrls: urls },
        });
    },

    // Paginated AGENT_SKILL audit rows from mcp_audit_info — same response shape as
    // fetchAuditData ({ auditData: [...], total: N }). One row per (skill, mcpHost)
    // detection. The Skills tab uses this both for table rows and for the badge count.
    async fetchSkillsData(skip = 0, limit = 50, sortKey = 'lastDetected', sortOrder = -1, filters = {}, searchString = '') {
        const resp = await request({
            url: '/api/fetchSkillsData',
            method: 'post',
            data: { skip, limit, sortKey, sortOrder, filters, searchString }
        });
        return resp || { auditData: [], total: 0 };
    },
    async fetchMcpAuditInfoByCollection(apiCollectionId) {
        const id = typeof apiCollectionId === 'string' ? parseInt(apiCollectionId, 10) : apiCollectionId;
        const resp = await request({
            url: '/api/fetchMcpAuditInfoByCollection',
            method: 'post',
            data: { apiCollectionId: id }
        });
        return resp?.mcpAuditInfoList || [];
    },
    // Batch form of fetchMcpAuditInfoByCollection — one request for many collection ids instead of
    // one request per id. Returns a plain object keyed by collection id (string) -> mcpAuditInfoList.
    async fetchMcpAuditInfoByCollectionBatch(apiCollectionIds) {
        const resp = await request({
            url: '/api/fetchMcpAuditInfoByCollection',
            method: 'post',
            data: { apiCollectionIds }
        });
        return resp?.mcpAuditInfoListByCollection || {};
    },

    async fetchDataTypeNames() {
        const resp = await request({
            url: '/api/fetchDataTypeNames',
            method: 'post',
            data: {}
        })
        return resp
    },
    fetchSubTypeCountMap(startTimestamp, endTimestamp) {
        return request({
            url: '/api/fetchSubTypeCountMap',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },
    resetSampleData() {
        return request({
            url: '/api/resetSampleData',
            method: 'post',
            data: {}
        })
    },
    fillSensitiveDataTypes() {
        return request({
            url: '/api/fillSensitiveDataTypes',
            method: 'post',
            data: {}
        })
    },
    async fetchSampleData(url, apiCollectionId, method) {
        const resp = await request({
            url: '/api/fetchSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            },
            suppress403Toast: true
        })
        return resp
    },
    async fetchSensitiveSampleData(url, apiCollectionId, method) {
        const resp = await request({
            url: '/api/fetchSensitiveSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            },
            suppress403Toast: true
        })
        return resp
    },
    fetchDataTypes() {
        return request({
            url: '/api/fetchDataTypes',
            method: 'post',
            data: {}
        })
    },
    async loadSensitiveParameters(apiCollectionId, url, method, subType) {
        const resp = await request({
            url: '/api/loadSensitiveParameters',
            method: 'post',
            data: {
                apiCollectionId,
                url,
                method,
                subType
            },
            suppress403Toast: true
        })
        return resp
    },

    saveCustomDataType(dataObj) {
        return request({
            url: '/api/saveCustomDataType',
            method: 'post',
            data: dataObj
        })
    },

    saveAktoDataType(dataObj) {
        return request({
            url: '/api/saveAktoDataType',
            method: 'post',
            data: dataObj
        })
    },
    async convertSampleDataToCurl(sampleData) {
        const resp = await request({
            url: '/api/convertSampleDataToCurl',
            method: 'post',
            data: { sampleData }
        })
        return resp
    },
    async convertSampleDataToBurpRequest(sampleData) {
        const resp = await request({
            url: '/api/convertSamleDataToBurpRequest',
            method: 'post',
            data: { sampleData }
        })
        return resp
    },

    async getAllCollections() {
        return await request({
            url: '/api/getAllCollections',
            method: 'post',
            data: {}
        })
    },
    async getAllCollectionsBasic() {
        return await request({
            url: '/api/getAllCollectionsBasic',
            method: 'post',
            data: {}
        })
    },
    // Paginated, sorted, searchable grouped-asset rows for the Agentic Assets "New Layout" table —
    // replaces computing all ~800 grouped rows client-side on every load (see
    // atlas-scale-test/DASHBOARD_OPTIMIZATION.md's "paginated server-side aggregation rebuild").
    // trafficMap/riskScoreMap are the same maps AgenticAssetsPage.jsx already fetches via
    // fetchAndCacheAgenticCollectionsBundle.
    // maliciousSkillKeys is NOT sent — AgenticObserveAction computes/caches it itself now
    // (getOrBuildSkillData) instead of requiring the whole account-wide set (14,218 entries /
    // ~500KB+ on Atlas Scale Test) to be re-POSTed on every paginated request.
    async fetchAgenticAssetsSummary({ skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, sensitiveMap, startTimestamp, endTimestamp, userAnalysisFlatMap, filters, violationsByCollectionId, usernameMap, userMetadataMap } = {}) {
        const resp = await request({
            url: '/api/fetchAgenticAssetsSummary',
            method: 'post',
            data: { skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, sensitiveMap, startTimestamp, endTimestamp, userAnalysisFlatMap, filters, violationsByCollectionId, usernameMap, userMetadataMap },
        })
        return { rows: resp?.rows || [], total: resp?.total || 0 }
    },
    // Lazy per-asset detail (hostNames/collectionIds/skillNames/mcpServers/mcpServerCollectionIds/
    // devices) for exactly ONE group — fetched only when a user opens that asset's flyout, not
    // shipped for every row of every fetchAgenticAssetsSummary page (that used to make a single
    // 50-row page 16MB, mostly from raw per-device breakdowns on rows with hundreds of devices).
    // trafficMap/riskScoreMap/userAnalysisFlatMap enrich the one asset's own device list the same
    // way fetchAgenticAssetsSummary's rows used to — needed by the Overview tab's topology graph.
    async fetchAgenticAssetDetail({ groupKey, rowType, trafficMap, riskScoreMap, userAnalysisFlatMap } = {}) {
        const resp = await request({
            url: '/api/fetchAgenticAssetDetail',
            method: 'post',
            data: { groupKey, rowType, trafficMap, riskScoreMap, userAnalysisFlatMap },
        })
        return {
            hostNames: resp?.assetHostNames || [],
            collectionIds: resp?.assetCollectionIds || [],
            skillNames: resp?.assetSkillNames || [],
            mcpServers: resp?.assetMcpServers || [],
            mcpServerCollectionIds: resp?.assetMcpServerCollectionIds || {},
            devices: resp?.assetDevices || [],
            // Agent rows only — needed by the legacy Endpoints.jsx page's row-click ->
            // Inventory-filter feature (buildAgenticInventoryFilterForRow); unused by the new
            // layout's flyout.
            tagKey: resp?.assetTagKey || null,
            rawTagValues: resp?.assetRawTagValues || [],
            // Inline-topology summary for the new layout's Overview tab — computed server-side,
            // scoped to just this asset's own collections (AgenticObserveAction.fetchAgenticAssetDetail),
            // so the browser no longer needs its own fetchApiInfosFromSTIs round-trip to derive them.
            // hasInlineLlm/inlineToolNames populated for "agent" rows, mcpComponentCount for
            // "service"/"llm" rows; the other stays at its default for whichever type this row isn't.
            hasInlineLlm: resp?.assetHasInlineLlm || false,
            inlineToolNames: resp?.assetInlineToolNames || [],
            mcpComponentCount: resp?.assetMcpComponentCount || 0,
        }
    },
    // Server-side paginated device list for ONE asset's flyout Devices tab — scoped to just
    // that asset's own apiCollectionIds (cheap), not the whole account. usernameMap is the
    // same Endpoint Shield map already fetched once for the main grid (fetchEndpointShieldUserMetadata),
    // reused here so search/sort can work against the resolved person's name, not just deviceId.
    async fetchAgenticAssetDevicesPage({ apiCollectionIds, skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, userAnalysisFlatMap, usernameMap } = {}) {
        const resp = await request({
            url: '/api/fetchAgenticAssetDevicesPage',
            method: 'post',
            data: { apiCollectionIds, skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, userAnalysisFlatMap, usernameMap },
        })
        return { devices: resp?.devices || [], total: resp?.total || 0 }
    },
    // Server-side paginated, per-device TREE list for ONE asset's legacy-layout row-click page —
    // the richer sibling of fetchAgenticAssetDevicesPage above: each row is a device with its own
    // children[] (the individual member collections + their own risk/sensitive/tags), matching
    // AgentEndpointTreeTable.jsx's expandable UI but scoped/paginated instead of account-wide.
    // Deliberately does NOT take trafficMap/riskScoreMap/sensitiveMap — unlike the list pages'
    // endpoints, which need those account-wide maps client-fetched once to enrich every row of a
    // big grid, this endpoint computes them itself server-side, scoped to apiCollectionIds — no
    // point requiring the client to fetch/repost a whole-account map for a handful of entries.
    async fetchAgenticAssetEndpointsPage({ apiCollectionIds, rowType, skip, limit, sortKey, sortOrder, queryValue, usernameMap, filters } = {}) {
        const resp = await request({
            url: '/api/fetchAgenticAssetEndpointsPage',
            method: 'post',
            data: { apiCollectionIds, rowType, skip, limit, sortKey, sortOrder, queryValue, usernameMap, filters },
        })
        return {
            endpoints: resp?.endpoints || [],
            total: resp?.total || 0,
            distinctEndpointIds: resp?.distinctEndpointIds || [],
            distinctUsernames: resp?.distinctUsernames || [],
        }
    },
    // Server-side paginated Components list for ONE AI-Agent asset's flyout — merges skills,
    // built-in tools, and connected MCP servers into one batched, server-sorted/paginated list.
    // mcpServerNames is asset.mcpServers, already known/cheap client-side, passed through rather
    // than re-derived server-side.
    async fetchAgenticComponentsPage({ apiCollectionIds, mcpServerNames, mcpServerCollectionIds, skip, limit, sortKey, sortOrder, queryValue } = {}) {
        const resp = await request({
            url: '/api/fetchAgenticComponentsPage',
            method: 'post',
            data: { apiCollectionIds, mcpServerNames, mcpServerCollectionIds, skip, limit, sortKey, sortOrder, queryValue },
        })
        return { components: resp?.components || [], total: resp?.total || 0 }
    },
    // Header-tile stats (asset counts by type) for the Agentic Assets page — same classification
    // pass as fetchAgenticAssetsSummary, aggregated rather than paginated. Also returns trend/delta
    // for the Agentic Assets + Violations cards and the Top Used Applications / Top Assets with
    // Violations lists — all derived server-side from data already fetched at mount, no new fetch.
    async fetchAgenticAssetsStats({ trafficMap, riskScoreMap, startTimestamp, endTimestamp, violationsByCollectionId, userAnalysisFlatMap } = {}) {
        const resp = await request({
            url: '/api/fetchAgenticAssetsStats',
            method: 'post',
            data: { trafficMap, riskScoreMap, startTimestamp, endTimestamp, violationsByCollectionId, userAnalysisFlatMap },
        })
        return {
            totalAssets: resp?.totalAssets || 0,
            totalEndpoints: resp?.totalEndpoints || 0,
            countsByType: resp?.countsByType || {},
            monthLabels: resp?.monthLabels || [],
            assetSparkline: resp?.assetSparkline || [],
            assetDelta: resp?.assetDelta || 0,
            violationsSparkline: resp?.violationsSparkline || [],
            violationsDelta: resp?.violationsDelta || 0,
            topAssetsWithViolations: resp?.topAssetsWithViolations || [],
            topUsedApplications: resp?.topUsedApplications || [],
        }
    },
    // Paginated, sorted, searchable user/device rows for Users-and-Devices / Endpoints — same
    // lightweight-summary-first-then-slice shape as fetchAgenticAssetsSummary. groupBy: "user"|"device".
    async fetchUsersAndDevicesSummary({ groupBy, skip, limit, sortKey, sortOrder, queryValue, filters, trafficMap, riskScoreMap, sensitiveMap, usernameMap, userMetadataMap, tagsByUsername } = {}) {
        const resp = await request({
            url: '/api/fetchUsersAndDevicesSummary',
            method: 'post',
            data: { groupBy, skip, limit, sortKey, sortOrder, queryValue, filters, trafficMap, riskScoreMap, sensitiveMap, usernameMap, userMetadataMap, tagsByUsername },
        })
        return { rows: resp?.rows || [], total: resp?.total || 0 }
    },
    // Tab-header counts ("Users (N)" / "Devices (N)") for Users-and-Devices / Endpoints, each tab's
    // "Agentic assets" total, plus distinct device-tag keys for the Tags filter/"Edit device tags" modal.
    async fetchUsersAndDevicesStats({ trafficMap, riskScoreMap, usernameMap, userMetadataMap, tagsByUsername } = {}) {
        const resp = await request({
            url: '/api/fetchUsersAndDevicesStats',
            method: 'post',
            data: { trafficMap, riskScoreMap, usernameMap, userMetadataMap, tagsByUsername },
        })
        return {
            usersCount: resp?.usersCount || 0,
            devicesCount: resp?.devicesCount || 0,
            usersAgenticAssetsTotal: resp?.usersAgenticAssetsTotal || 0,
            devicesAgenticAssetsTotal: resp?.devicesAgenticAssetsTotal || 0,
            usernames: resp?.usernames || [],
            tagKeys: resp?.tagKeys || [],
        }
    },
    // Paginated top-level device rows for Endpoints' tree grid, or (when parentDeviceId is set) one
    // device's (device,service) children — see AgenticObserveAction.fetchDeviceEndpointsSummary.
    async fetchDeviceEndpointsSummary({ parentDeviceId, skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, usernameMap, deviceMetadataMap, violationsByCollectionId, filters, tagsByUsername } = {}) {
        const resp = await request({
            url: '/api/fetchDeviceEndpointsSummary',
            method: 'post',
            data: { parentDeviceId, skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, usernameMap, deviceMetadataMap, violationsByCollectionId, filters, tagsByUsername },
        })
        return { rows: resp?.rows || [], total: resp?.total || 0 }
    },
    // Endpoints header stats: trend charts, deltas, Browsers/Endpoints/Users totals — see
    // AgenticObserveAction.fetchDeviceEndpointsStats.
    async fetchDeviceEndpointsStats({ usernameMap, deviceMetadataMap, startTimestamp, endTimestamp } = {}) {
        const resp = await request({
            url: '/api/fetchDeviceEndpointsStats',
            method: 'post',
            data: { usernameMap, deviceMetadataMap, startTimestamp, endTimestamp },
        })
        return {
            deviceCount: resp?.deviceCount || 0,
            browserDeviceCount: resp?.browserDeviceCount || 0,
            totalUsers: resp?.totalUsers || 0,
            monthLabels: resp?.monthLabels || [],
            osTrend: resp?.osTrend || {},
            browserTrend: resp?.browserTrend || {},
            sparklines: resp?.sparklines || {},
            deltaEndpoints: resp?.deltaEndpoints || 0,
            deltaBrowsers: resp?.deltaBrowsers || 0,
            deltaUsers: resp?.deltaUsers || 0,
            deltaViolations: resp?.deltaViolations || 0,
            deviceIds: resp?.deviceIds || [],
        }
    },
    async createCollection(name) {
        return await request({
            url: '/api/createCollection',
            method: 'post',
            data: { collectionName: name }
        })
    },

    async deleteCollection(apiCollectionId) {
        return await request({
            url: '/api/deleteCollection',
            method: 'post',
            data: { apiCollectionId }
        })
    },

    async deleteMultipleCollections(items) {
        return await request({
            url: '/api/deleteMultipleCollections',
            method: 'post',
            data: { apiCollections: items }
        })
    },

    async deleteUntrackedCollections(apiCollectionIds) {
        return await request({
            url: '/api/deleteUntrackedCollections',
            method: 'post',
            data: { apiCollectionIds }
        })
    },
    
    async updateUserCollections(userCollectionMap) {
        return await request({
            url: '/api/updateUserCollections',
            method: 'post',
            data: {
                userCollectionMap: userCollectionMap,
            }
        })
    },

    async getAllUsersCollections() {
        return await request({
            url: '/api/getAllUsersCollections',
            method: 'post',
            data: {}
        })
    },
    saveContent(apiSpec) {
        return request({
            url: '/api/saveContent',
            method: 'post',
            data: {
                apiSpec: apiSpec.swaggerContent,
                filename: apiSpec.filename,
                apiCollectionId: apiSpec.apiCollectionId
            }
        })
    },
    loadContent(apiCollectionId) {
        return request({
            url: '/api/loadContent',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
    },
    uploadHarFile(formData) {
        return request({
            url: '/api/uploadHar',
            method: 'post',
            data: formData,
        })
    },
    uploadOpenApiFile(formData) {
        return request({
            url: '/api/importDataFromOpenApiSpec',
            method: 'post',
            data: formData,
        })
    },
    uploadTcpFile(content, apiCollectionId, skipKafka) {
        return request({
            url: '/api/uploadTcp',
            method: 'post',
            data: {
                tcpContent: content, apiCollectionId, skipKafka
            }
        })
    },
    downloadOpenApiFile(apiCollectionId, lastFetchedUrl, lastFetchedMethod) {
        return request({
            url: '/api/generateOpenApiFile',
            method: 'post',
            data: {
                apiCollectionId, lastFetchedUrl, lastFetchedMethod
            }
        })
    },
    downloadOpenApiFileForSelectedApis(apiInfoKeyList, apiCollectionId) {
        return request({
            url: '/api/generateOpenApiFile',
            method: 'post',
            data: {
                apiInfoKeyList, apiCollectionId
            }
        })
    },
    fetchOpenApiSchema(apiCollectionId) {
        return request({
            url: '/api/fetchOpenApiSchema',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },
    uploadGraphQLSchema(apiCollectionId, graphqlSchemaString) {
        return request({
            url: '/api/uploadGraphQLSchema',
            method: 'post',
            data: {
                apiCollectionId,
                graphqlSchemaString
            }
        })
    },
    fetchGraphQLSchema(apiCollectionId) {
        return request({
            url: '/api/fetchGraphQLSchema',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },
    exportToPostman(apiCollectionId) {
        return request({
            url: '/api/createPostmanApi',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },
    exportToPostmanForSelectedApis(apiInfoKeyList, apiCollectionId) {
        return request({
            url: '/api/createPostmanApi',
            method: 'post',
            data: {
                apiInfoKeyList, apiCollectionId
            }
        })
    },

    async fetchAPIsFromSourceCode(apiCollectionId) {
        return await request({
            url: '/api/fetchCodeAnalysisApiInfos',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId,
            }
        })
    },

    async fetchApisFromStis(apiCollectionId) {
        return await request({
            url: '/api/fetchApiInfosFromSTIs',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId,
            }
        })
    },
    // Batch form of fetchApisFromStis — one request for many collection ids instead of one request
    // per id. Returns a plain object keyed by collection id (string) -> STI list.
    async fetchApisFromStisBatch(apiCollectionIds) {
        const resp = await request({
            url: '/api/fetchApiInfosFromSTIs',
            method: 'post',
            data: { apiCollectionIds }
        })
        return resp?.listByCollection || {}
    },

    async fetchApiInfosForCollection(apiCollectionId) {
        return await request({
            url: '/api/fetchApiInfosForCollection',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId,
            }
        })
    },
    // Batch form of fetchApiInfosForCollection — one request for many collection ids instead of one
    // request per id. Returns a plain object keyed by collection id (string) -> apiInfoList.
    async fetchApiInfosForCollectionBatch(apiCollectionIds) {
        const resp = await request({
            url: '/api/fetchApiInfosForCollection',
            method: 'post',
            data: { apiCollectionIds }
        })
        return resp?.apiInfoListByCollection || {}
    },
    // ATLAS: single call returning skill risk / malicious / misconfigured maps for the whole account
    // (replaces the per-collection fetchApiInfosForCollection N+1 on the agentic pages).
    async fetchAgenticSkillData() {
        return await request({
            url: '/api/fetchAgenticSkillData',
            method: 'post',
            data: {}
        })
    },
    redactCollection(apiCollectionId, redacted){
        return request({
            url: '/api/redactCollection',
            method: 'post',
            data:{
                apiCollectionId,redacted
            }
        })
    },

    deleteApis(apiList){
        return request({
            url: '/api/deleteApis',
            method: 'post',
            data: {
                apiList
            }
        })
    },

    async fetchAllUrlsAndMethods (apiCollectionId) {
        const resp = await request({
            url: '/api/fetchAllUrlsAndMethods',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
        return resp
    },

    addSensitiveField (x) {
        return request({
            url: 'api/addSensitiveField',
            method: 'post',
            data: {
                ...x
            }
        })
    },
    listAllSensitiveFields() {
        return request({
            url: 'api/listAllSensitiveFields',
            method: 'post',
            data: {}
        })
    },
    async loadRecentEndpoints (startTimestamp, endTimestamp, skip, limit, filters, filterOperators, searchString) {
        const resp = await request({
            url: '/api/loadRecentEndpoints',
            method: 'post',
            data: { startTimestamp, endTimestamp, skip, limit, filters, filterOperators, searchString}
        })
        return resp
    },
    async getSummaryInfoForChanges (startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/getSummaryInfoForChanges',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp
    },
    async fetchNewEndpointsTrendForHostCollections (startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchNewEndpointsTrendForHostCollections',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp
    },
    async fetchNewEndpointsTrendForNonHostCollections (startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchNewEndpointsTrendForNonHostCollections',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp
    },
    async fetchSensitiveParamsForEndpoints (urls) {
        const resp = await request({
            url: '/api/fetchSensitiveParamsForEndpoints',
            method: 'post',
            data: { urls },
            suppress403Toast: true
        })
        return resp
    },
    async fetchEndpointTrafficData (url, apiCollectionId, method, startEpoch, endEpoch) {
        const resp = await request({
            url: '/api/fetchEndpointTrafficData',
            method: 'post',
            data: {
                url, apiCollectionId, method, startEpoch, endEpoch
            }
        })
        return resp
    },
    async fetchApiInfoList(apiCollectionId) {
        const resp = await request({
            url: '/api/fetchApiInfoList',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
        return resp
    },
    async bulkAgentProxyGuardrail(apiInfoIds, enabled, schemaConfig = {}) {
        const resp = await request({
            url: '/api/apiInfo/bulkAgentProxyGuardrail',
            method: 'post',
            data: { apiInfoIds, enabled, ...schemaConfig }
        })
        return resp
    },
    async fetchFilters() {
        const resp = await request({
            url: '/api/fetchFilters',
            method: 'post',
            data: {}
        })
        return resp
    },
    fetchWorkflowTests() {
        return request({
            url: '/api/fetchWorkflowTests',
            method: 'post',
            data: {}
        })
    },
    createWorkflowTest(nodes, edges, mapNodeIdToWorkflowNodeDetails, state, apiCollectionId) {
        return request({
            url: '/api/createWorkflowTest',
            method: 'post',
            data: {nodes, edges, mapNodeIdToWorkflowNodeDetails, state, apiCollectionId}
        })
    },

    editWorkflowTest(id, nodes, edges, mapNodeIdToWorkflowNodeDetails) {
        return request({
            url: '/api/editWorkflowTest',
            method: 'post',
            data: {id, nodes, edges, mapNodeIdToWorkflowNodeDetails}
        })
    },

    setWorkflowTestState(id, state) {
        return request({
            url: '/api/setWorkflowTestState',
            method: 'post',
            data: {id, state}
        })
    },

    exportWorkflowTestAsString(id) {
        return request({
            url: '/api/exportWorkflowTestAsString',
            method: 'post',
            data: {id}
        })
    },
    editWorkflowNodeDetails(id, nodeId, workflowNodeDetails) {
        let mapNodeIdToWorkflowNodeDetails = {}
        mapNodeIdToWorkflowNodeDetails[nodeId] = workflowNodeDetails
        return request({
            url: '/api/editWorkflowNodeDetails',
            method: 'post',
            data: {id, mapNodeIdToWorkflowNodeDetails}
        })
    },

    runWorkflowTest(id) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {
                "testIdConfig" : 1,
                "workflowTestId": id,
                "type": "WORKFLOW",
                testName: id
            }
        })
    },

    scheduleWorkflowTest(id, recurringDaily, startTimestamp) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {
                "testIdConfig" : 1,
                "workflowTestId": id,
                "type": "WORKFLOW",
                "recurringDaily": recurringDaily,
                "startTimestamp": startTimestamp,
                testName: id
            }
        })
    },

    fetchWorkflowTestingRun(workflowId) {
        return request({
            url: '/api/fetchWorkflowTestingRun',
            method: 'post',
            data: {
                "workflowTestId" : workflowId
            }
        })
    },

    deleteScheduledWorkflowTests(workflowId) {
        return request({
            url: '/api/deleteScheduledWorkflowTests',
            method: 'post',
            data: {
                "workflowTestId" : workflowId
            }
        })
    },

    fetchWorkflowResult(id) {
        return request({
            url: '/api/fetchWorkflowResult',
            method: 'post',
            data: {
                "workflowTestId": id,
            }
        })
    },

    downloadWorkflowAsJson(id) {
        return request({
            url: '/api/downloadWorkflowAsJson',
            method: 'post',
            data: {
                "id": id,
            }
        })
    },

    uploadWorkflowJson(workflowTestJson, apiCollectionId) {
        return request({
            url: '/api/uploadWorkflowJson',
            method: 'post',
            data: { workflowTestJson, apiCollectionId }
        })
    },

    async setFalsePositives (falsePositives) {
        const resp = await request({
            url: '/api/setFalsePositives',
            method: 'post',
            data: { falsePositives: falsePositives }
        })
        return resp
    },
    fetchAktoGptConfig(apiCollectionId) {
        return request({
            url: '/api/fetchAktoGptConfig',
            method: 'post',
            data: { apiCollectionId }
        }).then((resp) => {
            return resp
        })
    },
    fetchAllMarketplaceSubcategories() {
        return request({
            url: 'api/fetchAllMarketplaceSubcategories',
            method: 'post',
            data: {}
        })
    },
    scheduleTestForCollection(apiCollectionId, startTimestamp, recurringDaily, recurringWeekly, recurringMonthly, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds = [], selectedMiniTestingServiceNames, selectedSlackWebhook, autoTicketingDetails, doNotMarkIssuesAsFixed, maxAgentTokens = -1, runAutomatedTests = false) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: { apiCollectionId, type: "COLLECTION_WISE", startTimestamp, recurringDaily,  recurringWeekly, recurringMonthly,selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds, selectedMiniTestingServiceNames, selectedSlackWebhook, autoTicketingDetails, doNotMarkIssuesAsFixed, maxAgentTokens, runAutomatedTests}
        }).then((resp) => {
            return resp
        })
    },
    scheduleTestForMultipleCollections(apiCollectionIds, startTimestamp, recurringDaily, recurringWeekly, recurringMonthly, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds = [], selectedMiniTestingServiceNames, selectedSlackWebhook, autoTicketingDetails, doNotMarkIssuesAsFixed, runAutomatedTests = false) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: { apiCollectionIds, type: "MULTI_COLLECTION", startTimestamp, recurringDaily,  recurringWeekly, recurringMonthly,selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds, selectedMiniTestingServiceNames, selectedSlackWebhook, autoTicketingDetails, doNotMarkIssuesAsFixed, runAutomatedTests}
        }).then((resp) => {
            return resp
        })
    },
    scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, recurringWeekly, recurringMonthly, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds = [], selectedMiniTestingServiceNames, selectedSlackWebhook, autoTicketingDetails, doNotMarkIssuesAsFixed, maxAgentTokens = -1, runAutomatedTests = false) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiInfoKeyList, type: "CUSTOM", startTimestamp, recurringDaily,  recurringWeekly, recurringMonthly,selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds, selectedMiniTestingServiceNames, selectedSlackWebhook, autoTicketingDetails, doNotMarkIssuesAsFixed, maxAgentTokens, runAutomatedTests}
        }).then((resp) => {
            return resp
        })
    },
    async loadParamsOfEndpoint (apiCollectionId, url, method) {
        const resp = await request({
            url: '/api/loadParamsOfEndpoint',
            method: 'post',
            data: {
                apiCollectionId,
                url,
                method
            }
        })
        return resp
    },

    async fetchSlackWebhooks() {
        const resp = await request({
            url: '/api/fetchSlackWebhooks',
            method: 'post',
            data: {}
        })
        return resp
    },

    async checkWebhook(webhookType, webhookOption) {
        const resp = await request({
            url: '/api/checkWebhook',
            method: 'post',
            data: { webhookType, webhookOption }
        })
        return resp
    },

    async fetchNewParametersTrend(startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchNewParametersTrend',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp.data.endpoints
    },
    saveContent(apiSpec) {
        return request({
            url: '/api/saveContent',
            method: 'post',
            data: {
                apiSpec: apiSpec.swaggerContent,
                filename: apiSpec.filename,
                apiCollectionId: apiSpec.apiCollectionId
            }
        })
    },
    loadContent(apiCollectionId) {
        return request({
            url: '/api/loadContent',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
    },

    async addApisToCustomCollection(apiList, collectionName) {
        return await request({
            url: '/api/addApisToCustomCollection',
            method: 'post',
            data: {
                apiList, collectionName
            }
        })
    },
    async syncExtractedAPIs(apiCollectionName, projectDir, codeAnalysisApisList) {
        return await request({
            url: '/api/syncExtractedAPIs',
            method: 'post',
            data: {
                apiCollectionName, projectDir, codeAnalysisApisList
            }
        })
    },
    async removeApisFromCustomCollection(apiList, collectionName) {
        return await request({
            url: '/api/removeApisFromCustomCollection',
            method: 'post',
            data: {
                apiList, collectionName
            }
        })
    },
    async computeCustomCollections(collectionName) {
        return await request({
            url: '/api/computeCustomCollections',
            method: 'post',
            data: {
                collectionName
            }
        })
    },
    async createCustomCollection(collectionName, conditions) {
        return await request({
            url: '/api/createCustomCollection',
            method: 'post',
            data: {
                collectionName, conditions
            }
        })
    },
    async updateCustomCollection(apiCollectionId, conditions) {
        return await request({
            url: '/api/updateCustomCollection',
            method: 'post',
            data: {
                apiCollectionId, conditions
            }
        })
    },
    async getEndpointsListFromConditions(conditions, skipTagsMismatch = false) {
        return await request({
            url: '/api/getEndpointsListFromConditions',
            method: 'post',
            data: {
                conditions,
                skipTagsMismatch
            }
        }).then((resp) => {
            return resp
        })
    },
    async getEndpointsFromConditions(conditions) {
        return await request({
            url: '/api/getEndpointsFromConditions',
            method: 'post',
            data: {
                conditions
            }
        })
    },
    fetchApiDependencies(apiCollectionId, url, method) {
        return request({
            url: '/api/fetchApiDependencies',
            method: 'post',
            data: {
                apiCollectionId, url, method
            }
        })
    },

    fetchLatestTraces(apiCollectionId) {
        return request({
            url: '/api/fetchLatestTraces',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },

    fetchSpansForTrace(traceId) {
        return request({
            url: '/api/fetchSpansForTrace',
            method: 'post',
            data: {
                traceId
            }
        })
    },

    async getCoverageInfoForCollections(apiCollectionIds){
        const data = {}
        if (apiCollectionIds && apiCollectionIds.length > 0) {
            data.apiCollectionIds = apiCollectionIds
        }
        return await request({
            url: '/api/getCoverageInfoForCollections',
            method: 'post',
            data,
        })
    },

    async getSeverityInfoForCollections(){
        return await request({
            url: '/api/getSeverityInfoForCollections',
            method: 'post',
            data:{},
        })
    },

    async getSensitiveInfoForCollections(type){
        const data = (typeof type !== 'undefined' && type !== null) ? { type } : {}
        return await request({
            url: '/api/getSensitiveInfoForCollections',
            method: 'post',
            data,
        })
    },

    async getLastTrafficSeen(){
        return await request({
            url: '/api/getLastSeenTrafficInfoForCollections',
            method: 'post',
            data:{},
        })
    },

    async getRiskScoreInfo() {
        return await request({
            url: '/api/getRiskScoreInfo',
            method: 'post',
            data: {}
        })
    },

    async lastUpdatedInfo() {
        return await request({
            url: '/api/getLastCalculatedInfo',
            method: 'post',
            data: {}
        })
    },
    
    async deMergeApi(apiCollectionId, url, method){
        return await request({
            url: '/api/deMergeApi',
            method: 'post',
            data: {apiCollectionId, url, method}
        })
    },
    async bulkDeMergeApis(apiInfoKeyList){
        return await request({
            url: '/api/bulkDeMergeApis',
            method: 'post',
            data: {apiInfoKeyList}
        })
    },
    async getUserEndpoints(){
        return await request({
            url: '/api/getCustomerEndpoints',
            method: 'post',
            data:{},
        })
    },
    async updateEnvTypeOfCollection(envType, apiCollectionIds,resetEnvTypes){
        return await request({
            url: '/api/updateEnvType',
            method: 'post',
            data: {envType, apiCollectionIds,resetEnvTypes}
        })
    },
    async updateApiInfoTags(envType, apiInfoKeys, resetTags) {
        return await request({
            url: '/api/updateApiInfoTags',
            method: 'post',
            data: { envType, apiInfoKeys, resetTags }
        })
    },
    fetchEndpoint(apiInfoKey){
        return request({
            url: '/api/getSingleEndpoint',
            method: 'post',
            data: {
                url: apiInfoKey.url,
                method: apiInfoKey.method,
                apiCollectionId: apiInfoKey.apiCollectionId
            }
        })
    },
    fetchCountMapOfApis(){
        return request({
            url: "/api/fetchCountMapOfApis",
            method: "post",
            data: {},
            suppress403Toast: true
        })
    },
    resetDataTypeRetro(name){
        return request({
            url: '/api/resetDataTypeRetro',
            method: 'post',
            data: { name }
        })
    },

    async saveEndpointDescription(apiCollectionId, url, method, description) {
        const resp = await request({
            url: '/api/saveEndpointDescription',
            method: 'post',
            data: { apiCollectionId, url, method, description }
        })
        return resp
    },

    async fetchApiCallStats(apiCollectionId, url, method, startEpoch, endEpoch) {
        const resp = await request({
            url: '/api/fetchApiCallStats',
            method: 'post',
            data: { apiCollectionId, url, method, startEpoch, endEpoch },
            suppress403Toast: true
        })
        return resp
    },

    async fetchIpLevelApiCallStats(apiCollectionId, url, method, startWindow, endWindow) {
        //url = "v1/api/test/orders"
        //method = "POST"
        // startWindow = 29189000
        // endWindow = 29199000
        const resp = await request({
            url: '/api/fetchIpLevelApiCallStats',
            method: 'post',
            data: {apiCollectionId, url, method, startWindow, endWindow },
            suppress403Toast: true
        })
        return resp
    },

    async checkIfDependencyGraphAvailable(apiCollectionId, url, method) {
        return await request({
            url: '/api/checkIfDependencyGraphAvailable',
            method: 'post',
            data: {
                apiCollectionId, url, method
            }
        })
    },

    async editCollectionName(apiCollectionId, collectionName) {
        return await request({
            url: '/api/editCollectionName',
            method: 'post',
            data: {
                apiCollectionId, collectionName
            }
        })
    },

    async getSeveritiesCountPerCollection(apiCollectionId) {
        return await request({
            url: '/api/getSeveritiesCountPerCollection',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },
    
    async saveCollectionDescription(apiCollectionId, description, isSystemPrompt = false) {
        return await request({
            url: '/api/saveCollectionDescription',
            method: 'post',
            data: {
                apiCollectionId, description, isSystemPrompt
            }
        })
    },

    async findSvcToSvcGraphEdges() {
        return await request({
            url: '/api/findSvcToSvcGraphEdges',
            method: 'post',
            data: {
                startTimestamp: 0,
                endTimestamp: 0,
                skip: 0,
                limit: 1000
            }
        })
    },

    async findSvcToSvcGraphNodes() {
        return await request({
            url: '/api/findSvcToSvcGraphNodes',
            method: 'post',
            data: {
                startTimestamp: 0,
                endTimestamp: 0,
                skip: 0,
                limit: 1000
            }
        })
    },
    allApisTestedRanges(apiCollectionIds) {
        const data = {}
        if (apiCollectionIds && apiCollectionIds.length > 0) {
            data.apiCollectionIds = apiCollectionIds
        }
        return request({
            url: '/api/fetchTestedApisRanges',
            method: 'post',
            data
        })
    },

    async getApiSequences(apiCollectionId) {
        const resp = await request({
            url: '/api/getApiSequences',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
        return resp
    },

    async fetchMcpToolsApiCalls(apiCollectionId) {
        const resp = await request({
            url: '/api/fetchMcpToolsApiCalls',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
        return resp
    },

    async getSwaggerDependencies(apiCollectionId) {
        const resp = await request({
            url: '/api/getSwaggerDependencies',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
        return resp
    },

    fetchIconsForHostnames(hostnames){
        return request({
            url: '/api/fetchIconsForHostnames',
            method: 'post',
            data: { hostnames }
        })
    },

    // Unpaginated — only for CreateNhiPolicyModal's agent/identity dropdown options, which need every
    // distinct name. The Identities page no longer uses this (it used to, for the topology graph and
    // summary cards/tab counts, which pulled the whole account — 13.8k+ rows on Atlas Scale Test —
    // just to draw a handful of nodes or compute four numbers); it now uses fetchAllNhiIdentities
    // (small, capped) and fetchNhiIdentitiesStats (counts-only) below instead.
    async fetchNhiIdentities(startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchNhiIdentities',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp?.identities || []
    },

    // Server-side paginated (ATLAS NHI Governance Identities page): pass skip/limit/sortKey/sortOrder/
    // queryValue/status ("Expired"/"Disabled"/omit-for-"All") to get one page. Returns {identities, total}.
    // Also used (with a small limit) to feed IdentityOverviewGraph's topology graph.
    async fetchAllNhiIdentities(startTimestamp, endTimestamp, { skip, limit, sortKey, sortOrder, queryValue, status } = {}) {
        const resp = await request({
            url: '/api/fetchAllNhiIdentities',
            method: 'post',
            data: { startTimestamp, endTimestamp, skip, limit, sortKey, sortOrder, queryValue, status }
        })
        return { identities: resp?.identities || [], total: resp?.total || 0 }
    },

    // Counts-only (ATLAS NHI Governance Identities page): feeds the summary cards and tab badges
    // without pulling any identity documents. violatingIdentityIds (optional) is the list of hex ids
    // already known to have violations, from the cheap fetchViolationCountsByIdentity() call, used to
    // compute the "Identities with Violations" count server-side. Hex ids, not identityNames — names
    // aren't unique across identities.
    async fetchNhiIdentitiesStats(startTimestamp, endTimestamp, violatingIdentityIds) {
        const resp = await request({
            url: '/api/fetchNhiIdentitiesStats',
            method: 'post',
            data: { startTimestamp, endTimestamp, violatingIdentityIds }
        })
        return {
            total: resp?.statTotal || 0,
            expired: resp?.statExpired || 0,
            disabled: resp?.statDisabled || 0,
            withViolations: resp?.statWithViolations || 0,
        }
    },

    // Server-side paginated (ATLAS NHI Governance): pass skip/limit/sortKey/sortOrder/queryValue/status
    // to get one page; omit them to get the backend's default page (50 rows). Returns {violations, total}
    // — callers that need charts/tab-counts across the whole range should use fetchNhiViolationsStats
    // instead of paging through everything.
    async fetchAllNhiViolations(startTimestamp, endTimestamp, { skip, limit, sortKey, sortOrder, queryValue, status } = {}) {
        const resp = await request({
            url: '/api/fetchAllNhiViolations',
            method: 'post',
            data: { startTimestamp, endTimestamp, skip, limit, sortKey, sortOrder, queryValue, status }
        })
        return { violations: resp?.violations || [], total: resp?.total || 0 }
    },

    // Server-computed aggregates for the Violations page (severity breakdown, day-bucketed trend,
    // open/fixed totals) — replaces client-side reduction over the full violations list.
    async fetchNhiViolationsStats(startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchNhiViolationsStats',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp?.stats || {}
    },

    // Scoped, paginated violations for ONE identity (identity-details flyout) — replaces fetching every
    // violation account-wide with no time bound just to filter down to a single identity client-side.
    async fetchViolationsByIdentity(identityId, { skip, limit } = {}) {
        const resp = await request({
            url: '/api/fetchViolationsByIdentity',
            method: 'post',
            data: { identityId, skip, limit }
        })
        return { violations: resp?.violations || [], total: resp?.total || 0, stats: resp?.stats || {} }
    },

    // Per-identity violation counts, grouped server-side (one row per identityName x severity)
    // instead of every non-fixed violation document.
    async fetchViolationCountsByIdentity() {
        const resp = await request({
            url: '/api/fetchViolationCountsByIdentity',
            method: 'post',
            data: {}
        })
        return resp?.identityViolationCounts || []
    },

    // Per-policy violation counts, grouped server-side (one row per policyId x severity)
    // instead of every non-fixed violation projected doc.
    async fetchViolationCountsByPolicy() {
        const resp = await request({
            url: '/api/fetchViolationCountsByPolicy',
            method: 'post',
            data: {}
        })
        return resp?.policyViolationCounts || []
    },

    async disableNhiIdentity(identityId) {
        const resp = await request({
            url: '/api/disableNhiIdentity',
            method: 'post',
            data: { identityId }
        })
        return resp?.success || false
    },

    async deleteNhiIdentities(identityIds) {
        const resp = await request({
            url: '/api/deleteNhiIdentities',
            method: 'post',
            data: { identityIds }
        })
        return resp?.success || false
    },

    async markViolationAsFixed(violationId) {
        const resp = await request({
            url: '/api/markViolationAsFixed',
            method: 'post',
            data: { violationId }
        })
        return resp?.success || false
    },

    async reopenViolation(violationId) {
        const resp = await request({
            url: '/api/reopenViolation',
            method: 'post',
            data: { violationId }
        })
        return resp?.success || false
    },

    async createJiraTicketFromViolation(violationId, aktoDashboardHost, projId, issueType, jiraMetaData) {
        const resp = await request({
            url: '/api/createJiraTicketFromViolation',
            method: 'post',
            data: { violationId, aktoDashboardHost, projId, issueType, jiraMetaData }
        })
        return resp
    },

    async fetchNhiPolicies() {
        const resp = await request({
            url: '/api/fetchNhiPolicies',
            method: 'post',
            data: {}
        })
        return resp?.policies || []
    },

    async saveNhiPolicy(policy, policyId) {
        const resp = await request({
            url: '/api/saveNhiPolicy',
            method: 'post',
            data: { policy, policyId }
        })
        return resp
    },

    async deleteNhiPolicies(policyIds) {
        const resp = await request({
            url: '/api/deleteNhiPolicies',
            method: 'post',
            data: { policyIds }
        })
        return resp?.success || false
    },

    async fetchSuspectSampleData({ skip = 0, startTimestamp, endTimestamp, hosts = [], limit = 100000, sort, sortBySeverity, searchText, looseHostKeys = [], claudeDeviceIds = [], matchClaudeConfig } = {}) {
        return request({
            url: '/api/fetchSuspectSampleData',
            method: 'post',
            data: {
                skip, ips: [], urls: [], types: [], apiCollectionIds: [],
                sort: sort || { detectedAt: -1 },
                ...(startTimestamp ? { startTimestamp } : {}),
                ...(endTimestamp   ? { endTimestamp }   : {}),
                latestAttack: [],
                limit,
                ...(hosts?.length  ? { hosts }          : {}),
                ...(sortBySeverity ? { sortBySeverity }  : {}),
                ...(searchText     ? { searchText }     : {}),
                ...(looseHostKeys?.length ? { looseHostKeys } : {}),
                ...(claudeDeviceIds?.length ? { claudeDeviceIds } : {}),
                ...(matchClaudeConfig ? { matchClaudeConfig } : {}),
            },
        })
    },

    // Per-host severity counts for the whole date range (every host, not raw events) — lets a caller
    // attribute violation counts to its own asset/device groupings via the host join key without
    // pulling every raw malicious-event doc just to run a per-row severity tally client-side.
    async fetchHostSeverityCounts(startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchHostSeverityCounts',
            method: 'post',
            data: { startTs: startTimestamp, endTs: endTimestamp },
        })
        return resp?.hostSeverityCounts || []
    },

}
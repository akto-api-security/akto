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
    async fetchAgenticAssetsSummary({ skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, startTimestamp, endTimestamp } = {}) {
        const resp = await request({
            url: '/api/fetchAgenticAssetsSummary',
            method: 'post',
            data: { skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, startTimestamp, endTimestamp },
        })
        return { rows: resp?.rows || [], total: resp?.total || 0 }
    },
    // Header-tile stats (asset counts by type) for the Agentic Assets page — same classification
    // pass as fetchAgenticAssetsSummary, aggregated rather than paginated.
    async fetchAgenticAssetsStats({ trafficMap, riskScoreMap, startTimestamp, endTimestamp } = {}) {
        const resp = await request({
            url: '/api/fetchAgenticAssetsStats',
            method: 'post',
            data: { trafficMap, riskScoreMap, startTimestamp, endTimestamp },
        })
        return { totalAssets: resp?.totalAssets || 0, countsByType: resp?.countsByType || {} }
    },
    // Paginated, sorted, searchable user/device rows for Users-and-Devices / Endpoints — same
    // lightweight-summary-first-then-slice shape as fetchAgenticAssetsSummary. groupBy: "user"|"device".
    async fetchUsersAndDevicesSummary({ groupBy, skip, limit, sortKey, sortOrder, queryValue, filters, trafficMap, riskScoreMap, sensitiveMap, usernameMap, userMetadataMap } = {}) {
        const resp = await request({
            url: '/api/fetchUsersAndDevicesSummary',
            method: 'post',
            data: { groupBy, skip, limit, sortKey, sortOrder, queryValue, filters, trafficMap, riskScoreMap, sensitiveMap, usernameMap, userMetadataMap },
        })
        return { rows: resp?.rows || [], total: resp?.total || 0 }
    },
    // Tab-header counts ("Users (N)" / "Devices (N)") for Users-and-Devices / Endpoints, each tab's
    // "Agentic assets" total, plus distinct team/role names for the "Edit team & role" modal's autocomplete.
    async fetchUsersAndDevicesStats({ trafficMap, riskScoreMap, usernameMap, userMetadataMap } = {}) {
        const resp = await request({
            url: '/api/fetchUsersAndDevicesStats',
            method: 'post',
            data: { trafficMap, riskScoreMap, usernameMap, userMetadataMap },
        })
        return {
            usersCount: resp?.usersCount || 0,
            devicesCount: resp?.devicesCount || 0,
            usersAgenticAssetsTotal: resp?.usersAgenticAssetsTotal || 0,
            devicesAgenticAssetsTotal: resp?.devicesAgenticAssetsTotal || 0,
            teams: resp?.teams || [],
            roles: resp?.roles || [],
        }
    },
    // Paginated top-level device rows for Endpoints' tree grid, or (when parentDeviceId is set) one
    // device's (device,service) children — see AgenticObserveAction.fetchDeviceEndpointsSummary.
    async fetchDeviceEndpointsSummary({ parentDeviceId, skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, usernameMap, deviceMetadataMap, violationsByCollectionId } = {}) {
        const resp = await request({
            url: '/api/fetchDeviceEndpointsSummary',
            method: 'post',
            data: { parentDeviceId, skip, limit, sortKey, sortOrder, queryValue, trafficMap, riskScoreMap, usernameMap, deviceMetadataMap, violationsByCollectionId },
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

    async fetchApiInfosForCollection(apiCollectionId) {
        return await request({
            url: '/api/fetchApiInfosForCollection',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId,
            }
        })
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

    async fetchNhiIdentities(startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchNhiIdentities',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp?.identities || []
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

    async fetchSuspectSampleData({ skip = 0, startTimestamp, endTimestamp, hosts = [], limit = 100000 } = {}) {
        return request({
            url: '/api/fetchSuspectSampleData',
            method: 'post',
            data: {
                skip, ips: [], urls: [], types: [], apiCollectionIds: [],
                sort: { detectedAt: -1 },
                ...(startTimestamp ? { startTimestamp } : {}),
                ...(endTimestamp   ? { endTimestamp }   : {}),
                latestAttack: [],
                limit,
                ...(hosts?.length  ? { hosts }          : {}),
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
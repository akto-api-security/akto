package com.akto.hybrid_parsers;

import com.akto.RuntimeMode;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.traffic.CollectionTags.TagSource;
import com.akto.hybrid_dependency.DependencyAnalyser;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.monitoring.FilterConfig.FILTER_TYPE;
import com.akto.dto.settings.DefaultPayload;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.usage.MetricTypes;
import com.akto.gen_ai.GenAiCollectionUtils;
import com.akto.graphql.GraphQLUtils;
import com.akto.jsonrpc.JsonRpcUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.rest.RestMethodUtils;
import com.akto.runtime.RuntimeUtil;
import com.akto.runtime.parser.SampleParser;
import com.akto.runtime.utils.Utils;
import com.akto.test_editor.execution.ParseAndExecute;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.akto.usage.OrgUtils;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_runtime.Main;
import com.akto.hybrid_runtime.URLAggregator;
import com.akto.util.Pair;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import com.google.gson.Gson;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections.CollectionUtils;

import static com.akto.runtime.RuntimeUtil.matchesDefaultPayload;
import static com.akto.runtime.utils.Utils.printL;
import static com.akto.testing.Utils.validateFilter;
import static com.akto.util.Constants.AKTO_MCP_SERVER_TAG;

public class HttpCallParser {
    private final int sync_threshold_count;
    private final int sync_threshold_time;
    private int sync_count = 0;
    private Map<Integer, Integer> apiCollectionIdTagsSyncTimestampMap = new HashMap<>();
    private int last_synced;
    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpCallParser.class, LogDb.RUNTIME);
    private static final Gson gson = new Gson();
    public APICatalogSync apiCatalogSync;
    public DependencyAnalyser dependencyAnalyser;
    private Map<String, Integer> hostNameToIdMap = new HashMap<>();
    private Map<TrafficMetrics.Key, TrafficMetrics> trafficMetricsMap = new HashMap<>();
    public static final ScheduledExecutorService trafficMetricsExecutor = Executors.newScheduledThreadPool(1);
    private static final String NON_HOSTNAME_KEY = "null" + " "; // used for collections created without hostnames

    private static final List<Integer> INPROCESS_ADVANCED_FILTERS_ACCOUNTS = Arrays.asList(1736798101, 1718042191, 1759692400);
    private DataActor dataActor = DataActorFactory.fetchInstance();
    private Map<Integer, ApiCollection> apiCollectionsMap = new HashMap<>();

    // Pre-compiled patterns for better performance
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}.*");
    private static final String EMPTY_JSON = "{}";

    // List of ignored host names for fast contains() checks
    private static final List<String> IGNORE_HOST_NAMES = Arrays.asList(
        "svc.cluster.local",
        "localhost",
        "kubernetes.default.svc"
    );

    public HttpCallParser(String userIdentifier, int thresh, int sync_threshold_count, int sync_threshold_time, boolean fetchAllSTI) {
        last_synced = 0;
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        apiCatalogSync = new APICatalogSync(userIdentifier, thresh, fetchAllSTI);
        apiCatalogSync.buildFromDB(false, fetchAllSTI);
        apiCollectionsMap = new HashMap<>();
        apiCollectionIdTagsSyncTimestampMap = new HashMap<>();
        List<ApiCollection> apiCollections = dataActor.fetchAllApiCollectionsMeta();
        for (ApiCollection apiCollection: apiCollections) {
            apiCollectionsMap.put(apiCollection.getId(), apiCollection);
        }
        if (Context.getActualAccountId() == 1745303931 || Context.getActualAccountId() == 1741069294 || Context.getActualAccountId() == 1749515934 || Context.getActualAccountId() == 1753864648) {
            this.dependencyAnalyser = new DependencyAnalyser(apiCatalogSync.dbState, Main.isOnprem, RuntimeMode.isHybridDeployment(), apiCollectionsMap);
        }
    }
    
    public static HttpResponseParams parseKafkaMessage(String message) throws Exception {
        return SampleParser.parseSampleMessage(message);
    }

    public static String getHeaderValue(Map<String,List<String>> headers, String headerKey) {
        if (headers == null) return null;
        for (String k: headers.keySet()) {
            if (k.equalsIgnoreCase(headerKey)) {
                List<String> hosts = headers.getOrDefault(k, new ArrayList<>());
                if (hosts.size() > 0) return hosts.get(0);
                return null;
            }
        }
        return null;
    }

    public int createCollectionSimple(int vxlanId) {
        dataActor.createCollectionSimple(vxlanId);
        return vxlanId;
    }

    public int createCollectionSimpleForVpc(int vxlanId, String vpcId, List<CollectionTags> tags) {
        dataActor.createCollectionSimpleForVpc(vxlanId, vpcId, tags);
        return vxlanId;
    }


    public int createCollectionBasedOnHostName(int id, String host, List<CollectionTags> tags)  throws Exception {
        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);
        String vpcId = System.getenv("VPC_ID");
        // 3 cases
        // 1. If 2 threads are trying to insert same host simultaneously then both will succeed with upsert true
        // 2. If we are trying to insert different host but same id (hashCode collision) then it will fail,
        //    so we loop 20 times till we succeed
        boolean flag = false;
        for (int i=0;i < 100; i++) {
            id += i;
            try {
                dataActor.createCollectionForHostAndVpc(host, id, vpcId, tags);
                flag = true;
                break;
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while inserting apiCollection, trying again " + i + " " + e.getMessage());
            }
        }
        if (flag) { // flag tells if we were successfully able to insert collection
            loggerMaker.infoAndAddToDb("Using collectionId=" + id + " for " + host);
            return id;
        } else {
            throw new Exception("Not able to insert");
        }
    }


    int numberOfSyncs = 0;

    private static int lastSyncLimitFetch = 0;
    private static final int REFRESH_INTERVAL = 60 * 10; // 10 minutes.
    private static SyncLimit syncLimit = FeatureAccess.fullAccess.fetchSyncLimit();
    private static SyncLimit mcpAssetSyncLimit = FeatureAccess.noAccess.fetchSyncLimit();
    private static SyncLimit aiAssetSyncLimit = FeatureAccess.noAccess.fetchSyncLimit();

    private void fetchSyncLimit() {
        try {
            if ((lastSyncLimitFetch + REFRESH_INTERVAL) >= Context.now()) {
                return;
            }

            int accountId = Context.getActualAccountId();
            // to:do check for on-prem air gapped deployments
            Organization organization = OrgUtils.getOrganizationCached(accountId);
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(organization, MetricTypes.ACTIVE_ENDPOINTS);
            loggerMaker.infoAndAddToDb("Fetching api sync limit for accountId: " + accountId + " with usageLimit: "
                + featureAccess.getUsageLimit() + " and usage: " + featureAccess.getUsage());
            syncLimit = featureAccess.fetchSyncLimit();
            mcpAssetSyncLimit = fetchSyncLimit(organization, MetricTypes.MCP_ASSET_COUNT);
            aiAssetSyncLimit = fetchSyncLimit(organization, MetricTypes.AI_ASSET_COUNT);
            lastSyncLimitFetch = Context.now();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetching sync limit from feature access " + e);
        }
    }

    public static boolean isBlockedHost(String hostName) {
        if (hostName == null) return false;
        hostName = hostName.toLowerCase();

        // Fast IP address check using pre-compiled pattern
        if (IP_ADDRESS_PATTERN.matcher(hostName).matches()) {
            return true;
        }

        // Fast domain check using the ignore list
        return IGNORE_HOST_NAMES.stream().anyMatch(hostName::contains);
    }

    public static boolean isBlockedContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) return false;
        contentType = contentType.toLowerCase();
        return contentType.contains("html") || contentType.contains("text/html");
    }

    public static boolean blockRedirects(HttpResponseParams responseParam) {
        // Early exit for non-302 responses
        if (responseParam.getStatusCode() != 302) return false;

        // Early exit for null request params or payload
        boolean isRequestPayloadEmpty = false;
        if (responseParam.getRequestParams().getPayload() == null
                || responseParam.getRequestParams().getPayload().isEmpty()
                || EMPTY_JSON.equals(responseParam.getRequestParams().getPayload())) {
            isRequestPayloadEmpty = true;
        }

        boolean isResponsePayloadEmpty = false;
        if (responseParam.getPayload() == null
                || responseParam.getPayload().isEmpty()
                || EMPTY_JSON.equals(responseParam.getPayload())) {
            isResponsePayloadEmpty = true;
        }

        if (!isRequestPayloadEmpty || !isResponsePayloadEmpty) {
            return false;
        }

        String locationHeader = getHeaderValue(responseParam.getHeaders(), "location");
        return locationHeader != null && locationHeader.contains("pagenotfound");
    }

    /**
     * Gets the hostname to use for collection creation. For AI agent traffic (N8N, LangChain, Copilot),
     * reconstructs the full hostname by prepending bot/workflow name to the base hostname.
     * For regular traffic, returns the hostname from headers as-is.
     *
     * @param responseParam The HTTP response parameters
     * @return The hostname to use for collection creation
     */
    private static String getHostnameForCollection(HttpResponseParams responseParam) {
        // Get base hostname from headers
        String baseHostname = getHeaderValue(responseParam.getRequestParams().getHeaders(), "host");
        if (baseHostname == null || baseHostname.isEmpty()) {
            return baseHostname;
        }

        // Check if this is AI agent traffic
        String tagsJson = responseParam.getTags();
        if (tagsJson == null || tagsJson.isEmpty()) {
            return baseHostname;
        }

        try {
            // Parse tags JSON
            @SuppressWarnings("unchecked")
            Map<String, String> tagsMap = gson.fromJson(tagsJson, Map.class);

            // Check if this is an AI agent source
            String source = tagsMap.get(Constants.AI_AGENT_TAG_SOURCE);
            if (source == null || (!source.equals(Constants.AI_AGENT_SOURCE_N8N)
                    && !source.equals(Constants.AI_AGENT_SOURCE_LANGCHAIN)
                    && !source.equals(Constants.AI_AGENT_SOURCE_COPILOT_STUDIO))) {
                // Not AI agent traffic, return base hostname
                return baseHostname;
            }

            // Extract bot name from tags
            String botName = tagsMap.get(Constants.AI_AGENT_TAG_BOT_NAME);
            if (botName == null || botName.isEmpty()) {
                // No bot name provided, log warning and use base hostname as-is
                loggerMaker.infoAndAddToDb("AI agent traffic from " + source + " missing bot-name in tags. Using base hostname: " + baseHostname, LogDb.RUNTIME);
                return baseHostname;
            }

            // Reconstruct full hostname: bot-name.base-hostname
            return botName + "." + baseHostname;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to reconstruct AI agent hostname, using base hostname");
            return baseHostname;
        }
    }

    public static FILTER_TYPE applyTrafficFilterInProcess(HttpResponseParams responseParam){

        FILTER_TYPE filterType = FILTER_TYPE.ALLOWED;
        String hostName = getHeaderValue(responseParam.getRequestParams().getHeaders(), "host");
        String contentType = getHeaderValue(responseParam.getRequestParams().getHeaders(), "content-type");

        // Block filter: Ignore ip host, localhost, kubernetes host etc.
        if (responseParam.getStatusCode() >= 400 || isBlockedHost(hostName) || isBlockedContentType(contentType)) {
            filterType = FILTER_TYPE.BLOCKED;
            return filterType;
        }

        if (blockRedirects(responseParam)) {
            return FILTER_TYPE.BLOCKED;
        }

        // Modify host header to Kubernetes Service filter
        // TODO: Make this generic or customer specific
        String serviceName = getHeaderValue(responseParam.getRequestParams().getHeaders(), "x-akto-k8s-privatecloud.agoda.com/service");
        if (serviceName != null && serviceName.length() > 0){
            responseParam.getRequestParams().getHeaders().put("host", Arrays.asList(hostName + "-" + serviceName));
            filterType = FILTER_TYPE.MODIFIED;
        }

        // Merge am-pc collections
        if(hostName.contains("am-pc")) {
            responseParam.getRequestParams().getHeaders().put("host", Arrays.asList("am-pc.ID.am.agoda.is"));
            filterType = FILTER_TYPE.MODIFIED;
        }

        return filterType;
    }

    public static FILTER_TYPE isValidResponseParam(HttpResponseParams responseParam, Map<String, FilterConfig> filterMap, Map<String, List<ExecutorNode>> executorNodesMap){
        FILTER_TYPE filterType = FILTER_TYPE.UNCHANGED;
        String message = responseParam.getOrig();
        RawApi rawApi = RawApi.buildFromMessage(message);
        rawApi.getRequest().setHeaders(responseParam.getRequestParams().getHeaders());
        int apiCollectionId = responseParam.requestParams.getApiCollectionId();
        String url = responseParam.getRequestParams().getURL();
        Method method = Method.fromString(responseParam.getRequestParams().getMethod());
        ApiInfoKey apiInfoKey = new ApiInfoKey(apiCollectionId, url, method);
        for (Entry<String, FilterConfig> apiFilterEntry : filterMap.entrySet()) {
            try {
                FilterConfig apiFilter = apiFilterEntry.getValue();
                Map<String, Object> varMap = apiFilter.resolveVarMap();
                VariableResolver.resolveWordList(varMap, new HashMap<ApiInfoKey, List<String>>() {
                    {
                        put(apiInfoKey, Arrays.asList(message));
                    }
                }, apiInfoKey);
                String filterExecutionLogId = UUID.randomUUID().toString();
                ValidationResult res = validateFilter(apiFilter.getFilter().getNode(), rawApi,
                        apiInfoKey, varMap, filterExecutionLogId);
                if (res.getIsValid()) {
                    // handle custom filters here
                    if(apiFilter.getId().equals(FilterConfig.DEFAULT_BLOCK_FILTER)){
                        return FILTER_TYPE.BLOCKED;
                    }

                    // handle execute here
                    List<ExecutorNode> nodes = executorNodesMap.getOrDefault(apiFilter.getId(), new ArrayList<>());
                    if(!nodes.isEmpty()){
                        RawApi modifiedApi = new ParseAndExecute().execute(nodes, rawApi, apiInfoKey, varMap, filterExecutionLogId);
                        responseParam = Utils.convertRawApiToHttpResponseParams(modifiedApi, responseParam);
                        filterType = FILTER_TYPE.MODIFIED;
                    }else{
                        filterType = FILTER_TYPE.ALLOWED;
                    }
                    
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, String.format("Error in httpCallFilter %s", e.toString()));
                filterType = FILTER_TYPE.ERROR;
            }
        }
        return filterType;
    }


    public static Pair<HttpResponseParams,FILTER_TYPE> applyAdvancedFilters(HttpResponseParams responseParams, Map<String, List<ExecutorNode>> executorNodesMap,  Map<String,FilterConfig> filterMap){
        if (filterMap != null && !filterMap.isEmpty()) {
            FILTER_TYPE filterType = FILTER_TYPE.UNCHANGED; 

            if (INPROCESS_ADVANCED_FILTERS_ACCOUNTS.contains(Context.getActualAccountId())) {

                filterType = applyTrafficFilterInProcess(responseParams);
            }else{

                filterType = isValidResponseParam(responseParams, filterMap,  executorNodesMap);
            }

            if(filterType.equals(FILTER_TYPE.BLOCKED)){
                return new Pair<HttpResponseParams,FilterConfig.FILTER_TYPE>(null, FILTER_TYPE.BLOCKED);
            }else{
                return new Pair<HttpResponseParams,FilterConfig.FILTER_TYPE>(responseParams, filterType);
            }
        }
        return new Pair<HttpResponseParams,FilterConfig.FILTER_TYPE>(responseParams, FILTER_TYPE.ERROR);
    }

    public void syncFunction(List<HttpResponseParams> responseParams, boolean syncImmediately, boolean fetchAllSTI, AccountSettings accountSettings)  {
        // USE ONLY filteredResponseParams and not responseParams
        List<HttpResponseParams> filteredResponseParams = responseParams;
        if (accountSettings != null && accountSettings.getDefaultPayloads() != null) {
            filteredResponseParams = filterDefaultPayloads(filteredResponseParams, accountSettings.getDefaultPayloads());
        }
        filteredResponseParams = filterHttpResponseParams(filteredResponseParams, accountSettings);
        boolean isHarOrPcap = aggregate(filteredResponseParams, aggregatorMap);
        apiCatalogSync.setMergeUrlsOnVersions(accountSettings.isAllowMergingOnVersions());

        for (int apiCollectionId: aggregatorMap.keySet()) {
            URLAggregator aggregator = aggregatorMap.get(apiCollectionId);
            apiCatalogSync.computeDelta(aggregator, false, apiCollectionId);
        }

        if (Context.getActualAccountId() == 1745303931 || Context.getActualAccountId() == 1741069294 || Context.getActualAccountId() == 1749515934 || Context.getActualAccountId() == 1753864648) {
            for (HttpResponseParams responseParam: filteredResponseParams) {
                dependencyAnalyser.analyse(responseParam.getOrig(), responseParam.requestParams.getApiCollectionId());
            }
        }

        this.sync_count += filteredResponseParams.size();
        int syncThresh = numberOfSyncs < 10 ? 10000 : sync_threshold_count;
        executeCatalogSync(syncImmediately, fetchAllSTI, isHarOrPcap, syncThresh);

    }

    private void executeCatalogSync(boolean syncImmediately, boolean fetchAllSTI, boolean isHarOrPcap, int syncThresh) {
        if (syncImmediately || this.sync_count >= syncThresh || (Context.now() - this.last_synced) > this.sync_threshold_time || isHarOrPcap) {
            long startTime = System.currentTimeMillis();
            numberOfSyncs++;
            loggerMaker.infoAndAddToDb("Starting Syncing API catalog..." + numberOfSyncs);
            List<ApiCollection> apiCollections = dataActor.fetchAllApiCollectionsMeta();
            for (ApiCollection apiCollection: apiCollections) {
                apiCollectionsMap.put(apiCollection.getId(), apiCollection);
            }
            fetchSyncLimit();
            apiCatalogSync.syncWithDB(syncImmediately, fetchAllSTI, syncLimit, mcpAssetSyncLimit, aiAssetSyncLimit);
            if (Context.getActualAccountId() == 1745303931 || Context.getActualAccountId() == 1741069294 || Context.getActualAccountId() == 1749515934 || Context.getActualAccountId() == 1753864648) {
                dependencyAnalyser.dbState = apiCatalogSync.dbState;
                dependencyAnalyser.syncWithDb();
            }
            syncTrafficMetricsWithDB();
            this.last_synced = Context.now();
            this.sync_count = 0;

            long endTime = System.currentTimeMillis();
            loggerMaker.infoAndAddToDb("Completed Syncing API catalog..." + numberOfSyncs + " " + (endTime - startTime) + " ms");
        }
    }

    private List<HttpResponseParams> filterDefaultPayloads(List<HttpResponseParams> filteredResponseParams, Map<String, DefaultPayload> defaultPayloadMap) {
        List<HttpResponseParams> ret = new ArrayList<>();
        for(HttpResponseParams httpResponseParams: filteredResponseParams) {
            if (matchesDefaultPayload(httpResponseParams, defaultPayloadMap)) {
                if (Utils.printDebugUrlLog(httpResponseParams.getRequestParams().getURL())) {
                    loggerMaker.infoAndAddToDb("Found debug url in filterDefaultPayloads " + httpResponseParams.getRequestParams().getURL());
                }
                continue;
            }


            ret.add(httpResponseParams);
        }

        return ret;
    }

    public void syncTrafficMetricsWithDB() {
        loggerMaker.infoAndAddToDb("Starting syncing traffic metrics");
        try {
            syncTrafficMetricsWithDBHelper();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while updating traffic metrics: " + e.getMessage());
        } finally {
            trafficMetricsMap = new HashMap<>();
        }
        loggerMaker.infoAndAddToDb("Finished syncing traffic metrics");
    }

    public void syncTrafficMetricsWithDBHelper() {
        List<BulkUpdates> bulkUpdates = new ArrayList<>();
        BasicDBObject metricsData = new BasicDBObject();
        int accountId = Context.getActualAccountId();
        Organization organization = OrgUtils.getOrganizationCached(accountId);
        for (TrafficMetrics trafficMetrics: trafficMetricsMap.values()) {
            TrafficMetrics.Key key = trafficMetrics.getId();
            Map<String, Long> countMap = trafficMetrics.getCountMap();

            if (countMap == null || countMap.isEmpty()) continue;

            Map<String, Object> filterMap = TrafficMetricsDao.getFiltersMap(key);

            int count = 0;
            ArrayList<String> individualUpdates = new ArrayList<>();
            for (String ts: countMap.keySet()) {
                UpdatePayload updatePayload = new UpdatePayload("countMap." + ts, countMap.get(ts), "inc");
                individualUpdates.add(updatePayload.toString());
                count += countMap.get(ts);
            }

            if (organization != null && (Main.isOnprem || RuntimeMode.isHybridDeployment())) {
                String metricKey = key.getName().name() + "|" + key.getIp() + "|" + key.getHost() + "|" + key.getVxlanID() + "|" + organization.getId() + "|" + accountId;
                count += (int) metricsData.getOrDefault(metricKey, 0);
                metricsData.put(metricKey, count);
            }

            bulkUpdates.add(new BulkUpdates(filterMap, individualUpdates));

        }

        if (bulkUpdates.size() > 0) {
            List<Object> writesForTraffic = new ArrayList<>();
            writesForTraffic.addAll(bulkUpdates);
            dataActor.bulkWriteTrafficMetrics(writesForTraffic);
        }
    }

    public static boolean useHostCondition(String hostName, HttpResponseParams.Source source) {
        List<HttpResponseParams.Source> whiteListSource = Arrays.asList(HttpResponseParams.Source.MIRRORING);
        boolean hostNameCondition;
        if (hostName == null) {
            hostNameCondition = false;
        } else {
            hostNameCondition = ! ( hostName.toLowerCase().equals(hostName.toUpperCase()) );
        }
        return whiteListSource.contains(source) &&  hostNameCondition && ApiCollection.useHost;
    }

    public static int getBucketStartEpoch() {
        return Context.now()/(3600*24);
    }

    public static int getBucketEndEpoch() {
        return Context.now()/(3600*24) + 1;
    }

    public static TrafficMetrics.Key getTrafficMetricsKey(HttpResponseParams httpResponseParam, TrafficMetrics.Name name) {
        int bucketStartEpoch = getBucketStartEpoch();
        int bucketEndEpoch = getBucketEndEpoch();

        String hostName = getHeaderValue(httpResponseParam.getRequestParams().getHeaders(), "host");

        if (hostName != null &&  hostName.toLowerCase().equals(hostName.toUpperCase()) ) {
            hostName = "ip-host";
        }

        return new TrafficMetrics.Key(
                httpResponseParam.getSourceIP(), hostName, httpResponseParam.requestParams.getApiCollectionId(),
                name, bucketStartEpoch, bucketEndEpoch
        );
    }

    public void incTrafficMetrics(TrafficMetrics.Key key, int value)  {
        if (trafficMetricsMap == null) trafficMetricsMap = new HashMap<>();
        TrafficMetrics trafficMetrics = trafficMetricsMap.get(key);
        if (trafficMetrics == null) {
            trafficMetrics = new TrafficMetrics(key, new HashMap<>());
            trafficMetricsMap.put(key, trafficMetrics);
        }

        trafficMetrics.inc(value);
    }

    public static final String CONTENT_TYPE = "CONTENT-TYPE";

    public boolean isRedundantEndpoint(String url, Pattern pattern){
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    private boolean isInvalidContentType(String contentType){
        boolean res = false;
        if(contentType == null || contentType.length() == 0) return res;

        res = contentType.contains("javascript") || contentType.contains("png");
        return res;
    }

    /**
     * Updates API collection tags if any new tags are detected.
     * 
     * @param hostNameMapKey     key for the host name map, which is used to create
     *                           collections based on host names.
     * @param httpResponseParams
     */
    public void updateApiCollectionTags(String hostNameMapKey, HttpResponseParams httpResponseParams) {
        int apiCollectionId = hostNameToIdMap.get(hostNameMapKey);
        ApiCollection apiCollection = apiCollectionsMap.get(apiCollectionId);

        if( apiCollection == null) {
            loggerMaker.debug("No tags updated. ApiCollection not found for id: " + apiCollectionId);
            return;
        }

        // Update the tags in-memory for the apiCollection
        if(Utils.printDebugUrlLog(httpResponseParams.getRequestParams().getURL()) || (Utils.printDebugHostLog(httpResponseParams) != null)) {
            loggerMaker.infoAndAddToDb("Updating tags in-memory for apiCollectionId: " + apiCollectionId + " with hostNameMapKey: " + hostNameMapKey
                    + " url: " + httpResponseParams.getRequestParams().getURL() + " and tags: " + httpResponseParams.getTags());
        }

        List<CollectionTags> tagsList = CollectionTags.convertTagsFormat(httpResponseParams.getTags());
        tagsList = CollectionTags.getUniqueTags(apiCollection, tagsList);
        apiCollection.setTagsList(tagsList);
        apiCollectionsMap.put(apiCollectionId, apiCollection);
        

        int lastSynctime = this.apiCollectionIdTagsSyncTimestampMap.getOrDefault(apiCollectionId, 0);
        if (Context.now() - lastSynctime < this.sync_threshold_time) {
            // Avoid updating tags too frequently
            return;
        }

        // Fetch from in-memory map 
        tagsList = apiCollectionsMap.get(apiCollectionId).getTagsList();
        this.apiCollectionIdTagsSyncTimestampMap.put(apiCollectionId, Context.now());

        if (CollectionUtils.isEmpty(apiCollection.getTagsList()) || apiCollection.getTagsList().stream()
            .noneMatch(t -> Constants.AKTO_MCP_SERVER_TAG.equals(t.getKeyName()))) {
            Optional<CollectionTags> mcpServerTagOpt = getMcpServerTag(httpResponseParams);
            if (tagsList == null) {
                tagsList = new ArrayList<>();
            }
            if (mcpServerTagOpt.isPresent()) {
                tagsList.add(mcpServerTagOpt.get());
            }
        }

        if (CollectionUtils.isEmpty(apiCollection.getTagsList()) || apiCollection.getTagsList().stream()
                .noneMatch(t -> Constants.AKTO_GEN_AI_TAG.equals(t.getKeyName()))) {
            Pair<Boolean, String> llmCollectionTag = GenAiCollectionUtils.checkAndTagLLMCollection(httpResponseParams);
            if (tagsList == null) {
                tagsList = new ArrayList<>();
            }
            if (llmCollectionTag.getFirst()) {
                tagsList.add(new CollectionTags(Context.now(),
                        Constants.AKTO_GEN_AI_TAG,
                        llmCollectionTag.getSecond(),
                        TagSource.KUBERNETES));
            }
        }

        if (tagsList == null || tagsList.isEmpty()) {
            return;
        }
        
        // Detects if collections were created based on hostName
        if (hostNameMapKey.contains(NON_HOSTNAME_KEY)) {
            String vpcId = System.getenv("VPC_ID");
            createCollectionSimpleForVpc(
                    httpResponseParams.requestParams.getApiCollectionId(), vpcId, tagsList);
        } else {
            try {
                createCollectionBasedOnHostName(hostNameMapKey.hashCode(), hostNameMapKey,
                        tagsList);
            } catch (Exception e) {
                loggerMaker.error(
                        "Error while updating api collection tags for host: " + hostNameMapKey + " " + e.getMessage());
                e.printStackTrace();
            }
        }
        String log = "Updated tags for apiCollectionId: " + apiCollectionId + " url: " + httpResponseParams.getRequestParams().getURL() + " with tags: " + tagsList + " hostNameMapKey: " + hostNameMapKey;
        printL(log);
        if(Utils.printDebugUrlLog(httpResponseParams.getRequestParams().getURL()) || (Utils.printDebugHostLog(httpResponseParams) != null)) {
            loggerMaker.warn(log);
        }
    }

    public int createApiCollectionId(HttpResponseParams httpResponseParam){
        int apiCollectionId;
        // Use getHostnameForCollection to get reconstructed hostname for AI agents
        String hostName = getHostnameForCollection(httpResponseParam);

        if (hostName != null && !hostNameToIdMap.containsKey(hostName) && RuntimeUtil.hasSpecialCharacters(hostName)) {
            hostName = "Special_Char_Host";
        }

        int vxlanId = httpResponseParam.requestParams.getApiCollectionId();
        String vpcId = System.getenv("VPC_ID");
        List<CollectionTags> tagList = CollectionTags.convertTagsFormat(httpResponseParam.getTags());

        if (useHostCondition(hostName, httpResponseParam.getSource())) {
            hostName = hostName.toLowerCase();
            hostName = hostName.trim();

            String key = hostName;
            boolean ismcpServer = false;

            if (hostNameToIdMap.containsKey(key)) {
                apiCollectionId = hostNameToIdMap.get(key);
                updateApiCollectionTags(key, httpResponseParam);

            } else {
                int id = hostName.hashCode();

                Optional<CollectionTags> mcpServerTagOpt = getMcpServerTag(httpResponseParam);
                if (mcpServerTagOpt.isPresent()) {
                    if (tagList == null) {
                        tagList = new ArrayList<>();
                    }
                    tagList.add(mcpServerTagOpt.get());
                    ismcpServer = true;
                }
                try {

                    apiCollectionId = createCollectionBasedOnHostName(id, hostName, tagList);
                    if(Utils.printDebugUrlLog(httpResponseParam.getRequestParams().getURL()) || (Utils.printDebugHostLog(httpResponseParam) != null)) {
                        loggerMaker.infoAndAddToDb("Created collection: " + apiCollectionId + " with hostNameMapKey: " + hostName 
                            + " url: " + httpResponseParam.getRequestParams().getURL() + " and tags: " + httpResponseParam.getTags()
                        );
                    }

                    //New MCP server detected, audit it
                    if(ismcpServer) {
                        McpAuditInfo auditInfo = null;
                        try {
                            auditInfo = new McpAuditInfo(
                                    Context.now(), "", AKTO_MCP_SERVER_TAG, 0,
                                    hostName != null ? hostName : "", "", null,
                                    apiCollectionId
                            );
                            dataActor.insertMCPAuditDataLog(auditInfo);
                        } catch (Exception e) {
                            loggerMaker.error("Error creating or inserting MCP audit info: " + e.getMessage());
                        }
                    }
                    hostNameToIdMap.put(key, apiCollectionId);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Failed to create collection for host : " + hostName);
                    createCollectionSimpleForVpc(vxlanId, vpcId, tagList);
                    hostNameToIdMap.put(NON_HOSTNAME_KEY + vxlanId, vxlanId);
                    apiCollectionId = httpResponseParam.requestParams.getApiCollectionId();
                }
            }

        } else {
            String key = NON_HOSTNAME_KEY + vxlanId;
            if (!hostNameToIdMap.containsKey(key)) {
                createCollectionSimpleForVpc(vxlanId, vpcId, tagList);
                hostNameToIdMap.put(key, vxlanId);
            }else{
                updateApiCollectionTags(key, httpResponseParam);
            }

            apiCollectionId = vxlanId;
        }
        return apiCollectionId;
    }



    private boolean isBlankResponseBodyForGET(String method, String contentType, String matchContentType,
            String responseBody) {
        boolean res = true;
        if (contentType == null || contentType.length() == 0)
            return false;
        res &= contentType.contains(matchContentType);
        res &= "GET".equals(method.toUpperCase());

        /*
         * To be sure that the content type
         * header matches the actual payload.
         *
         * We will need to add more type validation as needed.
         */
        if (matchContentType.contains("html")) {
            res &= responseBody.startsWith("<") && responseBody.endsWith(">");
        } else {
            res &= false;
        }
        return res;
    }

    public List<HttpResponseParams> filterHttpResponseParams(List<HttpResponseParams> httpResponseParamsList, AccountSettings accountSettings) {
        List<HttpResponseParams> filteredResponseParams = new ArrayList<>();
        int originalSize = httpResponseParamsList.size();

        List<String> redundantList = new ArrayList<>();
        if(accountSettings !=null && !accountSettings.getAllowRedundantEndpointsList().isEmpty()){
            redundantList = accountSettings.getAllowRedundantEndpointsList();
        }
        Pattern regexPattern = Utils.createRegexPatternFromList(redundantList);
        Map<String, List<ExecutorNode>> executorNodesMap = ParseAndExecute.createExecutorNodeMap(apiCatalogSync.advancedFilterMap);
        for (HttpResponseParams httpResponseParam: httpResponseParamsList) {

            if (httpResponseParam.getSource().equals(HttpResponseParams.Source.MIRRORING)) {
                TrafficMetrics.Key totalRequestsKey = getTrafficMetricsKey(httpResponseParam, TrafficMetrics.Name.TOTAL_REQUESTS_RUNTIME);
                incTrafficMetrics(totalRequestsKey,1);
            }

            boolean cond = HttpResponseParams.validHttpResponseCode(httpResponseParam.getStatusCode());
            if (httpResponseParam.getSource().equals(HttpResponseParams.Source.POSTMAN) && httpResponseParam.getStatusCode() <= 0) {
                cond = true;
            }

            if (!cond){
                if (Utils.printDebugUrlLog(httpResponseParam.getRequestParams().getURL())) {
                    loggerMaker.infoAndAddToDb("Found debug url in filterHttpResponseParams invalid response code "
                            + httpResponseParam.getRequestParams().getURL() + " response code " + httpResponseParam.getStatusCode());
                }
                continue;
            }

            String ignoreAktoFlag = getHeaderValue(httpResponseParam.getRequestParams().getHeaders(),Constants.AKTO_IGNORE_FLAG);
            if (ignoreAktoFlag != null){
                if (Utils.printDebugUrlLog(httpResponseParam.getRequestParams().getURL())) {
                    loggerMaker.infoAndAddToDb("Found debug url in filterHttpResponseParams ignoreAktoFlag "
                            + httpResponseParam.getRequestParams().getURL());
                }
                continue;
            }

            if (httpResponseParam.getRequestParams().getURL().toLowerCase().contains("/health")) {
                continue;
            }

            // check for garbage points here
            if(!redundantList.isEmpty()){
                if(isRedundantEndpoint(httpResponseParam.getRequestParams().getURL(),regexPattern)){
                    if (Utils.printDebugUrlLog(httpResponseParam.getRequestParams().getURL())) {
                        loggerMaker.infoAndAddToDb("Found debug url in filterHttpResponseParams isRedundantEndpoint "
                                + httpResponseParam.getRequestParams().getURL() + " pattern " + regexPattern.toString());
                    }
                    continue;
                }
                List<String> contentTypeList = (List<String>) httpResponseParam.getRequestParams().getHeaders().getOrDefault("content-type", new ArrayList<>());
                String contentType = null;
                if(!contentTypeList.isEmpty()){
                    contentType = contentTypeList.get(0);
                }
                if(isInvalidContentType(contentType)){
                    if (Utils.printDebugUrlLog(httpResponseParam.getRequestParams().getURL())) {
                        loggerMaker.infoAndAddToDb("Found debug url in filterHttpResponseParams isInvalidContentType "
                                + httpResponseParam.getRequestParams().getURL() + " contentType " + contentType);
                    }
                    continue;
                }

                try {
                    List<String> responseContentTypeList = (List<String>) httpResponseParam.getHeaders().getOrDefault("content-type", new ArrayList<>());
                    String allContentTypes = responseContentTypeList.toString();
                    String method = httpResponseParam.getRequestParams().getMethod();
                    String responseBody = httpResponseParam.getPayload();
                    boolean ignore = false;
                    for (String extension : accountSettings.getAllowRedundantEndpointsList()) {
                        if(extension.startsWith(CONTENT_TYPE)){
                            String matchContentType = extension.split(" ")[1];
                            if(isBlankResponseBodyForGET(method, allContentTypes, matchContentType, responseBody)){
                                ignore = true;
                                break;
                            }
                        }
                    }
                    if(ignore){
                        continue;
                    }

                } catch(Exception e){
                    loggerMaker.errorAndAddToDb(e, "Error while ignoring content-type redundant samples " + e.toString());
                }

            }

            if (Utils.printDebugUrlLog(httpResponseParam.getRequestParams().getURL())) {
                loggerMaker.infoAndAddToDb("Found debug url in filterHttpResponseParams starting advanced filters "
                        + httpResponseParam.getRequestParams().getURL());
            }

            Pair<HttpResponseParams,FILTER_TYPE> temp = applyAdvancedFilters(httpResponseParam, executorNodesMap, apiCatalogSync.advancedFilterMap);
            HttpResponseParams param = temp.getFirst();
            if(param == null || temp.getSecond().equals(FILTER_TYPE.UNCHANGED)){
                if(param == null && httpResponseParam != null && httpResponseParam.getRequestParams() != null){
                    loggerMaker.infoAndAddToDb("blocked api " + httpResponseParam.getRequestParams().getURL() + " " + httpResponseParam.getRequestParams().getApiCollectionId() + " " + httpResponseParam.getRequestParams().getMethod());
                    if (Utils.printDebugUrlLog(httpResponseParam.getRequestParams().getURL())) {
                        loggerMaker.infoAndAddToDb(
                                "Found debug url in filterHttpResponseParams advanced filters, skipping "
                                        + httpResponseParam.getRequestParams().getURL() + " filterType "
                                        + temp.getSecond());
                    }
                }
                continue;
            }else{
                httpResponseParam = param;
                if (Utils.printDebugUrlLog(httpResponseParam.getRequestParams().getURL())) {
                    loggerMaker.infoAndAddToDb("Found debug url in filterHttpResponseParams advanced filters, adding "
                            + httpResponseParam.getRequestParams().getURL() + " filterType " + temp.getSecond());
                }

            }

            int apiCollectionId = createApiCollectionId(httpResponseParam);

            httpResponseParam.requestParams.setApiCollectionId(apiCollectionId);

            //TODO("Parse JSON in one place for all the parser methods like Rest/GraphQL/JsonRpc")
            List<HttpResponseParams> responseParamsList = GraphQLUtils.getUtils().parseGraphqlResponseParam(httpResponseParam);
            if (responseParamsList.isEmpty()) {
                // Check for REST method payload structure (only for account 1758525547)
                if (Context.getActualAccountId() == 1758525547 || Context.getActualAccountId() == 1667235738) {
                    List<HttpResponseParams> restMethodResponseParams = RestMethodUtils.getUtils().parseRestMethodResponseParam(httpResponseParam);
                    if (!restMethodResponseParams.isEmpty()) {
                        filteredResponseParams.addAll(restMethodResponseParams);
                        loggerMaker.infoAndAddToDb("Adding " + restMethodResponseParams.size() + " new REST method endpoints in inventory");
                    } else {
                        HttpResponseParams jsonRpcResponse = JsonRpcUtils.parseJsonRpcResponse(httpResponseParam);
                        List<HttpResponseParams> mcpResponseParamsList = McpRequestResponseUtils.parseMcpResponseParams(jsonRpcResponse);
                        filteredResponseParams.addAll(mcpResponseParamsList);
                    }
                } else {
                    HttpResponseParams jsonRpcResponse = JsonRpcUtils.parseJsonRpcResponse(httpResponseParam);
                    List<HttpResponseParams> mcpResponseParamsList = McpRequestResponseUtils.parseMcpResponseParams(jsonRpcResponse);
                    filteredResponseParams.addAll(mcpResponseParamsList);
                }
            } else {
                filteredResponseParams.addAll(responseParamsList);
                loggerMaker.infoAndAddToDb("Adding " + responseParamsList.size() + "new graphql endpoints in inventory");
            }

            if (httpResponseParam.getSource().equals(HttpResponseParams.Source.MIRRORING)) {
                TrafficMetrics.Key processedRequestsKey = getTrafficMetricsKey(httpResponseParam, TrafficMetrics.Name.FILTERED_REQUESTS_RUNTIME);
                incTrafficMetrics(processedRequestsKey,1);
            }

        }
        int filteredSize = filteredResponseParams.size();
        loggerMaker.debugInfoAddToDb("Filtered " + (originalSize - filteredSize) + " responses");
        return filteredResponseParams;
    }

    private Map<Integer, URLAggregator> aggregatorMap = new HashMap<>();

    public void setAggregatorMap(Map<Integer, URLAggregator> aggregatorMap){
        this.aggregatorMap=aggregatorMap;
    }

    public Map<Integer, URLAggregator> getAggregatorMap(){
        return this.aggregatorMap;
    }

    public static boolean aggregate(List<HttpResponseParams> responses, Map<Integer, URLAggregator> aggregatorMap) {
        int count = 0;
        boolean ret = false;
        Set<String> urlSet= new HashSet<>();
        for (HttpResponseParams responseParams: responses) {
            if (responseParams.getSource() == HttpResponseParams.Source.HAR || responseParams.getSource() == HttpResponseParams.Source.PCAP) {
                ret = true;
            }

            HttpRequestParams requestParams = responseParams.requestParams;
            if (requestParams != null) {
                String path = requestParams.getMethod() + " " + requestParams.url;
                if (urlSet.size() < 50) {
                    urlSet.add(path);
                }
            }

            try {
                int collId = responseParams.getRequestParams().getApiCollectionId();
                URLAggregator aggregator = aggregatorMap.get(collId);
                if (aggregator == null) {
                    aggregator = new URLAggregator();
                    aggregatorMap.put(collId, aggregator);
                }

                aggregator.addURL(responseParams);
                count++;
            } catch (Exception  e) {
            }
        }

        loggerMaker.debugInfoAddToDb("URLs: " + urlSet.toString());
        loggerMaker.infoAndAddToDb("added " + count + " urls");
        return ret;
    }

    public int getLastSyncTime() {
        return this.last_synced;
    }

    public int getSyncCount() {
        return this.sync_count;
    }

    public Map<String, Integer> getHostNameToIdMap() {
        return hostNameToIdMap;
    }

    public void setHostNameToIdMap(Map<String, Integer> hostNameToIdMap) {
        this.hostNameToIdMap = hostNameToIdMap;
    }

    public void setTrafficMetricsMap(Map<TrafficMetrics.Key, TrafficMetrics> trafficMetricsMap) {
        this.trafficMetricsMap = trafficMetricsMap;
    }

    private Optional<CollectionTags> getMcpServerTag(HttpResponseParams responseParams) {
        if (McpRequestResponseUtils.isMcpRequest(responseParams).getFirst()) {
            return Optional.of(new CollectionTags(Context.now(), Constants.AKTO_MCP_SERVER_TAG, "MCP Server", TagSource.KUBERNETES));
        }
        return Optional.empty();
    }

    private SyncLimit fetchSyncLimit(Organization organization, MetricTypes metricType) {
        FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(organization, metricType);
        if (featureAccess.equals(FeatureAccess.noAccess)) {
            featureAccess.setUsageLimit(0);
        }
        return featureAccess.fetchSyncLimit();
    }
}

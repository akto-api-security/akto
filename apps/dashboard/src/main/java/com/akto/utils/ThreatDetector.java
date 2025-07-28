package com.akto.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ahocorasick.trie.Trie;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.bson.conversions.Bson;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.ProtoMessageUtils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventKafkaEnvelope;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.Metadata;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse.MaliciousEvent;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.client9.libinjection.SQLParse;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class ThreatDetector {

    private static final String LFI_OS_FILES_DATA = "/lfi-os-files.data";
    private static final String OS_COMMAND_INJECTION_DATA = "/os-command-injection.data";
    private static final String SSRF_DATA = "/ssrf.data";
    public static final String LFI_FILTER_ID = "LocalFileInclusionLFIRFI";
    public static final String SQL_INJECTION_FILTER_ID = "SQLInjection";
    public static final String OS_COMMAND_INJECTION_FILTER_ID = "OSCommandInjection";
    public static final String SSRF_FILTER_ID = "SSRF";
    private static Map<String, Object> varMap = new HashMap<>();
    private Trie lfiTrie;
    private Trie osCommandInjectionTrie;
    private Trie ssrfTrie;
    private static final LoggerMaker logger = new LoggerMaker(ThreatDetector.class, LogDb.THREAT_DETECTION);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private Map<String, FilterConfig> apiFilters;
    ThreatDetector threatDetector;
    private final CloseableHttpClient httpClient;
    private static final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();

    public ThreatDetector() throws Exception {
        lfiTrie = generateTrie(LFI_OS_FILES_DATA);
        osCommandInjectionTrie = generateTrie(OS_COMMAND_INJECTION_DATA);
        ssrfTrie = generateTrie(SSRF_DATA);
        List<YamlTemplate> templates = dataActor.fetchFilterYamlTemplates();
        apiFilters = FilterYamlTemplateDao.fetchFilterConfig(false, templates, false);
        try {
            threatDetector = new ThreatDetector();                
        } catch (Exception e) {
            threatDetector = null;
        }
        this.httpClient = HttpClients.custom()
        .setConnectionManager(connManager)
        .setKeepAliveStrategy((response, context) -> 30_000)
        .build();
    }

    public void triggerFunc() {
        Bson projections = Projections.include("_id");
        List<ApiCollection> apiCollectionList = ApiCollectionsDao.instance.findAll(new BasicDBObject(), projections);

        for (ApiCollection apiCollection: apiCollectionList) {
            runOnSamples(apiCollection.getId());
        }

    }

    public void runOnSamples(int apiCollectionId) {
        int batchSize = 100;
        int skip = 0;
        Bson projection = Projections.fields(
                Projections.include("_id"),
                Projections.slice("samples", 1)  // Only fetch the first element of the samples array
        );

        while (true) {

            Bson filters = Filters.eq("_id.apiCollectionId", apiCollectionId);
            List<SampleData> sampleDataList = SampleDataDao.instance.findAll(filters, skip, batchSize, null, projection);

            if (sampleDataList == null || sampleDataList.isEmpty() || sampleDataList.size() == 0) {
                break;
            }

            String sample;
            for (SampleData sd: sampleDataList) {
                if (sd.getSamples() == null || sd.getSamples().size() == 0) {
                    continue;
                }

                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, sd.getId().getUrl(), sd.getId().getMethod());
                sample = sd.getSamples().get(0);
                RawApi rawApi = RawApi.buildFromMessage(sample);
                for (FilterConfig apiFilter : apiFilters.values()) {
                    HttpResponseParams httpResponseParams;
                    try {
                        httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                    } catch (Exception e) {
                        continue;
                    }
                    boolean resp = threatDetector.applyFilter(apiFilter, httpResponseParams, rawApi, apiInfoKey);
                    if (resp) {
                        Metadata metadata = Metadata.newBuilder().setCountryCode("XY").build();
                        MaliciousEventMessage evt = buildMaliciousEventMessage(apiFilter, httpResponseParams, apiCollectionId, sd);
                        forwardMaliciousEvent(evt);
                    }
                }
            }
            skip += batchSize;
        }
    }

    public MaliciousEventMessage buildMaliciousEventMessage(FilterConfig apiFilter, HttpResponseParams httpResponseParams, int apiCollectionId, SampleData sd) {
        Metadata metadata = Metadata.newBuilder().setCountryCode("XY").build();
        MaliciousEventMessage evt = 
            MaliciousEventMessage.newBuilder().
                setActor("1.1.1.1").
                setFilterId(apiFilter.getId()).
                setDetectedAt(Context.now()).
                setEventType(EventType.EVENT_TYPE_SINGLE).
                setLatestApiIp("1.1.1.1").
                setLatestApiEndpoint(sd.getId().getUrl()).
                setLatestApiMethod(sd.getId().getMethod().name()).
                setLatestApiPayload(httpResponseParams.getOrig()).
                setLatestApiCollectionId(apiCollectionId).
                setCategory(apiFilter.getInfo().getCategory().getName()).
                setSubCategory(apiFilter.getInfo().getSubCategory()).
                setSeverity(apiFilter.getInfo().getSeverity()).
                setType("Rule-Based").
                setMetadata(metadata).
                build();
        return evt;
    }

    private void forwardMaliciousEvent(MaliciousEventMessage evt) {
        try {

            RecordMaliciousEventRequest.Builder reqBuilder =
                RecordMaliciousEventRequest.newBuilder().setMaliciousEvent(evt);

            RecordMaliciousEventRequest maliciousEventRequest = reqBuilder.build();
            String url = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_URL");
            String token = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN");
            ProtoMessageUtils.toString(maliciousEventRequest)
                .ifPresent(
                    msg -> {
                      StringEntity requestEntity =
                          new StringEntity(msg, ContentType.APPLICATION_JSON);
                      HttpPost req =
                          new HttpPost(
                              String.format("%s/api/threat_detection/record_malicious_event", url));
                      req.addHeader("Authorization", "Bearer " + token);
                      req.setEntity(requestEntity);
                      try {
                        logger.debugAndAddToDb("sending malicious event to threat backend for url " + evt.getLatestApiEndpoint() + " filterId " + evt.getFilterId() + " eventType " + evt.getEventType().toString());
                        this.httpClient.execute(req);
                      } catch (IOException e) {
                        logger.errorAndAddToDb("error sending malicious event " + e.getMessage());
                        e.printStackTrace();
                      }

                    });
          } catch (Exception e) {
            e.printStackTrace();
          }
    }

    private Trie generateTrie(String fileName) throws Exception {
        Trie.TrieBuilder builder = Trie.builder();
        try (InputStream is = ThreatDetector.class.getResourceAsStream(fileName);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                builder.addKeyword(line);
            }
        }

        return builder.build();
    }

    public boolean applyFilter(FilterConfig threatFilter, HttpResponseParams httpResponseParams, RawApi rawApi,
            ApiInfoKey apiInfoKey) {
        try {
            if (threatFilter.getId().equals(LFI_FILTER_ID)) {
                return isLFiThreat(httpResponseParams);
            }
            if (threatFilter.getId().equals(SQL_INJECTION_FILTER_ID)) {
                return isSqliThreat(httpResponseParams);
            }
            if (threatFilter.getId().equals(OS_COMMAND_INJECTION_FILTER_ID)) {
                return isOsCommandInjectionThreat(httpResponseParams); 
            }
            if (threatFilter.getId().equals(SSRF_FILTER_ID)) {
                return isSSRFThreat(httpResponseParams); 
            }
            return validateFilterForRequest(threatFilter, rawApi, apiInfoKey);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error in applyFilter " + e.getMessage());
            return false;
        }

    }

    private boolean validateFilterForRequest(
            FilterConfig apiFilter, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {
        try {
            varMap.clear();
            String filterExecutionLogId = "";
            ValidationResult res = TestPlugin.validateFilter(
                    apiFilter.getFilter().getNode(), rawApi, apiInfoKey, varMap, filterExecutionLogId);

            return res.getIsValid();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error in validateFilterForRequest " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public boolean isSqliThreat(HttpResponseParams httpResponseParams) {

        if (SQLParse.isSQLi(httpResponseParams.getRequestParams().getURL())) {
            return true;
        }

        if (SQLParse.isSQLi(httpResponseParams.getRequestParams().getHeaders().toString())) {
            return true;
        }

        return SQLParse.isSQLi(httpResponseParams.getRequestParams().getPayload());
    }

    public boolean isLFiThreat(HttpResponseParams httpResponseParams) {
        if (lfiTrie.containsMatch(httpResponseParams.getRequestParams().getURL())) {
            return true;
        }

        if (lfiTrie.containsMatch(httpResponseParams.getRequestParams().getHeaders().toString())) {
            return true;
        }

        return lfiTrie.containsMatch(httpResponseParams.getRequestParams().getPayload());
    }

    public boolean isOsCommandInjectionThreat(HttpResponseParams httpResponseParams) {
        if (osCommandInjectionTrie.containsMatch(httpResponseParams.getRequestParams().getURL())) {
            return true;
        }

        if (osCommandInjectionTrie.containsMatch(httpResponseParams.getRequestParams().getHeaders().toString())) {
            return true;
        }

        return osCommandInjectionTrie.containsMatch(httpResponseParams.getRequestParams().getPayload());
    }

    public boolean isSSRFThreat(HttpResponseParams httpResponseParams) {
        if (ssrfTrie.containsMatch(httpResponseParams.getRequestParams().getURL())) {
            return true;
        }

        if (ssrfTrie.containsMatch(httpResponseParams.getRequestParams().getHeaders().toString())) {
            return true;
        }

        return ssrfTrie.containsMatch(httpResponseParams.getRequestParams().getPayload());
    }

}

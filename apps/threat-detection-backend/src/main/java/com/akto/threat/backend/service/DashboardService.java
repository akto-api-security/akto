package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.message.malicious_event.dashboard.v1.DashboardMaliciousEventMessage;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.MaliciousEventModel;
import com.mongodb.BasicDBObject;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.conversions.Bson;

public class DashboardService {
    private final MongoClient mongoClient;

    public DashboardService(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    private static <T> Set<T> findDistinctFields(
            MongoCollection<MaliciousEventModel> coll, String fieldName, Class<T> tClass, Bson filters) {
        DistinctIterable<T> r = coll.distinct(fieldName, filters, tClass);
        Set<T> result = new HashSet<>();
        MongoCursor<T> cursor = r.cursor();
        while (cursor.hasNext()) {
            result.add(cursor.next());
        }
        return result;
    }

    public FetchAlertFiltersResponse fetchAlertFilters(
            String accountId, FetchAlertFiltersRequest request) {
        MongoCollection<MaliciousEventModel> coll = this.mongoClient
                .getDatabase(accountId)
                .getCollection("malicious_events", MaliciousEventModel.class);

        Set<String> actors = DashboardService.<String>findDistinctFields(coll, "actor", String.class, Filters.empty());
        Set<String> urls = DashboardService.<String>findDistinctFields(
                coll, "latestApiEndpoint", String.class, Filters.empty());

        return FetchAlertFiltersResponse.newBuilder().addAllActors(actors).addAllUrls(urls).build();
    }

    public ListMaliciousRequestsResponse listMaliciousRequests(
            String accountId, ListMaliciousRequestsRequest request) {
        int page = request.hasPage() && request.getPage() > 0 ? request.getPage() : 1;
        int limit = request.getLimit();
        int skip = (page - 1) * limit;

        MongoCollection<MaliciousEventModel> coll = this.mongoClient
                .getDatabase(accountId)
                .getCollection(
                        MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);

        BasicDBObject query = new BasicDBObject();
        try (MongoCursor<MaliciousEventModel> cursor = coll.find(query)
                .sort(new BasicDBObject("detectedAt", -1))
                .skip(skip)
                .limit(limit)
                .cursor()) {
            List<DashboardMaliciousEventMessage> maliciousEvents = new ArrayList<>();
            while (cursor.hasNext()) {
                MaliciousEventModel evt = cursor.next();
                maliciousEvents.add(
                        DashboardMaliciousEventMessage.newBuilder()
                                .setActor(evt.getActor())
                                .setFilterId(evt.getFilterId())
                                .setFilterId(evt.getFilterId())
                                .setId(evt.getId())
                                .setIp(evt.getLatestApiIp())
                                .setCountry(evt.getCountry())
                                .setPayload(evt.getLatestApiOrig())
                                .setEndpoint(evt.getLatestApiEndpoint())
                                .setMethod(evt.getLatestApiMethod().name())
                                .setDetectedAt(evt.getDetectedAt())
                                .build());
            }
            return ListMaliciousRequestsResponse.newBuilder()
                    .setPage(page)
                    .setTotal(maliciousEvents.size())
                    .addAllMaliciousEvents(maliciousEvents)
                    .build();
        }
    }
}

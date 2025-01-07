package com.akto.threat.backend.service;

import com.akto.dto.type.URLMethods;
import com.akto.kafka.Kafka;
import com.akto.kafka.KafkaConfig;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.threat.backend.client.IPLookupClient;
import com.akto.threat.backend.constants.KafkaTopic;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.AggregateSampleMaliciousEventModel;
import com.akto.threat.backend.db.MaliciousEventModel;
import com.akto.threat.backend.utils.KafkaUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.conversions.Bson;

public class MaliciousEventService {

  private final Kafka kafka;
  private MongoClient mongoClient;
  private IPLookupClient ipLookupClient;

  public MaliciousEventService(
      KafkaConfig kafkaConfig, MongoClient mongoClient, IPLookupClient ipLookupClient) {
    this.kafka = new Kafka(kafkaConfig);
    this.mongoClient = mongoClient;
    this.ipLookupClient = ipLookupClient;
  }

  public void recordMaliciousEvent(String accountId, RecordMaliciousEventRequest request) {
    System.out.println("Received malicious event: " + request);

    MaliciousEventMessage evt = request.getMaliciousEvent();
    String actor = evt.getActor();
    String filterId = evt.getFilterId();

    EventType eventType = evt.getEventType();

    MaliciousEventModel.EventType maliciousEventType =
        EventType.EVENT_TYPE_AGGREGATED.equals(eventType)
            ? MaliciousEventModel.EventType.AGGREGATED
            : MaliciousEventModel.EventType.SINGLE;

    MaliciousEventModel maliciousEventModel =
        MaliciousEventModel.newBuilder()
            .setDetectedAt(evt.getDetectedAt())
            .setActor(actor)
            .setFilterId(filterId)
            .setLatestApiEndpoint(evt.getLatestApiEndpoint())
            .setLatestApiMethod(URLMethods.Method.fromString(evt.getLatestApiMethod()))
            .setLatestApiOrig(evt.getLatestApiPayload())
            .setLatestApiCollectionId(evt.getLatestApiCollectionId())
            .setEventType(maliciousEventType)
            .setLatestApiIp(evt.getLatestApiIp())
            .setCountry(
                this.ipLookupClient.getCountryISOCodeGivenIp(evt.getLatestApiIp()).orElse(""))
            .setCategory(evt.getCategory())
            .setSubCategory(evt.getSubCategory())
            .build();

    if (MaliciousEventModel.EventType.AGGREGATED.equals(maliciousEventType)) {
      List<AggregateSampleMaliciousEventModel> events = new ArrayList<>();
      for (SampleMaliciousRequest sampleReq : request.getSampleRequestsList()) {
        events.add(
            AggregateSampleMaliciousEventModel.newBuilder()
                .setActor(actor)
                .setIp(sampleReq.getIp())
                .setUrl(sampleReq.getUrl())
                .setMethod(URLMethods.Method.fromString(sampleReq.getMethod()))
                .setOrig(sampleReq.getPayload())
                .setRequestTime(sampleReq.getTimestamp())
                .setApiCollectionId(sampleReq.getApiCollectionId())
                .setFilterId(filterId)
                .build());
      }

      this.kafka.send(
          KafkaUtils.generateMsg(
              events,
              MongoDBCollection.ThreatDetection.AGGREGATE_SAMPLE_MALICIOUS_REQUESTS,
              accountId),
          KafkaTopic.ThreatDetection.INTERNAL_DB_MESSAGES);
    }

    this.kafka.send(
        KafkaUtils.generateMsg(
            maliciousEventModel, MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, accountId),
        KafkaTopic.ThreatDetection.INTERNAL_DB_MESSAGES);
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
    MongoCollection<MaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection("malicious_events", MaliciousEventModel.class);

    Set<String> actors =
        MaliciousEventService.<String>findDistinctFields(
            coll, "actor", String.class, Filters.empty());
    Set<String> urls =
        MaliciousEventService.<String>findDistinctFields(
            coll, "latestApiEndpoint", String.class, Filters.empty());

    return FetchAlertFiltersResponse.newBuilder().addAllActors(actors).addAllUrls(urls).build();
  }

  public ListMaliciousRequestsResponse listMaliciousRequests(
      String accountId, ListMaliciousRequestsRequest request) {
    int limit = request.getLimit();
    int skip = request.hasSkip() ? request.getSkip() : 0;
    Map<String, Integer> sort = request.getSortMap();

    MongoCollection<MaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(
                MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);

    BasicDBObject query = new BasicDBObject();
    try (MongoCursor<MaliciousEventModel> cursor =
        coll.find(query)
            .sort(new BasicDBObject("detectedAt", sort.getOrDefault("detectedAt", -1)))
            .skip(skip)
            .limit(limit)
            .cursor()) {
      List<ListMaliciousRequestsResponse.MaliciousEvent> maliciousEvents = new ArrayList<>();
      while (cursor.hasNext()) {
        MaliciousEventModel evt = cursor.next();
        maliciousEvents.add(
            ListMaliciousRequestsResponse.MaliciousEvent.newBuilder()
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
                .setCategory(evt.getCategory())
                .setSubCategory(evt.getSubCategory())
                .build());
      }
      return ListMaliciousRequestsResponse.newBuilder()
          .setTotal(maliciousEvents.size())
          .addAllMaliciousEvents(maliciousEvents)
          .build();
    }
  }
}

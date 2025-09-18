package com.akto.threat.backend.router;

import com.akto.ProtoMessageUtils;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DailyActorsCountRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatConfiguration;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ModifyThreatActorStatusRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.SplunkIntegrationRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActivityTimelineRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorFilterRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.UpdateMaliciousEventStatusRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.UpdateMaliciousEventStatusResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.BulkUpdateMaliciousEventStatusRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.BulkUpdateMaliciousEventStatusResponse;
import com.akto.threat.backend.service.MaliciousEventService;
import com.akto.threat.backend.service.ThreatActorService;
import com.akto.threat.backend.service.ThreatApiService;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;
import java.util.List;
import java.util.Map;

public class DashboardRouter implements ARouter {

    private final MaliciousEventService dsService;
    private final ThreatActorService threatActorService;
    private final ThreatApiService threatApiService;

    public DashboardRouter(
        MaliciousEventService dsService,
        ThreatActorService threatActorService,
        ThreatApiService threatApiService
    ) {
        this.dsService = dsService;
        this.threatActorService = threatActorService;
        this.threatApiService = threatApiService;
    }

    @Override
    public Router setup(Vertx vertx) {
        Router router = Router.router(vertx);

        router
            .get("/fetch_filters")
            .blockingHandler(ctx -> {
                ProtoMessageUtils.toString(
                    dsService.fetchAlertFilters(
                        ctx.get("accountId"),
                        FetchAlertFiltersRequest.newBuilder().build()
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/list_malicious_requests")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                ListMaliciousRequestsRequest req = ProtoMessageUtils.<
                    ListMaliciousRequestsRequest
                >toProtoMessage(
                    ListMaliciousRequestsRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    dsService.listMaliciousRequests(ctx.get("accountId"), req)
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
                .post("/delete_all_malicious_events")
                .blockingHandler(ctx -> {
                    threatActorService.deleteAllMaliciousEvents(
                        ctx.get("accountId")
                    );
                    ctx.response().setStatusCode(200).end();
                });

        router
            .post("/list_threat_actors")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                ListThreatActorsRequest req = ProtoMessageUtils.<
                    ListThreatActorsRequest
                >toProtoMessage(
                    ListThreatActorsRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatActorService.listThreatActors(
                        ctx.get("accountId"),
                        req
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/list_threat_apis")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                ListThreatApiRequest req = ProtoMessageUtils.<
                    ListThreatApiRequest
                >toProtoMessage(
                    ListThreatApiRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatApiService.listThreatApis(ctx.get("accountId"), req)
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/get_actors_count_per_country")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                ThreatActorByCountryRequest req = ProtoMessageUtils.<
                    ThreatActorByCountryRequest
                >toProtoMessage(
                    ThreatActorByCountryRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatActorService.getThreatActorByCountry(
                        ctx.get("accountId"),
                        req 
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .get("/get_threat_configuration")
            .blockingHandler(ctx -> {
                ProtoMessageUtils.toString(
                    threatActorService.fetchThreatConfiguration(
                        ctx.get("accountId")
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/modify_threat_configuration")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                ThreatConfiguration req = ProtoMessageUtils.<
                    ThreatConfiguration
                >toProtoMessage(
                    ThreatConfiguration.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }
                ProtoMessageUtils.toString(
                    threatActorService.modifyThreatConfiguration(
                        ctx.get("accountId"),
                        req
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });
        router
            .get("/fetch_filters_for_threat_actors")
            .blockingHandler(ctx -> {
                ProtoMessageUtils.toString(
                    dsService.fetchThreatActorFilters(
                        ctx.get("accountId"),
                        ThreatActorFilterRequest.newBuilder().build()
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
        .post("/get_subcategory_wise_count")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                ThreatCategoryWiseCountRequest req = ProtoMessageUtils.<
                    ThreatCategoryWiseCountRequest
                >toProtoMessage(
                    ThreatCategoryWiseCountRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatApiService.getSubCategoryWiseCount(
                        ctx.get("accountId"),
                        req
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

            router
            .post("/get_severity_wise_count")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                ThreatSeverityWiseCountRequest req = ProtoMessageUtils.<
                    ThreatSeverityWiseCountRequest
                >toProtoMessage(
                    ThreatSeverityWiseCountRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatApiService.getSeverityWiseCount(
                        ctx.get("accountId"),
                        req
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/fetchAggregateMaliciousRequests")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                FetchMaliciousEventsRequest req = ProtoMessageUtils.<
                FetchMaliciousEventsRequest
                >toProtoMessage(
                    FetchMaliciousEventsRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatActorService.fetchAggregateMaliciousRequests(
                        ctx.get("accountId"),
                        req
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });
        
        router
            .post("/addSplunkIntegration")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                SplunkIntegrationRequest req = ProtoMessageUtils.<
                SplunkIntegrationRequest
                >toProtoMessage(
                    SplunkIntegrationRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatActorService.addSplunkIntegration(
                        ctx.get("accountId"),
                        req
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/modifyThreatActorStatus")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                ModifyThreatActorStatusRequest req = ProtoMessageUtils.<
                ModifyThreatActorStatusRequest
                >toProtoMessage(
                    ModifyThreatActorStatusRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatActorService.modifyThreatActorStatus(
                        ctx.get("accountId"),
                        req
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/get_daily_actor_count")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                DailyActorsCountRequest req = ProtoMessageUtils.<
                DailyActorsCountRequest
                >toProtoMessage(
                    DailyActorsCountRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatActorService.getDailyActorCounts(
                        ctx.get("accountId"),
                        req.getStartTs(),
                        req.getEndTs(),
                        req.getLatestAttackList()
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });
        
        router
            .post("/get_threat_activity_timeline")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                ThreatActivityTimelineRequest req = ProtoMessageUtils.<
                ThreatActivityTimelineRequest
                >toProtoMessage(
                    ThreatActivityTimelineRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                ProtoMessageUtils.toString(
                    threatActorService.getThreatActivityTimeline(
                        ctx.get("accountId"),
                        req.getStartTs(),
                        req.getEndTs(),
                        req.getLatestAttackList()
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/update_malicious_event_status")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                UpdateMaliciousEventStatusRequest req = ProtoMessageUtils.<
                    UpdateMaliciousEventStatusRequest
                >toProtoMessage(
                    UpdateMaliciousEventStatusRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                boolean success = dsService.updateMaliciousEventStatus(
                    ctx.get("accountId"),
                    req.getEventId(),
                    req.getStatus()
                );

                UpdateMaliciousEventStatusResponse resp = UpdateMaliciousEventStatusResponse.newBuilder()
                    .setSuccess(success)
                    .setMessage(success ? "Status updated successfully" : "Failed to update status")
                    .build();

                ProtoMessageUtils.toString(resp)
                    .ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/bulk_update_malicious_event_status")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                BulkUpdateMaliciousEventStatusRequest req = ProtoMessageUtils.<
                    BulkUpdateMaliciousEventStatusRequest
                >toProtoMessage(
                    BulkUpdateMaliciousEventStatusRequest.class,
                    reqBody.asString()
                ).orElse(null);

                if (req == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                int updatedCount = dsService.bulkUpdateMaliciousEventStatus(
                    ctx.get("accountId"),
                    req.getEventIdsList(),
                    req.getStatus()
                );

                BulkUpdateMaliciousEventStatusResponse resp = BulkUpdateMaliciousEventStatusResponse.newBuilder()
                    .setSuccess(updatedCount > 0)
                    .setMessage(updatedCount > 0 ? 
                        String.format("Successfully updated %d events", updatedCount) : 
                        "Failed to update events")
                    .setUpdatedCount(updatedCount)
                    .build();

                ProtoMessageUtils.toString(resp)
                    .ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .post("/bulk_update_filtered_events")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                Map<String, Object> request = null;
                try {
                    request = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        reqBody.asString(), Map.class);
                } catch (Exception e) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                if (request == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                Map<String, Object> filter = (Map<String, Object>) request.get("filter");
                String status = (String) request.get("status");
                
                int updatedCount = dsService.bulkUpdateFilteredEvents(
                    ctx.get("accountId"),
                    filter,
                    status
                );

                Map<String, Object> response = new java.util.HashMap<>();
                response.put("success", updatedCount > 0);
                response.put("updatedCount", updatedCount);
                response.put("message", updatedCount > 0 ? 
                    String.format("Successfully updated %d events", updatedCount) : 
                    "No events updated");

                try {
                    String jsonResponse = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(response);
                    ctx.response().setStatusCode(200).end(jsonResponse);
                } catch (Exception e) {
                    ctx.response().setStatusCode(500).end("Error generating response");
                }
            });


        router
            .post("/bulk_delete_malicious_events")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                Map<String, Object> request = null;
                try {
                    request = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        reqBody.asString(), Map.class);
                } catch (Exception e) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                if (request == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                List<String> eventIds = (List<String>) request.get("eventIds");
                if (eventIds == null || eventIds.isEmpty()) {
                    ctx.response().setStatusCode(400).end("No event IDs provided");
                    return;
                }

                int deletedCount = dsService.bulkDeleteMaliciousEvents(
                    ctx.get("accountId"),
                    eventIds
                );

                Map<String, Object> response = new java.util.HashMap<>();
                response.put("deleteSuccess", deletedCount > 0);
                response.put("deletedCount", deletedCount);
                response.put("deleteMessage", deletedCount > 0 ?
                    String.format("Successfully deleted %d events", deletedCount) :
                    "No events deleted");

                try {
                    String jsonResponse = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(response);
                    ctx.response().setStatusCode(200).end(jsonResponse);
                } catch (Exception e) {
                    ctx.response().setStatusCode(500).end("Error generating response");
                }
            });

        router
            .post("/bulk_delete_filtered_events")
            .blockingHandler(ctx -> {
                RequestBody reqBody = ctx.body();
                Map<String, Object> request = null;
                try {
                    request = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        reqBody.asString(), Map.class);
                } catch (Exception e) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                if (request == null) {
                    ctx.response().setStatusCode(400).end("Invalid request");
                    return;
                }

                Map<String, Object> filter = (Map<String, Object>) request.get("filter");

                int deletedCount = dsService.bulkDeleteFilteredEvents(
                    ctx.get("accountId"),
                    filter
                );

                Map<String, Object> response = new java.util.HashMap<>();
                response.put("deleteSuccess", deletedCount > 0);
                response.put("deletedCount", deletedCount);
                response.put("deleteMessage", deletedCount > 0 ?
                    String.format("Successfully deleted %d events", deletedCount) :
                    "No events deleted");

                try {
                    String jsonResponse = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(response);
                    ctx.response().setStatusCode(200).end(jsonResponse);
                } catch (Exception e) {
                    ctx.response().setStatusCode(500).end("Error generating response");
                }
            });

        return router;
    }
}

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
import com.akto.threat.backend.service.MaliciousEventService;
import com.akto.threat.backend.service.ThreatActorService;
import com.akto.threat.backend.service.ThreatApiService;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;

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


        return router;
    }
}

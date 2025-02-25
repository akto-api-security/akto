package com.akto.threat.backend.router;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountRequest;
import com.akto.proto.utils.ProtoMessageUtils;
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
            .get("/get_actors_count_per_country")
            .blockingHandler(ctx -> {
                ProtoMessageUtils.toString(
                    threatActorService.getThreatActorByCountry(
                        ctx.get("accountId"),
                        ThreatActorByCountryRequest.newBuilder().build()
                    )
                ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

        router
            .get("/get_subcategory_wise_count")
            .blockingHandler(ctx -> {
                ProtoMessageUtils.toString(
                    threatApiService.getSubCategoryWiseCount(
                        ctx.get("accountId"),
                        ThreatCategoryWiseCountRequest.newBuilder().build()
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


        return router;
    }
}

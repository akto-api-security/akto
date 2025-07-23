package com.akto.threat.backend.router;

import com.akto.ProtoMessageUtils;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ApiDistributionDataRequestPayload;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchApiDistributionDataRequest;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.threat.backend.service.ApiDistributionDataService;
import com.akto.threat.backend.service.MaliciousEventService;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;

public class ThreatDetectionRouter implements ARouter {

  private final MaliciousEventService maliciousEventService;
  private final ApiDistributionDataService apiDistributionDataService;

  public ThreatDetectionRouter(MaliciousEventService maliciousEventService, ApiDistributionDataService apiDistributionDataService) {
    this.maliciousEventService = maliciousEventService;
    this.apiDistributionDataService = apiDistributionDataService;
  }

  @Override
  public Router setup(Vertx vertx) {
    Router router = Router.router(vertx);

    router
        .post("/record_malicious_event")
        .blockingHandler(
            ctx -> {
              RequestBody reqBody = ctx.body();
              RecordMaliciousEventRequest req =
                  ProtoMessageUtils.<RecordMaliciousEventRequest>toProtoMessage(
                          RecordMaliciousEventRequest.class, reqBody.asString())
                      .orElse(null);

              if (req == null) {
                ctx.response().setStatusCode(400).end("Invalid request");
                return;
              }

              maliciousEventService.recordMaliciousEvent(ctx.get("accountId"), req);
              ctx.response().setStatusCode(202).end();
            });
    router
        .post("/save_api_distribution_data")
        .blockingHandler(ctx -> {
            RequestBody reqBody = ctx.body();
            ApiDistributionDataRequestPayload req = ProtoMessageUtils.<
            ApiDistributionDataRequestPayload
            >toProtoMessage(
                ApiDistributionDataRequestPayload.class,
                reqBody.asString()
            ).orElse(null);

            if (req == null) {
                ctx.response().setStatusCode(400).end("Invalid request");
                return;
            }
            ProtoMessageUtils.toString(
                apiDistributionDataService.saveApiDistributionData(
                    ctx.get("accountId"),
                    req
                )
            ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
        });
    
    router
        .post("/fetch_api_distribution_data")
        .blockingHandler(ctx -> {
            RequestBody reqBody = ctx.body();
            FetchApiDistributionDataRequest req = ProtoMessageUtils.<
            FetchApiDistributionDataRequest
            >toProtoMessage(
            FetchApiDistributionDataRequest.class,
                reqBody.asString()
            ).orElse(null);

            if (req == null) {
                ctx.response().setStatusCode(400).end("Invalid request");
                return;
            }
            ProtoMessageUtils.toString(
                apiDistributionDataService.getDistributionStats(
                    ctx.get("accountId"),
                    req
                )
            ).ifPresent(s -> ctx.response().setStatusCode(200).end(s));
        });


    return router;
  }
}

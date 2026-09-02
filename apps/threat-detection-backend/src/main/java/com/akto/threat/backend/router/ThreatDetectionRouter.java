package com.akto.threat.backend.router;

import com.akto.ProtoMessageUtils;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ApiDistributionDataRequestPayload;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchApiDistributionDataRequest;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.UpdateRemediationRequest;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.UpdateRemediationResponse;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.GetApprovalStatusRequest;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.GetApprovalStatusResponse;
import com.akto.proto.generated.threat_detection.service.agentic_session_service.v1.BulkUpdateAgenticSessionContextRequest;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
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
        .post("/update_remediation")
        .blockingHandler(
            ctx -> {
              RequestBody reqBody = ctx.body();
              UpdateRemediationRequest req =
                  ProtoMessageUtils.<UpdateRemediationRequest>toProtoMessage(
                          UpdateRemediationRequest.class, reqBody.asString())
                      .orElse(null);

              if (req == null || req.getRefId() == null || req.getRefId().isEmpty()) {
                ctx.response().setStatusCode(400).end("Invalid request");
                return;
              }

              int updatedCount = maliciousEventService.updateRemediation(
                  ctx.get("accountId"),
                  req.getRefId(),
                  req.getRemediation());

              UpdateRemediationResponse response = UpdateRemediationResponse.newBuilder()
                  .setSuccess(updatedCount > 0)
                  .setMessage(updatedCount > 0 ? "Remediation updated successfully" : "Event not found")
                  .setUpdatedCount(updatedCount)
                  .build();

              int statusCode = updatedCount > 0 ? 200 : 404;
              ProtoMessageUtils.toString(response)
                  .ifPresent(s -> ctx.response().setStatusCode(statusCode).end(s));
            });

    router
        .post("/get_approval_status")
        .blockingHandler(
            ctx -> {
              RequestBody reqBody = ctx.body();
              GetApprovalStatusRequest req =
                  ProtoMessageUtils.<GetApprovalStatusRequest>toProtoMessage(
                          GetApprovalStatusRequest.class, reqBody.asString())
                      .orElse(null);

              if (req == null || req.getRefId() == null || req.getRefId().isEmpty()) {
                ctx.response().setStatusCode(400).end("Invalid request");
                return;
              }

              MaliciousEventDto event =
                  maliciousEventService.getApprovalStatus(ctx.get("accountId"), req.getRefId());

              GetApprovalStatusResponse.Builder response = GetApprovalStatusResponse.newBuilder();
              if (event == null) {
                response.setFound(false);
              } else {
                response.setFound(true);
                response.setStatus(event.getStatus() == null ? "" : event.getStatus().name());
                response.setHumanResponse(
                    event.getHumanResponse() == null ? "" : event.getHumanResponse());
              }

              ProtoMessageUtils.toString(response.build())
                  .ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

    router
        .post("/bulk_update_agentic_session_context")
        .blockingHandler(
            ctx -> {
              RequestBody reqBody = ctx.body();
              BulkUpdateAgenticSessionContextRequest req =
                  ProtoMessageUtils.<BulkUpdateAgenticSessionContextRequest>toProtoMessage(
                          BulkUpdateAgenticSessionContextRequest.class, reqBody.asString())
                      .orElse(null);

              if (req == null) {
                ctx.response().setStatusCode(400).end("Invalid request");
                return;
              }

              maliciousEventService.bulkUpdateAgenticSessionContext(ctx.get("accountId"), req);
              ctx.response().setStatusCode(200).end();
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

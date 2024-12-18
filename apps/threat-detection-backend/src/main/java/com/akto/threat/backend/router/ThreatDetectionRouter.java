package com.akto.threat.backend.router;

import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.threat.backend.service.MaliciousEventService;
import com.akto.threat.backend.utils.ProtoMessageUtils;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;

public class ThreatDetectionRouter implements ARouter {

  private final MaliciousEventService maliciousEventService;

  public ThreatDetectionRouter(MaliciousEventService maliciousEventService) {
    this.maliciousEventService = maliciousEventService;
  }

  @Override
  public Router setup(Vertx vertx) {
    Router router = Router.router(vertx);

    router
        .post("/record_malicious_event")
        .handler(
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

    return router;
  }
}

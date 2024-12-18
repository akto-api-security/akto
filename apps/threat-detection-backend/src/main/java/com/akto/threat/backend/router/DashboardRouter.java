package com.akto.threat.backend.router;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.threat.backend.service.DashboardService;
import com.akto.threat.backend.utils.ProtoMessageUtils;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;

public class DashboardRouter implements ARouter {

  private final DashboardService dsService;

  public DashboardRouter(DashboardService dsService) {
    this.dsService = dsService;
  }

  @Override
  public Router setup(Vertx vertx) {
    Router router = Router.router(vertx);

    router
        .get("/fetch_filters")
        .handler(
            ctx -> {
              ProtoMessageUtils.toString(
                      dsService.fetchAlertFilters(
                          ctx.get("accountId"), FetchAlertFiltersRequest.newBuilder().build()))
                  .ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

    router
        .post("/list_malicious_requests")
        .handler(
            ctx -> {
              RequestBody reqBody = ctx.body();
              ListMaliciousRequestsRequest req =
                  ProtoMessageUtils.<ListMaliciousRequestsRequest>toProtoMessage(
                          ListMaliciousRequestsRequest.class, reqBody.asString())
                      .orElse(null);

              if (req == null) {
                ctx.response().setStatusCode(400).end("Invalid request");
                return;
              }

              ProtoMessageUtils.toString(dsService.listMaliciousRequests(ctx.get("accountId"), req))
                  .ifPresent(s -> ctx.response().setStatusCode(200).end(s));
            });

    return router;
  }
}

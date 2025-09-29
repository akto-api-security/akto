package com.akto.threat.backend;

import com.akto.threat.backend.interceptors.AuthenticationInterceptor;
import com.akto.threat.backend.router.DashboardRouter;
import com.akto.threat.backend.router.ThreatDetectionRouter;
import com.akto.threat.backend.service.ApiDistributionDataService;
import com.akto.threat.backend.service.MaliciousEventService;
import com.akto.threat.backend.service.ThreatActorService;
import com.akto.threat.backend.service.ThreatApiService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class BackendVerticle extends AbstractVerticle {

  private final MaliciousEventService maliciousEventService;
  private final ThreatActorService threatActorService;
  private final ThreatApiService threatApiService;
  private final ApiDistributionDataService apiDistributionDataService;

  public BackendVerticle(
      MaliciousEventService maliciousEventService,
      ThreatActorService threatActorService,
      ThreatApiService threatApiService,
      ApiDistributionDataService apiDistributionDataService) {
    this.maliciousEventService = maliciousEventService;
    this.threatActorService = threatActorService;
    this.threatApiService = threatApiService;
    this.apiDistributionDataService = apiDistributionDataService;
  }

  @Override
  public void start() {
    Vertx vertx = Vertx.vertx();

    // Create the router
    Router router = Router.router(vertx);

    Router api = Router.router(vertx);

    api.route().handler(BodyHandler.create());
    api.route().handler(new AuthenticationInterceptor());

    Router dashboardRouter =
        new DashboardRouter(maliciousEventService, threatActorService, threatApiService)
            .setup(vertx);
    Router threatDetectionRouter = new ThreatDetectionRouter(maliciousEventService, apiDistributionDataService).setup(vertx);

    api.route("/dashboard/*").subRouter(dashboardRouter);
    api.route("/threat_detection/*").subRouter(threatDetectionRouter);

    router.route("/api/*").subRouter(api);

    // Start the HTTP server

    router.route("/health").handler(ctx -> ctx.response().setStatusCode(200).end("OK"));

    // 404 handler
    router
        .route()
        .handler(
            rc -> {
              rc.response().setStatusCode(404).end("404 - Not Found: " + rc.request().uri());
            });

    int port =
        Integer.parseInt(
            System.getenv().getOrDefault("THREAT_DETECTION_BACKEND_SERVER_PORT", "9090"));

    vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(port)
        .onSuccess(
            server -> {
              System.out.println("HTTP server started on port " + port);
            })
        .onFailure(
            err -> {
              System.err.println("Failed to start HTTP server: " + err.getMessage());
              System.exit(1);
            });
  }
}

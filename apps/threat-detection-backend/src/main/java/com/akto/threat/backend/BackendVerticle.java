package com.akto.threat.backend;

import com.akto.kafka.KafkaConfig;
import com.akto.threat.backend.interceptors.AuthenticationInterceptor;
import com.akto.threat.backend.router.DashboardRouter;
import com.akto.threat.backend.router.ThreatDetectionRouter;
import com.akto.threat.backend.service.DashboardService;
import com.akto.threat.backend.service.MaliciousEventService;
import com.mongodb.client.MongoClient;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class BackendVerticle extends AbstractVerticle {

  private final MongoClient mongoClient;
  private final KafkaConfig kafkaConfig;

  public BackendVerticle(MongoClient mongoClient, KafkaConfig kafkaConfig) {
    this.mongoClient = mongoClient;
    this.kafkaConfig = kafkaConfig;
  }

  @Override
  public void start() {
    Vertx vertx = Vertx.vertx();

    // Create the router
    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());

    router.route().handler(new AuthenticationInterceptor());

    Router dashboardRouter = new DashboardRouter(new DashboardService(mongoClient)).setup(vertx);
    Router threatDetectionRouter =
        new ThreatDetectionRouter(new MaliciousEventService(kafkaConfig)).setup(vertx);

    router.route("/dashboard/*").subRouter(dashboardRouter);
    router.route("/threat_detection/*").subRouter(threatDetectionRouter);

    // Start the HTTP server

    vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(9090)
        .onSuccess(
            server -> {
              System.out.println("HTTP server started on port 9090");
            })
        .onFailure(
            err -> {
              System.err.println("Failed to start HTTP server: " + err.getMessage());
            });
  }
}

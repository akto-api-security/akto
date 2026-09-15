package com.akto.action;


import com.akto.dao.KafkaHealthMetricsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.KafkaHealthMetric;
import com.akto.dto.billing.Organization;
import com.akto.listener.InfraMetricsListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.telemetry.TelemetryJob;
import com.akto.testing.ServiceConnectivity;
import com.akto.util.DashboardMode;
import com.akto.util.UsageUtils;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.Document;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class InfraMetricsAction implements Action,ServletResponseAware, ServletRequestAware  {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsAction.class, LogDb.DASHBOARD);;

    // Whether the /metrics endpoint is served at all. Off by default so metrics are
    // never exposed unless explicitly turned on.
    private static final String METRICS_EXPOSED_ENV = "METRICS_ENABLED";
    // Whether Bearer-token auth is enforced on /metrics. On by default; set to "false"
    // to serve metrics without auth (e.g. a private network where the scraper cannot
    // send a token).
    private static final String METRICS_AUTH_ENABLED_ENV = "METRICS_AUTH_ENABLED";
    private static final String METRICS_AUTH_TOKEN_ENV = "METRICS_AUTH_TOKEN";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Prometheus scrape endpoint (/metrics). Emits pure Prometheus text exposition.
     *
     * Controls:
     *   METRICS_ENABLED     - "true" serves the endpoint. Backward compatible: a configured
     *                         METRICS_AUTH_TOKEN also implies exposed, so existing setups that
     *                         only set the token keep working unchanged.
     *   METRICS_AUTH_ENABLED - "true"/unset enforces Bearer auth (default); "false" disables it.
     *   METRICS_AUTH_TOKEN  - required when auth is enabled; the expected Bearer credential.
     *
     * When auth is enabled but no token is configured the endpoint fails closed (404)
     * rather than exposing metrics unauthenticated.
     */
    @Override
    public String execute() throws Exception {
        String configuredToken = System.getenv(METRICS_AUTH_TOKEN_ENV);
        boolean hasToken = configuredToken != null && !configuredToken.trim().isEmpty();

        // 1) endpoint must be exposed. Backward compatible: an explicit METRICS_ENABLED=true
        //    OR a configured token (the previous gating) exposes it. Nothing that worked
        //    before starts returning 404.
        boolean exposed = isTrue(System.getenv(METRICS_EXPOSED_ENV)) || hasToken;
        if (!exposed) {
            servletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        // 2) auth is enforced unless explicitly disabled
        boolean authEnabled = !isFalse(System.getenv(METRICS_AUTH_ENABLED_ENV));
        if (authEnabled) {
            if (!hasToken) {
                // auth on but nothing to check against -> fail closed, never serve open
                loggerMaker.errorAndAddToDb(METRICS_AUTH_ENABLED_ENV + " is on but " + METRICS_AUTH_TOKEN_ENV
                        + " is unset; refusing to expose /metrics", LogDb.DASHBOARD);
                servletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }

            String presentedToken = null;
            String authHeader = servletRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                presentedToken = authHeader.substring(BEARER_PREFIX.length()).trim();
            }

            if (presentedToken == null || !constantTimeEquals(configuredToken, presentedToken)) {
                servletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return null;
            }
        }

        servletResponse.setContentType("text/plain; version=0.0.4; charset=utf-8");
        PrintWriter out = servletResponse.getWriter();
        InfraMetricsListener.registry.scrape(out);
        out.flush();
        out.close();
        return null;
    }

    private static boolean isTrue(String v) {
        return v != null && "true".equalsIgnoreCase(v.trim());
    }

    private static boolean isFalse(String v) {
        return v != null && "false".equalsIgnoreCase(v.trim());
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public String detailedMetrics() throws Exception {
        if (!DashboardMode.isOnPremDeployment()) return Action.ERROR.toUpperCase();
        PrintWriter out = servletResponse.getWriter();
        Future<Boolean> telemetryConnectivityFuture = executorService.submit(() -> ServiceConnectivity.check(TelemetryJob.getTelemetryUrl(), ""));
        Future<Boolean> usageConnectivityFuture = executorService.submit(() -> ServiceConnectivity.check(UsageUtils.getUsageServiceUrl(), ""));

        InfraMetricsListener.registry.scrape(out);
        Organization organization = OrganizationsDao.instance.findOne(new BasicDBObject());
        String orgId = "null";
        if(organization != null){
            orgId = redact(organization.getId());
        }
        out.append("orgId: ").append(orgId).append("\n");
        boolean telemetryConnectivity = telemetryConnectivityFuture.get();
        boolean usageConnectivity = usageConnectivityFuture.get();
        out.append("Telemetry service: ").append(telemetryConnectivity ? "Reachable": "Unreachable").append("\n");
        out.append("Usage service: ").append(usageConnectivity ? "Reachable": "Unreachable").append("\n");
        out.flush();
        out.close();
        return null;
    }

    public String redact(String id) {
        String lastFour = id.substring(id.length() - 4);
        return "****-****-****-****-" + lastFour;
    }

    private final BasicDBObject akto_health = new BasicDBObject();
    public String health() {
        try {
            Object mongoHealth = mongoHealth();
            akto_health.put("mongo", mongoHealth);
        } catch (Exception e) {
            akto_health.put("mongo", "Error getting health metrics from mongo. Check logs.");
            loggerMaker.errorAndAddToDb(e,"ERROR health metrics from mongo " + e, LogDb.DASHBOARD);
        }

        try {
            List<KafkaHealthMetric> kafkaHealthMetrics = runtimeHealth();
            akto_health.put("runtime", kafkaHealthMetrics);
        } catch (Exception e) {
            akto_health.put("runtime", "Error getting health metrics from runtime. Check logs.");
            loggerMaker.errorAndAddToDb(e,"ERROR health metrics from runtime " + e, LogDb.DASHBOARD);
        }
        return SUCCESS.toUpperCase();
    }

    public Object mongoHealth() {
        Document stats = UsersDao.instance.getStats();
        Document metrics = (Document) stats.get("metrics");
        return metrics.get("document");
    }

    public List<KafkaHealthMetric> runtimeHealth() {
        return KafkaHealthMetricsDao.instance.findAll(new BasicDBObject());
    }

    protected HttpServletResponse servletResponse;
    @Override
    public void setServletResponse(HttpServletResponse httpServletResponse) {
        this.servletResponse= httpServletResponse;
    }

    protected HttpServletRequest servletRequest;
    @Override
    public void setServletRequest(HttpServletRequest httpServletRequest) {
        this.servletRequest = httpServletRequest;
    }

    public BasicDBObject getAkto_health() {
        return akto_health;
    }
}

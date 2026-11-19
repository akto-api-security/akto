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

    private static final String BEARER_PREFIX = "Bearer ";

    // Metrics config is resolved once at class load: env vars are fixed for the process
    // lifetime, so there is no point re-reading them on every /metrics request. Names match
    // the platform-wide convention used by the other services (writes-producer/consumer etc).
    //   PROMETHEUS_METRICS_ENABLED - "true" exposes /metrics (a configured token also implies
    //                                exposed, for backward compatibility).
    //   METRICS_AUTH_ENABLED       - "true"/unset enforces Bearer auth (default); "false" disables it.
    //   METRICS_AUTH_TOKEN         - the expected Bearer credential; required when auth is enabled.
    private static final String METRICS_AUTH_TOKEN = System.getenv("METRICS_AUTH_TOKEN");
    private static final boolean HAS_TOKEN = METRICS_AUTH_TOKEN != null && !METRICS_AUTH_TOKEN.trim().isEmpty();
    private static final boolean METRICS_EXPOSED = isTrue(System.getenv("PROMETHEUS_METRICS_ENABLED")) || HAS_TOKEN;
    private static final boolean METRICS_AUTH_ENABLED = !isFalse(System.getenv("METRICS_AUTH_ENABLED"));

    /**
     * Prometheus scrape endpoint (/metrics). Emits pure Prometheus text exposition.
     *
     * Controls (names match the platform-wide convention):
     *   PROMETHEUS_METRICS_ENABLED - "true" serves the endpoint. Backward compatible: a configured
     *                                METRICS_AUTH_TOKEN also implies exposed, so existing setups
     *                                that only set the token keep working unchanged.
     *   METRICS_AUTH_ENABLED       - "true"/unset enforces Bearer auth (default); "false" disables it.
     *   METRICS_AUTH_TOKEN         - required when auth is enabled; the expected Bearer credential.
     *
     * When auth is enabled but no token is configured the endpoint fails closed (404)
     * rather than exposing metrics unauthenticated.
     */
    @Override
    public String execute() throws Exception {
        // 1) endpoint must be exposed (explicit flag, or a configured token for back-compat)
        if (!METRICS_EXPOSED) {
            servletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        // 2) auth is enforced unless explicitly disabled
        if (METRICS_AUTH_ENABLED) {
            if (!HAS_TOKEN) {
                // auth on but nothing to check against -> fail closed, never serve open
                loggerMaker.errorAndAddToDb("METRICS_AUTH_ENABLED is on but METRICS_AUTH_TOKEN"
                        + " is unset; refusing to expose /metrics", LogDb.DASHBOARD);
                servletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }

            String presentedToken = null;
            String authHeader = servletRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                presentedToken = authHeader.substring(BEARER_PREFIX.length()).trim();
            }

            if (presentedToken == null || !constantTimeEquals(METRICS_AUTH_TOKEN, presentedToken)) {
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

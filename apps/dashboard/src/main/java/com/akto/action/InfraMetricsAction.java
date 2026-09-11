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

    private static final String METRICS_AUTH_TOKEN_ENV = "METRICS_AUTH_TOKEN";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Prometheus scrape endpoint (/metrics). Emits pure Prometheus text exposition.
     *
     * Gated purely by the METRICS_AUTH_TOKEN env var: if it is unset the endpoint is
     * disabled (404) so metrics are never publicly exposed by default; if set, callers
     * must present a matching "Authorization: Bearer <token>" header.
     */
    @Override
    public String execute() throws Exception {
        String configuredToken = System.getenv(METRICS_AUTH_TOKEN_ENV);
        if (configuredToken == null || configuredToken.trim().isEmpty()) {
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

        servletResponse.setContentType("text/plain; version=0.0.4; charset=utf-8");
        PrintWriter out = servletResponse.getWriter();
        InfraMetricsListener.registry.scrape(out);
        out.flush();
        out.close();
        return null;
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

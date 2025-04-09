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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class InfraMetricsAction implements Action,ServletResponseAware, ServletRequestAware  {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsAction.class, LogDb.DASHBOARD);;

    @Override
    public String execute() throws Exception {
        InfraMetricsListener.registry.scrape(servletResponse.getWriter());
        return null;
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

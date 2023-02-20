package com.akto.action;


import com.akto.dao.KafkaHealthMetricsDao;
import com.akto.dao.UsersDao;
import com.akto.dto.KafkaHealthMetric;
import com.akto.listener.InfraMetricsListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.Document;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InfraMetricsAction implements Action,ServletResponseAware, ServletRequestAware  {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsAction.class, LogDb.DASHBOARD);
    @Override
    public String execute() throws Exception {
        InfraMetricsListener.registry.scrape(servletResponse.getWriter());
        return null;
    }

    private final BasicDBObject akto_health = new BasicDBObject();
    public String health() {
        try {
            Object mongoHealth = mongoHealth();
            akto_health.put("mongo", mongoHealth);
        } catch (Exception e) {
            akto_health.put("mongo", "Error getting health metrics from mongo. Check logs.");
            loggerMaker.errorAndAddToDb("ERROR health metrics from mongo " + e);
        }

        try {
            List<KafkaHealthMetric> kafkaHealthMetrics = runtimeHealth();
            akto_health.put("runtime", kafkaHealthMetrics);
        } catch (Exception e) {
            akto_health.put("runtime", "Error getting health metrics from runtime. Check logs.");
            loggerMaker.errorAndAddToDb("ERROR health metrics from runtime " + e);
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

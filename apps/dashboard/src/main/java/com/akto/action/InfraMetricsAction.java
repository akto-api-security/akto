package com.akto.action;


import com.akto.dao.KafkaHealthMetricsDao;
import com.akto.dao.UsersDao;
import com.akto.dto.KafkaHealthMetric;
import com.akto.listener.InfraMetricsListener;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoDatabase;
import com.opensymphony.xwork2.Action;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class InfraMetricsAction implements Action,ServletResponseAware, ServletRequestAware  {

    @Override
    public String execute() throws Exception {
        InfraMetricsListener.registry.scrape(servletResponse.getWriter());
        return null;
    }

    private final BasicDBObject akto_health = new BasicDBObject();
    public String health() {
        akto_health.put("mongo", mongoHealth());
        akto_health.put("runtime", runtimeHealth());
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

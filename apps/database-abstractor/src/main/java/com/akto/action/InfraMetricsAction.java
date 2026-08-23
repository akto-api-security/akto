package com.akto.action;

import com.akto.listener.InfraMetricsListener;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class InfraMetricsAction extends ActionSupport {

    String ok;

    public String health() {
        ok = "ok";
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Prometheus scrape endpoint. Access is enforced by MetricsAuthFilter (mapped to /metrics and
     * /metrics.action). Writes the exposition format straight to the response and returns null so
     * Struts renders no result. getWriter() returns a plain java.io.Writer, so this stays
     * servlet-API-version agnostic.
     */
    @Override
    public String execute() throws Exception {
        ServletActionContext.getResponse().setContentType("text/plain; version=0.0.4; charset=utf-8");
        InfraMetricsListener.registry.scrape(ServletActionContext.getResponse().getWriter());
        return null;
    }

    public String getOk() {
        return ok;
    }

    public void setOk(String ok) {
        this.ok = ok;
    }
}

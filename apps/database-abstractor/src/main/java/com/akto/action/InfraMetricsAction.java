package com.akto.action;

import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

public class InfraMetricsAction extends ActionSupport {

    String ok;

    public String health() {
        ok = "ok";
        return Action.SUCCESS.toUpperCase();
    }

    public String getOk() {
        return ok;
    }

    public void setOk(String ok) {
        this.ok = ok;
    }
}
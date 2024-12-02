package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.interceptor.SessionAware;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public abstract class UserAction extends ActionSupport implements SessionAware {
    private User user;
    private Map<String, Object> session;
    public Map<String, Object> getSession() {
        return session;
    }
    public void setSession(Map<String, Object> session) {
        this.session = session;
        this.user = (User)(session.get("user"));
        if (this.user != null) {
            Context.userId.set(this.user.getId());
        }
    }

    public User getSUser() {
        return user;
    }

    public int today() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDateTime now = LocalDateTime.now();
        return Integer.parseInt(dtf.format(now));
    }
}

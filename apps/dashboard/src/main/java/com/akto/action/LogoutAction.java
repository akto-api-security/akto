package com.akto.action;

import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;

import static com.akto.filter.UserDetailsFilter.LOGIN_URI;

public class LogoutAction extends UserAction implements ServletRequestAware,ServletResponseAware {
    @Override
    public String execute() throws Exception {
        User user = getSUser();
        UsersDao.instance.updateOne(
                Filters.eq("_id", user.getId()),
                Updates.set("refreshTokens", new ArrayList<>())
        );
        Cookie cookie = AccessTokenAction.generateDeleteCookie();
        servletResponse.addCookie(cookie);
        HttpSession session = servletRequest.getSession();
        if (session != null) {
            session.setAttribute("logout", Context.now());
        }
        try {
            servletResponse.sendRedirect(LOGIN_URI);
            return null;
        } catch (IOException e) {
            ;
        }
        return Action.SUCCESS.toUpperCase();
    }

    protected HttpServletResponse servletResponse;
    protected HttpServletRequest servletRequest;
    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.servletResponse= response;
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.servletRequest = request;
    }
}

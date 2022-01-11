package com.akto.action;

import com.akto.dao.UsersDao;
import com.akto.dto.User;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static com.akto.filter.UserDetailsFilter.LOGIN_URI;

public class LogoutAction extends UserAction implements ServletResponseAware {
    @Override
    public String execute() throws Exception {
        User user = getSUser();
        System.out.println(user.getId());
        UsersDao.instance.updateOne(
                Filters.eq("_id", user.getId()),
                Updates.set("refreshTokens", new ArrayList<>())
        );
        Cookie cookie = AccessTokenAction.generateDeleteCookie();
        servletResponse.addCookie(cookie);
        try {
            servletResponse.sendRedirect(LOGIN_URI);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Action.SUCCESS.toUpperCase();
    }

    protected HttpServletResponse servletResponse;
    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.servletResponse= response;
    }
}

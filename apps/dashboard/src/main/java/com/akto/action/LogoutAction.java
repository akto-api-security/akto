package com.akto.action;

import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.SignupInfo;
import com.akto.dto.User;
import com.akto.utils.Auth0;
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
import java.util.Map;

import static com.akto.filter.UserDetailsFilter.LOGIN_URI;

public class LogoutAction extends UserAction implements ServletRequestAware,ServletResponseAware {

    private String logoutUrl;
    @Override
    public String execute() throws Exception {
        User user = getSUser();
        UsersDao.instance.updateOne(
                Filters.eq("_id", user.getId()),
                Updates.set("refreshTokens", new ArrayList<>())
        );
        Map<String, SignupInfo> signupInfoMap = user.getSignupInfoMap();
        Cookie cookie = AccessTokenAction.generateDeleteCookie();
        servletResponse.addCookie(cookie);
        HttpSession session = servletRequest.getSession();
        if (session != null) {
            session.setAttribute("logout", Context.now());
        }
        if(signupInfoMap.containsKey(Config.ConfigType.AUTH0.name())){
            return auth0Logout();
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


    public String auth0Logout() {
        if (servletRequest.getSession() != null) {
            servletRequest.getSession().invalidate();
        }
        String returnUrl = String.format("%s://%s", servletRequest.getScheme(), servletRequest.getServerName());

        if ((servletRequest.getScheme().equals("http") && servletRequest.getServerPort() != 80)
                || (servletRequest.getScheme().equals("https") && servletRequest.getServerPort() != 443)) {
            returnUrl += ":" + servletRequest.getServerPort();
        }
        returnUrl += "/";

        logoutUrl = String.format(
                "https://%s/v2/logout?client_id=%s&returnTo=%s",
                Auth0.getDomain(),
                Auth0.getClientId(),
                returnUrl);

        return null;
    }

    public String getLogoutUrl() {
        return logoutUrl;
    }

    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }
}

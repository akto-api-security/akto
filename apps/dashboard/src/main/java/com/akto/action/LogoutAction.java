package com.akto.action;

import static com.akto.filter.UserDetailsFilter.LOGIN_URI;

import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;
import com.akto.utils.Auth0;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

public class LogoutAction extends UserAction implements ServletRequestAware,ServletResponseAware {

    private static final LoggerMaker logger = new LoggerMaker(LogoutAction.class, LogDb.DASHBOARD);

    private String logoutUrl;
    private String redirectUrl;
    @Override
    public String execute() throws Exception {
        User user = getSUser();
        logger.debug(String.valueOf(user.getId()));
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
        if(DashboardMode.isSaasDeployment()){
            return auth0Logout();
        }
        try {
            servletResponse.sendRedirect(LOGIN_URI);
            return null;
        } catch (IOException e) {
            e.printStackTrace();
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

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String auth0Logout() throws UnsupportedEncodingException {
        if (servletRequest.getSession() != null) {
            servletRequest.getSession().invalidate();
        }
        String returnUrl = "";
        returnUrl = String.format("%s://%s", servletRequest.getScheme(), servletRequest.getServerName());

        if ((servletRequest.getScheme().equals("http") && servletRequest.getServerPort() != 80)
                || (servletRequest.getScheme().equals("https") && servletRequest.getServerPort() != 443)) {
            returnUrl += ":" + servletRequest.getServerPort();
        }
        if(redirectUrl != null) {
            returnUrl += redirectUrl;
        } else {
            returnUrl += "/";
        }

        String encoded = URLEncoder.encode(returnUrl, "UTF-8")
                .replaceAll("\\+", "%20")
                .replaceAll("\\%21", "!")
                .replaceAll("\\%27", "'")
                .replaceAll("\\%28", "(")
                .replaceAll("\\%29", ")")
                .replaceAll("\\%7E", "~");

        logoutUrl = String.format(
                "https://%s/v2/logout?client_id=%s&returnTo=%s",
                Auth0.getDomain(),
                Auth0.getClientId(),
                encoded);

        return SUCCESS.toUpperCase();
    }

    public String getLogoutUrl() {
        return logoutUrl;
    }

    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }
}

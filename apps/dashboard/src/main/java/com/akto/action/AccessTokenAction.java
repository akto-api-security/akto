package com.akto.action;

import static com.akto.action.LoginAction.REFRESH_TOKEN_COOKIE_NAME;

import com.akto.dao.UsersDao;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.Token;
import com.opensymphony.xwork2.Action;
import java.util.List;
import java.util.Objects;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

public class AccessTokenAction implements Action, ServletResponseAware, ServletRequestAware {

    private static final LoggerMaker logger = new LoggerMaker(AccessTokenAction.class, LogDb.DASHBOARD);
    public static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
    public static final String CONTEXT_SOURCE_HEADER = "x-context-source";
    public static final String AKTO_SESSION_TOKEN = "x-akto-session-token";

    @Override
    public String execute() {
        Token token = generateAccessTokenFromServletRequest(servletRequest);
        if (token == null) {
            Cookie cookie = generateDeleteCookie();
            servletResponse.addCookie(cookie);
            return Action.ERROR.toUpperCase();
        }
        String accessToken = token.getAccessToken();

        servletResponse.setHeader(ACCESS_TOKEN_HEADER_NAME, accessToken);

        return Action.SUCCESS.toUpperCase();
    }

    public static Cookie generateDeleteCookie() {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, null);
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setPath("/dashboard");
        String https = System.getenv("AKTO_HTTPS_FLAG");
        if (Objects.equals(https, "true")) {
            cookie.setSecure(true);
        }
        return cookie;
    }

    public static Token generateAccessTokenFromServletRequest(HttpServletRequest httpServletRequest) {

        Cookie[] cookies = httpServletRequest.getCookies();

        if (cookies == null) {
            return null;
        }

        String refreshToken = null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("refreshToken")) {
                refreshToken = cookie.getValue();
                break;
            }
        }

        if (refreshToken == null) {
            logger.debug("Could not find refresh token in generateAccessTokenFromServletRequest.");
            return null;
        }

        Token token ;
        try {
            token = new Token(refreshToken);
        } catch (Exception e) {
            logger.error("error in parsing refresh token in generateAccessTokenFromServletRequest");
            return null;
        }

        if (token.getSignedUp().equalsIgnoreCase("false")) {
            return token;
        }

        String username = token.getUsername();
        User user = UsersDao.instance.findOne("login", username);
        if (user == null) {
            logger.debug("Returning as user not found.");
            return null;
        }

        List<String> refreshTokens = user.getRefreshTokens();
        if (refreshTokens != null && refreshTokens.contains(refreshToken)) {
            return token;
        } else {
            logger.debug( "NOT FOUND");
            return null;
        }

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
}

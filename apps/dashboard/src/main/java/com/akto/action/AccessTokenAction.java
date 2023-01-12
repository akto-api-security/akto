package com.akto.action;

import com.akto.dao.UsersDao;
import com.akto.dto.User;
import com.akto.utils.Token;
import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import static com.akto.action.LoginAction.REFRESH_TOKEN_COOKIE_NAME;

public class AccessTokenAction implements Action, ServletResponseAware, ServletRequestAware {
    public static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
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
            return null;
        }

        Token token ;
        try {
            token = new Token(refreshToken);
        } catch (Exception e) {
            return null;
        }

        if (token.getSignedUp().equalsIgnoreCase("false")) {
            return token;
        }

        String username = token.getUsername();
        User user = UsersDao.instance.findOne("login", username);
        if (user == null) {
            return null;
        }

        List<String> refreshTokens = user.getRefreshTokens();
        if (refreshTokens != null && refreshTokens.contains(refreshToken)) {
            return token;
        } else {

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

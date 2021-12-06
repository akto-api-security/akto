package com.akto.action;

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

public class AccessTokenAction implements Action, ServletResponseAware, ServletRequestAware {
    public static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
    @Override
    public String execute() {
        Token token = generateAccessTokenFromServletRequest(servletRequest);
        if (token == null) {
            return Action.ERROR.toUpperCase();
        }
        String accessToken = token.getAccessToken();

        servletResponse.setHeader(ACCESS_TOKEN_HEADER_NAME, accessToken);

        return Action.SUCCESS.toUpperCase();
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
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            e.printStackTrace();
            return null;
        }

        return token;
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

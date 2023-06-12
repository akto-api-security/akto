package com.akto.action.growth_tools;

import com.akto.action.AccessTokenAction;
import com.akto.action.LoginAction;
import com.akto.action.UserAction;
import com.akto.dao.AccountsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.User;
import com.akto.listener.RuntimeListener;
import com.akto.utils.HttpUtils;
import com.akto.utils.JWT;
import com.akto.utils.Token;
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
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

public class PublicApiAction implements Action, ServletResponseAware, ServletRequestAware {
    protected HttpServletRequest request;
    protected HttpServletResponse response;

    @Override
    public String execute() throws Exception {
        User user = UsersDao.instance.findOne(Filters.eq(User.LOGIN, RuntimeListener.ANONYMOUS_EMAIL));
        String refreshToken;
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", user.getLogin());
        claims.put("signedUp", true + "");

        try {
            refreshToken = JWT.createJWT(
                    "",
                    claims,
                    "Akto",
                    "refreshToken",
                    Calendar.DAY_OF_MONTH,
                    6
            );
            List<String> refreshTokens = user.getRefreshTokens();
            if (refreshTokens == null) {
                refreshTokens = new ArrayList<>();
            }
            if (refreshTokens.size() > 10) {
                refreshTokens = refreshTokens.subList(refreshTokens.size() - 10, refreshTokens.size());
            }
            refreshTokens.add(refreshToken);

            Token token = new Token(refreshToken);
            response.addHeader(AccessTokenAction.ACCESS_TOKEN_HEADER_NAME, token.getAccessToken());
            Cookie cookie = new Cookie(LoginAction.REFRESH_TOKEN_COOKIE_NAME, refreshToken);
            cookie.setHttpOnly(true);
            cookie.setPath("/dashboard");

            cookie.setSecure(HttpUtils.isHttpsEnabled());


            response.addCookie(cookie);
            response.addHeader("Set-Cookie", "SameSite=None; Secure");
            HttpSession session = request.getSession(true);
            session.setAttribute("username", user.getLogin());
            session.setAttribute("user", user);
            session.setAttribute("login", Context.now());
            session.setAttribute("accountId", 1_000_000);
            UsersDao.instance.getMCollection().findOneAndUpdate(
                    Filters.eq("_id", user.getId()),
                    Updates.combine(
                            Updates.set("refreshTokens", refreshTokens),
                            Updates.set(User.LAST_LOGIN_TS, Context.now())
                    )
            );
            return Action.SUCCESS.toUpperCase();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            ;
        }

        return SUCCESS.toUpperCase();
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.response = response;
    }
}

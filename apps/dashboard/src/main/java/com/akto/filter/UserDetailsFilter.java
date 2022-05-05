package com.akto.filter;

import com.akto.action.AccessTokenAction;
import com.akto.action.ApiTokenAction;
import com.akto.action.ProfileAction;
import com.akto.dao.ApiTokensDao;
import com.akto.dao.SignupDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiToken;
import com.akto.dto.SignupUserInfo;
import com.akto.dto.User;
import com.akto.utils.JWT;
import com.akto.utils.Token;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.akto.action.LoginAction.REFRESH_TOKEN_COOKIE_NAME;

// This is the first filter which will hit for every request to server
// First checks if the access token is valid or not (from header)
// If not then checks if it can generate valid access token using the refresh token
// Adds the access token to response header
// Using the username from the access token it sets the user details in session to be used by other filters/action
public class UserDetailsFilter implements Filter {

    public static final String LOGIN_URI = "/login";
    public static final String API_URI = "/api";

    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() {}

    private void redirectIfNotLoginURI(FilterChain filterChain, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {

        if (!httpServletRequest.getRequestURI().contains(LOGIN_URI) && !httpServletRequest.getRequestURI().contains("/auth/login") && !httpServletRequest.getRequestURI().contains("api/googleConfig")) {
            System.out.println("redirecting from " + httpServletRequest.getRequestURI() + " to login");
            httpServletResponse.sendRedirect(LOGIN_URI+"?redirect_uri="+httpServletRequest.getRequestURI());
            return;
        }
        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    //TODO: logout if user in access-token is not the same as the user in cookie
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String accessTokenFromResponse = httpServletResponse.getHeader(AccessTokenAction.ACCESS_TOKEN_HEADER_NAME);
        String accessTokenFromRequest = httpServletRequest.getHeader(AccessTokenAction.ACCESS_TOKEN_HEADER_NAME);
        String requestURI = httpServletRequest.getRequestURI();

        // get api key header
        String apiKey = httpServletRequest.getHeader("X-API-KEY");
        String accessToken;

        // if api key present then check if valid api key or not and generate access token
        // else find access token from request header
        boolean apiKeyFlag = apiKey != null;
        if (apiKeyFlag) {
            if (endPointBlockedForApiToken(requestURI)) {
                httpServletResponse.sendError(403);
                return;
            }
            // check if valid key for path
            ApiToken apiToken = ApiTokensDao.instance.findByKey(apiKey);
            if (apiToken == null) {
                httpServletResponse.sendError(403);
                return;
            } else {
                boolean allCondition = apiToken.getAccessList().contains(ApiTokenAction.FULL_STRING_ALLOWED_API);
                boolean pathCondition = apiToken.getAccessList().contains(requestURI);
                if (!(allCondition || pathCondition)) {
                    httpServletResponse.sendError(403);
                    return;
                }
            }
            Context.accountId.set(apiToken.getAccountId());

            // convert apiKey to accessToken
            try {
                accessToken = Token.generateAccessToken(apiToken.getUsername(),"true");
            } catch (Exception e) {
                e.printStackTrace();
                httpServletResponse.sendError(403);
                return;
            }

        } else {
            if ("null".equalsIgnoreCase(accessTokenFromRequest)) {
                accessTokenFromRequest = null;
            }

            accessToken = accessTokenFromResponse;
            if (accessToken == null) {
                accessToken = accessTokenFromRequest;
            }
        }

        String username, signedUp;

        try {
            Jws<Claims> jws = JWT.parseJwt(accessToken, "/home/avneesh/Desktop/akto/dashboard/public.pem");
            username = jws.getBody().get("username").toString();
            signedUp = jws.getBody().get("signedUp").toString();
        } catch (Exception e) {
             if (requestURI.contains(API_URI)) {
                 ((HttpServletResponse) servletResponse).sendError(403);
                 return ;
             }
            Token token = AccessTokenAction.generateAccessTokenFromServletRequest(httpServletRequest);
            if (token == null) {
                Cookie cookie = AccessTokenAction.generateDeleteCookie();
                httpServletResponse.addCookie(cookie);
                redirectIfNotLoginURI(filterChain,httpServletRequest,httpServletResponse);
                return ;
            }
            username = token.getUsername();
            signedUp = token.getSignedUp();
            accessToken = token.getAccessToken();
            httpServletResponse.setHeader(AccessTokenAction.ACCESS_TOKEN_HEADER_NAME, accessToken);
        }

        if (username == null || signedUp == null) {
            redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
            return ;
        }

        HttpSession session = httpServletRequest.getSession(apiKeyFlag);
        if (session == null ) {
            redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
            return ;
        }

        // only for access-token based auth we check if session is valid or not
        if (!apiKeyFlag) {
            Object usernameObj = session.getAttribute("username");
            if (!Objects.equals(usernameObj, username)) {
                redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
                return ;
            }

            try {
                int loginTime = (int) session.getAttribute("login");
                Object logoutObj =  session.getAttribute("logout");
                if (logoutObj != null) {
                    int logoutTime = (int) logoutObj;
                    if (logoutTime > loginTime) {
                        redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
                        return ;
                    }
                }
            } catch (Exception ignored) {
                redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
                return ;
            }


        }

        session.setAttribute(AccessTokenAction.ACCESS_TOKEN_HEADER_NAME, accessToken);

        User user = (User) session.getAttribute("user");
        boolean isSignedUp = "true".equalsIgnoreCase(signedUp);

        boolean setupPathCondition = requestURI.startsWith("/dashboard/setup");
        boolean dashboardWithoutSetupCondition = requestURI.startsWith("/dashboard") && !setupPathCondition;
        if (setupPathCondition && !isSignedUp) {
            SignupUserInfo signupUserInfo = SignupDao.instance.findOne("user.login", username);
            if (signupUserInfo == null) {
                redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
                return ;
            }
            user = signupUserInfo.getUser();
            session.setAttribute("user", user);

            int step = 1;
            if (StringUtils.isEmpty(signupUserInfo.getCompanyName())) {
                step = 1;
            } else if (StringUtils.isEmpty(signupUserInfo.getTeamName())) {
                step = 2;
            } else if (signupUserInfo.getEmailInvitations() == null) {
                step = 3;
            }
            User signupUserInfoUser = signupUserInfo.getUser();
            BasicDBObject infoObj =
                    new BasicDBObject("username", signupUserInfoUser.getName())
                            .append("email", signupUserInfoUser.getLogin())
                            .append("companyName", signupUserInfo.getCompanyName())
                            .append("teamName", signupUserInfo.getTeamName())
                            .append("formVersion", 1)
                            .append("step", step);

            servletRequest.setAttribute("signupInfo", infoObj);

        } else if ((dashboardWithoutSetupCondition || httpServletRequest.getRequestURI().startsWith("/api")) && isSignedUp) {

            // if no user details in the session, ask from DB
            // TODO: if session info is too old, then also fetch from DB
            if (user == null || !username.equals(user.getLogin())) {
                user = UsersDao.instance.findOne("login", username);
                session.setAttribute("user", user);
            }

            Object accountIdObj = session.getAttribute("accountId");
            String accountIdStr = accountIdObj == null ? null : accountIdObj+"";

            if (StringUtils.isEmpty(accountIdStr)) {
                accountIdStr = httpServletRequest.getHeader("account");
            }

            if (StringUtils.isNotEmpty(accountIdStr)) {
                int accountId = Integer.parseInt(accountIdStr);
                if (accountId > 0) {
                    if(user.getAccounts().containsKey(accountIdStr)) {
                        Context.accountId.set(accountId);
                        System.out.println("choosing account: " + accountIdStr);
                    } else {
                        System.out.println("you don't have access to this account: " + accountIdStr + " " + user.getLogin());
                    }
                }
            }

            if (accessTokenFromRequest == null) {
                ProfileAction.executeMeta1(user, httpServletRequest);
            }
        } else {
            redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
            return ;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    public boolean endPointBlockedForApiToken(String endpoint) {
        if (endpoint.startsWith("/dashboard")) {
            return true;
        }

        List<String> blockedList = new ArrayList<>();
        blockedList.add("/api/inviteUsers");
        blockedList.add("/api/logout");
        blockedList.add("/api/getPostmanCredential");
        blockedList.add("/api/addBurpToken");
        blockedList.add("/api/addExternalApiToken");
        blockedList.add("/api/deleteApiToken");
        blockedList.add("/api/fetchApiTokens");

        return blockedList.contains(endpoint);
    }
}

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
import com.akto.dto.ApiToken.Utility;
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

        if (httpServletRequest.getRequestURI().contains(API_URI)) {
            httpServletResponse.sendError(403);
            return ;
        }

        if (!httpServletRequest.getRequestURI().contains(LOGIN_URI) && !httpServletRequest.getRequestURI().contains("/auth/login") && !httpServletRequest.getRequestURI().contains("api/googleConfig")) {

            boolean isSetupLink = "/dashboard/setup".equalsIgnoreCase(httpServletRequest.getRequestURI());
            String redirectParam = isSetupLink ? "" : ("?redirect_uri="+httpServletRequest.getRequestURI());
            httpServletResponse.sendRedirect(LOGIN_URI+redirectParam);
            return;
        }
        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    public static final String ACCOUNT_ID = "accountId";
    
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
        Utility utility = null;

        HttpSession session = httpServletRequest.getSession(false);
        if (apiKeyFlag) {
            // For apiKey sessions we want to start fresh. Hence, delete any existing session and create new one
            if (session != null) session.invalidate();
            session = httpServletRequest.getSession(true);

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
                boolean allCondition = apiToken.getUtility().getAccessList().contains(ApiToken.FULL_STRING_ALLOWED_API);
                boolean pathCondition = apiToken.getUtility().getAccessList().contains(requestURI);
                if (!(allCondition || pathCondition)) {
                    httpServletResponse.sendError(403);
                    return;
                }
            }
            Context.accountId.set(apiToken.getAccountId());
            utility = apiToken.getUtility();

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

        // session will be non-null for external API Key requests and when session data has not been deleted
        if (session == null ) {
            System.out.println("Session expired");
            Token tempToken = AccessTokenAction.generateAccessTokenFromServletRequest(httpServletRequest);
            // If we are able to extract token from Refresh Token then this means RT is valid and new session can be created
            if (tempToken== null) {
                redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
                return;
            }
            session = httpServletRequest.getSession(true);
            session.setAttribute("username", username);
            session.setAttribute("login", Context.now());
            session.setAttribute("signedUp", signedUp);
            System.out.println("New session created");
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
        if (utility != null) session.setAttribute("utility", utility+""); // todo: replace with enum (here and haraction)

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
                if (user == null) {
                    redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
                    return ;
                }
                session.setAttribute("user", user);
                session.setAttribute("username", user.getLogin());
                String accountId = Context.accountId.get() == null ? user.findAnyAccountId() : (""+Context.accountId.get());
                if (accountId == null) {
                    redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
                    return ;
                }
                session.setAttribute(ACCOUNT_ID, accountId);
            }
            Object accountIdObj = session.getAttribute(ACCOUNT_ID);
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

                        accountIdStr = user.findAnyAccountId();
                        if (accountIdStr == null) {
                            redirectIfNotLoginURI(filterChain, httpServletRequest, httpServletResponse);
                            return ;
                        }

                        accountId = Integer.parseInt(accountIdStr);

                        Context.accountId.set(accountId);
                        session.setAttribute(ACCOUNT_ID, accountId);

                    }
                }
            }
            if (accessTokenFromRequest == null) {
                ProfileAction.executeMeta1(utility, user, httpServletRequest, httpServletResponse);
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
        blockedList.add("/api/addApiToken");
        blockedList.add("/api/deleteApiToken");
        blockedList.add("/api/fetchApiTokens");

        return blockedList.contains(endpoint);
    }
}

package com.akto.action;

import com.akto.dao.SignupDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.SignupInfo;
import com.akto.dto.SignupUserInfo;
import com.akto.dto.User;
import com.akto.utils.Token;
import com.akto.utils.JWT;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

import static com.akto.filter.UserDetailsFilter.LOGIN_URI;

// Validates user from the supplied username and password
// Generates refresh token jwt using the username if valid user
// Saves the refresh token to db (TODO)
// Generates access token jwt using the refresh token
// Adds the refresh token to http-only cookie
// Adds the access token to header
public class LoginAction implements Action, ServletResponseAware, ServletRequestAware {
    private static final Logger logger = LoggerFactory.getLogger(LoginAction.class);
    
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    public BasicDBObject getLoginResult() {
        return loginResult;
    }

    public void setLoginResult(BasicDBObject loginResult) {
        this.loginResult = loginResult;
    }

    BasicDBObject loginResult = new BasicDBObject();
    @Override
    public String execute() throws IOException {
        System.out.println("LoginAction Hit");

        if (username == null) {
            return Action.ERROR.toUpperCase();
        }

        User user = UsersDao.instance.findOne(Filters.eq("login", username));

        if (user != null) {
            SignupInfo.PasswordHashInfo signupInfo = (SignupInfo.PasswordHashInfo) user.getSignupInfoMap().get(Config.ConfigType.PASSWORD + "-ankush");
            String salt = signupInfo.getSalt();
            String passHash = Integer.toString((salt + password).hashCode());
            if (!passHash.equals(signupInfo.getPasshash())) {
                return Action.ERROR.toUpperCase();
            }

        } else {

            SignupUserInfo signupUserInfo = SignupDao.instance.findOne("user.login", username);

            if (signupUserInfo != null) {
                SignupInfo.PasswordHashInfo passInfo =
                        (SignupInfo.PasswordHashInfo) signupUserInfo.getUser().getSignupInfoMap().get(Config.ConfigType.PASSWORD + "-ankush");

                String passHash = Integer.toString((passInfo.getSalt() + password).hashCode());

                if (passHash.equals(passInfo.getPasshash())) {
                    loginUser(signupUserInfo.getUser(), servletResponse, false, servletRequest);
                    loginResult.put("redirect", "/dashboard/setup");
                    return "SUCCESS";
                }
            }

            System.out.println("Auth Failed");
            return "ERROR";
        }
        String result = loginUser(user, servletResponse, true, servletRequest);
        decideFirstPage(loginResult);
        return result;
    }

    private void decideFirstPage(BasicDBObject loginResult){
        //TODO get this reviewed
        Context.accountId.set(1_000_000);
        long count = SingleTypeInfoDao.instance.getEstimatedCount();
        if(count == 0){
            logger.info("New user, showing quick start page");
            loginResult.put("redirect", "dashboard/quick-start");
        } else {
            logger.info("Existing user, not redirecting to quick start page");
        }
    }

    public static String loginUser(User user, HttpServletResponse servletResponse, boolean signedUp, HttpServletRequest servletRequest) {
        String refreshToken;
        Map<String,Object> claims = new HashMap<>();
        claims.put("username",user.getLogin());
        claims.put("signedUp",signedUp+"");
        try {
            refreshToken = JWT.createJWT(
                    "/home/avneesh/Desktop/akto/dashboard/private.pem",
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
                refreshTokens = refreshTokens.subList(refreshTokens.size()-10, refreshTokens.size());
            }
            refreshTokens.add(refreshToken);

            Token token = new Token(refreshToken);
            servletResponse.addHeader(AccessTokenAction.ACCESS_TOKEN_HEADER_NAME,token.getAccessToken());
            Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
            cookie.setHttpOnly(true);
            cookie.setPath("/dashboard");

            String https = System.getenv("AKTO_HTTPS_FLAG");
            if (Objects.equals(https, "true")) {
                cookie.setSecure(true);
            }

            servletResponse.addCookie(cookie);
            HttpSession session = servletRequest.getSession(true);
            session.setAttribute("username", user.getLogin());
            session.setAttribute("user", user);
            session.setAttribute("login", Context.now());
            if (signedUp) {
                UsersDao.instance.getMCollection().findOneAndUpdate(
                        Filters.eq("_id", user.getId()),
                        Updates.set("refreshTokens", refreshTokens)
                );
            }
            return Action.SUCCESS.toUpperCase();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            e.printStackTrace();
        }

        return Action.ERROR.toUpperCase();

    }

    private String username;
    private String password;


    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
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

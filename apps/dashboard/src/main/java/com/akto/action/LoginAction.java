package com.akto.action;

import com.akto.dao.BackwardCompatibilityDao;
import com.akto.dao.SignupDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.BackwardCompatibility;
import com.akto.dto.Config;
import com.akto.dto.SignupInfo;
import com.akto.dto.SignupUserInfo;
import com.akto.dto.User;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.Token;
import com.akto.utils.JWT;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import org.apache.commons.lang3.StringUtils;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final ExecutorService service = Executors.newFixedThreadPool(1);
    public BasicDBObject getLoginResult() {
        return loginResult;
    }

    public void setLoginResult(BasicDBObject loginResult) {
        this.loginResult = loginResult;
    }

    BasicDBObject loginResult = new BasicDBObject();
    @Override
    public String execute() throws IOException {
        logger.info("LoginAction Hit");

        if (username == null) {
            return Action.ERROR.toUpperCase();
        }

        User user = UsersDao.instance.findOne(Filters.eq(User.LOGIN, username));

        if (user != null && user.getSignupInfoMap()!=null && user.getSignupInfoMap().containsKey(Config.ConfigType.PASSWORD + Config.CONFIG_SALT)){
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
                    loginResult.put("redirect", "/dashboard/quick-start");
                    return "SUCCESS";
                }
            }

            logger.info("Auth Failed");
            return "ERROR";
        }
        String result = loginUser(user, servletResponse, true, servletRequest);
        //For the case when no account exists, the user will get access to 1_000_000 account
        String accountIdStr = user.getAccounts().keySet().isEmpty() ? "1000000" : user.getAccounts().keySet().iterator().next();
        int accountId = StringUtils.isNumeric(accountIdStr) ? Integer.parseInt(accountIdStr) : 1_000_000;
        try {
            service.submit(() ->{
                triggerVulnColUpdation(user);
            });
        } catch (Exception e) {
            logger.error("error updating vuln collection ", e);
        }
        decideFirstPage(loginResult, accountId);
        return result;
    }

    private static void triggerVulnColUpdation(User user) {
        for (String accountIdStr: user.getAccounts().keySet()) {
            int accountId = Integer.parseInt(accountIdStr);
            Context.accountId.set(accountId);
            logger.info("updating vulnerable api's collection for account " + accountId);
            try {
                BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
                if (backwardCompatibility.getVulnerableApiUpdationVersionV1() == 0) {
                    RuntimeListener.addSampleData();
                }
                BackwardCompatibilityDao.instance.updateOne(
                        Filters.eq("_id", backwardCompatibility.getId()),
                        Updates.set(BackwardCompatibility.VULNERABLE_API_UPDATION_VERSION_V1, Context.now())
                );
            } catch (Exception e) {
                logger.error("error updating vulnerable api's collection for account " + accountId + " " + e.getMessage());
            }
        }
    }

    private void decideFirstPage(BasicDBObject loginResult, int accountId){
        Context.accountId.set(accountId);
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
                    Calendar.MONTH,
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
                        Updates.combine(
                                Updates.set("refreshTokens", refreshTokens),
                                Updates.set(User.LAST_LOGIN_TS, Context.now())
                        )
                );
            }
            service.submit(() ->{
                triggerVulnColUpdation(user);
            });
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

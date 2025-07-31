package com.akto.action;

import com.akto.dao.ApiTokensDao;
import com.akto.dao.BurpPluginInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiToken;
import com.akto.dto.BurpPluginInfo;
import com.akto.dto.ApiToken.Utility;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import kotlin.text.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class BurpJarAction extends UserAction implements ServletResponseAware, ServletRequestAware {

    private static final LoggerMaker loggerMaker = new LoggerMaker(BurpJarAction.class, LogDb.DASHBOARD);;

    private ApiToken apiToken;
    private String host;

    @Override
    public String execute() {
        BurpPluginInfoDao.instance.updateLastDownloadedTimestamp(getSUser().getLogin());
        return SUCCESS.toUpperCase();
    }

    private String burpGithubLink = null;
    public String fetchBurpPluginDownloadLink() {
        burpGithubLink = "https://raw.githubusercontent.com/akto-api-security/akto-burp-extension/master/Akto.jar";
        return SUCCESS.toUpperCase();
    }

    public String fetchBurpCredentials() {
        host = servletRequest.getHeader("Origin");

        apiToken = ApiTokensDao.instance.findOne(
                Filters.and(
                        Filters.eq(ApiToken.USER_NAME, getSUser().getLogin()),
                        Filters.eq(ApiToken.ACCOUNT_ID, Context.accountId.get()),
                        Filters.eq(ApiToken.UTILITY, ApiToken.Utility.BURP)
                )
        );

        // if api token not found generate one
        if (apiToken == null) {
            ApiTokenAction apiTokenAction = new ApiTokenAction();
            apiTokenAction.setSession(this.getSession());
            apiTokenAction.setTokenUtility(Utility.BURP);
            apiTokenAction.addApiToken();
            List<ApiToken> apiTokenList = apiTokenAction.getApiTokenList();
            if (apiTokenList == null || apiTokenList.isEmpty()) {
                addActionError("Couldn't generate burp token");
                return ERROR.toUpperCase();
            }
            apiToken = apiTokenList.get(0);
        }

        return SUCCESS.toUpperCase();
    }

    private BurpPluginInfo burpPluginInfo;
    public String fetchBurpPluginInfo() {
        burpPluginInfo = BurpPluginInfoDao.instance.findByUsername(this.getSUser().getLogin());
        return SUCCESS.toUpperCase();
    }


    public String version;
    public int latestVersion;
    public String sendHealthCheck() {
        int versionInt;
        try {
            versionInt = Integer.parseInt(this.version);
        } catch (Exception e) {
            e.printStackTrace();
            versionInt = -1;
        }

        BurpPluginInfoDao.instance.updateOne(
            BurpPluginInfoDao.filterByUsername(this.getSUser().getLogin()),
            Updates.combine(
                Updates.set(BurpPluginInfo.LAST_BOOT_UP_TIMESTAMP, Context.now()),
                Updates.set(BurpPluginInfo.VERSION, versionInt)
            )
        );

        latestVersion = InitializerListener.burpPluginVersion;

        return SUCCESS.toUpperCase();
    }

    protected HttpServletResponse servletResponse;
    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.servletResponse= response;
    }

    protected HttpServletRequest servletRequest;

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.servletRequest = request;
    }

    public BurpPluginInfo getBurpPluginInfo() {
        return burpPluginInfo;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getLatestVersion() {
        return latestVersion;
    }

    public ApiToken getApiToken() {
        return apiToken;
    }

    public String getHost() {
        return host;
    }

    public String getBurpGithubLink() {
        return burpGithubLink;
    }
}

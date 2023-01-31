package com.akto.action;

import com.akto.dao.ApiTokensDao;
import com.akto.dao.BurpPluginInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiToken;
import com.akto.dto.BurpPluginInfo;
import com.akto.listener.InitializerListener;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import kotlin.text.Charsets;
import org.apache.commons.io.FileUtils;
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


    @Override
    public String execute() {
        String host = servletRequest.getHeader("Origin");

        ApiToken apiToken = ApiTokensDao.instance.findOne(
                Filters.and(
                        Filters.eq(ApiToken.USER_NAME, getSUser().getLogin()),
                        Filters.eq(ApiToken.UTILITY, ApiToken.Utility.BURP)
                )
        );

        // if api token not found generate one
        if (apiToken == null) {
            ApiTokenAction apiTokenAction = new ApiTokenAction();
            apiTokenAction.setSession(this.getSession());
            apiTokenAction.addBurpToken();
            List<ApiToken> apiTokenList = apiTokenAction.getApiTokenList();
            if (apiTokenList == null || apiTokenList.isEmpty()) {
                addActionError("Couldn't generate burp token");
                return ERROR.toUpperCase();
            }
            apiToken = apiTokenList.get(0);
        }

        String token = apiToken.getKey();
        String collectionName = "Burp";
        int version = InitializerListener.burpPluginVersion;

        File tmpJarFile;
        try {
            tmpJarFile = File.createTempFile("temp", "jar");
        } catch (IOException e) {
            addActionError("Failed creating temp file");
            return ERROR.toUpperCase();
        }

        URL url = this.getClass().getResource("/Akto.jar");
        if (url == null) {
            addActionError("Akto plugin not found!");
            return ERROR.toUpperCase();
        }

        JarFile jarFile;
        try {
            jarFile = new JarFile(url.getPath());
        } catch (IOException e) {
            addActionError("Failed creating JAR file");
            return ERROR.toUpperCase();
        }

        File credFile;
        try {
            credFile = File.createTempFile("creds", "txt"); // todo: remove
            FileUtils.writeStringToFile(credFile,host + "\n" + token + "\n" + collectionName + "\n" + version, Charsets.UTF_8);
        } catch (Exception e) {
            addActionError("Failed adding credentials");
            return ERROR.toUpperCase();
        }

        try {
            try (JarOutputStream tempJarOutputStream = new JarOutputStream(Files.newOutputStream(tmpJarFile.toPath()))) {
                Enumeration<JarEntry> jarEntries = jarFile.entries();
                Set<String> done = new HashSet<>();

                // copy existing elements
                while (jarEntries.hasMoreElements()) {
                    JarEntry entry = jarEntries.nextElement();
                    if (done.contains(entry.getName())) continue;
                    done.add(entry.getName());

                    InputStream entryInputStream = jarFile.getInputStream(entry);
                    tempJarOutputStream.putNextEntry(entry);

                    byte[] buffer = new byte[1024];
                    int bytesRead = 0;
                    while ((bytesRead = entryInputStream.read(buffer)) != -1) {
                        tempJarOutputStream.write(buffer, 0, bytesRead);
                    }
                }

                JarEntry entryNew = new JarEntry("credentials.txt");
                InputStream entryInputStream = new FileInputStream(credFile);
                tempJarOutputStream.putNextEntry(entryNew);

                byte[] buffer = new byte[1024];
                int bytesRead = 0;
                while ((bytesRead = entryInputStream.read(buffer)) != -1) {
                    tempJarOutputStream.write(buffer, 0, bytesRead);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }

        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (Exception ignored) {

                }
            }
        }

        servletResponse.setContentType("application/octet-stream");
        servletResponse.setHeader("Content-Disposition", "filename=\"Akto.jar\"");
        System.out.println("set header done");
        File srcFile = new File(tmpJarFile.getPath());
        try {
            FileUtils.copyFile(srcFile, servletResponse.getOutputStream());
        } catch (IOException e) {
            addActionError("Failed sending jar file");
            return ERROR.toUpperCase();
        }

        System.out.println("done");

        credFile.delete();
        tmpJarFile.delete();

        BurpPluginInfoDao.instance.updateLastDownloadedTimestamp(getSUser().getLogin());

        return null;
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
}

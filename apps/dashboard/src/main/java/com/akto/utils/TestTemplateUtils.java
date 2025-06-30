package com.akto.utils;

import static com.akto.listener.InitializerListener.loadTemplateFilesFromDirectory;

import com.akto.log.LoggerMaker;

public class TestTemplateUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestTemplateUtils.class, LoggerMaker.LogDb.DASHBOARD);

    public static byte[] getTestingTemplates() {
        GithubSync githubSync = new GithubSync();
        byte[] repoZip = githubSync.syncRepo("akto-api-security/tests-library", "master");
        if(repoZip == null) {
            loggerMaker.infoAndAddToDb("Failed to load test templates from github, trying to load from local directory");
            repoZip = loadTemplateFilesFromDirectory();
            if(repoZip == null) {
                loggerMaker.errorAndAddToDb("Failed to load test templates from github or local directory");
                return null;
            } else {
                loggerMaker.infoAndAddToDb("Loaded test templates from local directory");
            }
        } else {
            loggerMaker.infoAndAddToDb("Loaded test templates from github");
        }
        return repoZip;
    }

}

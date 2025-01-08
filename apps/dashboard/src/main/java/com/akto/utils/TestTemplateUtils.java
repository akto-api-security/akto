package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.util.DashboardMode;

import static com.akto.listener.InitializerListener.loadTemplateFilesFromDirectory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TestTemplateUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestTemplateUtils.class, LoggerMaker.LogDb.DASHBOARD);

    public static byte[] getTestingTemplates() {
        byte[] repoZip = null;

        if (DashboardMode.isMetered()) {
            GithubSync githubSync = new GithubSync();
            repoZip = githubSync.syncRepo("akto-api-security/tests-library", "master");

            if(repoZip == null) {
                loggerMaker.infoAndAddToDb("Failed to load test templates from github, trying to load from local directory");
            } else {
                loggerMaker.infoAndAddToDb("Loaded test templates from github");
            }
        }
        
        if (repoZip == null) {
            repoZip = loadTemplateFilesFromDirectory();

            if(repoZip == null) {
                loggerMaker.errorAndAddToDb("Failed to load test templates from local directory");
            } else {
                loggerMaker.infoAndAddToDb("Loaded test templates from local directory");
            }
        }
        
        return repoZip;
    }

    public static Map<String, byte[]> getZipFromMultipleRepoAndBranch(Set<String> repoAndBranchList) {
        
        Map<String, byte[]> ret = new HashMap<>();

        for (String repoAndBranch : repoAndBranchList) {
            ret.put(repoAndBranch, getZipFromRepoAndBranch(repoAndBranch));
        }

        return ret;
    }

    public static byte[] getZipFromRepoAndBranch(String repositoryUrlAndBranch) {

        String[] tokens = repositoryUrlAndBranch.split(":");

        boolean isBranchMaster = (tokens.length == 1 || tokens[1].length() == 0);

        return getProTemplates(tokens[0], isBranchMaster ? "master" : tokens[1]);
    }

    public static byte[] getProTemplates(String repositoryUrl, String branch) {
        byte[] repoZip = null;

        if (DashboardMode.isMetered()) {
            GithubSync githubSync = new GithubSync();
            repoZip = githubSync.syncRepo(repositoryUrl, branch);

            if(repoZip == null) {
                loggerMaker.infoAndAddToDb("Failed to load test templates from github, trying to load from local directory");
            } else {
                loggerMaker.infoAndAddToDb("Loaded test templates from github");
            }
        }
        
        return repoZip;
    }

}

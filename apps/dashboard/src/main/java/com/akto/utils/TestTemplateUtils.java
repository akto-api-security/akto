package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.TestCategory;

import static com.akto.listener.InitializerListener.loadTemplateFilesFromDirectory;

import java.util.Arrays;
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
                loggerMaker.debugAndAddToDb("Failed to load test templates from github, trying to load from local directory");
            } else {
                loggerMaker.debugAndAddToDb("Loaded test templates from github");
            }
        }
        
        if (repoZip == null) {
            repoZip = loadTemplateFilesFromDirectory();

            if(repoZip == null) {
                loggerMaker.errorAndAddToDb("Failed to load test templates from local directory");
            } else {
                loggerMaker.debugAndAddToDb("Loaded test templates from local directory");
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
                loggerMaker.debugAndAddToDb("Failed to load test templates from github, trying to load from local directory");
            } else {
                loggerMaker.debugAndAddToDb("Loaded test templates from github");
            }
        }
        
        return repoZip;
    }

    public static TestCategory[] getAllTestCategoriesWithinContext(CONTEXT_SOURCE contextSource) {
        if(contextSource == null) {
            contextSource = CONTEXT_SOURCE.API; // Default to API if contextSource is null
        }
        TestCategory[] allCategories = GlobalEnums.TestCategory.values();
        TestCategory[] mcpCategories = {
            TestCategory.MCP_AUTH,
            TestCategory.MCP_INPUT_VALIDATION,
            TestCategory.MCP_DOS,
            TestCategory.MCP_SENSITIVE_DATA_LEAKAGE,
            TestCategory.MCP,
            TestCategory.MCP_TOOL_POISONING,
            TestCategory.MCP_PROMPT_INJECTION,
            TestCategory.MCP_PRIVILEGE_ABUSE,
            TestCategory.MCP_INDIRECT_PROMPT_INJECTION,
            TestCategory.MCP_MALICIOUS_CODE_EXECUTION,
        };

        TestCategory[] llmCategories = {
            GlobalEnums.TestCategory.LLM,
            GlobalEnums.TestCategory.LLM01,
            GlobalEnums.TestCategory.LLM02,
            GlobalEnums.TestCategory.LLM03,
            GlobalEnums.TestCategory.LLM04,
            GlobalEnums.TestCategory.LLM05,
            GlobalEnums.TestCategory.LLM06,
            GlobalEnums.TestCategory.LLM07,
            GlobalEnums.TestCategory.LLM08,
            GlobalEnums.TestCategory.LLM09,
            GlobalEnums.TestCategory.LLM10
        };

        switch (contextSource) {
            case MCP:
                return mcpCategories;

            case GEN_AI:
                return llmCategories;
            
            default:
                return Arrays.stream(allCategories)
                    .filter(category -> !Arrays.asList(mcpCategories).contains(category) && !Arrays.asList(llmCategories).contains(category))
                    .toArray(TestCategory[]::new);
        }
    }

}

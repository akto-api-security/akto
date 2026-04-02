package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.TestCategory;

import static com.akto.listener.InitializerListener.loadTemplateFilesFromDirectory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
            TestCategory.MCP_FUNCTION_MANIPULATION,
            TestCategory.MCP_SECURITY,
        };

        final TestCategory[] llmCategories = {
            GlobalEnums.TestCategory.LLM,
            GlobalEnums.TestCategory.PROMPT_INJECTION,
            GlobalEnums.TestCategory.SENSITIVE_INFORMATION_DISCLOSURE,
            GlobalEnums.TestCategory.SUPPLY_CHAIN,
            GlobalEnums.TestCategory.DATA_AND_MODEL_POISONING,
            GlobalEnums.TestCategory.IMPROPER_OUTPUT_HANDLING,
            GlobalEnums.TestCategory.EXCESSIVE_AGENCY,
            GlobalEnums.TestCategory.SYSTEM_PROMPT_LEAKAGE,
            GlobalEnums.TestCategory.VECTOR_AND_EMBEDDING_WEAKNESSES,
            GlobalEnums.TestCategory.MISINFORMATION,
            GlobalEnums.TestCategory.UNBOUNDED_CONSUMPTION,
            TestCategory.AGENTIC_BUSINESS_ALIGNMENT,
            TestCategory.AGENTIC_HALLUCINATION_AND_TRUSTWORTHINESS,
            TestCategory.AGENTIC_SAFETY,
            TestCategory.AGENTIC_SECURITY,
            TestCategory.AGENTIC_SECURITY_PROMPT_INJECTION,
            TestCategory.AGENTIC_SECURITY_AGENT_EXPLOITATION,
            TestCategory.AGENTIC_SECURITY_INFRASTRUCTURE,
            TestCategory.AGENTIC_SECURITY_DATA_EXPOSURE,
            TestCategory.AGENTIC_SECURITY_CODE_EXECUTION,
            TestCategory.AGENT_GOAL_HIJACK,
            TestCategory.TOOL_MISUSE_AND_EXPLOITATION,
            TestCategory.IDENTITY_AND_PRIVILEGE_ABUSE,
            TestCategory.AGENTIC_SUPPLY_CHAIN,
            TestCategory.UNEXPECTED_CODE_EXECUTION,
            TestCategory.MEMORY_AND_CONTEXT_POISONING,
            TestCategory.INSECURE_INTER_AGENT_COMMUNICATION,
            TestCategory.CASCADING_FAILURES,
            TestCategory.HUMAN_AGENT_TRUST_EXPLOITATION,
            TestCategory.ROGUE_AGENTS,
        };

        switch (contextSource) {
            case MCP:
                return copyAllExcept(allCategories, llmCategories);

            case GEN_AI:
                return Arrays.copyOf(llmCategories, llmCategories.length);

            case AGENTIC:
            case ENDPOINT:
                // ARGUS / ATLAS should include both MCP and LLM/Agentic probe libraries,
                // while excluding classic API-only categories.
                return unionCategories(allCategories, mcpCategories, llmCategories);

            // for DAST and API security
            case DAST:
            case API:
            default:
                return copyExcludingBoth(allCategories, mcpCategories, llmCategories);
        }
    }

    private static boolean containsCategory(TestCategory[] arr, TestCategory category) {
        for (TestCategory c : arr) {
            if (c == category) {
                return true;
            }
        }
        return false;
    }

    private static TestCategory[] copyAllExcept(TestCategory[] all, TestCategory[] exclude) {
        List<TestCategory> out = new ArrayList<>();
        for (TestCategory c : all) {
            if (!containsCategory(exclude, c)) {
                out.add(c);
            }
        }
        return out.toArray(new TestCategory[0]);
    }

    private static TestCategory[] copyExcludingBoth(TestCategory[] all, TestCategory[] a, TestCategory[] b) {
        List<TestCategory> out = new ArrayList<>();
        for (TestCategory c : all) {
            if (!containsCategory(a, c) && !containsCategory(b, c)) {
                out.add(c);
            }
        }
        return out.toArray(new TestCategory[0]);
    }

    /** Categories present in {@code all} that appear in either {@code first} or {@code second}. */
    private static TestCategory[] unionCategories(TestCategory[] all, TestCategory[] first, TestCategory[] second) {
        List<TestCategory> out = new ArrayList<>();
        for (TestCategory c : all) {
            if (containsCategory(first, c) || containsCategory(second, c)) {
                out.add(c);
            }
        }
        return out.toArray(new TestCategory[0]);
    }

}

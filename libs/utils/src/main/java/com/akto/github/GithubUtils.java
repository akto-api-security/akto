package com.akto.github;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.log.LoggerMaker;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.client.model.Filters;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.lang.RuntimeEnvironment;
import okhttp3.OkHttpClient;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.*;
import org.kohsuke.github.connector.GitHubConnector;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

import static com.akto.dao.AccountSettingsDao.generateFilter;
import static com.akto.dao.MCollection.ID;

public class GithubUtils {

    private GithubUtils() {}

    static final OkHttpClient client = CoreHTTPClient.client.newBuilder().build();
    static final GitHubConnector connector = new OkHttpGitHubConnector(client);

    static {
        RuntimeEnvironment.enableBouncyCastleIfPossible();
    }


    public static String createJWT(String githubAppId,String secret, long ttlMillis) throws Exception {
        //The JWT signature algorithm we will be using to sign the token
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.RS256;

        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);

        //We will sign our JWT with our private key
        Key signingKey = get(Base64.getDecoder().decode(secret));

        //Let's set the JWT Claims
        JwtBuilder builder = Jwts.builder()
                .setIssuedAt(now)
                .setIssuer(githubAppId)
                .signWith(signingKey, signatureAlgorithm);

        //if it has been specified, let's add the expiration
        if (ttlMillis > 0) {
            long expMillis = nowMillis + ttlMillis;
            Date exp = new Date(expMillis);
            builder.setExpiration(exp);
        }

        //Builds the JWT and serializes it to a compact, URL-safe string
        return builder.compact();
    }

    static PrivateKey get(byte[] secrets) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(secrets);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private static final LoggerMaker loggerMaker = new LoggerMaker(GithubUtils.class);

    public static void publishGithubStatus(TestingRunResultSummary testingRunResultSummary) {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(generateFilter());
        String privateKey = accountSettings.getGithubAppSecretKey();
        String githubAppId = accountSettings.getGithubAppId();
        if (StringUtils.isEmpty(privateKey) || StringUtils.isEmpty(githubAppId)) {//If github app is not integrated
            return;
        }
        String jwtToken;
        try {
            Map<String, String> metaData = testingRunResultSummary.getMetadata();
            if (metaData == null) {//No metaData is present, i.e. not a cicd test
                return;
            }
            String repository = metaData.get("repository");
            String commitSHA = metaData.get("commit_sha_head");
            if (StringUtils.isEmpty(repository)  || StringUtils.isEmpty(commitSHA)) {
                return;
            }
            jwtToken = createJWT(githubAppId,privateKey, 10 * 60 * 1000);
            GitHub gitHub = new GitHubBuilder().withConnector(connector).withJwtToken(jwtToken).build();
            GHApp ghApp = gitHub.getApp();

            //Getting appInstallations
            List<GHAppInstallation> appInstallations = ghApp.listInstallations().toList();
            if (appInstallations.isEmpty()) {
                loggerMaker.infoAndAddToDb("Github app was not installed", LoggerMaker.LogDb.TESTING);
                return;
            }

            GHAppInstallation appInstallation = appInstallations.get(0);
            GHAppCreateTokenBuilder builder = appInstallation.createToken();
            GHAppInstallationToken token = builder.create();
            GitHub githubAccount =  new GitHubBuilder().withConnector(connector).withAppInstallationToken(token.getToken())
                    .build();

            GHRepository ghRepository = githubAccount.getRepository(repository);
            if (ghRepository == null) {
                loggerMaker.infoAndAddToDb("Github app doesn't have access to repository", LoggerMaker.LogDb.TESTING);
                return;
            }
            GHCheckRunBuilder ghCheckRunBuilder = ghRepository.createCheckRun("Akto Security Checks", commitSHA);
            ghCheckRunBuilder.withStatus(GHCheckRun.Status.IN_PROGRESS)
                    .withStartedAt(new Date())
                    .add(new GHCheckRunBuilder.Output("Akto CI/CD test running", ""))
                    .create();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Github checks error : " + e.getMessage(), LoggerMaker.LogDb.TESTING);
        }
    }

    public static void publishGithubComments(TestingRunResultSummary testingRunResultSummary) {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(generateFilter());
        String privateKey = accountSettings.getGithubAppSecretKey();
        String githubAppId = accountSettings.getGithubAppId();
        if (StringUtils.isEmpty(privateKey) || StringUtils.isEmpty(githubAppId)) {//If github app is not integrated
            return;
        }
        try {
            Map<String, String> metaData = testingRunResultSummary.getMetadata();
            if (metaData == null) {//No metaData is present, i.e. not a cicd test
                return;
            }
            String repository = metaData.get("repository");
            String pullRequestId = metaData.get("pull_request_id");
            String commitSHA = metaData.get("commit_sha_head");
            if (StringUtils.isEmpty(repository) || StringUtils.isEmpty(pullRequestId) || StringUtils.isEmpty(commitSHA)) {
                return;
            }

            boolean isCompleted = testingRunResultSummary.getState() == TestingRun.State.COMPLETED;
            StringBuilder messageStringBuilder = new StringBuilder();
            if (isCompleted) {
                buildCommentForCompletedTest(testingRunResultSummary, messageStringBuilder);
            } else {
                messageStringBuilder.append("Akto CI/CD test is currently in ").append(testingRunResultSummary.getState().name()).append(" state");
            }
            String message = messageStringBuilder.toString();
            //JWT Token creation for github app
            String jwtToken = GithubUtils.createJWT(githubAppId,privateKey, 10 * 60 * 1000);

            //Github app invocation
            GitHub gitHub = new GitHubBuilder().withConnector(connector).withJwtToken(jwtToken).build();
            GHApp ghApp = gitHub.getApp();

            //Getting appInstallations
            List<GHAppInstallation> appInstallations = ghApp.listInstallations().toList();
            if (appInstallations.isEmpty()) {
                loggerMaker.infoAndAddToDb("Github app was not installed", LoggerMaker.LogDb.TESTING);
                return;
            }
            GHAppInstallation appInstallation = appInstallations.get(0);
            GHAppCreateTokenBuilder builder = appInstallation.createToken();
            GHAppInstallationToken token = builder.create();
            GitHub githubAccount =  new GitHubBuilder().withConnector(connector).withAppInstallationToken(token.getToken())
                    .build();

            GHRepository ghRepository = githubAccount.getRepository(repository);
            if (ghRepository == null) {
                loggerMaker.infoAndAddToDb("Github app doesn't have access to repository", LoggerMaker.LogDb.TESTING);
                return;
            }
            if (!pullRequestId.startsWith("refs/pull/")) {
                loggerMaker.infoAndAddToDb("Pull request id not available", LoggerMaker.LogDb.TESTING);
                return;
            }
            String[] prArray = pullRequestId.split("/");
            int pullRequestNumber = Integer.parseInt(prArray[2]);//  typical pr GITHUB_REF is refs/pull/662/merge
            GHIssue issue = ghRepository.getIssue(pullRequestNumber);
            issue.comment(message);

            List<GHCheckRun> checkRunList = ghRepository.getCheckRuns(commitSHA).toList();
            GHCheckRun ghCheckRun = null;
            for (GHCheckRun checkRun : checkRunList) {
                if ("Akto Security Checks".equals(checkRun.getName())) {
                    ghCheckRun = checkRun;
                    break;
                }
            }
            if (ghCheckRun != null) {
                if (isCompleted) {
                    ghCheckRun.update()
                            .withStatus(GHCheckRun.Status.COMPLETED)
                            .withConclusion(GHCheckRun.Conclusion.SUCCESS)
                            .withCompletedAt(new Date())
                            .add(new GHCheckRunBuilder.Output("Akto Vulnerability report", "Conclusion").withText(message))
                            .create();
                } else {
                    ghCheckRun.update()
                            .withStatus(GHCheckRun.Status.COMPLETED)
                            .withConclusion(GHCheckRun.Conclusion.FAILURE)
                            .withCompletedAt(new Date())
                            .add(new GHCheckRunBuilder.Output("Akto Vulnerability report", "Conclusion").withText(message))
                            .create();
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while publishing github comment", LoggerMaker.LogDb.TESTING);
        }
    }

    private static Map<String, String> getShortNameVsDisplayNameMap () {
        Map<String, String> namesMap = new HashMap<>();
        for (GlobalEnums.TestCategory category :  GlobalEnums.TestCategory.values()) {
            namesMap.put(category.getName(), category.getDisplayName());
        }
        return namesMap;
    }

    private static void buildCommentForCompletedTest(TestingRunResultSummary testingRunResultSummary, StringBuilder messageStringBuilder) {
        TestingRun testingRun = TestingRunDao.instance.findOne(Filters.eq(ID, testingRunResultSummary.getTestingRunId()));
        messageStringBuilder.append(GITHUB_COMMENT);
        messageStringBuilder.replace(messageStringBuilder.indexOf("@@TEST_NAME@@")
                ,messageStringBuilder.indexOf("@@TEST_NAME@@") + "@@TEST_NAME@@".length(), testingRun.getName());
        Set<String> affectedEndpoints = new HashSet<>();
        Map<String, Integer> testSuperTypeCount = new HashMap<>();

        boolean isNewTestingSummary = VulnerableTestingRunResultDao.instance.isStoredInVulnerableCollection(testingRunResultSummary.getId(), true);

        boolean fetchMore = true;
        int skip = 0;
        int limit = 1000;
        List<TestingRunResult> testingRunResultList;
        while (fetchMore) {
            if(isNewTestingSummary){
                testingRunResultList = VulnerableTestingRunResultDao.instance.findAll(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummary.getId()), skip, limit, null);
            } else{
                testingRunResultList = TestingRunResultDao.instance.findAll(Filters.and(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummary.getId()),
                    Filters.eq(TestingRunResult.VULNERABLE, true)), skip, limit, null);
            }

            if (testingRunResultList == null || testingRunResultList.isEmpty() || testingRunResultList.size() < 1000) {
                fetchMore = false;
            } else {
                skip = skip + limit;
            }
            if(testingRunResultList != null){
                for (TestingRunResult testingRunResult : testingRunResultList) {
                    String superType = testingRunResult.getTestSuperType();
                    testSuperTypeCount.merge(superType, 1, Integer::sum);
                    ApiInfo.ApiInfoKey infoKey = testingRunResult.getApiInfoKey();
                    String url = infoKey.method.name() + " " + infoKey.getUrl();
                    affectedEndpoints.add(url);
                }
                testingRunResultList.clear();
            }
        }

        StringBuilder stringBuilder = new StringBuilder();

        int count = 3;
        for (Iterator<String> it = affectedEndpoints.stream().iterator(); it.hasNext() && count > 0; ) {
            String endpoint = it.next();
            stringBuilder.append("**{/}** ").append(endpoint).append("\n");
            count--;
        }
        if (affectedEndpoints.size() > 3) {
            stringBuilder.append(" +").append(affectedEndpoints.size() - 3).append(" more").append("\n");
        }

        messageStringBuilder.replace(messageStringBuilder.indexOf("@@ENDPOINTS_AFFECTED@@")
                ,messageStringBuilder.indexOf("@@ENDPOINTS_AFFECTED@@") + "@@ENDPOINTS_AFFECTED@@".length(), stringBuilder.toString());

        Map<String, String> categoryNameMap = getShortNameVsDisplayNameMap();
        count = 3;
        stringBuilder = new StringBuilder();
        for (String superCategory : testSuperTypeCount.keySet()) {
            if (count <= 0) {
                break;
            }
            if (testSuperTypeCount.get(superCategory) > 0) {
                stringBuilder.append(categoryNameMap.get(superCategory)).append(" - _").append(testSuperTypeCount.get(superCategory)).append(" issues_").append("\n");
                count--;
            }
        }
        if (count <= 0 && stringBuilder.length() != 0) {
            stringBuilder.append(" +").append(testSuperTypeCount.keySet().size() - 3).append(" more").append("\n");
        }

        messageStringBuilder.replace(messageStringBuilder.indexOf("@@ISSUES_FOUND@@")
                ,messageStringBuilder.indexOf("@@ISSUES_FOUND@@") + "@@ISSUES_FOUND@@".length(), stringBuilder.toString());

        Map<String, Integer> countIssues =  testingRunResultSummary.getCountIssues();
        for (String severity : countIssues.keySet()) {
            switch (severity) {
                case "CRITICAL":
                    messageStringBuilder.replace(messageStringBuilder.indexOf("@@CRITICAL_COUNT@@")
                            ,messageStringBuilder.indexOf("@@CRITICAL_COUNT@@") + "@@CRITICAL_COUNT@@".length(), String.valueOf(countIssues.get(severity)));
                    break;
                case "HIGH":
                    messageStringBuilder.replace(messageStringBuilder.indexOf("@@HIGH_COUNT@@")
                            ,messageStringBuilder.indexOf("@@HIGH_COUNT@@") + "@@HIGH_COUNT@@".length(), String.valueOf(countIssues.get(severity)));
                    break;
                case "MEDIUM":
                    messageStringBuilder.replace(messageStringBuilder.indexOf("@@MEDIUM_COUNT@@")
                            ,messageStringBuilder.indexOf("@@MEDIUM_COUNT@@") + "@@MEDIUM_COUNT@@".length(), String.valueOf(countIssues.get(severity)));
                    break;
                case "LOW":
                    messageStringBuilder.replace(messageStringBuilder.indexOf("@@LOW_COUNT@@")
                            ,messageStringBuilder.indexOf("@@LOW_COUNT@@") + "@@LOW_COUNT@@".length(), String.valueOf(countIssues.get(severity)));
                    break;
            }
        }


    }

    private static final String GITHUB_COMMENT = "### **Test on @@TEST_NAME@@ summary:**\n" +
            "**Issues:**\n" +
            "![High](https://akto-web-assets.s3.ap-south-1.amazonaws.com/assets/high.png 'High') @@HIGH_COUNT@@ High  \n" +
            "![High](https://akto-web-assets.s3.ap-south-1.amazonaws.com/assets/medium.png 'High') @@MEDIUM_COUNT@@ Medium  \n" +
            "![High](https://akto-web-assets.s3.ap-south-1.amazonaws.com/assets/low.png 'High') @@LOW_COUNT@@ Low \n" +
            "\n" +
            "**Vulnerability Type**\n" +
            "\n" +
            "@@ISSUES_FOUND@@" +
            "\n" +
            "**Endpoints Affected**\n" +
            "\n" +
            "@@ENDPOINTS_AFFECTED@@" +
            "\n" +
            "\n" +
            "See full details on Akto";
}

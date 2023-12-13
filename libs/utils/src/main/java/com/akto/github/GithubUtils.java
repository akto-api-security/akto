package com.akto.github;

import com.akto.dao.AccountSettingsDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.log.LoggerMaker;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.lang.RuntimeEnvironment;
import org.kohsuke.github.*;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.akto.dao.AccountSettingsDao.generateFilter;

public class GithubUtils {

    private GithubUtils() {}

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
        String jwtToken;
        try {
            Map<String, String> metaData = testingRunResultSummary.getMetadata();
            String repository = metaData.get("repository");
            String commitSHA = metaData.get("commit_sha_head");
            jwtToken = createJWT(githubAppId,privateKey, 10 * 60 * 1000);
            GitHub gitHub = new GitHubBuilder().withJwtToken(jwtToken).build();
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
            GitHub githubAccount =  new GitHubBuilder().withAppInstallationToken(token.getToken())
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
        try {
            Map<String, String> metaData = testingRunResultSummary.getMetadata();
            String repository = metaData.get("repository");
            String pullRequestId = metaData.get("pull_request_id");
            String commitSHA = metaData.get("commit_sha_head");
            boolean isCompleted = testingRunResultSummary.getState() == TestingRun.State.COMPLETED;
            StringBuilder messageStringBuilder = new StringBuilder();
            if (isCompleted) {
                Map<String, Integer> countIssues =  testingRunResultSummary.getCountIssues();
                messageStringBuilder.append("Akto vulnerability report\n");
                for (String severity : countIssues.keySet()) {
                    messageStringBuilder.append(severity).append(" - ").append(countIssues.get(severity)).append("\n");
                }
            } else {
                messageStringBuilder.append("Akto CI/CD test is currently in ").append(testingRunResultSummary.getState().name()).append(" state");
            }
            String message = messageStringBuilder.toString();
            //JWT Token creation for github app
            String jwtToken = GithubUtils.createJWT(githubAppId,privateKey, 10 * 60 * 1000);

            //Github app invocation
            GitHub gitHub = new GitHubBuilder().withJwtToken(jwtToken).build();
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
            GitHub githubAccount =  new GitHubBuilder().withAppInstallationToken(token.getToken())
                    .build();

            GHRepository ghRepository = githubAccount.getRepository(repository);
            if (ghRepository == null) {
                loggerMaker.infoAndAddToDb("Github app doesn't have access to repository", LoggerMaker.LogDb.TESTING);
                return;
            }
            if (pullRequestId == null || !pullRequestId.startsWith("refs/pull/")) {
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
}

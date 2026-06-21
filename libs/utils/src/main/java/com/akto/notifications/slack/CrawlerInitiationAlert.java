package com.akto.notifications.slack;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.List;

public class CrawlerInitiationAlert extends SlackAlerts {
    private final String userEmail;
    private final String hostname;
    private final String moduleName;
    private final Integer collectionId;
    private final String collectionName;
    private final Integer crawlingTime;
    private final String outscopeUrls;
    private final String crawlId;
    private final int timestamp;
    private final String username;
    private final boolean hasAuth;
    // Non-null only when a test role was configured but failed to authenticate; the crawl then runs unauthenticated.
    private final String testRoleError;

    public CrawlerInitiationAlert(
            String userEmail,
            String hostname,
            String moduleName,
            Integer collectionId,
            String collectionName,
            Integer crawlingTime,
            String outscopeUrls,
            String crawlId,
            int timestamp,
            String username,
            boolean hasAuth,
            String testRoleError) {
        super(SlackAlertType.CRAWLER_INITIATION_ALERT);
        this.userEmail = userEmail;
        this.hostname = hostname;
        this.moduleName = moduleName;
        this.collectionId = collectionId;
        this.collectionName = collectionName;
        this.crawlingTime = crawlingTime;
        this.outscopeUrls = outscopeUrls;
        this.crawlId = crawlId;
        this.timestamp = timestamp;
        this.username = username;
        this.hasAuth = hasAuth;
        this.testRoleError = testRoleError;
    }

    @Override
    public String toJson() {
        BasicDBList blocks = new BasicDBList();

        blocks.add(createHeader("🕷️ New Crawler Initiated"));

        // Mentions
        blocks.add(createTextSection("<@U06MQ667K5G> <@U01U1NUG8D9>"));

        if (testRoleError != null && !testRoleError.isEmpty()) {
            blocks.add(createTextSection(
                "⚠️ *Test role authentication failed* — the crawl is running *unauthenticated*. Reason: " + testRoleError));
        }

        blocks.add(createDivider());

        List<FieldsModel> mainFields = new ArrayList<>();
        mainFields.add(new FieldsModel("*Initiated By*", userEmail));
        mainFields.add(new FieldsModel("*Target Hostname*", hostname));
        mainFields.add(new FieldsModel("*DAST Module*", moduleName));
        mainFields.add(new FieldsModel("*Crawl ID*", crawlId));
        blocks.add(createFieldSection(mainFields));

        blocks.add(createDivider());

        List<FieldsModel> configFields = new ArrayList<>();
        configFields.add(new FieldsModel("*Collection*",
            collectionName != null ? collectionName + " (ID: " + collectionId + ")" : "ID: " + collectionId));
        configFields.add(new FieldsModel("*Crawling Duration*",
            crawlingTime + " seconds"));
        configFields.add(new FieldsModel("*Authentication*",
            hasAuth ? "✅ Configured" + (username != null ? " (Username: " + username + ")" : "") : "❌ None"));
        configFields.add(new FieldsModel("*Out-of-Scope URLs*",
            outscopeUrls != null && !outscopeUrls.isEmpty() ? outscopeUrls : "None"));
        blocks.add(createFieldSection(configFields));

        blocks.add(createTextContext("Started at <!date^" + timestamp + "^{date_num} {time_secs}|" + timestamp + ">"));

        BasicDBObject ret = new BasicDBObject("blocks", blocks);
        ret.append("text", "New crawler initiated by " + userEmail + " for " + hostname);

        return ret.toJson();
    }
}

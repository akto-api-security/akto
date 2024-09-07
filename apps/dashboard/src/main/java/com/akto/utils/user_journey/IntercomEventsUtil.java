package com.akto.utils.user_journey;

import com.akto.action.EventMetricsAction;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.events.EventsMetrics;
import com.akto.dto.user_journey.UserJourneyEvents;
import com.akto.dto.user_journey.UserJourneyEvents.EventInfo;
import com.akto.log.LoggerMaker;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import io.intercom.api.Event;
import io.intercom.api.Intercom;
import io.intercom.api.User;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.*;

public class IntercomEventsUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(IntercomEventsUtil.class, LoggerMaker.LogDb.DASHBOARD);

    public static void userSignupEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getUserSignup() == null ||
                !accountSettings.getUserJourneyEvents().getUserSignup().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.USER_SIGNUP);
            UserJourneyStrategy strategy = new UserJourneyStrategy.UserSignup();
            strategy.sendToIntercom(true);
        }

    }

    public static void teamInviteSentEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getTeamInviteSent() == null ||
                !accountSettings.getUserJourneyEvents().getTeamInviteSent().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.TEAM_INVITE_SENT);
        }
    }

    public static void trafficConnectorAddedEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getTrafficConnectorAdded() == null ||
                !accountSettings.getUserJourneyEvents().getTrafficConnectorAdded().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.TRAFFIC_CONNECTOR_ADDED);
        }
    }

    public static void inventorySummaryEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getInventorySummary() == null ||
                !accountSettings.getUserJourneyEvents().getInventorySummary().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.INVENTORY_SUMMARY);
        }
    }

    public static void firstTestRunEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getFirstTestRun() == null ||
                !accountSettings.getUserJourneyEvents().getFirstTestRun().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.FIRST_TEST_RUN);
        }
    }

    public static void authTokenSetupEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getAuthTokenSetup() == null ||
                !accountSettings.getUserJourneyEvents().getAuthTokenSetup().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.AUTH_TOKEN_SETUP);
        }
    }

    public static void issuesPageEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getIssuesPage() == null ||
                !accountSettings.getUserJourneyEvents().getIssuesPage().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.ISSUES_PAGE);
        }
    }

    public static void jiraIntegratedEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getJiraIntegrated() == null ||
                !accountSettings.getUserJourneyEvents().getJiraIntegrated().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.JIRA_INTEGRATED);
        }
    }

    public static void reportExportedEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getReportExported() == null ||
                !accountSettings.getUserJourneyEvents().getReportExported().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.REPORT_EXPORTED);
        }
    }

    public static void cicdScheduleTestsEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getCicdScheduledTests() == null ||
                !accountSettings.getUserJourneyEvents().getCicdScheduledTests().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.CICD_SCHEDULED_TESTS);
        }
    }

    public static void firstCustomTemplateCreatedEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getFirstCustomTemplateCreated() == null ||
                !accountSettings.getUserJourneyEvents().getFirstCustomTemplateCreated().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.FIRST_CUSTOM_TEMPLATE_CREATED);
        }
    }

    public static void customTemplateUnusedEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getCustomTemplateUnused() == null ||
                !accountSettings.getUserJourneyEvents().getCustomTemplateUnused().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.CUSTOM_TEMPLATE_UNUSED);
        }
    }

    public static void slackIntegrationEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getSlackIntegration() == null ||
                !accountSettings.getUserJourneyEvents().getSlackIntegration().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.SLACK_INTEGRATION);
        }
    }

    public static void pocEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getPocCompleted() == null ||
                !accountSettings.getUserJourneyEvents().getPocCompleted().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.POC_COMPLETED);
        }
    }

    public static void accountBlockedEvent() {
        AccountSettings accountSettings = getAccountSettings();

        if(accountSettings.getUserJourneyEvents() == null || accountSettings.getUserJourneyEvents().getAccountBlocked() == null ||
                !accountSettings.getUserJourneyEvents().getAccountBlocked().isShouldTriggerEvent()) {
            updateShouldTriggerFlag(UserJourneyEvents.ACCOUNT_BLOCKED);
        }
    }


    public static void sendEvent(Map<String, Object> metaData, String eventName, boolean eventFlag) {
        try {
            Intercom.setToken(System.getenv("INTERCOM_TOKEN"));
            int timeNow = Context.now();

            BasicDBList allUsers = UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get());
            for(Object obj: allUsers) {
                BasicDBObject userObj = (BasicDBObject) obj;
                String email = userObj.getString("login");

                Event event = new Event()
                        .setEventName(eventName)
                        .setCreatedAt(Context.now())
                        .setEmail(email);

                String name = email.split("@")[0];
                name = name.replaceAll("[^a-zA-Z0-9]", " ");
                name = StringUtils.capitalize(StringUtils.join(name.split("\\s+"), " "));

                if(metaData == null || !eventFlag) {
                    metaData = new HashMap<>();
                }

                metaData.put("user_name", name);
                metaData.put("sendEmail", eventFlag);

                User intercomUser = EventMetricsAction.findUserInIntercom(email);

                if(intercomUser == null) {
                    EventsMetrics.createUserInIntercom(email, name, timeNow);
                } else {
                    intercomUser.setUpdateLastRequestAt(true);
                    User.update(intercomUser);
                }

                event.setMetadata(metaData);

                // Sending intercom event
                Event.create(event);
            }

            // Updating event in DB
            AccountSettingsDao.instance.getMCollection().updateOne(
                    AccountSettingsDao.generateFilter(),
                    Updates.set(
                            (AccountSettings.USER_JOURNEY_EVENTS + "." + eventName + "." + EventInfo.IS_EVENT_SENT), eventFlag
                    )
            );
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("Error while sending intercom event: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
    }

    private static void updateShouldTriggerFlag(String eventName) {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(
                        (AccountSettings.USER_JOURNEY_EVENTS + "." + eventName + "." + EventInfo.SHOULD_TRIGGER_EVENT), true
                )
        );
    }

    private static AccountSettings getAccountSettings() {
        Bson projection = Projections.include(AccountSettings.USER_JOURNEY_EVENTS);
        return AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter(), projection);
    }

}

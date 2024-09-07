package com.akto.utils.user_journey;

import com.akto.action.observe.InventoryAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.user_journey.UserJourneyEvents;
import com.akto.listener.RuntimeListener;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import java.util.*;

public interface UserJourneyStrategy {
    void sendToIntercom(boolean eventFlag);
    boolean[] canSendEvent(UserJourneyEvents userJourneyEvents);
    
    enum UserJourneyHierarchy {
        USER_SIGNUP(UserJourneyEvents.USER_SIGNUP, 0, 1, new UserSignup()),
        TEAM_INVITE_SENT(UserJourneyEvents.TEAM_INVITE_SENT, 0, 2, new TeamInviteSend()),
        TRAFFIC_CONNECTOR_ADDED(UserJourneyEvents.TRAFFIC_CONNECTOR_ADDED, 0, 3, new TrafficConnectorAdded()),
        INVENTORY_SUMMARY(UserJourneyEvents.INVENTORY_SUMMARY, 0, 4, new InventorySummary()),
        FIRST_TEST_RUN(UserJourneyEvents.FIRST_TEST_RUN, 0, 5, new FirstTestRun()),
        AUTH_TOKEN_SETUP(UserJourneyEvents.AUTH_TOKEN_SETUP, 0, 6, new AuthTokenSetup()),
        ISSUES_PAGE(UserJourneyEvents.ISSUES_PAGE, 0, 7, new IssuesPage()),
        JIRA_INTEGRATED(UserJourneyEvents.JIRA_INTEGRATED, 0, 8, new JiraIntegrated()),
        REPORT_EXPORTED(UserJourneyEvents.REPORT_EXPORTED, 0, 9, new ReportExported()),
        CICD_SCHEDULED_TESTS(UserJourneyEvents.CICD_SCHEDULED_TESTS, 0, 10, new CicdScheduledTests()),
        FIRST_CUSTOM_TEMPLATE_CREATED(UserJourneyEvents.FIRST_CUSTOM_TEMPLATE_CREATED, 0, 11, new FirstCustomTemplateCreated()),
        CUSTOM_TEMPLATE_UNUSED(UserJourneyEvents.CUSTOM_TEMPLATE_UNUSED, 0, 12, new CustomTemplateUnused()),
        SLACK_INTEGRATION(UserJourneyEvents.SLACK_INTEGRATION, 0, 13, new SlackIntegration()),
        POC_COMPLETED(UserJourneyEvents.POC_COMPLETED, 0, 14, new PocCompleted()),
        ACCOUNT_BLOCKED(UserJourneyEvents.ACCOUNT_BLOCKED, 0, 15, new AccountBlocked());

        public static final int MAX_RETRIES = 2;

        private final String name;
        private int retryCount;
        private final int hierarchyLevel;
        private final UserJourneyStrategy strategy;

        UserJourneyHierarchy(String name, int retryCount, int hierarchyLevel, UserJourneyStrategy strategy) {
            this.name = name;
            this.retryCount = retryCount;
            this.hierarchyLevel = hierarchyLevel;
            this.strategy = strategy;
        }

        public String getName() {
            return name;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount() {
            this.retryCount++;
        }

        public int getHierarchyLevel() {
            return hierarchyLevel;
        }
        
        public UserJourneyStrategy getStrategy() {
            return strategy;
        }
    }

    class UserSignup implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.USER_SIGNUP, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getUserSignup() == null) return null;
            return new boolean[] {userJourneyEvents.getUserSignup().isShouldTriggerEvent(), userJourneyEvents.getUserSignup().isEventSent()};
        }
    }

    class TeamInviteSend implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.TEAM_INVITE_SENT, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getTeamInviteSent() == null) return null;
            return new boolean[] {userJourneyEvents.getTeamInviteSent().isShouldTriggerEvent(), userJourneyEvents.getTeamInviteSent().isEventSent()};
        }
    }

    class TrafficConnectorAdded implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.TRAFFIC_CONNECTOR_ADDED, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getTrafficConnectorAdded() == null) return null;
            return new boolean[] {userJourneyEvents.getTrafficConnectorAdded().isShouldTriggerEvent(), userJourneyEvents.getTrafficConnectorAdded().isEventSent()};
        }
    }

    class InventorySummary implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            int endTimestamp = Context.now();
            int startTimestamp = endTimestamp - 24 * 60 * 60;

            ArrayList<Integer> demos = new ArrayList<>();
            demos.add(0);
            demos.add(RuntimeListener.VULNERABLE_API_COLLECTION_ID);
            demos.add(RuntimeListener.LLM_API_COLLECTION_ID);
            Bson projections = Projections.include(ApiCollection.ID, ApiCollection.AUTOMATED);
            Bson filter = Filters.and(
                    Filters.nin(ApiCollection.ID, demos),
                    Filters.in(ApiCollection.AUTOMATED, false),
                    Filters.ne(ApiCollection.NAME, "juice_shop_demo")
            );

            List<ApiCollection> apiCollectionsList = ApiCollectionsDao.instance.findAll(filter, projections);

            Set<Integer> apiCollectionIdSet = new HashSet<>();
            for(ApiCollection apiCollection : apiCollectionsList) {
                apiCollectionIdSet.add(apiCollection.getId());
            }

            // Fetch recent endpoints
            InventoryAction inventoryAction = new InventoryAction();
            inventoryAction.setEndTimestamp(endTimestamp);
            inventoryAction.setStartTimestamp(startTimestamp);
            inventoryAction.loadRecentEndpoints();


            BasicDBObject response = inventoryAction.getResponse();

            BasicDBObject data = (BasicDBObject) response.get("data");
            List<BasicDBObject> endpoints = (List<BasicDBObject>) data.get("endpoints");
            List<ApiInfo> apiInfoList = (List<ApiInfo>) data.get("apiInfoList");

            //  ApiID     RiskScore LastTested
            Map<Integer, Pair<Float, Integer>> apiInfoPairMap = new HashMap<>();
            for(ApiInfo apiInfo : apiInfoList) {
                if(!apiCollectionIdSet.contains(apiInfo.getId().getApiCollectionId())) continue;
                apiInfoPairMap.put(apiInfo.getId().getApiCollectionId(), new Pair<>(apiInfo.getRiskScore(), apiInfo.getLastTested()));
            }

            int numberOfCollections = apiCollectionsList.size();
            int totalApis = 0;
            int criticalApis = 0;
            int totalSensitiveData;
            double avgRiskScore = 0;
            double testCoverage = 0;

            long thirtyDaysAgo = Context.now() - 30 * 24 * 60 * 60;

            for(BasicDBObject endpoint : endpoints) {
                BasicDBObject _id = (BasicDBObject) endpoint.get("_id");
                int apiCollectionId = (int) _id.get("apiCollectionId");

                if(apiCollectionIdSet.contains(apiCollectionId)) {
                    Pair<Float, Integer> riskScoreToLastTested = apiInfoPairMap.get(apiCollectionId);
                    Float riskScore = riskScoreToLastTested.getFirst();
                    Integer lastTested = riskScoreToLastTested.getSecond();

                    totalApis++;
                    criticalApis += riskScore >= 4.0 ? 1 : 0;
                    avgRiskScore += riskScore;
                    testCoverage += lastTested >= thirtyDaysAgo ? 1 : 0;
                }
            }

            if(totalApis > 0) {
                avgRiskScore = avgRiskScore / (double) totalApis;
                testCoverage = testCoverage / (double) totalApis * 100.0;
            } else {
                avgRiskScore = 0;
                testCoverage = 0;
            }


            List<String> sensitiveSubtypes = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
            sensitiveSubtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
            totalSensitiveData = SingleTypeInfoDao.instance.getSensitiveApisCount(sensitiveSubtypes, true, Filters.in(SingleTypeInfo._API_COLLECTION_ID, apiCollectionIdSet));

            Map<String, Object> metaData = new HashMap<>();
            metaData.put("number_of_collections", numberOfCollections);
            metaData.put("total_apis", totalApis);
            metaData.put("sensitive_data", totalSensitiveData);
            metaData.put("critical_APIs", criticalApis);
            // metaData.put("api_changes", 0); // TODO("implement this feature")
            metaData.put("avg_risk_score", avgRiskScore);
            metaData.put("test_coverage", testCoverage+"%");

            IntercomEventsUtil.sendEvent(metaData, UserJourneyEvents.INVENTORY_SUMMARY, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getInventorySummary() == null) return null;
            return new boolean[] {userJourneyEvents.getInventorySummary().isShouldTriggerEvent(), userJourneyEvents.getInventorySummary().isEventSent()};
        }
    }

    class FirstTestRun implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            Map<String, Object> metaData = null;
            if(!eventFlag) {
                metaData = new HashMap<>();
                metaData.put("cta_link", "https://app.akto.io/dashboard/testing");
            }
            IntercomEventsUtil.sendEvent(metaData, UserJourneyEvents.FIRST_TEST_RUN, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getFirstTestRun() == null) return null;
            return new boolean[] {userJourneyEvents.getFirstTestRun().isShouldTriggerEvent(), userJourneyEvents.getFirstTestRun().isEventSent()};
        }
    }

    class AuthTokenSetup implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.AUTH_TOKEN_SETUP, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getAuthTokenSetup() == null) return null;
            return new boolean[] {userJourneyEvents.getAuthTokenSetup().isShouldTriggerEvent(), userJourneyEvents.getAuthTokenSetup().isEventSent()};
        }
    }

    class IssuesPage implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.ISSUES_PAGE, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getIssuesPage() == null) return null;
            return new boolean[] {userJourneyEvents.getIssuesPage().isShouldTriggerEvent(), userJourneyEvents.getIssuesPage().isEventSent()};
        }
    }

    class JiraIntegrated implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.JIRA_INTEGRATED, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getJiraIntegrated() == null) return null;
            return new boolean[] {userJourneyEvents.getJiraIntegrated().isShouldTriggerEvent(), userJourneyEvents.getJiraIntegrated().isEventSent()};
        }
    }

    class ReportExported implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.REPORT_EXPORTED, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getReportExported() == null) return null;
            return new boolean[] {userJourneyEvents.getReportExported().isShouldTriggerEvent(), userJourneyEvents.getReportExported().isEventSent()};
        }
    }

    class CicdScheduledTests implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.CICD_SCHEDULED_TESTS, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getCicdScheduledTests() == null) return null;
            return new boolean[] {userJourneyEvents.getCicdScheduledTests().isShouldTriggerEvent(), userJourneyEvents.getCicdScheduledTests().isEventSent()};
        }
    }

    class FirstCustomTemplateCreated implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            Map<String, Object> metaData = null;
            if(eventFlag) {
                Bson projections = Projections.include("_id");
                YamlTemplate yamlTemplate = YamlTemplateDao.instance.findOne(Filters.eq(YamlTemplate.SOURCE, GlobalEnums.YamlTemplateSource.CUSTOM), projections);

                if(yamlTemplate != null) {
                    metaData = new HashMap<>();
                    metaData.put("cta_link", "https://app.akto.io/dashboard/test-editor/" + yamlTemplate.getId());
                }
            }
            IntercomEventsUtil.sendEvent(metaData, UserJourneyEvents.FIRST_CUSTOM_TEMPLATE_CREATED, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getFirstCustomTemplateCreated() == null) return null;
            return new boolean[] {userJourneyEvents.getFirstCustomTemplateCreated().isShouldTriggerEvent(), userJourneyEvents.getFirstCustomTemplateCreated().isEventSent()};
        }
    }

    class CustomTemplateUnused implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            Map<String, Object> metaData = null;
            if(eventFlag) {
                Bson projections = Projections.include("_id");
                YamlTemplate yamlTemplate = YamlTemplateDao.instance.findOne(Filters.eq(YamlTemplate.SOURCE, GlobalEnums.YamlTemplateSource.CUSTOM), projections);

                if(yamlTemplate != null) {
                    metaData = new HashMap<>();
                    metaData.put("cta_link", "https://app.akto.io/dashboard/test-editor/" + yamlTemplate.getId());
                }
            }
            IntercomEventsUtil.sendEvent(metaData, UserJourneyEvents.CUSTOM_TEMPLATE_UNUSED, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getCustomTemplateUnused() == null) return null;
            return new boolean[] {userJourneyEvents.getCustomTemplateUnused().isShouldTriggerEvent(), userJourneyEvents.getCustomTemplateUnused().isEventSent()};
        }
    }

    class SlackIntegration implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.SLACK_INTEGRATION, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getSlackIntegration() == null) return null;
            return new boolean[] {userJourneyEvents.getSlackIntegration().isShouldTriggerEvent(), userJourneyEvents.getSlackIntegration().isEventSent()};
        }
    }

    class PocCompleted implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.POC_COMPLETED, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getPocCompleted() == null) return null;
            return new boolean[] {userJourneyEvents.getPocCompleted().isShouldTriggerEvent(), userJourneyEvents.getPocCompleted().isEventSent()};
        }
    }

    class AccountBlocked implements UserJourneyStrategy {
        @Override
        public void sendToIntercom(boolean eventFlag) {
            IntercomEventsUtil.sendEvent(null, UserJourneyEvents.ACCOUNT_BLOCKED, eventFlag);
        }

        @Override
        public boolean[] canSendEvent(UserJourneyEvents userJourneyEvents) {
            if(userJourneyEvents.getAccountBlocked() == null) return null;
            return new boolean[] {userJourneyEvents.getAccountBlocked().isShouldTriggerEvent(), userJourneyEvents.getAccountBlocked().isEventSent()};
        }
    }
}


package com.akto.dao;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.SingleTypeInfo.SubType;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;
import org.junit.Test;

public class DemoData extends DaoConnect {
    BasicDBObject defaultCollKey = new BasicDBObject("_id", 0);

    private void updateDB(Map<String, String> origToNewURL) throws IOException {
        Set<String> newUrls = new HashSet<>();
        newUrls.addAll(origToNewURL.values());

        for(String newUrl: origToNewURL.values()) {
            System.out.println("newurl: " + newUrl);
        }

        ApiCollectionsDao.instance.updateOne(defaultCollKey, Updates.set("urls", newUrls));
        updateSingleTypeInfos(origToNewURL);

        updateSwaggerFile("/Users/ankushjain/Downloads/metrics.json");

        updateApiInfo(origToNewURL);

        updateTraffic(origToNewURL);
    }

    private void updateTraffic(Map<String, String> origToNewURL) {

        for(String newUrlAndMethod: origToNewURL.keySet()) {
            Method method = Method.fromString(newUrlAndMethod.split(" ")[1]);
            String url = newUrlAndMethod.split(" ")[0];
            TrafficInfoDao.instance.deleteAll(Filters.eq("_id.url", url));

            for(int m = 0 ; m < 4; m++) {
                int bucketStartEpoch = 633 + m;
                Bson[] updates = new Bson[24 * 30];

                for(int i = 0; i < 24 * 30; i ++) {
                    updates[i] = Updates.set("mapHoursToCount."+ (455839 + m * 24 * 30 + i), 1000 + (int) (Math.random() * 100 * ((url.hashCode() % 10) + 1)) );
                }


                TrafficInfoDao.instance.updateOne(
                    Filters.and(
                        Filters.eq("_id.url", url),
                        Filters.eq("_id.apiCollectionId", 0),
                        Filters.eq("_id.responseCode", -1),
                        Filters.eq("_id.method", method),
                        Filters.eq("_id.bucketStartEpoch", bucketStartEpoch),
                        Filters.eq("_id.bucketEndEpoch", bucketStartEpoch + 1)
                    ),
                    Updates.combine(updates)
                );
            }

        }
    }

    private void updateApiInfo(Map<String, String> origToNewURL) {

        for(String newUrlAndMethod: origToNewURL.keySet()) {
            Method method = Method.fromString(newUrlAndMethod.split(" ")[1]);
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0, newUrlAndMethod.split(" ")[0], method);

            ApiInfo apiInfo = new ApiInfo(apiInfoKey);
            int lastSeen = Context.now() - (int) (Math.random() * 60 * 60);

            if (apiInfoKey.getUrl().toLowerCase().contains("sql")) {
                lastSeen = Context.now() - 60 * 60 * 24 * 61;
            }

            apiInfo.setLastSeen(lastSeen);


            Set<Set<AuthType>> parentAuth = new HashSet<>();
            HashSet<AuthType> childAuth = new HashSet<>();
            parentAuth.add(childAuth);
            AuthType[] allAuths = AuthType.values();
            int authIndex = ((apiInfoKey.url.hashCode() % allAuths.length) + allAuths.length) % allAuths.length;
            childAuth.add(allAuths[authIndex]);
            apiInfo.setAllAuthTypesFound(parentAuth);


            Set<ApiAccessType> accessSet = new HashSet<>();
            ApiAccessType[] allAccesses = ApiAccessType.values();
            int accessIndex = ((apiInfoKey.url.hashCode() % allAccesses.length) + allAccesses.length) % allAccesses.length;
            accessSet.add(allAccesses[accessIndex]);
            apiInfo.setApiAccessTypes(accessSet);
            

            ApiInfoDao.instance.getMCollection().deleteOne(Filters.eq("_id", apiInfo.getId()));
            ApiInfoDao.instance.getMCollection().insertOne(apiInfo);
        }


    }

    private void updateSwaggerFile(String filepath) throws IOException {
        String metrics = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Core\",\"version\":\"1.0\",\"description\":\"AKTO1.0\",\"contact\":{\"name\":\"AnkushJain\",\"email\":\"ankush@akto.io\"},\"license\":{\"name\":\"Restricted/Internal\"}},\"servers\":[{\"url\":\"https://staging.akto.io/\"}],\"paths\":{\"/api/today\":{\"post\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"healthyMetrics\":{\"$ref\":\"#/components/schemas/MetricsArray\"},\"needAttentionMetrics\":{\"$ref\":\"#/components/schemas/MetricsArray\"}}}}}}}}},\"/api/actionItem\":{\"post\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"metrics_info\":{\"type\":\"object\",\"properties\":{\"metric_meta_id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"priority\":{\"type\":\"integer\"},\"value\":{\"type\":\"integer\"},\"health\":{\"type\":\"integer\"}}},\"action_item\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"creationDate\":{\"type\":\"integer\"},\"dueDate\":{\"type\":\"integer\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"owner_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"priority\":{\"type\":\"integer\"},\"description\":{\"type\":\"string\"},\"additional_info\":{\"type\":\"string\"},\"comments_count\":{\"type\":\"integer\"},\"watchers_count\":{\"type\":\"integer\"},\"current_status\":{\"type\":\"integer\"},\"unread\":{\"type\":\"boolean\"},\"userMentioned\":{\"type\":\"boolean\"}}},\"watching_preference\":{\"type\":\"integer\"}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action_item_id\":{\"type\":\"string\"}}}}}}}},\"/api/addComment\":{\"post\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"comment\":{\"type\":\"string\"},\"edited_comments_history\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}},\"follow_up\":{\"type\":\"boolean\"},\"follow_up_done\":{\"type\":\"boolean\"},\"mentioned_users\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}},\"replies_count\":{\"type\":\"integer\"},\"id\":{\"type\":\"integer\"},\"parent_id\":{\"type\":\"integer\"},\"parent_type\":{\"type\":\"string\"},\"timestamp\":{\"type\":\"integer\"},\"activity_type\":{\"type\":\"string\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action_item_id\":{\"type\":\"string\"},\"parent_id\":{\"type\":\"string\"},\"comment\":{\"type\":\"string\"},\"follow_up\":{\"type\":\"string\"},\"parent_type\":{\"type\":\"string\"}}}}}}}},\"/api/getCardInsights\":{\"get\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\"},\"actionItemId\":{\"type\":\"integer\"},\"actionItems\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}},\"assignee\":{\"type\":\"string\"},\"cardId\":{\"type\":\"integer\"},\"currValue\":{\"type\":\"number\"},\"dashboardId\":{\"type\":\"integer\"},\"dashboardName\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"dueDate\":{\"type\":\"integer\"},\"endDate\":{\"type\":\"integer\"},\"favourite\":{\"type\":\"boolean\"},\"history\":{\"type\":\"boolean\"},\"insights\":{\"type\":\"object\"},\"mapMetaToName\":{\"type\":\"object\"},\"mapMetaToTrend\":{\"type\":\"object\"},\"name\":{\"type\":\"string\"},\"ownerId\":{\"type\":\"integer\"},\"ownerName\":{\"type\":\"string\"},\"priority\":{\"type\":\"integer\"},\"sourceId\":{\"type\":\"integer\"},\"startDate\":{\"type\":\"integer\"},\"status\":{\"type\":\"integer\"},\"targetPlan\":{\"type\":\"object\"},\"targetValue\":{\"type\":\"number\"},\"trackingPeriod\":{\"type\":\"integer\"},\"unit\":{\"type\":\"string\"}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"cardId\":{\"type\":\"string\"}}}}}}}},\"/api/getCardInputMetrics\":{\"get\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\"},\"actionItemId\":{\"type\":\"integer\"},\"actionItems\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}},\"assignee\":{\"type\":\"string\"},\"cardId\":{\"type\":\"integer\"},\"currValue\":{\"type\":\"number\"},\"dashboardId\":{\"type\":\"integer\"},\"dashboardName\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"dueDate\":{\"type\":\"integer\"},\"endDate\":{\"type\":\"integer\"},\"favourite\":{\"type\":\"boolean\"},\"history\":{\"type\":\"object\"},\"insights\":{\"type\":\"object\"},\"mapMetaToName\":{\"type\":\"object\"},\"mapMetaToTrend\":{\"type\":\"object\"},\"name\":{\"type\":\"string\"},\"ownerId\":{\"type\":\"integer\"},\"ownerName\":{\"type\":\"string\"},\"priority\":{\"type\":\"integer\"},\"sourceId\":{\"type\":\"integer\"},\"startDate\":{\"type\":\"integer\"},\"status\":{\"type\":\"integer\"},\"targetPlan\":{\"type\":\"object\"},\"targetValue\":{\"type\":\"number\"},\"trackingPeriod\":{\"type\":\"integer\"},\"unit\":{\"type\":\"string\"}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"cardId\":{\"type\":\"string\"}}}}}}}},\"/api/getCardActionItems\":{\"get\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\"},\"actionItemId\":{\"type\":\"integer\"},\"actionItems\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"metrics_meta_id\":{\"type\":\"integer\"},\"creationDate\":{\"type\":\"integer\"},\"dueDate\":{\"type\":\"integer\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"owner_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"priority\":{\"type\":\"integer\"},\"description\":{\"type\":\"string\"},\"additional_info\":{\"type\":\"string\"},\"comments_count\":{\"type\":\"integer\"},\"watchers_count\":{\"type\":\"integer\"},\"current_status\":{\"type\":\"integer\"},\"unread\":{\"type\":\"boolean\"},\"last_updated_date\":{\"type\":\"integer\"},\"userMentioned\":{\"type\":\"boolean\"}}},\"assignee\":{\"type\":\"string\"},\"cardId\":{\"type\":\"integer\"},\"currValue\":{\"type\":\"number\"},\"dashboardId\":{\"type\":\"integer\"},\"dashboardName\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"dueDate\":{\"type\":\"integer\"},\"endDate\":{\"type\":\"integer\"},\"favourite\":{\"type\":\"boolean\"},\"history\":{\"type\":\"object\"},\"insights\":{\"type\":\"object\"},\"mapMetaToName\":{\"type\":\"object\"},\"mapMetaToTrend\":{\"type\":\"object\"},\"name\":{\"type\":\"string\"},\"ownerId\":{\"type\":\"integer\"},\"ownerName\":{\"type\":\"string\"},\"priority\":{\"type\":\"integer\"},\"sourceId\":{\"type\":\"integer\"},\"startDate\":{\"type\":\"integer\"},\"status\":{\"type\":\"integer\"},\"targetPlan\":{\"type\":\"number\"},\"targetValue\":{\"type\":\"number\"},\"trackingPeriod\":{\"type\":\"integer\"},\"unit\":{\"type\":\"string\"}}}}}}}}},\"/api/getCardData\":{\"get\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\"},\"actionItemId\":{\"type\":\"integer\"},\"actionItems\":{\"type\":\"object\"},\"assignee\":{\"type\":\"string\"},\"cardId\":{\"type\":\"integer\"},\"currValue\":{\"type\":\"number\"},\"dashboardId\":{\"type\":\"integer\"},\"dashboardName\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"dueDate\":{\"type\":\"integer\"},\"endDate\":{\"type\":\"integer\"},\"favourite\":{\"type\":\"boolean\"},\"history\":{\"type\":\"object\"},\"insights\":{\"type\":\"object\"},\"mapMetaToName\":{\"type\":\"object\"},\"mapMetaToTrend\":{\"type\":\"object\"},\"name\":{\"type\":\"string\"},\"ownerId\":{\"type\":\"integer\"},\"ownerName\":{\"type\":\"string\"},\"priority\":{\"type\":\"integer\"},\"sourceId\":{\"type\":\"integer\"},\"startDate\":{\"type\":\"integer\"},\"status\":{\"type\":\"integer\"},\"targetPlan\":{\"type\":\"object\",\"properties\":{\"author_id\":{\"type\":\"integer\"},\"creationDate\":{\"type\":\"integer\"},\"id\":{\"type\":\"integer\"},\"last_updated_by_id\":{\"type\":\"integer\"},\"last_updated_time\":{\"type\":\"integer\"},\"targetInstanceList\":{\"type\":\"object\",\"properties\":{\"endDate\":{\"type\":\"integer\"},\"startDate\":{\"type\":\"integer\"},\"value\":{\"type\":\"number\"}}}}},\"targetValue\":{\"type\":\"number\"},\"trackingPeriod\":{\"type\":\"integer\"},\"unit\":{\"type\":\"string\"}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"cardId\":{\"type\":\"string\"}}}}}}}},\"/api/board\":{\"post\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"cards\":{\"type\":\"object\",\"properties\":{\"currValue\":{\"type\":\"number\"},\"favourite\":{\"type\":\"boolean\"},\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"},\"owner\":{\"type\":\"integer\"},\"ownerName\":{\"type\":\"string\"},\"priority\":{\"type\":\"integer\"},\"status\":{\"type\":\"integer\"},\"targetValue\":{\"type\":\"number\"},\"trackingPeriod\":{\"type\":\"integer\"},\"updateTime\":{\"type\":\"integer\"}}},\"dashboard\":{\"type\":\"object\",\"properties\":{\"author_id\":{\"type\":\"integer\"},\"cards\":{\"type\":\"integer\"},\"id\":{\"type\":\"integer\"},\"last_updated_ts\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"},\"orgAccessType\":{\"type\":\"string\"},\"owner_id\":{\"type\":\"integer\"},\"teamAccessType\":{\"type\":\"string\"},\"teamId\":{\"type\":\"integer\"}}},\"id\":{\"type\":\"integer\"},\"listCards\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"},\"ownerName\":{\"type\":\"string\"},\"owner\":{\"type\":\"integer\"},\"currValue\":{\"type\":\"number\"},\"targetValue\":{\"type\":\"number\"},\"status\":{\"type\":\"integer\"},\"favourite\":{\"type\":\"boolean\"},\"trackingPeriod\":{\"type\":\"integer\"},\"pendingActionItems\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"metrics_meta_id\":{\"type\":\"integer\"},\"creationDate\":{\"type\":\"integer\"},\"dueDate\":{\"type\":\"integer\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"owner_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"priority\":{\"type\":\"integer\"},\"description\":{\"type\":\"string\"},\"additional_info\":{\"type\":\"string\"},\"comments_count\":{\"type\":\"integer\"},\"watchers_count\":{\"type\":\"integer\"},\"current_status\":{\"type\":\"integer\"},\"unread\":{\"type\":\"boolean\"},\"last_updated_date\":{\"type\":\"integer\"},\"userMentioned\":{\"type\":\"boolean\"}}}}},\"orgAccessType\":{\"type\":\"string\"},\"teamAccessType\":{\"type\":\"string\"},\"teamId\":{\"type\":\"integer\"},\"usersInfoMap\":{\"type\":\"object\"}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}}}}}},\"/api/toggleFavourite\":{\"post\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"parent_id\":{\"type\":\"integer\"},\"preference\":{\"type\":\"integer\"},\"type\":{\"type\":\"string\"},\"watchers\":{\"type\":\"string\"},\"watchersInfo\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action_item_id\":{\"type\":\"string\"},\"preference\":{\"type\":\"string\"},\"parent_id\":{\"type\":\"string\"},\"type\":{\"type\":\"string\"}}}}}}}},\"/api/saveNewActionItem\":{\"post\":{\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"owner_id\":{\"type\":\"string\"},\"metricsMetaId\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"assignee\":{\"type\":\"string\"},\"priority\":{\"type\":\"string\"},\"dueDate\":{\"type\":\"string\"},\"follow_up_dates\":{\"type\":\"string\"},\"additional_info\":{\"type\":\"string\"}}}}}},\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"creationDate\":{\"type\":\"integer\"},\"dueDate\":{\"type\":\"integer\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"owner_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"priority\":{\"type\":\"integer\"},\"description\":{\"type\":\"string\"},\"additional_info\":{\"type\":\"string\"},\"comments_count\":{\"type\":\"integer\"},\"watchers_count\":{\"type\":\"integer\"},\"current_status\":{\"type\":\"integer\"},\"unread\":{\"type\":\"boolean\"},\"userMentioned\":{\"type\":\"boolean\"}}}}}}}}},\"/api/editComment\":{\"post\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"comment\":{\"type\":\"string\"},\"edited_comments_history\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}},\"follow_up\":{\"type\":\"boolean\"},\"follow_up_done\":{\"type\":\"boolean\"},\"mentioned_users\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}},\"replies_count\":{\"type\":\"integer\"},\"id\":{\"type\":\"integer\"},\"parent_id\":{\"type\":\"integer\"},\"parent_type\":{\"type\":\"string\"},\"timestamp\":{\"type\":\"integer\"},\"activity_type\":{\"type\":\"string\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}}}}}}},\"401\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\"}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"comment_id\":{\"type\":\"string\"},\"comment\":{\"type\":\"string\"}}}}}}}},\"/api/getCardTrend\":{\"get\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\"},\"actionItemId\":{\"type\":\"integer\"},\"actionItems\":{\"type\":\"object\"},\"assignee\":{\"type\":\"string\"},\"cardId\":{\"type\":\"integer\"},\"currValue\":{\"type\":\"number\"},\"dashboardId\":{\"type\":\"integer\"},\"dashboardName\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"dueDate\":{\"type\":\"integer\"},\"endDate\":{\"type\":\"integer\"},\"favourite\":{\"type\":\"boolean\"},\"history\":{\"type\":\"object\"},\"insights\":{\"type\":\"object\"},\"mapMetaToName\":{\"type\":\"object\"},\"mapMetaToTrend\":{\"type\":\"object\"},\"name\":{\"type\":\"string\"},\"ownerId\":{\"type\":\"integer\"},\"ownerName\":{\"type\":\"string\"},\"priority\":{\"type\":\"integer\"},\"sourceId\":{\"type\":\"integer\"},\"startDate\":{\"type\":\"integer\"},\"status\":{\"type\":\"integer\"},\"targetPlan\":{\"type\":\"object\",\"properties\":{\"author_id\":{\"type\":\"integer\"},\"creationDate\":{\"type\":\"integer\"},\"id\":{\"type\":\"integer\"},\"last_updated_by_id\":{\"type\":\"integer\"},\"last_updated_time\":{\"type\":\"integer\"},\"targetInstanceList\":{\"type\":\"object\",\"properties\":{\"endDate\":{\"type\":\"integer\"},\"startDate\":{\"type\":\"integer\"},\"value\":{\"type\":\"number\"}}}}},\"targetValue\":{\"type\":\"number\"},\"trackingPeriod\":{\"type\":\"integer\"},\"unit\":{\"type\":\"string\"}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"field\":{\"type\":\"string\"},\"value\":{\"type\":\"string\"}}}}}}}},\"/api/userActionItems\":{\"post\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"creationDate\":{\"type\":\"integer\"},\"dueDate\":{\"type\":\"integer\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"owner_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"priority\":{\"type\":\"integer\"},\"description\":{\"type\":\"string\"},\"additional_info\":{\"type\":\"string\"},\"comments_count\":{\"type\":\"integer\"},\"watchers_count\":{\"type\":\"integer\"},\"current_status\":{\"type\":\"integer\"},\"unread\":{\"type\":\"boolean\"},\"userMentioned\":{\"type\":\"boolean\"}}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"owner_id\":{\"type\":\"integer\"}}}}}}}},\"/api/updateActionItem\":{\"post\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"field\":{\"type\":\"string\"},\"delta\":{\"type\":\"object\",\"properties\":{\"old_value\":{\"type\":\"string\"},\"new_value\":{\"type\":\"string\"}}},\"id\":{\"type\":\"integer\"},\"parent_id\":{\"type\":\"integer\"},\"parent_type\":{\"type\":\"string\"},\"timestamp\":{\"type\":\"integer\"},\"activity_type\":{\"type\":\"string\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}}}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action_item_id\":{\"type\":\"integer\"},\"data\":{\"type\":\"object\",\"properties\":{\"field\":{\"type\":\"string\"},\"value\":{\"type\":\"string\"}}}}}}}}}},\"/api/actionItemActivities\":{\"post\":{\"responses\":{\"200\":{\"description\":\"description\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"comment\":{\"type\":\"string\"},\"edited_comments_history\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}},\"follow_up\":{\"type\":\"boolean\"},\"follow_up_done\":{\"type\":\"boolean\"},\"mentioned_users\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}},\"replies_count\":{\"type\":\"integer\"},\"id\":{\"type\":\"integer\"},\"parent_id\":{\"type\":\"integer\"},\"parent_type\":{\"type\":\"string\"},\"timestamp\":{\"type\":\"integer\"},\"activity_type\":{\"type\":\"string\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}}}}}}}}},\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"action_item_id\":{\"type\":\"integer\"}}}}}}}}},\"components\":{\"schemas\":{\"MetricsArray\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"},\"ownerName\":{\"type\":\"string\"},\"owner\":{\"type\":\"integer\"},\"currValue\":{\"type\":\"number\"},\"targetValue\":{\"type\":\"number\"},\"status\":{\"type\":\"integer\"},\"favourite\":{\"type\":\"boolean\"},\"trackingPeriod\":{\"type\":\"integer\"},\"dashboardName\":{\"type\":\"string\"},\"dashboardId\":{\"type\":\"integer\"},\"pendingActionItems\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"metrics_meta_id\":{\"type\":\"integer\"},\"creationDate\":{\"type\":\"integer\"},\"dueDate\":{\"type\":\"integer\"},\"author_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"owner_info\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"login\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}},\"priority\":{\"type\":\"integer\"},\"description\":{\"type\":\"string\"},\"additional_info\":{\"type\":\"string\"},\"comments_count\":{\"type\":\"integer\"},\"watchers_count\":{\"type\":\"integer\"},\"current_status\":{\"type\":\"integer\"},\"unread\":{\"type\":\"boolean\"},\"last_updated_date\":{\"type\":\"integer\"},\"userMentioned\":{\"type\":\"boolean\"}}}}}}}}";
        APISpecDao.instance.updateOne(Filters.eq("apiCollectionId", 0), Updates.set("content", metrics));
    }

    private void updateSingleTypeInfos(Map<String, String> origToNewURL) {
        for(String newUrlAndMethod: origToNewURL.keySet()) {
            String newUrl = newUrlAndMethod.split(" ")[0];
            Bson filterQ = Filters.eq("url", newUrl);
            Bson updates = Updates.set("method", newUrlAndMethod.split(" ")[1]);
    
            SingleTypeInfoDao.instance.getMCollection().updateMany(filterQ, updates);

            if (newUrlAndMethod.toLowerCase().contains("getcarddata")) {
                SingleTypeInfo singleTypeInfo = SingleTypeInfoDao.instance.findOne("url", newUrl, "responseCode", 200);
                String ssn = "card_owner_ssn";
                singleTypeInfo.setParam(ssn);
                singleTypeInfo.setSubType(SingleTypeInfo.SSN);
                singleTypeInfo.setExamples(null);
                singleTypeInfo.setTimestamp(Context.now() - 60 * 60 * 24 * 7);
                SingleTypeInfoDao.instance.getMCollection().deleteOne(Filters.eq("param", ssn));
                SingleTypeInfoDao.instance.insertOne(singleTypeInfo);

                String ccn = "credit_card_number";
                singleTypeInfo.setParam(ccn);
                singleTypeInfo.setSubType(SingleTypeInfo.CREDIT_CARD);
                singleTypeInfo.setExamples(null);
                singleTypeInfo.setTimestamp(Context.now() - 60 * 60 * 24 * 7);
                SingleTypeInfoDao.instance.getMCollection().deleteOne(Filters.eq("param", ccn));
                SingleTypeInfoDao.instance.insertOne(singleTypeInfo);
            }
    
        }
    }


    public void fixData() throws IOException {

        ApiCollection defaultCollection = ApiCollectionsDao.instance.findOne(defaultCollKey);

        Map<String, String> origToNewURL = new HashMap<>();

        for(String origUrl: defaultCollection.getUrls()) {
            String newUrl = origUrl;
            if (origUrl.contains("/get")) {
                newUrl = origUrl.split(" ")[0] + " GET";
            }
            origToNewURL.put(origUrl, newUrl);
        }

        Set<String> newUrls = new HashSet<>();
        newUrls.addAll(origToNewURL.values());

        for(String newUrl: origToNewURL.values()) {
            System.out.println("newurl: " + newUrl);
        }

        updateDB(origToNewURL);
    }

}

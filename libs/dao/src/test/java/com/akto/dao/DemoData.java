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

import org.apache.commons.io.FileUtils;
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
            Method method = Method.valueOf(newUrlAndMethod.split(" ")[1]);
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
            Method method = Method.valueOf(newUrlAndMethod.split(" ")[1]);
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
        APISpecDao.instance.updateOne(Filters.eq("apiCollectionId", 0), Updates.set("content", FileUtils.readFileToString(new File(filepath))));
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
                singleTypeInfo.setSubType(SubType.SSN);
                singleTypeInfo.setExamples(null);
                singleTypeInfo.setTimestamp(Context.now() - 60 * 60 * 24 * 7);
                SingleTypeInfoDao.instance.getMCollection().deleteOne(Filters.eq("param", ssn));
                SingleTypeInfoDao.instance.insertOne(singleTypeInfo);

                String ccn = "credit_card_number";
                singleTypeInfo.setParam(ccn);
                singleTypeInfo.setSubType(SubType.CREDIT_CARD);
                singleTypeInfo.setExamples(null);
                singleTypeInfo.setTimestamp(Context.now() - 60 * 60 * 24 * 7);
                SingleTypeInfoDao.instance.getMCollection().deleteOne(Filters.eq("param", ccn));
                SingleTypeInfoDao.instance.insertOne(singleTypeInfo);
            }
    
        }
    }


    @Test
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

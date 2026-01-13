package com.akto.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.hybrid_runtime.policies.AuthPolicy;

import static com.akto.dto.ApiInfo.ALL_AUTH_TYPES_FOUND;

public class CustomAuthUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CustomAuthUtil.class);

    public static Bson getFilters(ApiInfo apiInfo, Boolean isHeader, List<String> params){
        return Filters.and(
                Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                Filters.eq(SingleTypeInfo._URL,apiInfo.getId().getUrl()),
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID,apiInfo.getId().getApiCollectionId()),
                Filters.eq(SingleTypeInfo._METHOD,apiInfo.getId().getMethod().name()),
                Filters.eq(SingleTypeInfo._IS_HEADER,isHeader),
                Filters.in(SingleTypeInfo._PARAM,params)
        );
    }

    private static Set<ApiInfo.AuthType> customTypes = new HashSet<>(Collections.singletonList(ApiInfo.AuthType.CUSTOM));
    private static Set<ApiInfo.AuthType> unauthenticatedTypes = new HashSet<>(Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED));

    private static Set<Set<ApiInfo.AuthType>> addCustomAuth(Set<Set<ApiInfo.AuthType>> authTypes) {

        // remove unauthenticated and add custom auth type
        authTypes.remove(unauthenticatedTypes);
        
        if(!authTypes.contains(customTypes)){
            authTypes.add(customTypes);
        }

        return authTypes;
    }

    private static Set<Set<ApiInfo.AuthType>> addUnauthenticatedIfNoAuth(Set<Set<ApiInfo.AuthType>> authTypes) {

        if(authTypes.isEmpty()){
            authTypes.add(unauthenticatedTypes);
        }

        return authTypes;
    }

    public static void customAuthTypeUtil(List<CustomAuthType> customAuthTypes){


        List<String> COOKIE_LIST = Collections.singletonList("cookie");

        List<WriteModel<ApiInfo>> apiInfosUpdates = new ArrayList<>();

        int skip = 0;
        int limit = 1000;
        boolean fetchMore = false;
        do {
            fetchMore = false;
            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(new BasicDBObject(), skip, limit,
                    Sorts.descending("_id"));

            loggerMaker.infoAndAddToDb("Read " + (apiInfos.size() + skip) + " api infos for custom auth type",
                    LogDb.DASHBOARD);

        for (ApiInfo apiInfo : apiInfos) {

            Set<Set<ApiInfo.AuthType>> authTypes = apiInfo.getAllAuthTypesFound();
            authTypes.remove(new HashSet<>());

            boolean foundCustomAuthType = false;

            for (CustomAuthType customAuthType : customAuthTypes) {

                Set<String> headerAndCookieKeys = new HashSet<>();
                List<SingleTypeInfo> headerSTIs = SingleTypeInfoDao.instance.findAll(getFilters(apiInfo, true, customAuthType.getHeaderKeys()));
                if(headerSTIs!=null){
                    for(SingleTypeInfo sti:headerSTIs){
                        headerAndCookieKeys.add(sti.getParam());
                    }
                }
                SingleTypeInfo cookieSTI = SingleTypeInfoDao.instance.findOne(getFilters(apiInfo, true, COOKIE_LIST));
                if(cookieSTI!=null){
                    Map<String,String> cookieMap = AuthPolicy.parseCookie(new ArrayList<>(cookieSTI.getValues().getElements()));
                    headerAndCookieKeys.addAll(cookieMap.keySet());
                }

                // checking headerAuthKeys in header and cookie in any unauthenticated API
                if (!headerAndCookieKeys.isEmpty() && !customAuthType.getHeaderKeys().isEmpty() && headerAndCookieKeys.containsAll(customAuthType.getHeaderKeys())) {
                    UpdateOneModel<ApiInfo> update = new UpdateOneModel<>(
                            ApiInfoDao.getFilter(apiInfo.getId()),
                            Updates.set(ALL_AUTH_TYPES_FOUND, addCustomAuth(authTypes)),
                            new UpdateOptions().upsert(false)
                    );
                    apiInfosUpdates.add(update);
                    foundCustomAuthType = true;
                    break;
                }

                if (customAuthType.getPayloadKeys().isEmpty()) {
                    continue;
                }

                // checking if all payload keys occur in any unauthenticated API
                List<SingleTypeInfo> payloadSTIs = SingleTypeInfoDao.instance.findAll(getFilters(apiInfo, false, customAuthType.getPayloadKeys()));
                if (payloadSTIs!=null && payloadSTIs.size()==customAuthType.getPayloadKeys().size()) {

                    UpdateOneModel<ApiInfo> update = new UpdateOneModel<>(
                            ApiInfoDao.getFilter(apiInfo.getId()),
                            Updates.set(ALL_AUTH_TYPES_FOUND, addCustomAuth(authTypes)),
                            new UpdateOptions().upsert(false)
                    );
                    apiInfosUpdates.add(update);
                    foundCustomAuthType = true;
                    break;
                }
            }

            if(!foundCustomAuthType){
                UpdateOneModel<ApiInfo> update = new UpdateOneModel<>(
                        ApiInfoDao.getFilter(apiInfo.getId()),
                        Updates.set(ALL_AUTH_TYPES_FOUND, addUnauthenticatedIfNoAuth(authTypes)),
                        new UpdateOptions().upsert(false)
                );
                apiInfosUpdates.add(update);
            }

        }

        if (apiInfos.size() == limit) {
            skip += limit;
            fetchMore = true;
        }

    } while (fetchMore);

            if (apiInfosUpdates.size() > 0) {
                ApiInfoDao.instance.getMCollection().bulkWrite(apiInfosUpdates);
            }
    }

    public static void resetAllCustomAuthTypes() {

        /*
         * 1. remove custom auth type from all entries. 
         * 2. remove unauthenticated auth type from all entries since on reset,
         * auth type should be calculated again.
         */
        ApiInfoDao.instance.updateMany(new BasicDBObject(),
                Updates.pull(ALL_AUTH_TYPES_FOUND + ".$[]", new BasicDBObject().append("$in",
                        new String[] { ApiInfo.AuthType.CUSTOM.name(), ApiInfo.AuthType.UNAUTHENTICATED.name() })));
    }
}

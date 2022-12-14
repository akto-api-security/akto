package com.akto.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.runtime.policies.AuthPolicy;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class CustomAuthUtil {
    
    public static Bson getFilters(ApiInfo apiInfo,Boolean isHeader,List<String> params){
        return Filters.and(
                Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                Filters.eq(SingleTypeInfo._URL,apiInfo.getId().getUrl()),
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID,apiInfo.getId().getApiCollectionId()),
                Filters.eq(SingleTypeInfo._METHOD,apiInfo.getId().getMethod().name()),
                Filters.eq(SingleTypeInfo._IS_HEADER,isHeader),
                Filters.in(SingleTypeInfo._PARAM,params)
        );
    }
    public static void customAuthTypeUtil(List<CustomAuthType> customAuthTypes){

        System.out.println("customAuthTypes count: " + customAuthTypes.size());
        Set<ApiInfo.AuthType> unauthenticatedTypes = new HashSet<>(Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED));
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.eq("allAuthTypesFound",unauthenticatedTypes));
        System.out.println("api count: " + apiInfos.size());
        
        Set<ApiInfo.AuthType> customTypes = new HashSet<>(Collections.singletonList(ApiInfo.AuthType.CUSTOM));
        Set<Set<ApiInfo.AuthType>> authTypes = new HashSet<>(Collections.singletonList(customTypes));

        List<String> COOKIE_LIST = Collections.singletonList("cookie");

        for (ApiInfo apiInfo : apiInfos) {
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

                // checking headerAuthKeys in header and cookie in any unathenticated API
                System.out.println( apiInfo.key() + " : " +  headerAndCookieKeys.size());
                if (headerAndCookieKeys.containsAll(customAuthType.getHeaderKeys())) {
                    ApiInfoDao.instance.updateOne(ApiInfoDao.getFilter(apiInfo.getId()),
                            Updates.set(ApiInfo.ALL_AUTH_TYPES_FOUND, authTypes));
                    break;
                }

                // checking if all payload keys occur in any unauthenticated API
                List<SingleTypeInfo> payloadSTIs = SingleTypeInfoDao.instance.findAll(getFilters(apiInfo, false, customAuthType.getPayloadKeys()));
                System.out.println( apiInfo.key() + " : " +  headerAndCookieKeys.size());
                System.out.println("\n");
                if (payloadSTIs!=null && payloadSTIs.size()==customAuthType.getPayloadKeys().size()) {
                    ApiInfoDao.instance.updateOne(ApiInfoDao.getFilter(apiInfo.getId()),
                            Updates.set(ApiInfo.ALL_AUTH_TYPES_FOUND, authTypes));
                    break;
                }
            }
        }
    }
}

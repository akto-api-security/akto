package com.akto.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class CustomAuthUtil {
    
    public static Bson getFilters(ApiInfo apiInfo,Boolean isHeader,String param){
        Bson filters = Filters.and(
            Filters.eq("url",apiInfo.getId().getUrl()),
            Filters.eq("apiCollectionId",apiInfo.getId().getApiCollectionId()),
            Filters.eq("method",apiInfo.getId().getMethod().name()),
            Filters.eq("isHeader",isHeader),
            Filters.eq("param",param)
        );
        return filters;
    }
    public static void customAuthTypeUtil(List<CustomAuthType> customAuthTypes){
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(new BasicDBObject());
        Set<ApiInfo.AuthType> unauthenticatedTypes = new HashSet<>();
        unauthenticatedTypes.add(ApiInfo.AuthType.UNAUTHENTICATED);
        Set<ApiInfo.AuthType> customTypes = new HashSet<>();
        customTypes.add(ApiInfo.AuthType.CUSTOM);
        Set<Set<ApiInfo.AuthType>> authTypes = new HashSet<>();
        authTypes.add(customTypes);
        for(ApiInfo apiInfo : apiInfos){
            if(apiInfo.getAllAuthTypesFound().contains(unauthenticatedTypes)){
                for(CustomAuthType customAuthType:customAuthTypes){
                    Boolean check = true;
                    for(String headerKey: customAuthType.getHeaderKeys()){
                        SingleTypeInfo sti = SingleTypeInfoDao.instance.findOne(getFilters(apiInfo, true, headerKey));
                        // TODO: also check in cookie
                        if(sti==null){
                            check=false;
                            break;
                        }
                    }
                    if(check){
                        ApiInfoDao.instance.updateOne(ApiInfoDao.getFilter(apiInfo.getId()),
                                Updates.set(ApiInfo.ALL_AUTH_TYPES_FOUND, authTypes));
                        break;
                    }
                    check = true;
                    for(String payloadKey: customAuthType.getPayloadKeys()){
                        SingleTypeInfo sti = SingleTypeInfoDao.instance.findOne(getFilters(apiInfo, false, payloadKey));
                        if(sti==null){
                            check=false;
                            break;
                        }
                    }
                    if(check){
                        ApiInfoDao.instance.updateOne(ApiInfoDao.getFilter(apiInfo.getId()),
                                Updates.set(ApiInfo.ALL_AUTH_TYPES_FOUND, authTypes));
                        break;
                    }
                }
            }
        }
    }
}

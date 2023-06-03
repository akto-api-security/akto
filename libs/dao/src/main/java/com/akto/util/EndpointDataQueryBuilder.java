package com.akto.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.testing.EndpointDataFilterCondition;
import com.akto.dto.testing.EndpointDataQuery;
import com.akto.dto.testing.EndpointDataSortCondition;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

public class EndpointDataQueryBuilder {
    
    public ArrayList<Bson> buildEndpointDataFilterList(EndpointDataQuery endpointDataQuery) {

        ArrayList<Bson> filterList = new ArrayList<>();
        String key, operator;
        ArrayList<String> values;
        String prefix;
        Boolean sensitiveParamInQuery = false;

        List<String> queryOrder = Arrays.asList("apiCollectionId", "method", "authType", "accessType", "sensitiveTags", "url", "discoveredTs", "lastSeenTs");
        
        for (String queryParam: queryOrder) {
            for (EndpointDataFilterCondition endpointDataFilterCondition: endpointDataQuery.getFilterConditions()) {

                key = endpointDataFilterCondition.getKey();
                values = endpointDataFilterCondition.getValues();
                operator = endpointDataFilterCondition.getOperator();
                if (!key.equals(queryParam)) {
                    continue;
                }

                if (queryParam.equals("apiCollectionId")) {
                    if (values.size() > 1) {
                        filterList.add(Filters.in("_id." + key, values));
                    } else if (values.size() == 1) {
                        filterList.add(Filters.eq("_id." + key, Integer.parseInt(values.get(0))));
                    }
                }

                if (key.equals("method")) {

                    prefix = "_id." + key;

                    if (operator.equals("AND")) {
                        filterList.add(Filters.all("_id.method", values));
                    } else if (operator.equals("NOT")) {
                        filterList.add(Filters.nin("_id.method", values));
                    } else if (operator.equals("OR")) {
                        filterList.add(Filters.in("_id.method", values));
                    }
                }

                if (key.equals("authType") || key.equals("accessType")) {

                    List<String> valuesWithPrefix = new ArrayList<>();
                    prefix = key + "_";
                    for (Object value: values) {
                        valuesWithPrefix.add(prefix + value.toString());
                    }

                    if (operator.equals("AND")) {
                        filterList.add(Filters.all("combinedData", valuesWithPrefix));
                    } else if (operator.equals("NOT")) {
                        filterList.add(Filters.nin("combinedData", valuesWithPrefix));
                    } else if (operator.equals("OR")) {
                        filterList.add(Filters.in("combinedData", valuesWithPrefix));
                    }    
                }

                if (key.equals("sensitiveTags")) {
                    ArrayList<String> reqSensitiveParamsToBeAdded = new ArrayList<>();
                    ArrayList<String> respSensitiveParamsToBeAdded = new ArrayList<>();
                    sensitiveParamInQuery = true;
                    List<String> alwaysSensitiveSubTypes = SingleTypeInfoDao.instance.sensitiveSubTypeNames();
    
                    List<String> sensitiveInResponse;
                    List<String> sensitiveInRequest;
                    sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
                    sensitiveInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();
                    //Map<String> sen
        
                    Set<String> sensitiveReqSet = new HashSet<>();
                    for (Object param: values) {

                        if (alwaysSensitiveSubTypes.contains(param)) {
                            reqSensitiveParamsToBeAdded.add("reqSensitive_" + param);
                            respSensitiveParamsToBeAdded.add("respSensitive_" + param);

                        } else if (sensitiveInRequest.contains(param)) {
                            reqSensitiveParamsToBeAdded.add("reqSensitive_" + param);
                            
                        } else if (sensitiveInResponse.contains(param)) {
                            respSensitiveParamsToBeAdded.add("respSensitive_" + param);
                        }
                        // SingleTypeInfo.SubType subtype = SingleTypeInfo.subTypeMap.get(param);
                        // subtype.getSensitivePosition()
                        sensitiveReqSet.add(param.toString());
                    }
    
                    if (sensitiveParamInQuery && (reqSensitiveParamsToBeAdded.size() + respSensitiveParamsToBeAdded.size()) == 0 && !operator.equals("NOT")) {
                        return null;
                    } else {
                        if (operator.equals("AND")) {
                            filterList.add(Filters.or(Filters.all("combinedData", reqSensitiveParamsToBeAdded), 
                            Filters.all("combinedData", respSensitiveParamsToBeAdded)));
                        } else if (operator.equals("NOT")) {
                            reqSensitiveParamsToBeAdded.addAll(respSensitiveParamsToBeAdded);
                            filterList.add(Filters.nin("combinedData", reqSensitiveParamsToBeAdded));
                        } else if (operator.equals("OR")) {

                            filterList.add(Filters.or(Filters.in("combinedData", reqSensitiveParamsToBeAdded), 
                            Filters.in("combinedData", respSensitiveParamsToBeAdded)));
                        } 
                    }
                }

                if (key.equals("url")) {
                    filterList.add(Filters.regex("_id." + key, ".*"+values.get(0)+".*", "i"));
                }
    
                if (key.equals( "lastSeenTs") || key.equals("discoveredTs")) {
    
                    int ltTs = Integer.parseInt(values.get(0));
                    int gtTs =  Integer.parseInt(values.get(1));
                    if (gtTs > ltTs) {
                        int temp = ltTs;
                        ltTs = gtTs;
                        gtTs = temp;   
                    }
                    filterList.add(Filters.and(Filters.lte(key, ltTs), Filters.gte(key, gtTs)));
                }

            }
        }
        return filterList;
    }

    public Bson buildEndpointInfoSort(EndpointDataQuery endpointDataQuery) {

        List<Bson> sorts = new ArrayList<>();
        String key;
        int sortOrder;

        for (EndpointDataSortCondition endpointDataSortCondition: endpointDataQuery.getSortConditions()) {
            key = endpointDataSortCondition.getKey();
            sortOrder = endpointDataSortCondition.getSortOrder();

            if (key.equals("discoveredTs")) {
                if (sortOrder == 1) {
                    sorts.add(Sorts.ascending("discoveredTs"));
                } else {
                    sorts.add(Sorts.descending("discoveredTs"));
                }
            }

            if (key.equals("lastSeenTs")) {
                if (sortOrder == 1) {
                    sorts.add(Sorts.ascending("lastSeenTs"));
                } else {
                    sorts.add(Sorts.descending("lastSeenTs"));
                }
            }
        }

        Bson sort = Sorts.orderBy(sorts);
        return sort;
    }

}

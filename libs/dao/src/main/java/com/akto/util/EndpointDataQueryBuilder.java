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
        Bson filters;

        List<String> queryOrder = Arrays.asList("apiCollectionId", "logicalGroups", "method", "authType", "accessType", "sensitiveTags", "url", "discoveredTs", "lastSeenTs");
        
        for (String queryParam: queryOrder) {
            for (EndpointDataFilterCondition endpointDataFilterCondition: endpointDataQuery.getFilterConditions()) {

                key = endpointDataFilterCondition.getKey();
                values = endpointDataFilterCondition.getValues();
                operator = endpointDataFilterCondition.getOperator();
                if (!key.equals(queryParam)) {
                    continue;
                }

                filters = buildApiCollectionFilter(key, values);
                if (filters != null) {
                    filterList.add(filters);
                }

                filters = buildLogicalGroupFilter(key, values);
                if (filters != null) {
                    filterList.add(filters);
                }

                filters = buildMethodFilter(key, values, operator);
                if (filters != null) {
                    filterList.add(filters);
                }

                filters = buildAuthTypeAndAccessTypeFilter(key, values, operator);
                if (filters != null) {
                    filterList.add(filters);
                }

                filters = buildSensitiveTagFilter(key, values, operator);
                if (key == "sensitiveTags" && filters == null) {
                    return null;
                } 
                if (filters != null) {
                    filterList.add(filters);
                }

                filters = buildUrlFilter(key, values, operator);
                if (filters != null) {
                    filterList.add(filters);
                }

                filters = buildTsFilter(key, values, operator);
                if (filters != null) {
                    filterList.add(filters);
                }
            }
        }
        return filterList;
    }

    public Bson buildApiCollectionFilter(String key, List<String> values) {
        Bson filters = null;
        if (key.equals("apiCollectionId")) {
            filters = Filters.eq("_id." + key, Integer.parseInt(values.get(0)));
        }
        return filters;
    }
    
    public Bson buildLogicalGroupFilter(String key, List<String> values) {
        Bson filters = null;
        if (key.equals("logicalGroups")) {
            filters = Filters.in("combinedData", "logicalGroup_" + Integer.parseInt(values.get(0)));
        }
        return filters;
    }

    public Bson buildMethodFilter(String key, List<String> values, String operator) {
        Bson filters = null;
        String actualKey;
        if (key.equals("method")) {
            actualKey = "_id." + key;
            if (operator.equals("AND")) {
                filters = Filters.all(actualKey, values);
            } else if (operator.equals("NOT")) {
                filters = Filters.nin(actualKey, values);
            } else if (operator.equals("OR")) {
                filters = Filters.in(actualKey, values);
            }
        }
        return filters;
    }

    public Bson buildAuthTypeAndAccessTypeFilter(String key, List<String> values, String operator) {
        Bson filters = null;
        String prefix;
        if (key.equals("authType") || key.equals("accessType")) {
            List<String> valuesWithPrefix = new ArrayList<>();
            prefix = key + "_";
            for (Object value: values) {
                valuesWithPrefix.add(prefix + value.toString());
            }
            if (operator.equals("AND")) {
                filters = Filters.all("combinedData", valuesWithPrefix);
            } else if (operator.equals("NOT")) {
                filters = Filters.nin("combinedData", valuesWithPrefix);
            } else if (operator.equals("OR")) {
                filters = Filters.in("combinedData", valuesWithPrefix);
            }    
        }
        return filters;
    }

    public Bson buildSensitiveTagFilter(String key, List<String> values, String operator) {
        Bson filters = null;
        ArrayList<String> reqSensitiveParamsToBeAdded = new ArrayList<>();
        ArrayList<String> respSensitiveParamsToBeAdded = new ArrayList<>();
        Boolean sensitiveParamInQuery = false;
        
        if (key.equals("sensitiveTags")) {
            sensitiveParamInQuery = true;
            List<String> alwaysSensitiveSubTypes = SingleTypeInfoDao.instance.sensitiveSubTypeNames();

            List<String> sensitiveInResponse;
            List<String> sensitiveInRequest;
            sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
            sensitiveInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();

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
                sensitiveReqSet.add(param.toString());
            }

            if (sensitiveParamInQuery && (reqSensitiveParamsToBeAdded.size() + respSensitiveParamsToBeAdded.size()) == 0) {
                return null;
            } else {
                if (operator.equals("AND")) {
                    filters = Filters.or(Filters.all("combinedData", reqSensitiveParamsToBeAdded), 
                    Filters.all("combinedData", respSensitiveParamsToBeAdded));
                } else if (operator.equals("NOT")) {
                    reqSensitiveParamsToBeAdded.addAll(respSensitiveParamsToBeAdded);
                    filters = Filters.nin("combinedData", reqSensitiveParamsToBeAdded);
                } else if (operator.equals("OR")) {
                    filters = Filters.or(Filters.in("combinedData", reqSensitiveParamsToBeAdded), 
                    Filters.in("combinedData", respSensitiveParamsToBeAdded));
                } 
            }
        }
        return filters;
    }

    public Bson buildUrlFilter(String key, List<String> values, String operator) {
        Bson filters = null;
        if (key.equals("url")) {
            filters = Filters.regex("_id." + key, ".*"+values.get(0)+".*");
        }
        return filters;
    }

    public Bson buildTsFilter(String key, List<String> values, String operator) {
        Bson filters = null;
        if (key.equals( "lastSeenTs") || key.equals("discoveredTs")) {
    
            int ltTs = Integer.parseInt(values.get(0));
            int gtTs =  Integer.parseInt(values.get(1));
            if (gtTs > ltTs) {
                int temp = ltTs;
                ltTs = gtTs;
                gtTs = temp;   
            }
            filters = Filters.and(Filters.lt(key, ltTs), Filters.gt(key, gtTs));
        }
        return filters;
    }

    public Bson buildEndpointInfoSort(EndpointDataQuery endpointDataQuery) {

        List<Bson> sorts = new ArrayList<>();
        String key;
        Bson discoveredTsSort = Sorts.descending("discoveredTs");
        Bson lastSeenTsSort = null;
        int sortOrder;

        for (EndpointDataSortCondition endpointDataSortCondition: endpointDataQuery.getSortConditions()) {
            key = endpointDataSortCondition.getKey();
            sortOrder = endpointDataSortCondition.getSortOrder();

            if (key.equals("discoveredTs") && sortOrder == 1) {
                discoveredTsSort = Sorts.ascending("discoveredTs");
            }
            if (key.equals("lastSeenTs")) {
                if (sortOrder == -1) {
                    lastSeenTsSort = Sorts.descending("lastSeenTs");
                } else {
                    lastSeenTsSort = Sorts.ascending("lastSeenTs");
                }
            }
        }

        sorts.add(discoveredTsSort);
        if (lastSeenTsSort != null) {
            sorts.add(lastSeenTsSort);
        }

        Bson sort = Sorts.orderBy(sorts);

        return sort;
    }

}

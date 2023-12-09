package com.akto.utils.sso;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;

import com.akto.dao.ConfigsDao;
import com.mongodb.client.model.Filters;

public class SsoUtils {
    
    public static String getQueryString(Map<String,String> paramMap){
        return paramMap.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }
    
    public static boolean isAnySsoActive(){
        List<String> ssoList = Arrays.asList("OKTA-ankush", "GITHUB-ankush", "AZURE-ankush");
        Bson filter = Filters.in("_id", ssoList);
        return ConfigsDao.instance.count(filter) > 0;
    }

}

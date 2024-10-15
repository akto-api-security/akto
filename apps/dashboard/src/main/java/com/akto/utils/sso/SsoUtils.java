package com.akto.utils.sso;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.bson.conversions.Bson;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.sso.SAMLConfig;
import com.akto.util.Constants;
import com.akto.utils.CustomHttpsWrapper;
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

    public static HttpServletRequest getWrappedRequest(HttpServletRequest servletRequest){
        String requestUri = servletRequest.getRequestURL().toString();
        String savedRequestUri =CustomSamlSettings.getInstance(ConfigType.AZURE).getSamlConfig().getAcsUrl();

        if(requestUri.equals(savedRequestUri)){
            return servletRequest;
        }
        String tempRequestUri = requestUri.substring(7);
        String tempSavedRequestUri = savedRequestUri.substring(8);
        
        if(tempRequestUri.equals(tempSavedRequestUri)){
            return new CustomHttpsWrapper(servletRequest);
        }else{
            return servletRequest;
        }
    }

    public static SAMLConfig findSAMLConfig(ConfigType configType){
        String configString = configType.name() + "-" + Config.CONFIG_SALT;
        SAMLConfig config = (SAMLConfig) ConfigsDao.instance.findOne(Constants.ID, configString);
        return config;
    }
}

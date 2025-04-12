package com.akto.utils.sso;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.bson.conversions.Bson;

import com.akto.dao.ConfigsDao;
import com.akto.dao.SSOConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.Config.OktaConfig;
import com.akto.dto.sso.SAMLConfig;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.utils.CustomHttpsWrapper;
import com.mongodb.client.model.Filters;

public class SsoUtils {
    
    public static String getQueryString(Map<String,String> paramMap){
        return paramMap.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public static boolean isAnySsoActive(int accountId){
        String configId = String.valueOf(accountId);
        long count = SSOConfigsDao.instance.count(Filters.eq(Constants.ID, configId));
        return count > 0;
    }
    
    public static boolean isAnySsoActive(){
        int accountId = Context.accountId.get();
        String oktaIdString = OktaConfig.getOktaId(accountId);
        if(DashboardMode.isMetered() && !DashboardMode.isOnPremDeployment()){
            if(!isAnySsoActive(accountId)){
                return ConfigsDao.instance.count(Filters.and(
                    Filters.eq(Constants.ID, oktaIdString),
                    Filters.eq(OktaConfig.ACCOUNT_ID, accountId)
                )) > 0;
            }else{
                return true;
            }
        }else{
            List<String> ssoList = Arrays.asList(oktaIdString, "GITHUB-ankush", "AZURE-ankush");
            Bson filter = Filters.in("_id", ssoList);
            accountId = Context.accountId.get() != null ? Context.accountId.get() : 1_000_000;
            return ConfigsDao.instance.count(filter) > 0 || isAnySsoActive(accountId);
        }
    }

    public static HttpServletRequest getWrappedRequest(HttpServletRequest servletRequest, ConfigType configType, int accountId){
        String requestUri = servletRequest.getRequestURL().toString();
        String savedRequestUri = CustomSamlSettings.getInstance(configType, accountId).getSamlConfig().getAcsUrl();
        
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

    public static SAMLConfig findSAMLConfig(ConfigType configType, int accountId){
        String idString = String.valueOf(accountId);
        SAMLConfig config = (SAMLConfig) SSOConfigsDao.instance.findOne(
            Filters.and(
                Filters.eq(Constants.ID, idString),
                Filters.eq("configType", configType.name())
            )
        );
        return config;
    }
}

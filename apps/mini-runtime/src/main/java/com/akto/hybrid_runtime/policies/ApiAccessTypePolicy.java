package com.akto.hybrid_runtime.policies;

import com.akto.dao.AccountSettingsDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.hybrid_parsers.HttpCallParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApiAccessTypePolicy {
    private List<String> privateCidrList;

	public static final String X_FORWARDED_FOR = "x-forwarded-for";
    private static final Logger logger = LoggerFactory.getLogger(ApiAccessTypePolicy.class);

    public ApiAccessTypePolicy(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }


    public void findApiAccessType(HttpResponseParams httpResponseParams, ApiInfo apiInfo, RuntimeFilter filter, List<String> partnerIpList) {
        if (privateCidrList == null || privateCidrList.isEmpty()) return ;
        List<String> xForwardedForValues = httpResponseParams.getRequestParams().getHeaders().get(X_FORWARDED_FOR);
        if (xForwardedForValues == null) xForwardedForValues = new ArrayList<>();


        List<String> ipList = new ArrayList<>();
        for (String ip: xForwardedForValues) {
            String[] parts = ip.trim().split("\\s*,\\s*"); // This approach splits the string by commas and also trims any whitespaces around the individual elements. 
            ipList.addAll(Arrays.asList(parts));
        }

        String sourceIP = httpResponseParams.getSourceIP();

        if (sourceIP != null && !sourceIP.isEmpty() && !sourceIP.equals("null")) {
            ipList.add(sourceIP);
        }

        if (ipList.isEmpty() ) return;

        boolean isAccessTypePartner = false;

        for (String ip: ipList) {
           if (ip == null) continue;
           ip = ip.replaceAll(" ", "");
           try {
                boolean result = ipInCidr(ip);
                if (!result) {
                    if(isPartnerIp(ip, partnerIpList)){
                        isAccessTypePartner = true;
                    }
                    else{
                        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
                        return;
                    }
                }
           } catch (Exception e) {
                return;
           }
        }
        ApiAccessType accessType = ApiAccessType.PRIVATE;
        if(isAccessTypePartner){
            accessType = ApiAccessType.PARTNER;
        }
        apiInfo.getApiAccessTypes().add(accessType);

        return;
    }

    public boolean ipInCidr(String ip) {
        IpAddressMatcher ipAddressMatcher;
        for (String cidr: privateCidrList) {
            ipAddressMatcher = new IpAddressMatcher(cidr);
            boolean result = ipAddressMatcher.matches(ip);
            if (result) return true;
        }

        return false;
    }

    private boolean isPartnerIp(String ip, List<String> partnerIpList){
        if(partnerIpList == null){
            return false;
        }
        for(String partnerIp: partnerIpList){
            if(ip.equals(partnerIp)){
                return true;
            }
        }
        return false;
    }

    public void setPrivateCidrList(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }
}
package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.parsers.HttpCallParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.List;

public class ApiAccessTypePolicy {
    private List<String> privateCidrList;
    private List<String> partnerIpsList;

	public static final String X_FORWARDED_FOR = "x-forwarded-for";
    private static final Logger logger = LoggerFactory.getLogger(ApiAccessTypePolicy.class);

    public ApiAccessTypePolicy(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }


    public void findApiAccessType(HttpResponseParams httpResponseParams, ApiInfo apiInfo, RuntimeFilter filter) {
        if (privateCidrList == null || privateCidrList.isEmpty()) return;
        List<String> ipList = httpResponseParams.getRequestParams().getHeaders().get(X_FORWARDED_FOR);

        if (ipList == null) {
            ipList = new ArrayList<>();
        }

        String sourceIP = httpResponseParams.getSourceIP();

        if (sourceIP != null && !sourceIP.isEmpty()) {
            ipList.add(sourceIP);
        }

        boolean isAccessTypePartner = false;

        for (String ip: ipList) {
           if (ip == null) continue;
           ip = ip.replaceAll(" ", "");
           try {
                boolean result = ipInCidr(ip);
                if (!result) {
                    if(isPartnerIp(ip)){
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
        ApiAccessType accessType = isAccessTypePartner ? ApiAccessType.PARTNER : ApiAccessType.PRIVATE;
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

    private boolean isPartnerIp(String ip){
        if(partnerIpsList == null){
            return false;
        }
        for(String partnerIp: partnerIpsList){
            if(ip.equals(partnerIp)){
                return true;
            }
        }
        return false;
    }

    public void setPrivateCidrList(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }

    public void setPartnerIpsList(List<String> partnerIpsList) {
		this.partnerIpsList = partnerIpsList;
	}
}

package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.parsers.HttpCallParser;
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


    public boolean findApiAccessType(HttpResponseParams httpResponseParams, ApiInfo apiInfo, RuntimeFilter filter) {
        if (privateCidrList == null || privateCidrList.isEmpty()) return false;
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

        if (ipList.isEmpty() ) return false;

        for (String ip: ipList) {
           if (ip == null) continue;
           ip = ip.replaceAll(" ", "");
           try {
                boolean result = ipInCidr(ip);
                if (!result) {
                    apiInfo.getApiAccessTypes().add(ApiInfo.ApiAccessType.PUBLIC);
                    return false;
                }
           } catch (Exception e) {
                return false;
           }
        }

        apiInfo.getApiAccessTypes().add(ApiInfo.ApiAccessType.PRIVATE);

        return false;
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

    public void setPrivateCidrList(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }
}

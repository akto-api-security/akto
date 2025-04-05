package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.ApiInfo.ApiAccessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApiAccessTypePolicy {
    private List<String> privateCidrList;
    private List<String> partnerIpList;

	public static final String X_FORWARDED_FOR = "x-forwarded-for";
    private static final Logger logger = LoggerFactory.getLogger(ApiAccessTypePolicy.class);

    public ApiAccessTypePolicy(List<String> privateCidrList, List<String> partnerIpList) {
        this.privateCidrList = privateCidrList;
        this.partnerIpList = partnerIpList;
    }

    // RFC standard list. To be used later.
    static final private List<String> STANDARD_PRIVATE_IP_RANGES = Arrays.asList(
            "10.0.0.0/8",
            "100.64.0.0/10",
            "172.16.0.0/12",
            "192.0.0.0/24",
            "198.18.0.0/15",
            "192.168.0.0/16");

    static final private List<String> CLIENT_IP_HEADERS = Arrays.asList(
            "x-forwarded-for",
            "x-real-ip",
            "x-cluster-client-ip",
            "true-client-ip",
            "x-original-forwarded-for",
            "x-client-ip",
            "client-ip");

    public String cleanIp(String ip) {
        try {
            String[] parts = ip.split(":");
            return parts[0];
        } catch (Exception e) {
        }
        return ip;
    }

    public void findApiAccessType(HttpResponseParams httpResponseParams, ApiInfo apiInfo) {
        if (privateCidrList == null || privateCidrList.isEmpty()) return ;
        List<String> clientIps = new ArrayList<>();
        for (String header : CLIENT_IP_HEADERS) {
            List<String> headerValues = httpResponseParams.getRequestParams().getHeaders().get(header);
            if (headerValues != null) {
                clientIps.addAll(headerValues);
            }
        }

        List<String> ipList = new ArrayList<>();
        for (String ip: clientIps) {
            String[] parts = ip.trim().split("\\s*,\\s*"); // This approach splits the string by commas and also trims any whitespaces around the individual elements. 
            ipList.addAll(Arrays.asList(parts));
        }

        String sourceIP = httpResponseParams.getSourceIP();

        if (sourceIP != null && !sourceIP.isEmpty() && !sourceIP.equals("null")) {
            ipList.add(cleanIp(sourceIP));
        }
        
        String destIP = httpResponseParams.getDestIP();

        if (destIP != null && !destIP.isEmpty() && !destIP.equals("null")) {
            ipList.add(cleanIp(destIP));
        }

        if (ipList.isEmpty() ) return;

        String direction = httpResponseParams.getDirection();
        /*
         * 1 represents incoming calls to the server, by default all calls are incoming.
         * 2 represents outgoing calls from the server, e.g. third party or partner calls.
         */
        int directionInt = 1;
        try {
            directionInt = Integer.parseInt(direction);
        } catch (Exception e) {
        }

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
                        if(directionInt == 1){
                            apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
                        } else {
                            apiInfo.getApiAccessTypes().add(ApiAccessType.THIRD_PARTY);
                        }
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
        // todo: add standard private IP list
        List<String> checkList = new ArrayList<>();
        if (privateCidrList != null && !privateCidrList.isEmpty()) {
            checkList.addAll(privateCidrList);
        }

        for (String cidr : checkList) {
            ipAddressMatcher = new IpAddressMatcher(cidr);
            boolean result = ipAddressMatcher.matches(ip);
            if (result) return true;
        }

        return false;
    }

    private boolean isPartnerIp(String ip, List<String> partnerIpList){
        if(partnerIpList == null || partnerIpList.isEmpty()){
            return false;
        }
        for(String partnerIp: partnerIpList){
            // the IP may contain port as well, thus using contains.
            if(ip.contains(partnerIp)){
                return true;
            }
        }
        return false;
    }

    public void setPrivateCidrList(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }

    public List<String> getPartnerIpList() {
        return partnerIpList;
    }


    public void setPartnerIpList(List<String> partnerIpList) {
        this.partnerIpList = partnerIpList;
    }
}
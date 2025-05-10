package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.log.LoggerMaker;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApiAccessTypePolicy {
    private List<String> privateCidrList;

	public static final String X_FORWARDED_FOR = "x-forwarded-for";
    private static final LoggerMaker logger = new LoggerMaker(ApiAccessTypePolicy.class);

    public ApiAccessTypePolicy(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }

    // RFC standard list. To be used later.
    static final private List<String> STANDARD_PRIVATE_IP_RANGES = Arrays.asList(
            /*
             * localhost IP
             * 127.0.0.1/32
             */
    
            /*
             * private internets : https://datatracker.ietf.org/doc/html/rfc1918#section-3
             */
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16"
    /*
     * reserved for IANA : https://datatracker.ietf.org/doc/html/rfc6598
     * "100.64.0.0/10",
     */
    /*
     * reserved for IANA: https://datatracker.ietf.org/doc/html/rfc6890
     * "192.0.0.0/24",
     */
    /*
     * Used for benchmarking: https://datatracker.ietf.org/doc/html/rfc2544
     * "198.18.0.0/15"
     */
    );

    static final public List<String> CLIENT_IP_HEADERS = Arrays.asList(
            "x-forwarded-for",
            "x-real-ip",
            "x-cluster-client-ip",
            "true-client-ip",
            "x-original-forwarded-for",
            "x-client-ip",
            "client-ip");

    public static List<String> getSourceIps(HttpResponseParams httpResponseParams){
        List<String> clientIps = new ArrayList<>();
        for (String header : CLIENT_IP_HEADERS) {
            List<String> headerValues = httpResponseParams.getRequestParams().getHeaders().get(header);
            if (headerValues != null) {
                clientIps.addAll(headerValues);
            }
        }
        List<String> ipList = new ArrayList<>();
        for (String ip: clientIps) {
            String[] parts = ip.trim().split("\\s*,\\s*"); // This approach splits the string by commas and also trims any whitespace around the individual elements. 
            ipList.addAll(Arrays.asList(parts));
        }

        logger.debug("Client IPs: " + clientIps);
        String sourceIP = httpResponseParams.getSourceIP();

        if (sourceIP != null && !sourceIP.isEmpty() && !sourceIP.equals("null")) {
            logger.debug("Received source IP: " + sourceIP);
            ipList.add(sourceIP);
        }
        logger.debug("Final IP list: " + ipList);
        return ipList;
    }

    public void findApiAccessType(HttpResponseParams httpResponseParams, ApiInfo apiInfo, RuntimeFilter filter, List<String> partnerIpList) {
        if (privateCidrList == null || privateCidrList.isEmpty()) return ;
        List<String> ipList = getSourceIps(httpResponseParams);

        String destIP = httpResponseParams.getDestIP();
        if (destIP != null && !destIP.isEmpty() && !destIP.equals("null")) {
            ipList.add(destIP);
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
}
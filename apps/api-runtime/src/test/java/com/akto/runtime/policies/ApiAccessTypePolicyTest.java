package com.akto.runtime.policies;

import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import org.junit.jupiter.api.Assertions;
import org.junit.Test;

import java.util.*;

public class ApiAccessTypePolicyTest {
    ApiAccessTypePolicy apiAccessTypePolicy = new ApiAccessTypePolicy(Collections.singletonList("172.31.0.0/16"));

    public static HttpResponseParams generateHttpResponseParams(List<String> ipList) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(ApiAccessTypePolicy.X_FORWARDED_FOR, ipList);
        HttpRequestParams httpRequestParams = new HttpRequestParams("GET", "/a", "", headers, "", 0);
        return new HttpResponseParams("",200,"",new HashMap<>(),"",httpRequestParams ,0,"0",false, HttpResponseParams.Source.OTHER, "", "");
    }

    @Test
    public void testPublic() {
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setPartnerIpList(new ArrayList<>());
        List<String> ipList = Arrays.asList("3.109.56.64", "118.185.162.194");
        HttpResponseParams httpResponseParams = generateHttpResponseParams(ipList);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        apiAccessTypePolicy.findApiAccessType(httpResponseParams,apiInfo, null, new ArrayList<>());
        Assertions.assertEquals(apiInfo.getApiAccessTypes().size(),1);
        Assertions.assertTrue(apiInfo.getApiAccessTypes().contains(ApiInfo.ApiAccessType.PUBLIC));
    }

    @Test
    public void testPublicAlreadyPrivate() {
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setPartnerIpList(new ArrayList<>());
        List<String> ipList = Collections.singletonList("3.109.56.64");
        HttpResponseParams httpResponseParams = generateHttpResponseParams(ipList);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        apiInfo.getApiAccessTypes().add(ApiInfo.ApiAccessType.PRIVATE);
        apiAccessTypePolicy.findApiAccessType(httpResponseParams,apiInfo, null, new ArrayList<>());
        Assertions.assertEquals(apiInfo.getApiAccessTypes().size(), 2);
    }

    @Test
    public void testPrivateAlreadyPublic() {
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setPartnerIpList(new ArrayList<>());
        List<String> ipList = Arrays.asList("172.31.8.188", "172.31.255.255");
        HttpResponseParams httpResponseParams = generateHttpResponseParams(ipList);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        apiInfo.getApiAccessTypes().add(ApiInfo.ApiAccessType.PUBLIC);
        apiAccessTypePolicy.findApiAccessType(httpResponseParams,apiInfo, null, new ArrayList<>());
        Assertions.assertEquals(apiInfo.getApiAccessTypes().size(), 2);
    }

    @Test
    public void testPrivate() {
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setPartnerIpList(new ArrayList<>());
        List<String> ipList = Arrays.asList("172.31.8.188", "172.31.255.255");
        HttpResponseParams httpResponseParams = generateHttpResponseParams(ipList);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        apiAccessTypePolicy.findApiAccessType(httpResponseParams,apiInfo, null, new ArrayList<>());
        Assertions.assertTrue(apiInfo.getApiAccessTypes().contains(ApiInfo.ApiAccessType.PRIVATE));
    }

    @Test
    public void testPublicAndPrivateMultiple() {
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setPartnerIpList(new ArrayList<>());
        List<String> ipList = Arrays.asList("172.31.255.255", "118.185.162.194");
        HttpResponseParams httpResponseParams = generateHttpResponseParams(ipList);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        apiAccessTypePolicy.findApiAccessType(httpResponseParams,apiInfo, null, new ArrayList<>());
        Assertions.assertEquals(apiInfo.getApiAccessTypes().size(), 1);
        Assertions.assertTrue(apiInfo.getApiAccessTypes().contains(ApiInfo.ApiAccessType.PUBLIC));
    }

    @Test 
    public void testPartnerAccessType(){
        List<String> ipList = Arrays.asList("106.222.203.142");
        HttpResponseParams httpResponseParams = generateHttpResponseParams(ipList);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        apiAccessTypePolicy.findApiAccessType(httpResponseParams,apiInfo, null, Arrays.asList("14.143.179.162", "106.222.203.142"));
        Assertions.assertEquals(apiInfo.getApiAccessTypes().size(), 1);
        Assertions.assertTrue(apiInfo.getApiAccessTypes().contains(ApiInfo.ApiAccessType.PARTNER));
    }
}

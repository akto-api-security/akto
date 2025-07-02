package com.akto.test_editor.execution;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.LoginRequestAuthParam;
import com.akto.dto.testing.RequestData;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.AuthParam.Location;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.dto.type.URLMethods.Method;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.ApiExecutor;
import com.akto.testing.kafka_utils.TestingConfigurations;
import com.akto.testing.workflow_node_executor.ApiNodeExecutor;
import com.akto.testing.workflow_node_executor.Utils;
import com.akto.util.DashboardMode;
import com.akto.util.RecordedLoginFlowUtil;
import com.akto.util.enums.LoginFlowEnums.AuthMechanismTypes;
import com.mongodb.BasicDBObject;

import net.bytebuddy.asm.Advice.Origin;

@RunWith(PowerMockRunner.class)
@PrepareForTest({com.akto.util.RecordedLoginFlowUtil.class, com.akto.util.DashboardMode.class, ApiExecutor.class, com.akto.test_editor.Utils.class})
@SuppressStaticInitializationFor("com.akto.test_editor.Utils")
public class TestAutomatedAuth {

    private AuthWithCond createAuthWithCondRequestLogin() {
        Map<String, String> headerKVPairs = new HashMap<>();
        headerKVPairs.put("host", "api.somebackend.com");
        headerKVPairs.put("x-akto-type", "LOGIN_FORM");
        List<AuthParam> authParams = new ArrayList<>();
        authParams.add(new LoginRequestAuthParam(Location.HEADER, "token", "${x1.response.body.token}", true));
        ArrayList<RequestData> requestDataList = new ArrayList<>();
        requestDataList.add(new RequestData("{}", "{\"token\":[\"Bearer old_request_token\"]}", "a=1", "api.somebackend.com/url", "GET", "LOGIN_FORM", "", "", "command", false));
        AuthMechanism authMechanism = new AuthMechanism(authParams, requestDataList, AuthMechanismTypes.LOGIN_REQUEST.name(), new ArrayList<>());
        RecordedLoginFlowInput recordedLoginFlowInput = null;
        authMechanism.setRecordedLoginFlowInput(recordedLoginFlowInput);
        AuthWithCond authWithCond = new AuthWithCond(authMechanism, headerKVPairs, recordedLoginFlowInput);

        return authWithCond;
    }


    private AuthWithCond createAuthWithCondRecordedLogin() {
        Map<String, String> headerKVPairs = new HashMap<>();
        headerKVPairs.put("host", "api.somebackend.com");
        headerKVPairs.put("x-akto-type", "RECORDED_FLOW");
        List<AuthParam> authParams = new ArrayList<>();
        authParams.add(new LoginRequestAuthParam(Location.HEADER, "token", "${x1.response.body.token}", true));
        ArrayList<RequestData> requestDataList = new ArrayList<>();
        requestDataList.add(new RequestData("", "", "", "", "", "RECORDED_FLOW", "", "", "command", false));
        AuthMechanism authMechanism = new AuthMechanism(authParams, requestDataList, AuthMechanismTypes.LOGIN_REQUEST.name(), new ArrayList<>());
        RecordedLoginFlowInput recordedLoginFlowInput = new RecordedLoginFlowInput("content", "command", "outputfile", "errorFile");
        authMechanism.setRecordedLoginFlowInput(recordedLoginFlowInput);
        AuthWithCond authWithCond = new AuthWithCond(authMechanism, headerKVPairs, recordedLoginFlowInput);

        return authWithCond;
    }

    private AuthWithCond createAuthWithCondSampleData() {
        Map<String, String> headerKVPairs = new HashMap<>();
        headerKVPairs.put("host", "api.somebackend.com");
        headerKVPairs.put("x-akto-type", "SAMPLE_DATA");
        List<AuthParam> authParams = new ArrayList<>();
        authParams.add(new LoginRequestAuthParam(Location.HEADER, "token", "${x1.response.body.token}", true));
        AuthMechanism authMechanism = new AuthMechanism(authParams, null, AuthMechanismTypes.SAMPLE_DATA.name(), new ArrayList<>());
        AuthWithCond authWithCond = new AuthWithCond(authMechanism, headerKVPairs, null);

        return authWithCond;
    }




    private RawApi createRawApi(boolean isRecordedLogin) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("token", Arrays.asList("Bearer old_token"));
        headers.put("host", Arrays.asList("api.somebackend.com"));
        headers.put("x-akto-type", Arrays.asList(isRecordedLogin ? "RECORDED_FLOW" : "LOGIN_FORM"));
        OriginalHttpRequest request = new OriginalHttpRequest("https://api.somebackend.com/url", "a=1", "GET", null, headers, "");
        OriginalHttpResponse response = new OriginalHttpResponse(null, null, 0);
        RawApi rawApi = new RawApi(request, response, "");
        return rawApi;
    }

    private RawApi createRawApiForSampleData() {
        RawApi ret = createRawApi(false);
        ret.getRequest().getHeaders().put("x-akto-type", Arrays.asList("SAMPLE_DATA"));
        return ret;
    }

    private TestRoles createTestRole() {
        PowerMockito.mockStatic(RecordedLoginFlowUtil.class);
        try {
            BasicDBObject mockTokenOutput = new BasicDBObject("token", "Bearer recorded_flow_token").append("aktoOutput", new BasicDBObject("authTokenHeader", "Bearer authTokenHeader"));
            PowerMockito.doNothing().when(RecordedLoginFlowUtil.class);
            RecordedLoginFlowUtil.triggerFlow("", "", "", "", 0);
            PowerMockito.when(RecordedLoginFlowUtil.fetchToken(anyString(), anyString())).thenReturn(mockTokenOutput.toJson());
        } catch (Exception e) {

            e.printStackTrace();
        }
        ObjectId id = new ObjectId();
        String name = "test_role_01";
        List<AuthWithCond> authWithCondList = new ArrayList<>();
        authWithCondList.add(createAuthWithCondRecordedLogin());
        authWithCondList.add(createAuthWithCondRequestLogin());
        authWithCondList.add(createAuthWithCondSampleData());
        List<Integer> collectionIds = new ArrayList<>();
        TestRoles recordedFlowTestRole = new TestRoles(id, name, null, authWithCondList, "akto_test", Context.now(), Context.now(), collectionIds, "akto_test");

        // String output = Utils.fetchToken("somerole", createAuthWithCondRecordedLogin().getRecordedLoginFlowInput(), 1);
        // System.out.println("output: " + output);

        return recordedFlowTestRole;
    }

    @Test
    public void testModifyAuthTokenInRawApi() throws Exception {
        PowerMockito.mockStatic(DashboardMode.class);
        when(DashboardMode.getDashboardMode()).thenReturn(DashboardMode.LOCAL_DEPLOY);

        PowerMockito.mockStatic(com.akto.test_editor.Utils.class);
        PowerMockito.doReturn(null).when(com.akto.test_editor.Utils.class, "createHttpClient");


        PowerMockito.mockStatic(ApiExecutor.class);

        OriginalHttpResponse mockResp = new OriginalHttpResponse("{\"token\": \"Bearer login_form_token\"}", new HashMap<>(), 200);
        when(ApiExecutor.sendRequest(anyObject(), anyBoolean(), anyObject(), anyBoolean(), anyObject(), anyBoolean())).thenReturn(mockResp);
        TestRoles testRole = createTestRole();

        testModifyToken(testRole, true);
        testModifyToken(testRole, false);

        RawApi sampleDataAuthApi = createRawApiForSampleData();
        Map<ApiInfoKey, List<String>> apiInfoKeyToValues = new HashMap<>();
        String sd = "{\"path\":\"http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/society/ncc\",\"requestHeaders\":\"{\\\"Access-Token\\\":\\\"JWT eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InJpc2hhdi5zb2xhbmtpQGFrdG8uaW8iLCJpYXQiOjE2ODg3MTMzNTUsImV4cCI6MTY4ODcxNDI1NX0.ToSrgQdEWaTVBphY9QMPBmo1zWgaDt_2zRlFb4gLYcgn3x58ClnTciRXN--9 LeoKojWo466S2rDDK8KH3IhR7gTDKk9ihKfLaVoKIg7M7RaHxFgp-vtjWenFcR6IBqLXqYh_kCqBFDH3hjrbD1Qtoaieu_L1rtJFwqz2xoIZP0VEmTPXT4vxT6yoVlbgloROzu1cJFGnoFQm69OUNHpCLf9S_7Qs-9eV2V-AlzeClfMnblTqhQP_s4znPit2Ik0ypNIH-mEwgxL-coWVmphuFYy5uG5c2Z4F4te7r_QP9jlOVYFjwB6_9gQSwi1lrm8qKdNml1UKnh4NNizc1878oQ\\\",\\\"Content-Type\\\": \\\"application/json\\\", \\\"HOST\\\": \\\"vulnerableapi.com\\\"}\",\"responseHeaders\":\"\",\"method\":\"GET\",\"requestPayload\":\"\",\"responsePayload\":\"{\\\"token\\\":\\\"sd_token\\\"}\",\"status\":\"OK\",\"statusCode\":\"200\",\"akto_account_id\":\"1000000\",\"ip\":\"null\",\"time\":\"1695295235\",\"type\":\"HTTP/1.1\",\"contentType\":\"application/json\",\"source\":\"HAR\",\"akto_vxlan_id\":1111111111}";
        apiInfoKeyToValues.put(new ApiInfoKey(0, "/url01", Method.GET), Arrays.asList(sd));
        TestingConfigurations.getInstance().init(new TestingUtil(SampleMessageStore.create(apiInfoKeyToValues), null, null, null), null, false, null, 0);
        Executor.modifyAuthTokenInRawApi(testRole, sampleDataAuthApi);

        sampleDataAuthApi.getRequest().getHeaders().forEach((k, v) -> {
            System.out.println("key: " + k + ", value: " + v);
        });

        String actualToken = sampleDataAuthApi.getRequest().getHeaders().get("token").get(0);
        assertEquals("sd_token", actualToken);
    }


    private void testModifyToken(TestRoles testRole, boolean isRecordedLogin) {
        RawApi rawApiRecordedLogin = createRawApi(isRecordedLogin);
        Executor.modifyAuthTokenInRawApi(testRole, rawApiRecordedLogin);

        rawApiRecordedLogin.getRequest().getHeaders().forEach((k, v) -> {
            System.out.println("key: " + k + ", value: " + v);
        });

        String actualToken = rawApiRecordedLogin.getRequest().getHeaders().get("token").get(0);
        assertEquals("Bearer " + (isRecordedLogin ? "recorded_flow_token" : "login_form_token"), actualToken);
    }


}

package com.akto.action.testing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.testing.ApiExecutor;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TestRunTestsFromEditor extends MongoBasedTest {

    @Test
    public void TestAttachFileOperator() throws Exception{

        String validRemoteUrl = "https://temp-aryan.s3.ap-south-1.amazonaws.com/big_image.jpeg";
        String currentCookieValue = "_BEAMER_USER_ID_TEEsyHNL42222=350d4563-63a9-4732-99e8-bf8ddcbdb9b2; _BEAMER_FIRST_VISIT_TEEsyHNL42222=2023-12-02T08:28:32.664Z; intercom-id-xjvl0z2h=8efe8e84-4f98-499d-b1ec-96cbeb9a6243; intercom-device-id-xjvl0z2h=0a609431-b163-4271-a731-80d13863c955; _ga=GA1.1.995063340.1702709571; _gcl_au=1.1.1377381098.1702709571; _hjSessionUser_3538216=eyJpZCI6IjI1YzYxOTUyLTNiZTctNWE1OS1iZTc2LTFjOWUxMGRkYTY3OSIsImNyZWF0ZWQiOjE3MDI3MDk1NzEwMTMsImV4aXN0aW5nIjp0cnVlfQ==; cb_user_id=null; cb_group_id=null; cb_anonymous_id=%228f0411b9-0788-491f-9135-19f676d96e70%22; _clck=4g8d4j%7C2%7Cfif%7C0%7C1475; _rdt_uuid=1707125191687.c46d45f4-8372-4a49-9f54-2cedeb6e0960; _hjSessionUser_3823272=eyJpZCI6ImZlNTQwM2JiLWEzMmItNWVmNS04NDAwLWU3NTUyZDg1ZThkZSIsImNyZWF0ZWQiOjE3MDczODAxOTEwMDMsImV4aXN0aW5nIjp0cnVlfQ==; _ga_DEM5S3JBNR=GS1.1.1707971743.18.0.1707971743.0.0.0; _ga_GGW4LCJNV5=GS1.1.1707971743.20.0.1707971743.0.0.0; language=en; welcomebanner_status=dismiss; cookieconsent_status=dismiss; intercom-session-xjvl0z2h=SS9hQ256Z2dFZldwenh6MkVhMG5uS0ZoZCsyNVFlc2tTUjhndVRscmU3dGkxWUdpdDQ3TXpLSWNodDArK3dNSi0tbm5wUVpkTTlJaHFOYzF4aFcyM29Bdz09--7aeb04ec5e15cc2a72e659f4f672c0b614793b0b; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22aryan%40akto.io_ON_PREM%22%2C%22%24device_id%22%3A%20%2218c29a4bb9f15d4-03fd022ba99e1e-16525634-13c680-18c29a4bb9f15d4%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%2C%22__mps%22%3A%20%7B%7D%2C%22__mpso%22%3A%20%7B%7D%2C%22__mpus%22%3A%20%7B%7D%2C%22__mpa%22%3A%20%7B%7D%2C%22__mpu%22%3A%20%7B%7D%2C%22__mpr%22%3A%20%5B%5D%2C%22__mpap%22%3A%20%5B%5D%2C%22%24user_id%22%3A%20%22aryan%40akto.io_ON_PREM%22%2C%22email%22%3A%20%22aryan%40akto.io%22%2C%22dashboard_mode%22%3A%20%22ON_PREM%22%2C%22account_name%22%3A%20%22akto%22%7D; __cuid=f4c092a9faf6477f884bcee458fed3a3; amp_fef1e8=c645b56c-4f16-4ec6-ac94-610109baabd1R...1hna83fna.1hna85fqn.2n.l.3c; token=eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjIsInVzZXJuYW1lIjoiYXR0YWNrZXIiLCJlbWFpbCI6ImF0dGFja2VyQGdtYWlsLmNvbSIsInBhc3N3b3JkIjoiMTY2YzU1YjUxZmQ1ZmJkYTg3NWFiNmMzMTg0NDIyNTUiLCJyb2xlIjoiY3VzdG9tZXIiLCJkZWx1eGVUb2tlbiI6IiIsImxhc3RMb2dpbklwIjoidW5kZWZpbmVkIiwicHJvZmlsZUltYWdlIjoiYXNzZXRzL3B1YmxpYy9pbWFnZXMvdXBsb2Fkcy8yMi5qcGciLCJ0b3RwU2VjcmV0IjoiIiwiaXNBY3RpdmUiOnRydWUsImNyZWF0ZWRBdCI6IjIwMjQtMDItMTcgMDI6MDg6NTEuNjU3ICswMDowMCIsInVwZGF0ZWRBdCI6IjIwMjQtMDItMjIgMTA6MzY6MDAuNTMzICswMDowMCIsImRlbGV0ZWRBdCI6bnVsbH0sImlhdCI6MTcwODY2ODE5NywiZXhwIjoyMDI0MDI4MTk3fQ.KOvc7TxYYgnW0cy4s4DktLq5CoTaM1n7b9Zl0m288-uJWT96cT8QAlfWNYlyNJ6sD-WcBbmjPsMt7Qm1ur8GfR00h5HrE-8PcsU6roLrpvbULPj5Cy-q6cYrwWSPjoiPHhBIQnFV1diwdAcS06J33NUuNe079_k-c40wZjirJIA";


        String inValidImageUrl = "https://temp-aryan.s3.ap-south-1.amazonaws.com/IMG20240217153938.jpg";
        String uploadUrl = "https://juiceshop.akto.io/profile/image/file";

        RequestBody requestBody = ApiExecutor.getFileRequestBody(validRemoteUrl);
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                    .url(uploadUrl)
                    .addHeader("cookie",currentCookieValue)
                    .post(requestBody)
                    .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            assertEquals(200, code);
        }

        requestBody = ApiExecutor.getFileRequestBody(inValidImageUrl);

        request = new Request.Builder()
                    .url(uploadUrl)
                    .addHeader("cookie",currentCookieValue)
                    .post(requestBody)
                    .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();          
            assertEquals(500, code);
        }
    }
    
}

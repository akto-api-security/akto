package com.akto.oas.scan;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.akto.dao.AttemptsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Attempt;
import com.akto.dto.Attempt.Success;
import com.mongodb.client.model.Updates;

import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class RequestDispatcher {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

    static class CustomResponseHandler implements ResponseHandler<String> {

        Attempt.Success attemptResult;

        public CustomResponseHandler(Attempt.Success attemptResult) {
            this.attemptResult = attemptResult;
        }

        @Override
        public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
            int status = response.getStatusLine().getStatusCode();
            this.attemptResult.setErrorCode(status);

            attemptResult.setResponseHeaders(ScanUtil.getHeaders(response.getAllHeaders()));

            String responseBody = "";
            
            HttpEntity entity = response.getEntity();
            if(entity != null) {
                responseBody = EntityUtils.toString(entity);
            }
            
            attemptResult.setResponseBody(responseBody);
            
            return "";
        }
      
    }

    public RequestDispatcher() {}

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    public static void dispatch(HttpUriRequest request, Attempt attempt, int accountId) {
        executorService.submit(new Runnable(){

            @Override
            public void run() {
                try {
                    Context.accountId.set(accountId);
                    Success attemptResult = (Success) (attempt.getAttemptResult());
                    dispatch0(request, attemptResult);
                    attempt.setStatus(Attempt.Status.SENT.toString());
                    AttemptsDao.instance.updateOne("_id", attempt.getId(), Updates.set("attemptResult", attemptResult));
    
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    attempt.setStatus(Attempt.Status.FAILED.toString());
                    
                }

                AttemptsDao.instance.updateOne("_id", attempt.getId(), Updates.set("status", attempt.getStatus()));
            }
        });
    }

    private static void dispatch0(HttpUriRequest request, Success attemptResult) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        httpClient.execute(
            request, 
            new ResponseHandler<String>(){

                @Override
                public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    attemptResult.setErrorCode(status);
        
                    attemptResult.setResponseHeaders(ScanUtil.getHeaders(response.getAllHeaders()));
        
                    String responseBody = "";
                    
                    HttpEntity entity = response.getEntity();
                    if(entity != null) {
                        responseBody = EntityUtils.toString(entity);
                    }
                    
                    attemptResult.setResponseBody(responseBody);
                    
                    return "";
                } 
            }, 
            null
        );
        attemptResult.setTimeTakenInMillis((int) (System.currentTimeMillis()-startTime));
    }
}

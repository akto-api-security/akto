package com.akto.testing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HostValidator {

    public static final boolean SKIP_SSRF_CHECK = ("true".equalsIgnoreCase(System.getenv("SKIP_SSRF_CHECK")) || !DashboardMode.isSaasDeployment());
    public static final boolean IS_SAAS = "true".equalsIgnoreCase(System.getenv("IS_SAAS"));
    public static ExecutorService executor = Executors.newFixedThreadPool(15);

    private static final LoggerMaker loggerMaker = new LoggerMaker(HostValidator.class, LogDb.TESTING);
    
    static Map<String, Boolean> hostReachabilityMap = new HashMap<>();

    public static void validate(String url) throws Exception {
        if (hostReachabilityMap != null) {

            String checkUrl = getUniformUrl(url);

            if (!hostReachabilityMap.getOrDefault(checkUrl, true)) {
                loggerMaker.infoAndAddToDb(String.format(
                        "Skipping url %s due to host unreachable previously", url));
                throw new Exception("Host unreachable previously");
            }
        }
    }

    private static boolean checkDomainReach(Request request, boolean followRedirects, String requestProtocol) throws Exception{

        if (HTTPClientHandler.instance == null) {
            HTTPClientHandler.initHttpClientHandler(IS_SAAS);
        }

        OkHttpClient client = HTTPClientHandler.instance.getHTTPClient(followRedirects, requestProtocol);

        if (!SKIP_SSRF_CHECK && !HostDNSLookup.isRequestValid(request.url().host())) {
            throw new IllegalArgumentException("SSRF attack attempt");
        }

        Call call = client.newCall(request);
        Response response = null;
        try {
            response = call.execute();
        } catch (IOException e) {
            if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                loggerMaker.errorAndAddToDb("Error while executing request " + request.url() + ": " + e, LogDb.TESTING);
            } else {
                System.out.println("Error while executing request " + request.url() + ": " + e);
            }
            return false;
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return true;
    }

    public static String getUniformUrl(String url){
        try {
            HttpUrl uniformUrl = HttpUrl.get(url);
            return getUniformUrlUtil(uniformUrl);
        } catch(Exception e){
            loggerMaker.errorAndAddToDb(e, "Error in getUniformUrl " + e.getMessage());
        }
        return url;
    }

    private static String getUniformUrlUtil(HttpUrl url){
        return String.format("%s://%s:%s", url.scheme(), url.host(), url.port());
    }

    public static void compute(Map<String, String> hostAndContentType, TestingRunConfig testingRunConfig) {
        hostReachabilityMap = new HashMap<>();
        if (hostAndContentType == null) {
            return;
        }

        List<Future<Void>> futures = new ArrayList<>();
        int accountId = Context.accountId.get();
        for (String host : hostAndContentType.keySet()) {
            futures.add(
                executor.submit(() -> {
                    Context.accountId.set(accountId);
                    try {
                        String url = host;
                        if (!url.endsWith("/"))
                        url += "/";

                        String contentType = hostAndContentType.get(host);
                        Map<String, List<String>> headers = new HashMap<>();

                        if (contentType != null) {
                            headers.put("content-type", Arrays.asList(contentType));
                        }
                        if (host != null && !host.isEmpty()) {
                            headers.put("host", Arrays.asList(host));
                        }

                        OriginalHttpRequest request = new OriginalHttpRequest(url, null, URLMethods.Method.GET.name(), null, new HashMap<>(), "");
                        Request actualRequest = ApiExecutor.buildRequest(request, testingRunConfig);
                        String attemptUrl = getUniformUrlUtil(actualRequest.url());
                        loggerMaker.infoAndAddToDb("checking reachability for host: " + attemptUrl);
                        if(!hostReachabilityMap.containsKey(attemptUrl)){
                            boolean reachable = checkDomainReach(actualRequest, false, contentType);
                            hostReachabilityMap.put(attemptUrl, reachable);
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("", LogDb.TESTING);
                    }
                    return null;
                })
            );
        }

        for (Future<Void> future : futures) {
            try {
                future.get(1, TimeUnit.MINUTES);
            } catch (InterruptedException | TimeoutException e) {
                future.cancel(true); // Cancel the task
                loggerMaker.errorAndAddToDb(e, "Timeout in host validation task");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in host validation task " + e.getMessage());
            }
        }
    }

    public static String getResponseBodyForHostValidation(String host, Map<String, String> hostAndContentType, int index, TestingRunConfig testingRunConfig, boolean SKIP_SSRF_CHECK) throws Exception {
        String url = host;
        if (!url.endsWith("/")) url += "/";
        if (index > 0) url += "akto-" + index; // we want to hit host url once too

        String contentType = hostAndContentType.get(host);
        Map<String, List<String>> headers = new HashMap<>();

        if (contentType != null) {
            headers.put("content-type", Arrays.asList(contentType));
        }
        if (host != null && !host.isEmpty()) {
            headers.put("host", Arrays.asList(host));
        }

        OriginalHttpRequest request = new OriginalHttpRequest(url, null, URLMethods.Method.GET.name(), null, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, testingRunConfig, false, new ArrayList<>(), SKIP_SSRF_CHECK);
        boolean isStatusGood = Utils.isStatusGood(response.getStatusCode());
        if (!isStatusGood) return null;

        return response.getBody();
    } 
}

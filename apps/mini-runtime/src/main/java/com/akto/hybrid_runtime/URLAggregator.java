package com.akto.hybrid_runtime;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;

public class URLAggregator {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    ConcurrentMap<URLStatic, Set<HttpResponseParams>> urls;

    public static URLStatic getBaseURL(String url, String method) {

        if (url == null) {
            return null;
        }

        return new URLStatic(url.split("\\?")[0], Method.fromString(method));
    }


    public URLAggregator() {
        this.urls = new ConcurrentHashMap<>();
    }

    public URLAggregator(ConcurrentMap<URLStatic, Set<HttpResponseParams>> urls) {
        this.urls = urls;
    }

    public void addURL(HttpResponseParams responseParams) {
        URLStatic url = getBaseURL(responseParams.getRequestParams().getURL(), responseParams.getRequestParams().getMethod());

        Set<HttpResponseParams> responses = urls.get(url);
        if (responses == null) {
            responses = Collections.newSetFromMap(new ConcurrentHashMap<HttpResponseParams, Boolean>());
            urls.put(url, responses);
        }

        responses.add(responseParams);

    }

    public void addURL(Set<HttpResponseParams> responseParams, URLStatic url) {
        Set<HttpResponseParams> responses = urls.get(url);
        if (responses == null) {
            responses = Collections.newSetFromMap(new ConcurrentHashMap<HttpResponseParams, Boolean>());
            urls.put(url, responses);
        }

        responses.addAll(responseParams);

    }
    

    public void printPendingURLs() {
        for(URLStatic s: urls.keySet()) {
            loggerMaker.info(s+":"+urls.get(s).size());
        }
    }
}

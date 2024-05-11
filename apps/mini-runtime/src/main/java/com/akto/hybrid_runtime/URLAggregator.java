package com.akto.hybrid_runtime;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.type.SingleTypeInfo.SubType;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.akto.dto.type.KeyTypes.patternToSubType;
import static com.akto.hybrid_runtime.APICatalogSync.isAlphanumericString;
import static com.akto.hybrid_runtime.APICatalogSync.tokenize;

public class URLAggregator {

    private static final Logger logger = LoggerFactory.getLogger(URLAggregator.class);

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
            logger.info(s+":"+urls.get(s).size());
        }
    }
}

package com.akto.runtime;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.akto.parsers.HttpCallParser.HttpResponseParams;
import com.mongodb.BasicDBObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class URLAggregator {

    private static final Logger logger = LoggerFactory.getLogger(URLAggregator.class);

    ConcurrentMap<String, Set<HttpResponseParams>> urls;

    public static String getBaseURL(String url) {
        if (url == null) {
            return null;
        }

        return url.split("\\?")[0];
    }

    public static BasicDBObject getQueryJSON(String url) {
        BasicDBObject ret = new BasicDBObject();
        if (url == null) {
            return ret;
        }
        
        String[] splitURL = url.split("\\?");

        if (splitURL.length != 2) {
            return ret;
        }

        String queryParamsStr = splitURL[1];
        if (queryParamsStr == null) {
            return ret;
        }

        String[] queryParams = queryParamsStr.split("&");

        for(String queryParam: queryParams) {
            String[] keyVal = queryParam.split("=");
            if (keyVal.length != 2) {
                continue;
            }
            try {
                keyVal[0] = URLDecoder.decode(keyVal[0], "UTF-8");
                keyVal[1] = URLDecoder.decode(keyVal[1], "UTF-8");
                ret.put(keyVal[0], keyVal[1]);
            } catch (UnsupportedEncodingException e) {
                continue;
            }
        }

        return ret;

    }

    public URLAggregator() {
        this.urls = new ConcurrentHashMap<>();
    }

    public URLAggregator(ConcurrentMap<String, Set<HttpResponseParams>> urls) {
        this.urls = urls;
    }

    public void addURL(HttpResponseParams responseParams) {
        String url = getBaseURL(responseParams.getRequestParams().getURL());

        Set<HttpResponseParams> responses = urls.get(url);
        if (responses == null) {
            responses = Collections.newSetFromMap(new ConcurrentHashMap<HttpResponseParams, Boolean>());
            urls.put(url, responses);
        }

        responses.add(responseParams);

    }

    public void addURL(Set<HttpResponseParams> responseParams, String url) {
        Set<HttpResponseParams> responses = urls.get(url);
        if (responses == null) {
            responses = Collections.newSetFromMap(new ConcurrentHashMap<HttpResponseParams, Boolean>());
            urls.put(url, responses);
        }

        responses.addAll(responseParams);

    }

    public void printPendingURLs() {
        for(String s: urls.keySet()) {
            logger.info(s+":"+urls.get(s).size());
        }
    }
}

package com.akto.runtime;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLTemplate;
import com.mongodb.BasicDBObject;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.akto.dto.type.KeyTypes.patternToSubType;
import static com.akto.runtime.APICatalogSync.isAlphanumericString;
import static com.akto.runtime.APICatalogSync.tokenize;

public class URLAggregator {

    private static final Logger logger = LoggerFactory.getLogger(URLAggregator.class);

    ConcurrentMap<URLStatic, Set<HttpResponseParams>> urls;

    public static URLStatic getBaseURL(String url, String method) {
        if (url == null) {
            return null;
        }


        String urlStr = url.split("\\?")[0];

        String[] urlTokens = tokenize(urlStr);

        Pattern pattern = patternToSubType.get(SingleTypeInfo.UUID);

        SingleTypeInfo.SuperType[] newTypes = new SingleTypeInfo.SuperType[urlTokens.length];

        for(int i = 0; i < urlTokens.length; i ++) {
            String tempToken = urlTokens[i];

            if (tempToken.length() == 0) {
                continue;
            }

            String numToken = tempToken;
            if (tempToken.charAt(0) == '+') {
                numToken = tempToken.substring(1);
            }

            if (NumberUtils.isParsable(numToken)) {
                newTypes[i] = SingleTypeInfo.SuperType.INTEGER;
                urlTokens[i] = null;
            } else if(pattern.matcher(tempToken).matches()){
                newTypes[i] = SingleTypeInfo.SuperType.STRING;
                urlTokens[i] = null;
            } else if (isAlphanumericString(tempToken)) {
                newTypes[i] = SingleTypeInfo.SuperType.STRING;
                urlTokens[i] = null;
            }
        }

        Method m = Method.valueOf(method);
        URLStatic ret = new URLStatic(new URLTemplate(urlTokens, newTypes, m).getTemplateString(), m);

        return ret;
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

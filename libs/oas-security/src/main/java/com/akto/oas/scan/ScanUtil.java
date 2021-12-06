package com.akto.oas.scan;

import com.akto.dao.context.Context;
import com.akto.dto.Attempt;

import org.apache.http.Header;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ScanUtil {

    public static Map<String, List<String>> getHeaders(Header[] headers) {
        
        Map<String, List<String>> ret = new HashMap<>();

        for(Header header: headers) {
            ArrayList<String> list = new ArrayList<>();
            list.add(header.getValue());
            ret.put(header.getName(), list);
        }

        return ret;
    }

    public static String getBody(HttpUriRequest request) {

        if (!(request instanceof HttpEntityEnclosingRequestBase)) {
            return "";
        }

        HttpEntityEnclosingRequestBase r = (HttpEntityEnclosingRequestBase) request;

        try {
            return EntityUtils.toString(r.getEntity());
        } catch (ParseException | IOException e) {
            return "";
        }

    }

    public static Attempt createAttempt(HttpUriRequest request, boolean isHappy) {
        String uuid = UUID.randomUUID().toString();
        return new Attempt(
            Context.now(),
            uuid, 
            request.getURI().toString(), 
            request.getMethod(), 
            new Attempt.Success(
                getHeaders(request.getAllHeaders()), 
                getBody(request),
                null, 
                null, 
                0, 
                -1
            ),
            isHappy
        );
    }
}

package com.akto.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class CustomHttpsWrapper extends HttpServletRequestWrapper {
    public CustomHttpsWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public StringBuffer getRequestURL() {
        StringBuffer url = super.getRequestURL();
        if (url != null && url.toString().startsWith("http://")) {
            return new StringBuffer(url.toString().replaceFirst("http://", "https://"));
        }
        return url;
    }
}

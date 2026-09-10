package com.akto.detection;

import java.util.Objects;

/**
 * Identifies one parameter of one endpoint: the thing the external classifier gives an answer about.
 *
 * The asynchronous path is built around this rather than around individual values. An answer that
 * comes back seconds later cannot be attached to the value that prompted it, because that value has
 * already been recorded and will most likely never be seen again. It can be attached to the
 * parameter, and that is what makes the answer worth keeping.
 */
public class ParamLocation {

    private final int apiCollectionId;
    private final String url;
    private final String method;
    private final String param;

    public ParamLocation(int apiCollectionId, String url, String method, String param) {
        this.apiCollectionId = apiCollectionId;
        this.url = url == null ? "" : url;
        this.method = method == null ? "" : method;
        this.param = param == null ? "" : param;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public String getParam() {
        return param;
    }

    /** Stable text form, used as the Kafka record key so one parameter keeps to one partition. */
    public String asKey() {
        return apiCollectionId + "|" + method + "|" + url + "|" + param;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParamLocation)) return false;
        ParamLocation other = (ParamLocation) o;
        return apiCollectionId == other.apiCollectionId
                && url.equals(other.url)
                && method.equals(other.method)
                && param.equals(other.param);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiCollectionId, url, method, param);
    }

    @Override
    public String toString() {
        return asKey();
    }
}

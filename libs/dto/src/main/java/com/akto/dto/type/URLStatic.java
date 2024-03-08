package com.akto.dto.type;

import com.akto.dto.type.URLMethods.Method;

public class URLStatic {
    
    String url;
    Method method;

    public URLStatic() {
    }

    public URLStatic(String url, Method method) {
        this.url = url;
        this.method = method;
    }

    public String getFullString() {
        return this.url + " " + this.method.name();
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Method getMethod() {
        return this.method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof URLStatic)) {
            return false;
        }
        URLStatic uRLStatic = (URLStatic) o;
        return this.getFullString().equalsIgnoreCase(uRLStatic.getFullString());
    }

    @Override
    public int hashCode() {
        return this.getFullString().hashCode();
    }

    @Override
    public String toString() {
        return "{" +
            " url='" + getUrl() + "'" +
            ", method='" + getMethod() + "'" +
            "}";
    }
    

}

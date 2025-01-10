package com.akto.threat.backend.db;

import com.akto.dto.type.URLMethods.Method;
import java.util.UUID;

public class AggregateSampleMaliciousEventModel {

  private String id;
  private String filterId;
  private String actor;
  private String ip;
  private String url;
  private String country;
  private Method method;
  private String orig;
  private int apiCollectionId;
  private long requestTime;

  public AggregateSampleMaliciousEventModel() {}

  private AggregateSampleMaliciousEventModel(Builder builder) {
    this.id = UUID.randomUUID().toString();
    this.filterId = builder.filterId;
    this.actor = builder.actor;
    this.ip = builder.ip;
    this.country = builder.country;
    this.method = builder.method;
    this.orig = builder.orig;
    this.requestTime = builder.requestTime;
    this.url = builder.url;
    this.apiCollectionId = builder.apiCollectionId;
  }

  public static class Builder {
    public int apiCollectionId;
    private String filterId;
    private String actor;
    private String ip;
    private String country;
    private String url;
    private Method method;
    private String orig;
    private long requestTime;

    public Builder setFilterId(String filterId) {
      this.filterId = filterId;
      return this;
    }

    public Builder setActor(String actor) {
      this.actor = actor;
      return this;
    }

    public Builder setIp(String ip) {
      this.ip = ip;
      return this;
    }

    public Builder setCountry(String country) {
      this.country = country;
      return this;
    }

    public Builder setUrl(String url) {
      this.url = url;
      return this;
    }

    public Builder setMethod(Method method) {
      this.method = method;
      return this;
    }

    public Builder setOrig(String orig) {
      this.orig = orig;
      return this;
    }

    public Builder setRequestTime(long requestTime) {
      this.requestTime = requestTime;
      return this;
    }

    public Builder setApiCollectionId(int apiCollectionId) {
      this.apiCollectionId = apiCollectionId;
      return this;
    }

    public AggregateSampleMaliciousEventModel build() {
      return new AggregateSampleMaliciousEventModel(this);
    }
  }

  public String getId() {
    return id;
  }

  public String getFilterId() {
    return filterId;
  }

  public String getActor() {
    return actor;
  }

  public String getIp() {
    return ip;
  }

  public String getUrl() {
    return url;
  }

  public String getCountry() {
    return country;
  }

  public Method getMethod() {
    return method;
  }

  public String getOrig() {
    return orig;
  }

  public long getRequestTime() {
    return requestTime;
  }

  public int getApiCollectionId() {
    return apiCollectionId;
  }

  public static Builder newBuilder() {
    return new Builder();
  }
}

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
  private String refId;
  private String metadata; 

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
    this.refId = builder.refId;
    this.metadata = builder.metadata; 
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
    private String refId;
    private String severity;
    private String type;
    private String metadata; 

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

    public Builder setRefId(String refId) {
      this.refId = refId;
      return this;
    }

    public Builder setSeverity(String severity) {
      this.severity = severity;
      return this;
    }

    public Builder setMetadata(String metadata) {
      this.metadata= metadata;
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

  public String getRefId() {
    return refId;
  }
  
  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String metadata) {
    this.metadata = metadata;
  }

  public static Builder newBuilder() {
    return new Builder();
  }
}

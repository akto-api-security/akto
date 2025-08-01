package com.akto.action.threat_detection;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ThreatConfigurationAction extends AbstractThreatDetectionAction {

private ThreatConfiguration threatConfiguration;

public ThreatConfiguration getThreatConfiguration() {
    return threatConfiguration;
}

public void setThreatConfiguration(ThreatConfiguration threatConfiguration) {
    this.threatConfiguration = threatConfiguration;
}



  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatConfigurationAction.class, LogDb.DASHBOARD);

  public ThreatConfigurationAction() {
    super();
  }

  public String fetchThreatConfiguration() {
    String requestUrl = String.format("%s/api/dashboard/get_threat_configuration", this.getBackendUrl());
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
    headers.put("Content-Type", Collections.singletonList("application/json"));

    OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "GET", "", headers, "application/json");

    try {
      OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);  
      String responseBody = resp.getBody();
      this.threatConfiguration = objectMapper.readValue(responseBody, ThreatConfiguration.class);

    } catch (Exception e) {
      e.printStackTrace();
      loggerMaker.errorAndAddToDb("Error while getting threat configuration" + e.getStackTrace());
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String modifyThreatConfiguration() {
    String requestUrl = String.format("%s/api/dashboard/modify_threat_configuration", this.getBackendUrl());
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
    headers.put("Content-Type", Collections.singletonList("application/json"));
    String json;
    try {
      json = objectMapper.writeValueAsString(this.threatConfiguration);
    } catch (Exception e) {
      e.printStackTrace();
      loggerMaker.errorAndAddToDb("Error while modifying threat configuration" + e.getStackTrace());
      return ERROR.toUpperCase();
    }
    OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "POST", json, headers, "application/json");
    try {
      OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);
      String responseBody = resp.getBody();
      this.threatConfiguration = objectMapper.readValue(responseBody, ThreatConfiguration.class);

      return SUCCESS.toUpperCase();
    } catch (Exception e) {
      e.printStackTrace();
      loggerMaker.errorAndAddToDb("Error while modifying threat configuration" + e.getStackTrace());
      return ERROR.toUpperCase();
    }
  }
}

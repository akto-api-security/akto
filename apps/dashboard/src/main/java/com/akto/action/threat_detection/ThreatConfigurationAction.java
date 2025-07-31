package com.akto.action.threat_detection;

import java.util.stream.Collectors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ThreatConfigurationAction extends AbstractThreatDetectionAction {

private ThreatConfiguration threatConfiguration;

public ThreatConfiguration getThreatConfiguration() {
    return threatConfiguration;
}

public void setThreatConfiguration(ThreatConfiguration threatConfiguration) {
    this.threatConfiguration = threatConfiguration;
}
// TODO: remove this, use API Executor.
  private final CloseableHttpClient httpClient;



  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatConfigurationAction.class, LogDb.DASHBOARD);

  public ThreatConfigurationAction() {
    super();
    this.httpClient = HttpClients.createDefault();
  }

  public String fetchThreatConfiguration() {
    HttpGet get =
        new HttpGet(
            String.format("%s/api/dashboard/get_threat_configuration", this.getBackendUrl()));
    get.addHeader("Authorization", "Bearer " + this.getApiToken());
    get.addHeader("Content-Type", "application/json");

    try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
      String responseBody = EntityUtils.toString(resp.getEntity());
      this.threatConfiguration = objectMapper.readValue(responseBody, ThreatConfiguration.class);

    } catch (Exception e) {
      e.printStackTrace();
      loggerMaker.errorAndAddToDb("Error while getting threat configuration" + e.getStackTrace());
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String modifyThreatConfiguration() {
    HttpPost post =
        new HttpPost(
            String.format("%s/api/dashboard/modify_threat_configuration", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");
    try {
      String json = objectMapper.writeValueAsString(this.threatConfiguration);
      post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
    } catch (Exception e) {
      e.printStackTrace();
      loggerMaker.errorAndAddToDb("Error while modifying threat configuration" + e.getStackTrace());
      return ERROR.toUpperCase();
    }
    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());
      this.threatConfiguration = objectMapper.readValue(responseBody, ThreatConfiguration.class);

      return SUCCESS.toUpperCase();
    } catch (Exception e) {
      e.printStackTrace();
      loggerMaker.errorAndAddToDb("Error while modifying threat configuration" + e.getStackTrace());
      return ERROR.toUpperCase();
    }
  }
}

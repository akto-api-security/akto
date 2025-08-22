package com.akto.action;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.akto.dao.testing.sources.TestReportsDao;
import com.akto.dto.testing.sources.TestReports;
import com.mongodb.MongoCommandException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;

import org.apache.struts2.ServletActionContext;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import com.akto.ApiRequest;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.Token;
import com.fasterxml.jackson.databind.JsonNode;

import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.CollectionWiseTestingEndpoints;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.util.Constants;


public class ReportAction extends UserAction {

    private String reportId;
    private String organizationName;
    private String username;
    private String reportDate;
    private String reportUrl;
    private String pdf;
    private String status;
    private boolean firstPollRequest;

    private static final LoggerMaker loggerMaker = new LoggerMaker(ReportAction.class, LogDb.DASHBOARD);

    public String downloadReportPDF() {
        if(reportUrl == null || reportUrl.isEmpty()) {
            status = "ERROR";
            addActionError("Report URL cannot be empty");
            return ERROR.toUpperCase();
        }

        String reportUrlId;
        try {
            String path = new URL(reportUrl).getPath();
            String[] segments = path.split("/");
            reportUrlId = segments[segments.length - 1];
        } catch (Exception e) {
            status = "ERROR";
            addActionError("Report URL cannot be empty");
            return ERROR.toUpperCase();
        }

        if(!ObjectId.isValid(reportUrlId)) {
            status = "ERROR";
            addActionError("Report URL is invalid");
            return ERROR.toUpperCase();
        }

        ObjectId reportUrlIdObj = new ObjectId(reportUrlId);

        if(firstPollRequest) {
            TestReports testReport = TestReportsDao.instance.findOne(Filters.eq("_id", reportUrlIdObj));
            if(testReport != null && (testReport.getPdfReportString() != null && !testReport.getPdfReportString().isEmpty())) {
                status = "COMPLETED";
                pdf = testReport.getPdfReportString();
                return SUCCESS.toUpperCase();
            }
        }

        if (reportId == null) {
            // Initiate PDF generation

            reportId = new ObjectId().toHexString();
            loggerMaker.debugAndAddToDb("Triggering pdf download for report id - " + reportId, LogDb.DASHBOARD);

            // Make call to puppeteer service
            try {
                HttpServletRequest request = ServletActionContext.getRequest();
                HttpSession session = request.getSession();
                String jsessionId = session.getId();
                User user = getSUser();
                String accessToken = Token.generateAccessToken(user.getLogin(), "true");

                // Set login time if API triggered PDF download
                String apiKey = request.getHeader("X-API-KEY");
                boolean apiKeyFlag = apiKey != null;
                if (apiKeyFlag) {
                    session.setAttribute("login", Context.now());
                }

                String url = System.getenv("PUPPETEER_REPLAY_SERVICE_URL") + "/downloadReportPDF";
                JSONObject requestBody = new JSONObject();
                requestBody.put("reportId", reportId);
                requestBody.put("username", username);
                requestBody.put("accessToken", accessToken);
                requestBody.put("jsessionId", jsessionId);
                requestBody.put("organizationName", organizationName);
                requestBody.put("reportDate", reportDate);
                requestBody.put("reportUrl", reportUrl);
                String reqData = requestBody.toString();
                JsonNode node = ApiRequest.postRequest(new HashMap<>(), url, reqData);
                status = node.get("status").textValue();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while triggering pdf download for report id - " + reportId, LogDb.DASHBOARD);
                status = "ERROR";
            }
        } else {
            // Check for report completion
            loggerMaker.debugAndAddToDb("Polling pdf download status for report id - " + reportId, LogDb.DASHBOARD);

            try {
                String url = System.getenv("PUPPETEER_REPLAY_SERVICE_URL") + "/downloadReportPDF";
                JSONObject requestBody = new JSONObject();
                requestBody.put("reportId", reportId);
                String reqData = requestBody.toString();
                JsonNode node = ApiRequest.postRequest(new HashMap<>(), url, reqData);
                if(node == null) {
                    addActionError("The report is too large to save. Please reduce its size and try again.");
                    status = "ERROR";
                    return ERROR.toUpperCase();
                }
                status = (String) node.get("status").textValue();
                loggerMaker.debugAndAddToDb("Pdf download status for report id - " + reportId + " - " + status, LogDb.DASHBOARD);

                if (status.equals("COMPLETED")) {
                    loggerMaker.debugAndAddToDb("Pdf download status for report id - " + reportId + " completed. Attaching pdf in response ", LogDb.DASHBOARD);
                    pdf = node.get("base64PDF").textValue();
                    try {
                        TestReportsDao.instance.updateOne(Filters.eq("_id", reportUrlIdObj), Updates.set(TestReports.PDF_REPORT_STRING, pdf));
                    } catch(Exception e) {
                        loggerMaker.errorAndAddToDb("Error: " + e.getMessage() + ", while updating report binary for reportId: " + reportId, LogDb.DASHBOARD);
                        if (e instanceof MongoCommandException) {
                            MongoCommandException mongoException = (MongoCommandException) e;
                            if (mongoException.getCode() == 17420) {
                                addActionError("The report is too large to save. Please reduce its size and try again.");
                            } else {
                                addActionError("A database error occurred while saving the report. Try again later.");
                            }
                        } else {
                            addActionError("An error occurred while updating the report in DB. Please try again.");
                        }
                        status = "ERROR";
                    }
                } else if(status.equals("FAILED")) {
                    status = "ERROR";
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while polling pdf download for report id - " + reportId, LogDb.DASHBOARD);
                status = "ERROR";
            }
        }

        return SUCCESS.toUpperCase();
    }

    public String downloadSamplePdf() {
        try {
            String url = System.getenv("PUPPETEER_REPLAY_SERVICE_URL") + "/samplePDF";

            JSONObject requestBody = new JSONObject();
            String reqData = requestBody.toString();

            JsonNode responseNode = ApiRequest.postRequest(new HashMap<>(), url, reqData);
            
            if (responseNode == null || !responseNode.has("base64PDF")) {
                status = "ERROR";
                System.err.println("No PDF data found in the response.");
                return ERROR.toUpperCase();
            }

            pdf = responseNode.get("base64PDF").asText();

            status = "COMPLETED";
            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            status = "ERROR";
            e.printStackTrace();
            return ERROR.toUpperCase();
        }
    }

    @Setter
    private String testingRunId;

    @Getter
    private String uniqueHostsTestedForRun;

    public String getUniqueHostsTested() {
        if (testingRunId == null || testingRunId.isEmpty()) {
            return ERROR.toUpperCase();
        }

        try {
            // Fetch the testing run by ID
            TestingRun testingRun = TestingRunDao.instance.findOne(Filters.eq("_id", new ObjectId(testingRunId)));
            if (testingRun == null || testingRun.getTestingEndpoints() == null) {
                this.uniqueHostsTestedForRun = "";
                return SUCCESS.toUpperCase();
            }

            TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
            Set<String> uniqueHosts = new HashSet<>();
            Set<Integer> uniqueApiCollectionIds = new HashSet<>(); 

            if (testingEndpoints.getType().equals(TestingEndpoints.Type.COLLECTION_WISE)) {
                // Handle collection-wise testing endpoints
                CollectionWiseTestingEndpoints collectionWiseEndpoints = (CollectionWiseTestingEndpoints) testingEndpoints;
                int apiCollectionId = collectionWiseEndpoints.getApiCollectionId();
                
                // Get the API collection to check if it has a hostname
                ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(
                    Filters.eq(Constants.ID, apiCollectionId),
                    Projections.include(ApiCollection.HOST_NAME)
                );
                
                if (apiCollection != null && apiCollection.getHostName() != null && !apiCollection.getHostName().isEmpty()) {
                    // If collection has hostname, return it
                    this.uniqueHostsTestedForRun = apiCollection.getHostName();
                    return SUCCESS.toUpperCase();
                } else {
                    // If no hostname, get unique hosts from API info keys
                    List<ApiInfo.ApiInfoKey> apiInfoKeys = testingEndpoints.returnApis();
                    for (ApiInfo.ApiInfoKey apiInfoKey : apiInfoKeys) {
                        String hostname = extractHostnameFromUrl(apiInfoKey.getUrl());
                        if (hostname != null && !hostname.isEmpty()) {
                            uniqueHosts.add(hostname);
                        }
                        uniqueApiCollectionIds.add(apiInfoKey.getApiCollectionId());
                    }
                }
            } else if (testingEndpoints.getType() == TestingEndpoints.Type.CUSTOM) {
                // Handle custom testing endpoints
                CustomTestingEndpoints customEndpoints = (CustomTestingEndpoints) testingEndpoints;
                List<ApiInfo.ApiInfoKey> apisList = customEndpoints.getApisList();
                
                if (apisList != null) {
                    for (ApiInfo.ApiInfoKey apiInfoKey : apisList) {
                        String hostname = extractHostnameFromUrl(apiInfoKey.getUrl());
                        if (hostname != null && !hostname.isEmpty()) {
                            uniqueHosts.add(hostname);
                        }
                        uniqueApiCollectionIds.add(apiInfoKey.getApiCollectionId());
                    }
                }
            }

            // Get unique hosts from API collections for type: API_GROUP
            if (uniqueApiCollectionIds.size() > 0) {
                List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(Filters.and(Filters.in(Constants.ID, uniqueApiCollectionIds), Filters.ne(ApiCollection._DEACTIVATED, true)), Projections.include(ApiCollection.HOST_NAME));
                for (ApiCollection apiCollection : apiCollections) {
                    if (apiCollection.getHostName() != null && !apiCollection.getHostName().isEmpty()) {
                        uniqueHosts.add(apiCollection.getHostName());
                    }
                }
            }

            // Return comma-separated list of unique hosts
            this.uniqueHostsTestedForRun = String.join(", ", uniqueHosts);
            return SUCCESS.toUpperCase();
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error getting unique hosts tested: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }

    }

    private String extractHostnameFromUrl(String url) {
        try {
            if (url == null || url.isEmpty()) {
                return null;
            }
            
            // Only process URLs that start with http:// or https://
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return null;
            }
            
            URI uri = new URI(url);
            return uri.getHost();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error extracting hostname from URL: " + url + " - " + e.getMessage(), LogDb.DASHBOARD);
            return null;
        }
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getReportDate() {
        return reportDate;
    }

    public void setReportDate(String reportDate) {
        this.reportDate = reportDate;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    public void setReportUrl(String reportUrl) {
        this.reportUrl = reportUrl;
    }

    public String getPdf() {
        return pdf;
    }

    public void setPdf(String pdf) {
        this.pdf = pdf;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setFirstPollRequest(boolean firstPollRequest) {
        this.firstPollRequest = firstPollRequest;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

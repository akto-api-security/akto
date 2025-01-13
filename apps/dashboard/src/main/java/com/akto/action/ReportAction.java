package com.akto.action;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.akto.dao.testing.sources.TestReportsDao;
import com.akto.dto.testing.sources.TestReports;
import com.mongodb.MongoCommandException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.struts2.ServletActionContext;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import com.akto.ApiRequest;
import com.akto.TimeoutObject;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.Token;
import com.fasterxml.jackson.databind.JsonNode;
import com.twilio.rest.proxy.v1.service.Session;

public class ReportAction extends UserAction {

    private String reportId;
    private String organizationName;
    private String reportDate;
    private String reportUrl;
    private String pdf;
    private String status;
    private boolean firstPollRequest;

    private static final LoggerMaker loggerMaker = new LoggerMaker(ReportAction.class);

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
            loggerMaker.infoAndAddToDb("Triggering pdf download for report id - " + reportId, LogDb.DASHBOARD);

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
                requestBody.put("username", user.getName());
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
            loggerMaker.infoAndAddToDb("Polling pdf download status for report id - " + reportId, LogDb.DASHBOARD);

            try {
                String url = System.getenv("PUPPETEER_REPLAY_SERVICE_URL") + "/downloadReportPDF";
                JSONObject requestBody = new JSONObject();
                requestBody.put("reportId", reportId);
                String reqData = requestBody.toString();
                JsonNode node = ApiRequest.postRequest(new HashMap<>(), url, reqData);
                status = (String) node.get("status").textValue();
                loggerMaker.infoAndAddToDb("Pdf download status for report id - " + reportId + " - " + status, LogDb.DASHBOARD);

                if (status.equals("COMPLETED")) {
                    loggerMaker.infoAndAddToDb("Pdf download status for report id - " + reportId + " completed. Attaching pdf in response ", LogDb.DASHBOARD);
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
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while polling pdf download for report id - " + reportId, LogDb.DASHBOARD);
                status = "ERROR";
            }
        }

        return SUCCESS.toUpperCase();
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
}

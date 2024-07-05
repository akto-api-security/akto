package com.akto.action;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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

    private static final LoggerMaker loggerMaker = new LoggerMaker(ReportAction.class);
    
    public String downloadReportPDF() {
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
}

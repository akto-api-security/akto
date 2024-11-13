package com.akto.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.struts2.ServletActionContext;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import com.akto.ApiRequest;
import com.akto.dao.TestReportsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.TestReport;
import com.akto.dto.User;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.EmailAccountName;
import com.akto.utils.Token;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.model.Filters;

public class ReportAction extends UserAction {

    private String reportId;
    private String organizationName;
    private String reportDate;
    private String reportUrl;
    private String pdf;
    private String status;

    private static final LoggerMaker loggerMaker = new LoggerMaker(ReportAction.class, LogDb.DASHBOARD);
    
    public String downloadReportPDF() {
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
            downloadReportPDFCore(jsessionId, accessToken);
        } catch(Exception e){
            loggerMaker.errorAndAddToDb(e, "Error while triggering pdf download for report id - " + reportId, LogDb.DASHBOARD);
            status = "ERROR";
        }

        return SUCCESS.toUpperCase();
    }

    private void downloadReportPDFCore(String jsessionId, String accessToken){
        if (reportId == null) {
            // Initiate PDF generation

            reportId = new ObjectId().toHexString();
            loggerMaker.infoAndAddToDb("Triggering pdf download for report id - " + reportId, LogDb.DASHBOARD);

            // Make call to puppeteer service
            try {
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
    }

    String testingRunResultSummaryHexId;
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public String saveReportPDF() {
        TestingRunResultSummary summary = TestingRunResultSummariesDao.instance.findOne(Constants.ID,
                new ObjectId(this.testingRunResultSummaryHexId));
        if (summary != null && TestingRun.State.COMPLETED.equals(summary.getState())) {
            TestingRun testingRun = TestingRunDao.instance.findOne(
                    Filters.eq(Constants.ID, summary.getTestingRunId()));
            if (testingRun.getSaveReport()) {
                TestReport rp = TestReportsDao.instance.findOne(
                        Filters.and(
                                Filters.eq(TestReport._ACCOUNT_ID, Context.accountId.get()),
                                Filters.eq(TestReport._TESTING_RUN_ID, testingRun.getId().toHexString()),
                                Filters.eq(TestReport._TESTING_RUN_RESULT_SUMMARY_ID,
                                        this.testingRunResultSummaryHexId)));
                if (rp == null) {
                    int accountId = Context.accountId.get();
                    Map<String, Object> outerSession = getSession();
                    HttpServletRequest request = ServletActionContext.getRequest();
                    HttpSession session = request.getSession();
                    String jsessionId = session.getId();
                    User user = getSUser();
                    String accessToken = "";
                    try {
                        accessToken = Token.generateAccessToken(user.getLogin(), "true");
                    } catch (Exception e) {
                    }
                    String accessToken2 = accessToken;
                    executorService.schedule(new Runnable() {
                        public void run() {
                            Context.accountId.set(accountId);
                            ReportAction reportAction = new ReportAction();
                            String accessTokenInternal = accessToken2;
                            reportAction.setSession(outerSession);
                            String username = getSUser().getLogin();
                            EmailAccountName emailAccountName = new EmailAccountName(username);
                            String accountName = emailAccountName.getAccountName();
                            reportAction.setOrganizationName(accountName);
                            reportAction.setReportUrl(reportUrl);
                            int count = 0;
                            while (true) {
                                reportAction.downloadReportPDFCore(jsessionId, accessTokenInternal);
                                count++;
                                if (count > 30) {
                                    break;
                                }
                                if ("COMPLETED".equals(reportAction.getStatus())) {
                                    saveReport(testingRun, testingRunResultSummaryHexId, reportAction.getPdf());
                                    break;
                                }
                                try {
                                    Thread.sleep(2500);
                                } catch (Exception e) {
                                }
                            }
                        }
                    }, 0, TimeUnit.SECONDS);
                }
            }
        }
        return SUCCESS.toUpperCase();
    }

    private void saveReport(TestingRun testingRun, String testingRunResultSummaryId, String pdf) {
        try {
            if (testingRun.getSaveReport()) {
                List<String> pdfParts = new ArrayList<>();
                final int LENGTH = 10 * 1024 * 1024;
                for (int i = 0; i <= pdf.length() / LENGTH; i++) {
                    pdfParts.add(pdf.substring(i, Math.min((i + 1) * LENGTH, pdf.length())));
                }
                TestReport testReport = new TestReport(pdfParts, Context.accountId.get(), testingRunResultSummaryId,
                        testingRun.getId().toHexString());
                TestReportsDao.instance.insertOne(testReport);
            }
        } catch (Exception f) {
            loggerMaker.errorAndAddToDb(f, "Error while saving pdf download for report id - " + reportId,
                    LogDb.DASHBOARD);
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

    public String getTestingRunResultSummaryHexId() {
        return testingRunResultSummaryHexId;
    }

    public void setTestingRunResultSummaryHexId(String testingRunResultSummaryHexId) {
        this.testingRunResultSummaryHexId = testingRunResultSummaryHexId;
    }
}

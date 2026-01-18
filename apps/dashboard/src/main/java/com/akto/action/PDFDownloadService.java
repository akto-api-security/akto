package com.akto.action;

import java.net.URL;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.struts2.ServletActionContext;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import com.akto.ApiRequest;
import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.Token;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoCommandException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class PDFDownloadService {

    private static final LoggerMaker loggerMaker = new LoggerMaker(PDFDownloadService.class, LogDb.DASHBOARD);

    public static class PDFDownloadResult {
        private String pdf;
        private String status;
        private String error;
        private String reportId;

        public PDFDownloadResult(String pdf, String status, String error, String reportId) {
            this.pdf = pdf;
            this.status = status;
            this.error = error;
            this.reportId = reportId;
        }

        public String getPdf() {
            return pdf;
        }

        public String getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public String getReportId() {
            return reportId;
        }
    }

    public static <T> PDFDownloadResult downloadPDF(
            String reportId,
            String organizationName,
            String reportDate,
            String reportUrl,
            String username,
            boolean firstPollRequest,
            AccountsContextDao<T> dao,
            String pdfReportStringField,
            User user,
            String reportType) {

        if (reportUrl == null || reportUrl.isEmpty()) {
            return new PDFDownloadResult(null, "ERROR", "Report URL cannot be empty", reportId);
        }

        String reportUrlId;
        try {
            String path = new URL(reportUrl).getPath();
            String[] segments = path.split("/");
            reportUrlId = segments[segments.length - 1];
        } catch (Exception e) {
            return new PDFDownloadResult(null, "ERROR", "Report URL cannot be empty", reportId);
        }

        if (!ObjectId.isValid(reportUrlId)) {
            return new PDFDownloadResult(null, "ERROR", "Report URL is invalid", reportId);
        }

        ObjectId reportUrlIdObj = new ObjectId(reportUrlId);

        if (firstPollRequest) {
            try {
                T report = dao.findOne(Filters.eq("_id", reportUrlIdObj));
                if (report != null) {
                    java.lang.reflect.Method getter = report.getClass().getMethod("getPdfReportString");
                    String existingPdf = (String) getter.invoke(report);
                    if (existingPdf != null && !existingPdf.isEmpty()) {
                        return new PDFDownloadResult(existingPdf, "COMPLETED", null, reportId);
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error checking existing PDF for " + reportType + " download", LogDb.DASHBOARD);
            }
        }

        if (reportId == null) {
            reportId = new ObjectId().toHexString();
            loggerMaker.debugAndAddToDb("Triggering " + reportType + " download for report id - " + reportId, LogDb.DASHBOARD);

            try {
                HttpServletRequest request = ServletActionContext.getRequest();
                HttpSession session = request.getSession();
                String jsessionId = session.getId();
                String accessToken = Token.generateAccessToken(user.getLogin(), "true");

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
                String status = node.get("status").textValue();
                return new PDFDownloadResult(null, status, null, reportId);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while triggering " + reportType + " download for report id - " + reportId, LogDb.DASHBOARD);
                return new PDFDownloadResult(null, "ERROR", e.getMessage(), reportId);
            }
        } else {
            loggerMaker.debugAndAddToDb("Polling " + reportType + " download status for report id - " + reportId, LogDb.DASHBOARD);

            try {
                String url = System.getenv("PUPPETEER_REPLAY_SERVICE_URL") + "/downloadReportPDF";
                JSONObject requestBody = new JSONObject();
                requestBody.put("reportId", reportId);
                String reqData = requestBody.toString();
                JsonNode node = ApiRequest.postRequest(new HashMap<>(), url, reqData);

                if (node == null) {
                    return new PDFDownloadResult(null, "ERROR", "The report is too large to save. Please reduce its size and try again.", reportId);
                }

                String status = node.get("status").textValue();
                loggerMaker.debugAndAddToDb(reportType + " download status for report id - " + reportId + " - " + status, LogDb.DASHBOARD);

                if (status.equals("COMPLETED")) {
                    loggerMaker.debugAndAddToDb(reportType + " download status for report id - " + reportId + " completed. Attaching pdf in response", LogDb.DASHBOARD);
                    String pdf = node.get("base64PDF").textValue();
                    try {
                        dao.updateOne(Filters.eq("_id", reportUrlIdObj), Updates.set(pdfReportStringField, pdf));
                        return new PDFDownloadResult(pdf, "COMPLETED", null, reportId);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error: " + e.getMessage() + ", while updating report binary for reportId: " + reportId, LogDb.DASHBOARD);
                        String errorMsg;
                        if (e instanceof MongoCommandException) {
                            MongoCommandException mongoException = (MongoCommandException) e;
                            if (mongoException.getCode() == 17420) {
                                errorMsg = "The report is too large to save. Please reduce its size and try again.";
                            } else {
                                errorMsg = "A database error occurred while saving the report. Try again later.";
                            }
                        } else {
                            errorMsg = "An error occurred while updating the report in DB. Please try again.";
                        }
                        return new PDFDownloadResult(pdf, "ERROR", errorMsg, reportId);
                    }
                } else if (status.equals("FAILED")) {
                    return new PDFDownloadResult(null, "ERROR", "PDF generation failed", reportId);
                }

                return new PDFDownloadResult(null, status, null, reportId);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while polling " + reportType + " download for report id - " + reportId, LogDb.DASHBOARD);
                return new PDFDownloadResult(null, "ERROR", e.getMessage(), reportId);
            }
        }
    }
}

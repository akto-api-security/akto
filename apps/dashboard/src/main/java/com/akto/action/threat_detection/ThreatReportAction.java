package com.akto.action.threat_detection;

import java.util.*;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.sources.ThreatReportsDao;
import com.akto.dto.User;
import com.akto.dto.testing.sources.ThreatReports;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;

import lombok.Getter;
import lombok.Setter;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

@Getter
@Setter
public class ThreatReportAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatReportAction.class, LogDb.DASHBOARD);

    private Map<String, List<String>> filtersForReport;
    private String generatedReportId;
    private List<String> threatIdsForReport;
    private BasicDBObject response;

    // Fields for PDF download
    private String reportId;
    private String organizationName;
    private String username;
    private String reportDate;
    private String reportUrl;
    private String pdf;
    private String status;
    private boolean firstPollRequest;

    /**
     * Generates a threat report document in MongoDB with the provided filters
     * @return SUCCESS if report is created, ERROR otherwise
     */
    public String generateThreatReport() {
        try {
            // Ensure label filter is set to THREAT
            if (filtersForReport == null) {
                filtersForReport = new HashMap<>();
            }
            filtersForReport.put("label", Arrays.asList("THREAT"));

            ThreatReports threatReport = new ThreatReports(filtersForReport, Context.now(), "", threatIdsForReport);
            InsertOneResult insertResult = ThreatReportsDao.instance.insertOne(threatReport);
            this.generatedReportId = insertResult.getInsertedId().asObjectId().getValue().toHexString();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in generating threat report", LogDb.DASHBOARD);
            addActionError("Error in generating threat report");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Retrieves the filters for a previously generated threat report
     * @return SUCCESS if filters are found, ERROR otherwise
     */
    public String getThreatReportFilters() {
        if (this.generatedReportId == null) {
            addActionError("Report id cannot be null");
            return ERROR.toUpperCase();
        }

        try {
            response = new BasicDBObject();
            ObjectId reportId = new ObjectId(this.generatedReportId);
            ThreatReports reportDoc = ThreatReportsDao.instance.findOne(Filters.eq(Constants.ID, reportId));

            if (reportDoc == null) {
                addActionError("Threat report not found");
                return ERROR.toUpperCase();
            }

            response.put(ThreatReports.FILTERS_FOR_REPORT, reportDoc.getFiltersForReport());
            response.put(ThreatReports.THREAT_IDS_FOR_REPORT, reportDoc.getThreatIdsForReport());
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error retrieving threat report filters", LogDb.DASHBOARD);
            addActionError("Error retrieving threat report filters");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Downloads threat report PDF - handles both initiation and polling
     * Uses PDFDownloadService to centralize PDF generation logic
     * @return SUCCESS for all status updates, ERROR on exceptions
     */
    public String downloadThreatReportPDF() {
        try {
            User user = getSUser();
            com.akto.action.PDFDownloadService.PDFDownloadResult result = com.akto.action.PDFDownloadService.downloadPDF(
                reportId,
                organizationName,
                reportDate,
                reportUrl,
                username,
                firstPollRequest,
                ThreatReportsDao.instance,
                ThreatReports.PDF_REPORT_STRING,
                user,
                "threat PDF"
            );

            this.pdf = result.getPdf();
            this.status = result.getStatus();
            this.reportId = result.getReportId();

            if (result.getError() != null) {
                addActionError(result.getError());
                if (this.status.equals("ERROR")) {
                    return ERROR.toUpperCase();
                }
            }

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in downloadThreatReportPDF", LogDb.DASHBOARD);
            status = "ERROR";
            addActionError("An unexpected error occurred during PDF download");
            return ERROR.toUpperCase();
        }
    }
}

package com.akto.dto.reports;

import org.bson.codecs.pojo.annotations.BsonId;

import java.util.ArrayList;

public class ReportMeta {

    @BsonId
    String id;

    ArrayList<ReportElement> reportElements;
    int receiverId;
    ReportSchedule reportSchedule;

    public ReportMeta() {}

    public ReportMeta(ArrayList<ReportElement> reportElements, int receiverId, ReportSchedule reportSchedule) {
        this.reportElements = reportElements;
        this.receiverId = receiverId;
        this.reportSchedule = reportSchedule;
    }

    public ArrayList<ReportElement> getReportElements() {
        return reportElements;
    }

    public void setReportElements(ArrayList<ReportElement> reportElements) {
        this.reportElements = reportElements;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(int receiverId) {
        this.receiverId = receiverId;
    }

    public ReportSchedule getReportSchedule() {
        return reportSchedule;
    }

    public void setReportSchedule(ReportSchedule reportSchedule) {
        this.reportSchedule = reportSchedule;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}

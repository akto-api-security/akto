package com.akto.action;

import java.util.Base64;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import com.akto.dao.TestReportsDao;
import com.akto.dto.TestReport;
import com.akto.util.Util;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.ActionSupport;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class PublicAction extends ActionSupport implements ServletRequestAware, ServletResponseAware {

    protected HttpServletRequest request;
    protected HttpServletResponse response;
    private InputStream inputStream;
    private String filename;

    /*
     * This API is not behind authentication checks.
     */
    public String accessReportPublic() {
        String queryString = request.getQueryString();
        String accountIdBase64 = Util.getValueFromQueryString(queryString, "accountId");
        String testSummaryId = Util.getValueFromQueryString(queryString, "summaryId");
        int accountId = Integer.parseInt(new String(Base64.getDecoder().decode(accountIdBase64)));

        TestReport report = TestReportsDao.instance.findOne(
                Filters.and(
                        Filters.eq(TestReport._ACCOUNT_ID, accountId),
                        Filters.eq(TestReport._TESTING_RUN_RESULT_SUMMARY_ID, testSummaryId)));

        String pdf = "";
        if (report.getReportEncoded() != null && !report.getReportEncoded().isEmpty()) {
            for (String chunks : report.getReportEncoded()) {
                pdf += chunks;
            }
        }

        try {
            // Decode the base64 content
            byte[] pdfBytes = Base64.getDecoder().decode(pdf);
            setFilename("report.pdf");
            inputStream = new ByteArrayInputStream(pdfBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.response= response;
    }

}

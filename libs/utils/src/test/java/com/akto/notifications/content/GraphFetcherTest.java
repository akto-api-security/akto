package com.akto.notifications.content;

import com.akto.dao.context.Context;
import com.akto.dto.notifications.content.CompositeContent;
import com.akto.dto.reports.MetricReportElement;
import com.akto.dto.reports.ReportElement;
import com.akto.dto.reports.ReportMeta;
import com.akto.dto.reports.ReportSchedule;
import com.akto.notifications.reports.ReportGenerator;
import com.akto.utils.DaoConnect;
import org.junit.Test;

import java.util.ArrayList;

public class GraphFetcherTest extends DaoConnect {

    @Test
    public void reportGenerator() throws Exception {
        Context.accountId.set(1619721922);
        ArrayList<ReportElement> reportElements = new ArrayList<>();
        reportElements.add(new MetricReportElement(1621809755));
        CompositeContent compositeContent = ReportGenerator.getReport(new ReportMeta(reportElements, 1621799967, new ReportSchedule()));

        PDFVisitor.run(compositeContent, "/var/tmp/1619721922_1621809755.pdf");
    }

}

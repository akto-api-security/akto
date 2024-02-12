package com.akto.notifications.reports;

import com.akto.dao.context.Context;
import com.akto.dto.notifications.content.*;
import com.akto.dto.reports.ReportElement;
import com.akto.dto.reports.ReportMeta;
import com.akto.notifications.util.GraphFetcher;
import com.akto.util.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ReportGenerator {


    public static CompositeContent getReport(ReportMeta reportMeta) {

        CompositeContent compositeContent = new CompositeContent();

        for(ReportElement reportElement: reportMeta.getReportElements()) {

            BlockContent blockContent = new BlockContent();
            compositeContent.getContents().add(blockContent);
            ArrayList<Content> contents = blockContent.getContents();
        }

        return compositeContent;

    }

}

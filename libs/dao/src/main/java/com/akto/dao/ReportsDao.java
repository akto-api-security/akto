package com.akto.dao;

import com.akto.dto.reports.ReportMeta;

public class ReportsDao extends AccountsContextDao<ReportMeta> {

    public static final ReportsDao instance = new ReportsDao();

    private ReportsDao() {}

    @Override
    public String getCollName() {
        return "reports";
    }

    @Override
    public Class<ReportMeta> getClassT() {
        return ReportMeta.class;
    }
}

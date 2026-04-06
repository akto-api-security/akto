package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.threat_detection.HyperScanTemplate;

public class HyperScanTemplateDao extends AccountsContextDao<HyperScanTemplate> {

    public static final HyperScanTemplateDao instance = new HyperScanTemplateDao();

    @Override
    public String getCollName() {
        return "hyper_scan_templates";
    }

    @Override
    public Class<HyperScanTemplate> getClassT() {
        return HyperScanTemplate.class;
    }
}

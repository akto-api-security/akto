package com.akto.dao.test_editor.strategy;

import java.util.Map;

import com.akto.dto.test_editor.Strategy;

public class StrategyParser {
    
    public Strategy parse(Object metadataObj) {
        Map<String, Object> metadataMap = (Map) metadataObj;
        Strategy strategy = new Strategy();

        if (metadataMap == null) {
            return strategy;
        }

        Object val = metadataMap.get("run_once");
        if (val != null) {
            strategy.setRunOnce(val.toString());
        }

        Object insertVulnApi = metadataMap.get("insertVulnApi");
        if (insertVulnApi != null) {
            strategy.setInsertVulnApi((boolean) insertVulnApi);
        }

        return strategy;
    }

}

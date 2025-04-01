package com.akto.dto.test_run_findings;
import java.util.Map;
import com.mongodb.BasicDBObject;

public class SourceCodeVulnerabilities {
    private String agentProcessId;
    public static final String AGENT_PROCESS_ID = "agentProcessId";

    private int totalApisScanned;
    public static final String TOTAL_APIS_SCANNED = "totalApisScanned";

    private Map<String, BasicDBObject> vulnerabilitiesMap;
    public static final String VULNERABILITIES_MAP = "vulnerabilitiesMap";

    public SourceCodeVulnerabilities(String agentProcessId, int totalApisScanned, Map<String, BasicDBObject> vulnerabilitiesMap) {
        this.agentProcessId = agentProcessId;
        this.totalApisScanned = totalApisScanned;
        this.vulnerabilitiesMap = vulnerabilitiesMap;
    }

    public SourceCodeVulnerabilities() {
    }

    public String getAgentProcessId() {
        return agentProcessId;
    }

    public void setAgentProcessId(String agentProcessId) {
        this.agentProcessId = agentProcessId;
    }

    public int getTotalApisScanned() {
        return totalApisScanned;
    }

    public void setTotalApisScanned(int totalApisScanned) {
        this.totalApisScanned = totalApisScanned;
    }

    public Map<String, BasicDBObject> getVulnerabilitiesMap() {
        return vulnerabilitiesMap;
    }

    public void setVulnerabilitiesMap(Map<String, BasicDBObject> vulnerabilitiesMap) {
        this.vulnerabilitiesMap = vulnerabilitiesMap;
    }

}

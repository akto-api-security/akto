package com.akto.action.endpoint_shield;

import com.akto.action.UserAction;
import com.akto.dao.endpoint_shield.EndpointShieldDiagnosticDao;
import com.akto.dto.endpoint_shield.EndpointShieldDiagnostic;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

/**
 * Device panel → Diagnostics tab: a device's diagnostic history (installer,
 * agent post-provision, on-demand reports) and a single report.
 */
@Getter
@Setter
public class EndpointShieldDiagnosticsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(EndpointShieldDiagnosticsAction.class, LogDb.DASHBOARD);
    private static final int MAX_LIST = 20; // the db-abstractor keeps the newest 20 per device

    // Request
    private String deviceId;
    private int limit;
    private String id;

    // Response
    private List<Map<String, Object>> diagnostics;
    private Map<String, Object> diagnostic;

    /** Summaries (no report text), newest first. */
    public String fetchEndpointShieldDiagnostics() {
        if (StringUtils.isBlank(deviceId)) {
            addActionError("deviceId is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            int lim = (limit <= 0 || limit > MAX_LIST) ? MAX_LIST : limit;
            List<EndpointShieldDiagnostic> rows = EndpointShieldDiagnosticDao.instance.findAll(
                    Filters.eq(EndpointShieldDiagnostic.DEVICE_ID, deviceId), 0, lim,
                    Sorts.descending(EndpointShieldDiagnostic.CREATED_AT),
                    Projections.exclude(EndpointShieldDiagnostic.REPORT));
            diagnostics = new ArrayList<>(rows.size());
            for (EndpointShieldDiagnostic d : rows) {
                diagnostics.add(toView(d, false));
            }
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "fetchEndpointShieldDiagnostics failed: " + e.getMessage());
            addActionError("Could not load diagnostics");
            return Action.ERROR.toUpperCase();
        }
    }

    /** One report, including its full text. */
    public String fetchEndpointShieldDiagnostic() {
        if (StringUtils.isBlank(id) || !ObjectId.isValid(id)) {
            addActionError("valid id is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            EndpointShieldDiagnostic d = EndpointShieldDiagnosticDao.instance.findOne(Filters.eq("_id", new ObjectId(id)));
            if (d == null) {
                addActionError("diagnostic not found");
                return Action.ERROR.toUpperCase();
            }
            diagnostic = toView(d, true);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "fetchEndpointShieldDiagnostic failed: " + e.getMessage());
            addActionError("Could not load the diagnostic");
            return Action.ERROR.toUpperCase();
        }
    }

    // A flat view with a string id: the ObjectId itself does not serialize usefully to JSON.
    private static Map<String, Object> toView(EndpointShieldDiagnostic d, boolean withReport) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getHexId());
        m.put("deviceId", d.getDeviceId());
        m.put("agentId", d.getAgentId());
        m.put("source", d.getSource());
        m.put("installId", d.getInstallId());
        m.put("executionId", d.getExecutionId());
        m.put("requestedBy", d.getRequestedBy());
        m.put("profile", d.getProfile());
        m.put("agentVersion", d.getAgentVersion());
        m.put("os", d.getOs());
        m.put("verdict", d.getVerdict());
        m.put("healthy", d.isHealthy());
        m.put("findings", d.getFindings());
        m.put("warningCount", d.getWarningCount());
        m.put("errorCount", d.getErrorCount());
        m.put("createdAt", d.getCreatedAt());
        if (withReport) {
            m.put("report", d.getReport());
        }
        return m;
    }
}

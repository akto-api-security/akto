package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.EndpointShieldDiagnostic;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Util;
import com.akto.utils.DiagnosticReportSummary;
import com.opensymphony.xwork2.ActionSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

/**
 * Agent/installer-facing: store one endpoint-shield diagnostic report.
 *
 * Reports used to be shipped line by line into installation-logs, inside the
 * capped logs_endpoint_shield collection that agent logs fill within a day — the
 * most useful artifact for a failed device was the first to be evicted. Each
 * report is now one document, and a device keeps its newest
 * EndpointShieldDiagnostic.RETENTION_PER_DEVICE.
 */
@Getter
@Setter
public class EndpointShieldDiagnosticAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(EndpointShieldDiagnosticAction.class, LogDb.DB_ABS);

    // Request
    private EndpointShieldDiagnostic diagnostic;

    // Response
    private String id;

    public String insertEndpointShieldDiagnostic() {
        EndpointShieldDiagnostic d = diagnostic;
        if (d == null || StringUtils.isBlank(d.getDeviceId())) {
            addActionError("diagnostic.deviceId is required");
            return ERROR.toUpperCase();
        }
        if (d.getSource() == null || !Arrays.asList(EndpointShieldDiagnostic.SOURCES).contains(d.getSource())) {
            addActionError("diagnostic.source must be one of " + Arrays.toString(EndpointShieldDiagnostic.SOURCES));
            return ERROR.toUpperCase();
        }
        String tooLong = firstTooLongId(d);
        if (tooLong != null) {
            addActionError("diagnostic." + tooLong + " exceeds " + EndpointShieldDiagnostic.MAX_ID_LENGTH + " characters");
            return ERROR.toUpperCase();
        }

        d.setId(new ObjectId());       // always a new document; set here so the id can be returned
        d.setCreatedAt(Context.now()); // server time, not the device clock
        d.setReport(Util.truncateUtf8(d.getReport(), EndpointShieldDiagnostic.MAX_REPORT_BYTES));
        // The stored summary is derived from the report here, not taken from the
        // device, so every installer/agent version is summarised by the same rules.
        // A device that sent no report keeps whatever summary it sent.
        if (StringUtils.isNotBlank(d.getReport())) {
            DiagnosticReportSummary summary = DiagnosticReportSummary.of(d.getReport());
            d.setVerdict(summary.getVerdict());
            d.setFindings(summary.getFindings());
            d.setErrorCount(summary.getErrorCount());
            d.setWarningCount(summary.getWarningCount());
        }
        d.setVerdict(Util.truncateUtf8(d.getVerdict(), EndpointShieldDiagnostic.MAX_TEXT_BYTES));
        d.setOs(Util.truncateUtf8(d.getOs(), EndpointShieldDiagnostic.MAX_TEXT_BYTES));
        d.setAgentVersion(Util.truncateUtf8(d.getAgentVersion(), EndpointShieldDiagnostic.MAX_TEXT_BYTES));
        d.setOutcome(Util.truncateUtf8(d.getOutcome(), EndpointShieldDiagnostic.MAX_TEXT_BYTES));
        d.setFindings(boundFindings(d.getFindings()));
        d.setHealthy(d.getVerdict() != null && d.getVerdict().trim().startsWith("HEALTHY"));

        try {
            DbLayer.insertEndpointShieldDiagnostic(d);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertEndpointShieldDiagnostic " + e.getMessage());
            addActionError("failed to store diagnostic: " + e.getMessage());
            return ERROR.toUpperCase();
        }
        this.id = d.getHexId();

        // The report is stored; a failed trim must not make the caller retry and duplicate it.
        try {
            DbLayer.trimEndpointShieldDiagnostics(d.getDeviceId(), EndpointShieldDiagnostic.RETENTION_PER_DEVICE);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error trimming endpoint shield diagnostics for device "
                    + d.getDeviceId() + ": " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    /** Name of the first identifier field longer than MAX_ID_LENGTH, or null. */
    private static String firstTooLongId(EndpointShieldDiagnostic d) {
        String[][] fields = {
                {EndpointShieldDiagnostic.DEVICE_ID, d.getDeviceId()},
                {EndpointShieldDiagnostic.AGENT_ID, d.getAgentId()},
                {EndpointShieldDiagnostic.INSTALL_ID, d.getInstallId()},
                {EndpointShieldDiagnostic.EXECUTION_ID, d.getExecutionId()},
                {EndpointShieldDiagnostic.PROFILE, d.getProfile()},
        };
        for (String[] f : fields) {
            if (f[1] != null && f[1].length() > EndpointShieldDiagnostic.MAX_ID_LENGTH) {
                return f[0];
            }
        }
        return null;
    }

    /** Keep the first MAX_FINDINGS findings, each at most MAX_TEXT_BYTES. */
    private static List<String> boundFindings(List<String> findings) {
        if (findings == null) {
            return null;
        }
        int n = Math.min(findings.size(), EndpointShieldDiagnostic.MAX_FINDINGS);
        List<String> bounded = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            bounded.add(Util.truncateUtf8(findings.get(i), EndpointShieldDiagnostic.MAX_TEXT_BYTES));
        }
        return bounded;
    }
}

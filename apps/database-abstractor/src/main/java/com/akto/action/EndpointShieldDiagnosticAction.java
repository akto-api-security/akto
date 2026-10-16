package com.akto.action;

import com.akto.dao.EndpointRemoteCommandDao;
import com.akto.dao.EndpointRemoteCommandExecutionDao;
import com.akto.dao.EndpointShieldDiagnosticDao;
import com.akto.dao.context.Context;
import com.akto.dto.EndpointRemoteCommand;
import com.akto.dto.EndpointRemoteCommandExecution;
import com.akto.dto.EndpointShieldDiagnostic;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.ActionSupport;
import org.bson.conversions.Bson;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
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
        try {
            EndpointShieldDiagnostic d = diagnostic;
            if (d == null || isBlank(d.getDeviceId())) {
                addActionError("diagnostic.deviceId is required");
                return ERROR.toUpperCase();
            }
            if (d.getSource() == null || !Arrays.asList(EndpointShieldDiagnostic.SOURCES).contains(d.getSource())) {
                addActionError("diagnostic.source must be one of " + Arrays.toString(EndpointShieldDiagnostic.SOURCES));
                return ERROR.toUpperCase();
            }

            d.setId(new ObjectId());       // always a new document; set here so the id can be returned
            d.setCreatedAt(Context.now()); // server time, not the device clock
            d.setReport(truncateUtf8(d.getReport(), EndpointShieldDiagnostic.MAX_REPORT_BYTES));
            if (d.getFindings() != null && d.getFindings().size() > EndpointShieldDiagnostic.MAX_FINDINGS) {
                d.setFindings(new ArrayList<>(d.getFindings().subList(0, EndpointShieldDiagnostic.MAX_FINDINGS)));
            }
            d.setHealthy(d.getVerdict() != null && d.getVerdict().trim().startsWith("HEALTHY"));
            // Who asked for an on-demand run comes from the dashboard's command,
            // never from the device.
            d.setRequestedBy(isBlank(d.getExecutionId()) ? null : requesterOf(d.getExecutionId(), d.getDeviceId()));

            EndpointShieldDiagnosticDao.instance.insertOne(d);
            this.id = d.getHexId();
            trimDeviceHistory(d.getDeviceId());
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertEndpointShieldDiagnostic " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /** The dashboard user who queued the remote command behind this execution, or null. */
    private static String requesterOf(String executionId, String deviceId) {
        try {
            EndpointRemoteCommandExecution ex = EndpointRemoteCommandExecutionDao.instance.findOne(Filters.and(
                    Filters.eq(EndpointRemoteCommandExecution.ID, executionId),
                    Filters.eq(EndpointRemoteCommandExecution.DEVICE_ID, deviceId)));
            if (ex == null || isBlank(ex.getCommandId())) {
                return null;
            }
            String cid = ex.getCommandId();
            // Dashboard-created commands carry only _id; older ones a string id.
            Bson byId = ObjectId.isValid(cid)
                    ? Filters.or(Filters.eq("_id", new ObjectId(cid)), Filters.eq(EndpointRemoteCommand.ID, cid))
                    : Filters.eq(EndpointRemoteCommand.ID, cid);
            EndpointRemoteCommand cmd = EndpointRemoteCommandDao.instance.findOne(byId);
            return cmd == null ? null : cmd.getCreatedBy();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "insertEndpointShieldDiagnostic: requester lookup failed " + e.getMessage());
            return null;
        }
    }

    /** Delete this device's reports beyond the newest RETENTION_PER_DEVICE. */
    private static void trimDeviceHistory(String deviceId) {
        List<EndpointShieldDiagnostic> older = EndpointShieldDiagnosticDao.instance.findAll(
                Filters.eq(EndpointShieldDiagnostic.DEVICE_ID, deviceId),
                EndpointShieldDiagnostic.RETENTION_PER_DEVICE, 1000,
                Sorts.descending(EndpointShieldDiagnostic.CREATED_AT),
                Projections.include("_id"));
        if (older == null || older.isEmpty()) {
            return;
        }
        List<ObjectId> ids = new ArrayList<>(older.size());
        for (EndpointShieldDiagnostic o : older) {
            if (o.getId() != null) {
                ids.add(o.getId());
            }
        }
        if (!ids.isEmpty()) {
            EndpointShieldDiagnosticDao.instance.deleteAll(Filters.in("_id", ids));
        }
    }

    /** Truncate to at most maxBytes of UTF-8 without splitting a character, noting the cut. */
    static String truncateUtf8(String s, int maxBytes) {
        if (s == null) {
            return null;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return s;
        }
        int end = maxBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) { // don't cut inside a multi-byte char
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8)
                + "\n[truncated by server: report was " + bytes.length + " bytes]";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

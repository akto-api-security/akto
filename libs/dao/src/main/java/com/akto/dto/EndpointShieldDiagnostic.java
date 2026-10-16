package com.akto.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

/**
 * One endpoint-shield diagnostic report (diagnose_mac.sh / diagnose_windows.ps1)
 * for a device: taken by the installer, by the agent after provisioning, or on
 * demand from the dashboard. The newest RETENTION_PER_DEVICE are kept per device.
 */
@Getter
@Setter
@NoArgsConstructor
public class EndpointShieldDiagnostic {

    public static final int RETENTION_PER_DEVICE = 20;
    public static final int MAX_REPORT_BYTES = 512 * 1024;
    public static final int MAX_FINDINGS = 10;

    @BsonId
    private ObjectId id;

    public static final String DEVICE_ID = "deviceId";
    private String deviceId;

    public static final String AGENT_ID = "agentId";
    private String agentId;

    /** install | verify-failed | pkg-failed | post-provision | on-demand | manual */
    public static final String SOURCE = "source";
    private String source;

    public static final String INSTALL_ID = "installId";
    private String installId;

    /** Remote-command execution id, for source=on-demand. */
    public static final String EXECUTION_ID = "executionId";
    private String executionId;

    public static final String REQUESTED_BY = "requestedBy";
    private String requestedBy;

    /** full | install | post-provision | quick */
    public static final String PROFILE = "profile";
    private String profile;

    public static final String AGENT_VERSION = "agentVersion";
    private String agentVersion;

    public static final String OS = "os";
    private String os;

    /** The report's VERDICT line, e.g. "HEALTHY — …" or "CONFIGURED BUT NOT RUNNING — …". */
    public static final String VERDICT = "verdict";
    private String verdict;

    public static final String HEALTHY = "healthy";
    private boolean healthy;

    public static final String FINDINGS = "findings";
    private List<String> findings;

    public static final String WARNING_COUNT = "warningCount";
    private int warningCount;

    public static final String ERROR_COUNT = "errorCount";
    private int errorCount;

    public static final String REPORT = "report";
    private String report;

    /** Epoch seconds, set by the server. */
    public static final String CREATED_AT = "createdAt";
    private int createdAt;

    public static final String[] SOURCES = {
            "install", "verify-failed", "pkg-failed", "post-provision", "on-demand", "manual"
    };

    @BsonIgnore
    public String getHexId() {
        return id == null ? null : id.toHexString();
    }
}

package com.akto.dto.endpoint_shield;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

/**
 * One endpoint-shield diagnostic report for a device — taken by the installer,
 * by the agent after provisioning, or on demand from the dashboard. Written by
 * the db-abstractor (api/insertEndpointShieldDiagnostic), which keeps the newest
 * 20 per device; read here for the device panel's Diagnostics tab.
 */
@Getter
@Setter
@NoArgsConstructor
public class EndpointShieldDiagnostic {

    public static final String DEVICE_ID = "deviceId";
    public static final String AGENT_ID = "agentId";
    public static final String SOURCE = "source";
    public static final String INSTALL_ID = "installId";
    public static final String EXECUTION_ID = "executionId";
    public static final String PROFILE = "profile";
    public static final String OUTCOME = "outcome";
    public static final String AGENT_VERSION = "agentVersion";
    public static final String OS = "os";
    public static final String VERDICT = "verdict";
    public static final String HEALTHY = "healthy";
    public static final String FINDINGS = "findings";
    public static final String WARNING_COUNT = "warningCount";
    public static final String ERROR_COUNT = "errorCount";
    public static final String REPORT = "report";
    public static final String CREATED_AT = "createdAt";

    @BsonId
    private ObjectId id;
    private String deviceId;
    private String agentId;
    /** install | verify-failed | pkg-failed | post-provision | on-demand | manual */
    private String source;
    private String installId;
    private String executionId;
    /** full | install | post-provision | quick */
    private String profile;
    /**
     * Install / provisioning outcome (installed, failed, hooks-failed, …); empty
     * for on-demand. The verdict describes the device, which can be healthy
     * after a failed install.
     */
    private String outcome;
    private String agentVersion;
    private String os;
    private String verdict;
    private boolean healthy;
    private List<String> findings;
    private int warningCount;
    private int errorCount;
    private String report;
    /** Epoch seconds (server time). */
    private int createdAt;

    @BsonIgnore
    public String getHexId() {
        return id == null ? null : id.toHexString();
    }
}

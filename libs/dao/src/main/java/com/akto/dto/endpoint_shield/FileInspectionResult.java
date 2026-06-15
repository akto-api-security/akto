package com.akto.dto.endpoint_shield;

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.List;

/**
 * One execution of a {@link FileInspectionRule} on one device.
 *
 * Nested types live alongside Result because they only exist in a Result's context:
 *   {@link Status} — outcome of the execution.
 *   {@link Match}  — one path entry inside {@link #matches}; for a directory rule, one
 *                    entry for the directory itself plus one per immediate child.
 */
@Getter
@Setter
public class FileInspectionResult {

    public static final String ID = "_id";
    public static final String RULE_ID = "ruleId";
    public static final String ACCOUNT_ID = "accountId";
    public static final String AGENT_ID = "agentId";
    public static final String DEVICE_ID = "deviceId";
    public static final String DEVICE_LABEL = "deviceLabel";
    public static final String EXECUTED_AT = "executedAt";
    public static final String STATUS = "status";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String MATCHES = "matches";

    public enum Status {
        OK,
        PARTIAL,    // hit a server-side cap before exhausting (e.g. >200 children)
        ERROR
    }

    @Getter
    @Setter
    public static class Match {
        public static final String PATH = "path";
        public static final String EXISTS = "exists";
        public static final String IS_DIR = "isDir";
        public static final String SIZE = "size";
        public static final String MODIFIED_AT = "modifiedAt";
        public static final String SHA256 = "sha256";
        public static final String CONTENT_INLINE = "contentInline";
        public static final String CONTENT_BLOB_NAME = "contentBlobName";
        public static final String CONTENT_TRUNCATED = "contentTruncated";
        public static final String READ_ERROR = "readError";

        private String path;
        private boolean exists;
        private Boolean isDir;
        private long size;
        private int modifiedAt;
        private String sha256;          // present when content was read
        private String contentInline;   // populated for small (<=64KB) UTF-8 content
        private String contentBlobName; // Azure blob name for larger content
        private boolean contentTruncated;
        private String readError;       // permission denied, encoding error, etc.

        @BsonIgnore
        private String contentRaw;      // transient: base64 bytes from agent, replaced by contentBlobName before DB write

        public Match() {}
    }

    @BsonId
    private ObjectId id;
    private String ruleId;
    private int accountId;
    private String agentId;
    private String deviceId;
    private String deviceLabel;
    private int executedAt;
    private Status status;
    private String errorMessage;
    private List<Match> matches;
}

package com.akto.agent_risk;

import java.util.regex.Pattern;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

/**
 * dataRisk = (0.45 * sensitivity + 0.20 * operation) / 0.65.
 * Volume and egress are omitted until those signals exist.
 */
public class DataRisk implements RiskCategory {

    private static final LoggerMaker logger = new LoggerMaker(DataRisk.class, LogDb.DB_ABS);

    private static final double SENSITIVITY_WEIGHT = 0.45d;
    private static final double OPERATION_WEIGHT = 0.20d;
    private static final double ACTIVE_WEIGHT = SENSITIVITY_WEIGHT + OPERATION_WEIGHT;

    private static final Pattern INTERNAL = Pattern.compile("\\b(internal|intranet|corp)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIDENTIAL = Pattern.compile("\\b(confidential|restricted|nda)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_CODE = Pattern.compile(
            "\\b(source[_ ]?code|github|gitlab|bitbucket)\\b|\\.(java|py|go|ts|tsx|js|rs|kt)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");
    private static final Pattern PHI = Pattern.compile(
            "\\b(phi|hipaa|diagnosis|patient|medical[_ ]record|prescription|icd-?\\d+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT = Pattern.compile("eyj[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_OR_KEY = Pattern.compile("(?:[a-f0-9]{32,}|sk-[a-z0-9]{16,}|akto_[a-z0-9_-]{8,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PEM = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");

    private static final Pattern OP_SECRETS = Pattern.compile(
            "\\b(get|fetch|retrieve|read)\\b.{0,40}\\b(secret|password|private[_ ]key|api[_ ]?key|credential)s?\\b"
                    + "|\\b(secret|password|private[_ ]key)\\b.{0,40}\\b(get|fetch|retrieve)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_EXPORT = Pattern.compile(
            "\\b(export\\s+externally|email\\s+to|send\\s+to|webhook|upload|exfil)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_DELETE = Pattern.compile("\\b(delete|drop|unlink|rmdir|truncate)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_MODIFY = Pattern.compile("\\b(update|patch|edit|modify)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_WRITE = Pattern.compile("\\b(insert|create|write|save)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_BULK = Pattern.compile("\\b(bulk\\s+read|dump|export\\s+all|all\\s+records|batch\\s+get)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_SEARCH = Pattern.compile("\\b(search|query|find|select)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_READ = Pattern.compile("\\b(get|fetch|show|read)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OP_META = Pattern.compile("\\b(schema|describe|list\\s+tables|metadata)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String id() {
        return "data";
    }

    @Override
    public void apply(RiskContext ctx, AgentRiskScore score) {
        int sensitivity = sensitivity(ctx);
        int operation = operation(ctx);
        int dataRisk = combined(sensitivity, operation);
        score.setDataClassMax(sensitivity);
        score.setDataOperation(operation);
        score.setDataRisk(dataRisk);
        logger.info("agent-risk DataRisk applied sensitivity=" + sensitivity
                + " operation=" + operation
                + " dataRisk=" + dataRisk
                + " textChars=" + (ctx == null || ctx.getRawText() == null ? 0 : ctx.getRawText().length())
                + " traceId=" + (ctx == null ? "" : ctx.getTraceId()));
    }

    @Override
    public boolean stale(RiskContext ctx, AgentRiskScore other) {
        if (other == null) {
            return true;
        }
        return sensitivity(ctx) > other.getDataClassMax()
                || combined(sensitivity(ctx), operation(ctx)) > other.getDataRisk();
    }

    static int detect(RiskContext ctx) {
        return sensitivity(ctx);
    }

    static int combined(int sensitivity, int operation) {
        return RiskMath.clamp((int) Math.round(
                (SENSITIVITY_WEIGHT * sensitivity + OPERATION_WEIGHT * operation) / ACTIVE_WEIGHT));
    }

    static int sensitivity(RiskContext ctx) {
        return ctx == null ? 0 : sensitivity(ctx.getRawText());
    }

    static int sensitivity(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int max = 0;
        if (INTERNAL.matcher(text).find()) {
            max = Math.max(max, 20);
        }
        if (CONFIDENTIAL.matcher(text).find()) {
            max = Math.max(max, 40);
        }
        if (SOURCE_CODE.matcher(text).find()) {
            max = Math.max(max, 50);
        }
        if (EMAIL.matcher(text).find() || SSN.matcher(text).find()) {
            max = Math.max(max, 65);
        }
        if (PAN.matcher(text).find() || CARD.matcher(text).find()) {
            max = Math.max(max, 75);
        }
        if (PHI.matcher(text).find()) {
            max = Math.max(max, 80);
        }
        if (JWT.matcher(text).find() || HEX_OR_KEY.matcher(text).find()) {
            max = Math.max(max, 90);
        }
        if (PEM.matcher(text).find()) {
            max = Math.max(max, 100);
        }
        return max;
    }

    static int operation(RiskContext ctx) {
        return ctx == null ? 0 : operation(ctx.getRawText());
    }

    static int operation(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int max = 0;
        if (OP_META.matcher(text).find()) {
            max = Math.max(max, 5);
        }
        if (OP_READ.matcher(text).find()) {
            max = Math.max(max, 20);
        }
        if (OP_SEARCH.matcher(text).find()) {
            max = Math.max(max, 30);
        }
        if (OP_BULK.matcher(text).find()) {
            max = Math.max(max, 50);
        }
        if (OP_WRITE.matcher(text).find()) {
            max = Math.max(max, 50);
        }
        if (OP_MODIFY.matcher(text).find()) {
            max = Math.max(max, 60);
        }
        if (OP_DELETE.matcher(text).find()) {
            max = Math.max(max, 75);
        }
        if (OP_EXPORT.matcher(text).find()) {
            max = Math.max(max, 90);
        }
        if (OP_SECRETS.matcher(text).find()) {
            max = Math.max(max, 100);
        }
        return max;
    }
}

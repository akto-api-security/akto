package com.akto.action;

import com.akto.gateway.Gateway;
import com.akto.gateway.GuardrailsClient;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Forwards a multipart file-validation request to guardrails-service {@code /api/validate/file}
 * so callers that only reach data-ingestion-service (e.g. the AI egress proxy) can validate
 * files. The path and form fields match guardrails-service, so a caller switches by base URL only:
 * {@code file} parts (repeatable), {@code url} fields (repeatable), and the same metadata fields.
 *
 * The verdict is returned as guardrails-service sent it (lowercase {@code allowed}/{@code reason}).
 * When guardrails-service is unreachable or errors, GuardrailsClient fails open with
 * {@code allowed=true, failOpen=true}.
 *
 * With {@code ingest_data=true} the call is also ingested like an http-proxy call, so it shows in
 * Traces: the request carries the text read from each file and the response the verdict, which is
 * recorded as the trace's guardrail result.
 */
@lombok.Getter
@lombok.Setter
public class FileValidationAction extends ActionSupport implements UploadedFilesAware {

    private static final LoggerMaker loggerMaker = new LoggerMaker(FileValidationAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final GuardrailsClient guardrailsClient = new GuardrailsClient();
    private static final Gateway gateway = Gateway.getInstance();

    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
    }

    private List<String> url;
    private String contextSource;
    private String path;
    private String method;
    private String requestHeaders;
    private String hostname;
    private String ip;
    private String time;
    private String statusCode;
    private String status;
    private String tag;
    private String metadata;
    private String akto_account_id;
    private String akto_vxlan_id;
    private String akto_connector;
    private String ingest_data;

    private final List<GuardrailsClient.FilePart> files = new ArrayList<>();
    private Map<String, Object> data;

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        for (UploadedFile uploaded : uploadedFiles) {
            if ("file".equals(uploaded.getInputName()) && uploaded.getContent() instanceof File) {
                files.add(new GuardrailsClient.FilePart(
                        (File) uploaded.getContent(), uploaded.getOriginalName(), uploaded.getContentType()));
            }
        }
    }

    public String validateFile() {
        long start = System.currentTimeMillis();
        List<String> urls = url != null ? url : new ArrayList<>();
        boolean ingest = "true".equalsIgnoreCase(ingest_data);
        Map<String, String> fields = buildFields();
        if (ingest) {
            fields.put("return_content", "true");
        }
        data = guardrailsClient.callValidateFile(fields, files, urls);
        if (ingest) {
            ingest(data, urls);
        }
        loggerMaker.infoAndAddToDb("[validate-file] completed - path: {}, files: {}, urls: {}, account: {}, latencyMs: {}",
                path, files.size(), urls.size(), akto_account_id, System.currentTimeMillis() - start);
        return Action.SUCCESS.toUpperCase();
    }

    /** Publishes the call to Kafka; never changes the verdict, which goes back without the file text. */
    private void ingest(Map<String, Object> verdict, List<String> urls) {
        Object read = verdict.remove("files");
        try {
            boolean allowed = !Boolean.FALSE.equals(verdict.get("allowed"));
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("path", path);
            requestData.put("method", method);
            requestData.put("requestHeaders", HttpProxyAction.normalizeHostInRequestHeaders(requestHeaders));
            requestData.put("responseHeaders", "{}");
            requestData.put("requestPayload", new BasicDBObject("files", read instanceof List ? read : fileNames(urls)).toJson());
            requestData.put("responsePayload", new BasicDBObject(verdict).toJson());
            requestData.put("statusCode", allowed ? "200" : "403");
            requestData.put("status", allowed ? "OK" : "Forbidden");
            requestData.put("type", "HTTP/1.1");
            // mini-runtime's parser rejects a record whose is_pending or source is null.
            requestData.put("is_pending", "false");
            requestData.put("source", "MIRRORING");
            requestData.put("ip", ip);
            requestData.put("time", time != null && !time.isEmpty() ? time : String.valueOf(System.currentTimeMillis() / 1000));
            requestData.put("tag", HttpProxyAction.withGuardrailMode(tag, akto_connector));
            requestData.put("akto_account_id", akto_account_id);
            requestData.put("akto_vxlan_id", akto_vxlan_id);
            gateway.ingestWithVerdict(requestData, verdict);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "[validate-file] could not ingest the file check: " + e.getMessage());
        }
    }

    /** What is known about the files when guardrails-service read none (failed open, no policy). */
    private List<Map<String, String>> fileNames(List<String> urls) {
        List<Map<String, String>> names = new ArrayList<>();
        for (GuardrailsClient.FilePart part : files) {
            Map<String, String> entry = new HashMap<>();
            entry.put("name", part.getFileName());
            names.add(entry);
        }
        for (String u : urls) {
            Map<String, String> entry = new HashMap<>();
            entry.put("url", u);
            names.add(entry);
        }
        return names;
    }

    private Map<String, String> buildFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("contextSource", contextSource);
        fields.put("path", path);
        fields.put("method", method);
        fields.put("requestHeaders", HttpProxyAction.normalizeHostInRequestHeaders(requestHeaders));
        fields.put("hostname", hostname);
        fields.put("ip", ip);
        fields.put("time", time);
        fields.put("statusCode", statusCode);
        fields.put("status", status);
        fields.put("tag", tag);
        fields.put("metadata", metadata);
        fields.put("akto_account_id", akto_account_id);
        fields.put("akto_vxlan_id", akto_vxlan_id);
        return fields;
    }
}

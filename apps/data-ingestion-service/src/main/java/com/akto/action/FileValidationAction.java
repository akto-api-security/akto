package com.akto.action;

import com.akto.gateway.GuardrailsClient;
import com.akto.log.LoggerMaker;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;

import java.io.File;
import java.util.ArrayList;
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
 */
@lombok.Getter
@lombok.Setter
public class FileValidationAction extends ActionSupport implements UploadedFilesAware {

    private static final LoggerMaker loggerMaker = new LoggerMaker(FileValidationAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final GuardrailsClient guardrailsClient = new GuardrailsClient();

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
        data = guardrailsClient.callValidateFile(buildFields(), files, urls);
        loggerMaker.infoAndAddToDb("[validate-file] completed - path: {}, files: {}, urls: {}, account: {}, latencyMs: {}",
                path, files.size(), urls.size(), akto_account_id, System.currentTimeMillis() - start);
        return Action.SUCCESS.toUpperCase();
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

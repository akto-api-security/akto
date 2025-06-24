package com.akto.dto;

@lombok.Getter
@lombok.Setter
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class IngestDataBatch {
    
    String path;
    String requestHeaders;
    String responseHeaders;
    String method;
    String requestPayload;
    String responsePayload;
    String ip;
    String destIp;
    String time;
    String statusCode;
    String type;
    String status;
    String akto_account_id;
    String akto_vxlan_id;
    String is_pending;
    String source;
    String direction;
    String process_id;
    String socket_id;
    String daemonset_id;
    String enabled_graph;
    String tag;

}

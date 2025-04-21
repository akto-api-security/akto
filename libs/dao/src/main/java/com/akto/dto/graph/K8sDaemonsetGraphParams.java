package com.akto.dto.graph;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class K8sDaemonsetGraphParams extends SvcToSvcGraphParams {

    public static final String DIRECTION_OUTGOING = "2";
    public static final String DIRECTION_INCOMING = "1";

    String hostInApiRequest;
    String processId;
    String socketId;
    String daemonsetId;
    String direction;

    @Override
    public Type getType() {
        return Type.K8S;
    }
}

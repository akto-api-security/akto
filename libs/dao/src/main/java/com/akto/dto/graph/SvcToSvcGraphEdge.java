package com.akto.dto.graph;

import org.bson.codecs.pojo.annotations.BsonId;

import com.akto.dao.context.Context;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class SvcToSvcGraphEdge {
    @BsonId
    private String id;

    private String source;

    private String target;

    public static final String CREATTION_EPOCH = "creationEpoch";
    private int creationEpoch;

    private SvcToSvcGraphParams.Type type;

    private int lastSeenEpoch;

    private int counter;

    public static SvcToSvcGraphEdge createFromK8s(String source, String target) {
        int ts = Context.now();
        return new SvcToSvcGraphEdge(source + "_" + target, source, target, ts, SvcToSvcGraphParams.Type.K8S, ts, 0);
    }
}

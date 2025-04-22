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
    public static final String ID = "_id";
    @BsonId
    private String id;
    public static final String SOURCE = "source";
    private String source;

    public static final String TARGET = "target";
    private String target;

    public static final String CREATTION_EPOCH = "creationEpoch";
    private int creationEpoch;

    public static final String TYPE = "type";
    private SvcToSvcGraphParams.Type type;

    public static final String LAST_SEEN_EPOCH = "lastSeenEpoch";
    private int lastSeenEpoch;

    public static final String COUNTER = "counter";
    private int counter;

    public static SvcToSvcGraphEdge createFromK8s(String source, String target) {
        int ts = Context.now();
        return new SvcToSvcGraphEdge(source + "_" + target, source, target, ts, SvcToSvcGraphParams.Type.K8S, ts, 0);
    }
}

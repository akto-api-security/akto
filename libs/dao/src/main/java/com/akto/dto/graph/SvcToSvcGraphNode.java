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

public class SvcToSvcGraphNode {
    @BsonId
    private String id;

    private int creationEpoch;

    private SvcToSvcGraphParams.Type type;

    private int lastSeenEpoch;

    private int counter;

    public static SvcToSvcGraphNode createFromK8s(String name) {
        int ts = Context.now();
        return new SvcToSvcGraphNode(name, ts, SvcToSvcGraphParams.Type.K8S, ts, 0);
    }
}

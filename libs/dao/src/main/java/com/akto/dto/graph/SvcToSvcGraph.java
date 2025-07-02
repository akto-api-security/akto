package com.akto.dto.graph;

import java.util.List;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
public class SvcToSvcGraph {
    private List<SvcToSvcGraphEdge> edges;
    private List<SvcToSvcGraphNode> nodes;
    private int lastFetchFromDb;
}

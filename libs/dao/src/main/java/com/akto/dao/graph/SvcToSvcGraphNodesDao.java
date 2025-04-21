package com.akto.dao.graph;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.graph.SvcToSvcGraphNode;

public class SvcToSvcGraphNodesDao extends AccountsContextDao<SvcToSvcGraphNode> {

    public static final SvcToSvcGraphNodesDao instance = new SvcToSvcGraphNodesDao();

    private SvcToSvcGraphNodesDao() {}

    @Override
    public String getCollName() {
        return "svc_to_svc_graph_nodes";
    }

    @Override
    public Class<SvcToSvcGraphNode> getClassT() {
        return SvcToSvcGraphNode.class;
    }

}

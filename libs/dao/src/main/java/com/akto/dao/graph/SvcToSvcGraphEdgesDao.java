package com.akto.dao.graph;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.graph.SvcToSvcGraphEdge;

public class SvcToSvcGraphEdgesDao extends AccountsContextDao<SvcToSvcGraphEdge> {

    public static final SvcToSvcGraphEdgesDao instance = new SvcToSvcGraphEdgesDao();

    private SvcToSvcGraphEdgesDao() {}

    @Override
    public String getCollName() {
        return "svc_to_svc_graph_edges";
    }

    @Override
    public Class<SvcToSvcGraphEdge> getClassT() {
        return SvcToSvcGraphEdge.class;
    }

}
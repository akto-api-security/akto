package com.akto.dao;

import com.akto.dto.dependency_flow.Node;


public class DependencyFlowNodesDao extends AccountsContextDao<Node>{

    public static final DependencyFlowNodesDao instance = new DependencyFlowNodesDao();

    @Override
    public String getCollName() {
        return "dependency_flow_nodes";
    }

    @Override
    public Class<Node> getClassT() {
        return Node.class;
    }

}

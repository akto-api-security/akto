package com.akto.action.graph;

import java.util.ArrayList;
import java.util.List;

import com.akto.action.UserAction;
import com.akto.dao.graph.SvcToSvcGraphEdgesDao;
import com.akto.dao.graph.SvcToSvcGraphNodesDao;
import com.akto.dto.graph.SvcToSvcGraphEdge;
import com.akto.dto.graph.SvcToSvcGraphNode;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import lombok.*;

@Getter
public class SvcToSvcGraphAction extends UserAction {

    @Override
    public String execute() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<SvcToSvcGraphEdge> svcTosvcGraphEdges = new ArrayList<>();
    public List<SvcToSvcGraphNode> svcTosvcGraphNodes = new ArrayList<>();

    public String findSvcToSvcGraphEdges() {
        svcTosvcGraphEdges = SvcToSvcGraphEdgesDao.instance.findAll(Filters.empty());
        return Action.SUCCESS.toUpperCase();
    }
    
    public String findSvcToSvcGraphNodes() {
        svcTosvcGraphNodes = SvcToSvcGraphNodesDao.instance.findAll(Filters.empty());
        return Action.SUCCESS.toUpperCase();
    }
    
}

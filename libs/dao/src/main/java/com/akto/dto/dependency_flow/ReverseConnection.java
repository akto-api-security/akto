package com.akto.dto.dependency_flow;

import java.util.List;
import java.util.Objects;

public class ReverseConnection {
    private String param;
    private List<ReverseEdge> reverseEdges;

    public ReverseConnection() {
    }

    public ReverseConnection(String param, List<ReverseEdge> reverseEdges) {
        this.param = param;
        this.reverseEdges = reverseEdges;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReverseConnection that = (ReverseConnection) o;
        return param.equals(that.param);
    }

    @Override
    public int hashCode() {
        return Objects.hash(param);
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public List<ReverseEdge> getReverseEdges() {
        return reverseEdges;
    }

    public void setReverseEdges(List<ReverseEdge> reverseEdges) {
        this.reverseEdges = reverseEdges;
    }
}

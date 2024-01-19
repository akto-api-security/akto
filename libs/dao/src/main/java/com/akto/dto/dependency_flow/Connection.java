package com.akto.dto.dependency_flow;


import java.util.List;
import java.util.Objects;

public class Connection {
    private String param;
    private List<Edge> edges;

    public Connection() {
    }

    public Connection(String param, List<Edge> edges) {
        this.param = param;
        this.edges = edges;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Connection that = (Connection) o;
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

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }
}

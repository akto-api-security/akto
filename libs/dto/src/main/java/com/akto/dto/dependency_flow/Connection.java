package com.akto.dto.dependency_flow;


import java.util.List;
import java.util.Objects;

public class Connection {
    private String param;
    private boolean isUrlParam;
    private boolean isHeader;
    private List<Edge> edges;

    public Connection() {
    }

    public Connection(String param, List<Edge> edges, boolean isUrlParam, boolean isHeader) {
        this.param = param;
        this.edges = edges;
        this.isUrlParam = isUrlParam;
        this.isHeader = isHeader;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Connection that = (Connection) o;
        return isUrlParam == that.isUrlParam && isHeader == that.isHeader && param.equals(that.param);
    }

    @Override
    public int hashCode() {
        return Objects.hash(param, isUrlParam, isHeader);
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

    public boolean getIsUrlParam() {
        return isUrlParam;
    }

    public void setIsUrlParam(boolean urlParam) {
        isUrlParam = urlParam;
    }

    public boolean getIsHeader() {
        return isHeader;
    }

    public void setIsHeader(boolean header) {
        isHeader = header;
    }
}

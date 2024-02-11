package com.akto.oas;
import java.util.List;

public class SpecLine {
    private String line;
    private List<String> path;

    public SpecLine(String line, List<String> path) {
        this.line = line;
        this.path = path;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public List<String> getPath() {
        return path;
    }

    public void setPath(List<String> path) {
        this.path = path;
    }
}

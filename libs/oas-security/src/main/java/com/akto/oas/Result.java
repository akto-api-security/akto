package com.akto.oas;
import java.util.ArrayList;
import java.util.List;

public class Result {
    private List<Issue> issues;
    private List<SpecLine> specLines;

    public Result(List<Issue> issueList, List<SpecLine> stringList) {
        this.issues = issueList;
        this.specLines = stringList;
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public void addIssue(Issue issue) {
        issues.add(issue);
    }

    public void setIssues(List<Issue> issues) {
        this.issues = issues;
    }

    public List<SpecLine> getSpecLines() {
        return specLines;
    }

    public void setSpecLines(List<SpecLine> specLines) {
        this.specLines = specLines;
    }

    public void addSpecLine(SpecLine specLine) {
        // TODO: find a better way
        List<String> p = new ArrayList<>(specLine.getPath());
        SpecLine s = new SpecLine(specLine.getLine(),p);
        this.specLines.add(s);
    }

    public void addResults(Result result) {
        specLines.addAll(result.getSpecLines());
        issues.addAll(result.getIssues());
    }
}

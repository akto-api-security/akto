package com.akto.threat.detection.utils;

import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class CRSEvaluator {
    private List<Rule> rules = new ArrayList<>();
    private List<Pattern> compiledRules = new ArrayList<>();
    private ExecutorService executor;
    private RuleSetLoader ruleSetLoader;

    public CRSEvaluator(RuleSetLoader ruleSetLoader) {
        this.ruleSetLoader = ruleSetLoader;
        this.executor = initExecutor();
        this.rules.addAll(ruleSetLoader.load());
        for (Rule rule : rules) {
            if (rule.regexString != null && !rule.regexString.isEmpty()) {
                this.compiledRules.add(Pattern.compile(rule.regexString));
            }
        }
    }

    private ExecutorService initExecutor() {
        int nThreads = Runtime.getRuntime().availableProcessors();
        return Executors.newFixedThreadPool(nThreads);
    }

    public boolean evaluate(String input) {
        for (int i = 0; i < compiledRules.size(); i++) {
                Pattern pattern = compiledRules.get(i);
                if (pattern.matcher(input).find()) {
                    Rule rule = rules.get(i);
                    System.out.println("Matched Rule ID: " + rule.id);
                    System.out.println("Message: " + rule.message);
                    System.out.println("Severity: " + rule.severity);
                    return true;
                }
        }
                return false;
        }


    public static void main(String[] args) {
        String testString = "curl -X POST http://example.com -d 'id=1; rm -rf /'";
        RuleSetLoader loader = new RuleSetLoader("/RCE.conf");
        CRSEvaluator evaluator = new CRSEvaluator(loader);
        boolean result = evaluator.evaluate(testString);
        if (result) {
            System.out.println("Potential RCE detected in input: " + testString);
        } else {
            System.out.println("No RCE detected in input: " + testString);
        }
    }
}

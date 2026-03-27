package com.akto.threat.detection.utils;

import java.io.*;
import java.util.*;

public class RuleSetLoader {
    private final String confResource;

    public RuleSetLoader(String confResource) {
        this.confResource = confResource;
    }

    public List<Rule> load() {
        List<Rule> rules = new ArrayList<>();
        List<String> ruleBlocks = readRuleBlocks();
        for (String ruleText : ruleBlocks) {
            Rule rule = parseRule(ruleText);
            rules.add(rule);
        }
        return rules;
    }

    private List<String> readRuleBlocks() {
        List<String> ruleBlocks = new ArrayList<>();
        StringBuilder ruleBlock = new StringBuilder();
        boolean inRule = false;
        try (InputStream is = RuleSetLoader.class.getResourceAsStream(confResource);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("SecRule")) {
                    inRule = true;
                    ruleBlock.setLength(0);
                }
                if (inRule) {
                    ruleBlock.append(line.trim()).append(" ");
                    if (line.trim().endsWith("\"")) {
                        ruleBlocks.add(ruleBlock.toString());
                        inRule = false;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ruleBlocks;
    }

    private int extractInt(String text, String key) {
        int idx = text.indexOf(key);
        if (idx == -1) return 0;
        int end = text.indexOf(',', idx);
        if (end == -1) end = text.length();
        try {
            return Integer.parseInt(text.substring(idx + key.length(), end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractString(String text, String start, String end) {
        int idx = text.indexOf(start);
        if (idx == -1) return "";
        int endIdx = text.indexOf(end, idx + start.length());
        if (endIdx == -1) return "";
        return text.substring(idx + start.length(), endIdx);
    }

    private String extractRegex(String text) {
        int idx = text.indexOf("@rx");
        if (idx == -1) return "";
        int start = idx + 3;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        if (start < text.length() && text.charAt(start) == '"') start++;
        StringBuilder regex = new StringBuilder();
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) break;
            regex.append(c);
        }
        return regex.toString().trim();
    }

    private Rule parseRule(String ruleText) {
        int id = extractInt(ruleText, "id:");
        String msg = extractString(ruleText, "msg:'", "',");
        String severity = extractString(ruleText, "severity:'", "',");
        String regex = extractRegex(ruleText);
        Rule rule = new Rule();
        rule.id = id;
        rule.message = msg;
        rule.severity = severity;
        rule.score = 0;
        rule.regexString = regex;
        return rule;
    }
}

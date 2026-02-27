package com.akto.threat.detection.utils;

public class Rule {
    public int id;
    public String name;
    public String message;
    public String severity;
    public int score;
    public String regexString;

    // TODO: ? Add rule type(trie or regex) and evaluation logic. 
}

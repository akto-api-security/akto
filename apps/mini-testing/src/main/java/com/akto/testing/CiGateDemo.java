package com.akto.testing;

public class CiGateDemo {

    public static String classify(int score) {
        if (score >= 50) {
            return "pass";
        } else {
            return "fail";
        }
    }
}

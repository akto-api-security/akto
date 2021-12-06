package com.akto.log;

public class LoggerMaker {
    public static org.slf4j.Logger make(Class c) {
        return org.slf4j.LoggerFactory.getLogger(c);
    }
}

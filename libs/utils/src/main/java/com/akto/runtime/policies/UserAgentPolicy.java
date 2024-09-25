package com.akto.runtime.policies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserAgentPolicy {
    public static final String USER_AGENT_HEADER = "user-agent";
    public static final String COOKIE_NAME = "cookie";
    private static final Logger logger = LoggerFactory.getLogger(UserAgentPolicy.class);

    public static String findUserAgentType(String userAgentValue){
        if(userAgentValue.contains("cloudfare")){
            return "Cloudfare";
        }
        if(userAgentValue.contains("android")){
            return "Mobile browser";
        }
        if(userAgentValue.contains("mozilla") || userAgentValue.contains("chrome")){
            return "Browser";
        }

        
        return "Custom";
    }
}

package com.akto;

public enum RuntimeMode {

    NORMAL, HYBRID;

    public static RuntimeMode getRuntimeMode(){
        String runtimeMode = System.getenv("RUNTIME_MODE");
        if("hybrid".equalsIgnoreCase(runtimeMode)){
            return HYBRID;
        }
        return NORMAL;
    }

    public static boolean isHybridDeployment(){
        RuntimeMode runtimeMode = RuntimeMode.getRuntimeMode();
        return runtimeMode.equals(HYBRID);
    }

}

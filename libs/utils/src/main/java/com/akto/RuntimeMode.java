package com.akto;

public enum RuntimeMode {

    NORMAL, HYBRID;

    public static RuntimeMode getRuntimeMode(){
        String runtimeMode = "hybrid";
        if(1==1){
            return HYBRID;
        }
        return NORMAL;
    }

    public static boolean isHybridDeployment(){
        RuntimeMode runtimeMode = RuntimeMode.getRuntimeMode();
        return runtimeMode.equals(HYBRID);
    }

}

package com.akto.util.data_actor;

import com.akto.RuntimeMode;

public class DataActorFactory {

    private DataActorFactory(){
    }

    private static DataActor INSTANCE = null;

    public static DataActor fetchInstance() {
        if(INSTANCE != null){
            return INSTANCE;
        }
        boolean hybridSaas = RuntimeMode.isHybridDeployment();
        if (hybridSaas) {
            INSTANCE = new ClientActor();
        } else {
            INSTANCE = new DbActor();
        }
        return INSTANCE;

    }

}

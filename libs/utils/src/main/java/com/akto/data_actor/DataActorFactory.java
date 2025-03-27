package com.akto.data_actor;

import com.akto.RuntimeMode;

public class DataActorFactory {

    public static DataActor fetchInstance() {

        boolean hybridSaas = RuntimeMode.isHybridDeployment();
        return new DbActor();
        // if (hybridSaas) {
        //     return new ClientActor();
        // } else {
            
        // }

    }

}

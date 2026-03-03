package com.akto.data_actor;

import com.akto.RuntimeMode;

public class DataActorFactory {

    public static DataActor fetchInstance() {

        boolean hybridSaas = true;//RuntimeMode.isHybridDeployment();
        if (hybridSaas) {
            return new ClientActor();
        } else {
            return new DbActor();
        }

    }

}

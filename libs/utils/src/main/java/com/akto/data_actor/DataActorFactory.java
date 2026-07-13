package com.akto.data_actor;

import com.akto.RuntimeMode;

public class DataActorFactory {

    // Delegates straight to ClientActor rather than gating anything on fetchInstance()'s own
    // timing: fetchInstance() can be triggered arbitrarily early by unrelated static initializers
    // (e.g. LoggerMaker itself calls fetchInstance()), so nothing hooked into construction time here
    // is guaranteed to run after this has been set. ClientActor resolves it lazily on first real use.
    public static void setModuleType(String type) {
        ClientActor.setPendingModuleType(type);
    }

    public static DataActor fetchInstance() {

        boolean hybridSaas = RuntimeMode.isHybridDeployment();
        if (hybridSaas) {
            return new ClientActor();
        } else {
            return new DbActor();
        }

    }

}

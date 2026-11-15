package com.akto.testing.scripts;

import com.akto.data_actor.DataActor;
import com.akto.dto.testing.config.TestScript;

/**
 * Adapts DataActor (ClientActor -> database-abstractor over HTTP, or DbActor -> Mongo) to the provider
 * contract. DataActor.fetchTestScript / fetchTestScriptById return null for "no such script" and throw
 * when the script could not be read, which is exactly what the caching decorator relies on.
 */
public class RemoteTestScriptProvider implements TestScriptProvider {

    private final DataActor dataActor;

    public RemoteTestScriptProvider(DataActor dataActor) {
        this.dataActor = dataActor;
    }

    @Override
    public TestScript findById(String id) {
        return dataActor.fetchTestScriptById(id);
    }

    @Override
    public TestScript findDefault(TestScript.Type type) {
        return dataActor.fetchTestScript(type);
    }
}

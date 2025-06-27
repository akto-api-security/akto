package com.akto.hybrid_runtime;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.akto.data_actor.DataActor;

public class RuntimeVersion {

    public void updateVersion(String fieldName, DataActor dataActor) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/version.txt")) {
            if (in != null) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
                String imageTag = bufferedReader.readLine();
                String buildTime = bufferedReader.readLine();

                String version = imageTag + " - " + buildTime;
                dataActor.updateRuntimeVersion(fieldName, version);
            } else  {
                throw new Exception("Input stream null");
            }
        }
    }

}

package com.akto.hybrid_runtime;

import com.akto.data_actor.DataActor;
import com.akto.dto.settings.DataControlSettings;
import com.akto.sql.SampleDataAltDb;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataControlFetcher {

    private static DataControlSettings dataControlSettings = null;

    public DataControlFetcher() {}
    public static DataControlSettings get() {
        return dataControlSettings;
    }

    public static final ScheduledExecutorService es = Executors.newScheduledThreadPool(1);
    public static void init(DataActor dataActor) {
        es.scheduleAtFixedRate(new Runnable() {
            public void run() {
                String prevCommand = "";
                String prevResult = "";
                if (dataControlSettings != null) {
                    prevCommand = dataControlSettings.getOldPostgresCommand();
                    prevResult = dataControlSettings.getPostgresResult();
                }
                dataControlSettings = dataActor.fetchDataControlSettings(prevResult, prevCommand);

                if (dataControlSettings != null) {
                    if (dataControlSettings.getPostgresCommand() == null) return;
                    if (!dataControlSettings.getPostgresCommand().equalsIgnoreCase(dataControlSettings.getOldPostgresCommand())) {
                        runPostgresCommand();
                    }
                }
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    public static boolean stopIngestionFromKafka() {
        if (dataControlSettings == null) return false;
        if (dataControlSettings.getKafkaConsumerRecordsPerMin() == -1) return false;

        return false;
    }

    public static void runPostgresCommand() {
        if (dataControlSettings == null) return;

        String ret = "";
        String comm = dataControlSettings.getPostgresCommand();
        if (StringUtils.isEmpty(comm)) ret = "no command";

        try {
            ret = "command: " + comm + ": " + SampleDataAltDb.runCommand(comm);
        } catch (Exception e) {
            ret = "command: " + comm + ": " + "exception in runPostgres - " + e.getMessage();
        }

        dataControlSettings.setPostgresResult(ret);
        dataControlSettings.setOldPostgresCommand(comm);
    }

    public static boolean discardOldApi() {
        return dataControlSettings != null && dataControlSettings.isDiscardOldApi();
    }
    public static boolean discardNewApi() {
        return dataControlSettings != null && dataControlSettings.isDiscardNewApi();
    }
}

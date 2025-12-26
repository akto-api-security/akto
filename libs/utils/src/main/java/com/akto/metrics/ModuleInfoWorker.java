package com.akto.metrics;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.log.LoggerMaker;
import com.akto.util.VersionUtil;

import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.kafka.Kafka;
import com.akto.kafka.KafkaConfig;

public class ModuleInfoWorker {
    private final static LoggerMaker loggerMaker = new LoggerMaker(ModuleInfoWorker.class, LoggerMaker.LogDb.RUNTIME);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ModuleInfo.ModuleType moduleType;
    private final int startedTs = Context.now();
    private final String version;
    private final DataActor dataActor;
    private final String moduleName;

    private ModuleInfoWorker(ModuleInfo.ModuleType moduleType, String version, DataActor dataActor, String name) {
        this.moduleType = moduleType;
        this.version = version;
        this.dataActor = dataActor;
        this.moduleName = name;
    }

    private ModuleInfoWorker() {
        this.moduleType = null;
        this.version = null;
        this.dataActor = null;
        this.moduleName = null;
    }

    private void scheduleHeartBeatUpdate() {
        ModuleInfoWorker _this = this;
        ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setModuleType(this.moduleType);
        moduleInfo.setCurrentVersion(this.version);
        moduleInfo.setStartedTs(this.startedTs);
        moduleInfo.setId(moduleInfo.getId());// Setting new uuid for id
        moduleInfo.setName(this.moduleName);

        scheduler.scheduleWithFixedDelay(() -> {
            moduleInfo.setLastHeartbeatReceived(Context.now());
            assert _this.dataActor != null;
            ModuleInfo moduleInfoFromService = _this.dataActor.updateModuleInfo(moduleInfo);
            loggerMaker.info("Sent heartbeat at : " + moduleInfoFromService.getLastHeartbeatReceived() + " for module: "
                    + moduleInfoFromService.getModuleType().name());

            /*
             * Some theory on exit codes and signals:
             * https://man7.org/linux/man-pages/man3/sysexits.h.3head.html [ system exit codes ]
             * https://tldp.org/LDP/abs/html/exitcodes.html [ All signal ranges ]
             * states that exit code 128+n indicates termination by signal n
             * OS signals (n) : https://man7.org/linux/man-pages/man7/signal.7.html [ 0-31 are coming from here ]
             * 
             */

            if (moduleInfoFromService.isDeleteTopicAndReboot() &&
                    ModuleType.MINI_RUNTIME.equals(moduleInfoFromService.getModuleType())) {
                // Delete Kafka topic before restarting
                String kafkaBrokerUrl = KafkaConfig.getKafkaBrokerUrl();
                String topicName = KafkaConfig.getTopicName();
                Kafka.deleteKafkaTopic(topicName, kafkaBrokerUrl);
            }
            if (moduleInfoFromService.isReboot() || moduleInfoFromService.isDeleteTopicAndReboot()) {
                loggerMaker.warnAndAddToDb("Rebooting module: " + moduleInfoFromService.getModuleType().name() + " id: " + moduleInfoFromService.getId());
                System.exit(0);
                return;
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    public static void init(ModuleInfo.ModuleType moduleType, DataActor dataActor, String name) {
        String version;
        try (InputStream in = ModuleInfoWorker.class.getResourceAsStream("/version.txt")) {
            if (in != null) {
                version = VersionUtil.getVersion(in);
            } else {
                throw new Exception("Input stream null");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error getting local version, skipping heartbeat check");
            return;
        }
        loggerMaker.warnAndAddToDb("Starting heartbeat update for module: " + moduleType.name() + " with version: " + version + " and name: " + name);
        ModuleInfoWorker infoWorker = new ModuleInfoWorker(moduleType, version, dataActor, name);
        infoWorker.scheduleHeartBeatUpdate();
    }
}

package com.akto.detection;

import com.akto.dto.AccountSettings;
import com.akto.dto.settings.DetectionCorrectorSettings;
import com.akto.log.LoggerMaker;


/**
 * Builds the active {@link DetectionCorrector} from account settings and installs it.
 *
 * Called on the same refresh cadence as custom data types, so a configuration change reaches the
 * runtime within one refresh interval. The previous corrector is kept when nothing relevant has
 * changed, because rebuilding would throw away its answer cache and send lookup volume back to
 * cold-start levels.
 */
public class DetectionCorrectorInstaller {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DetectionCorrectorInstaller.class);

    private static volatile String installedSignature = null;

    private DetectionCorrectorInstaller() {
    }

    public static synchronized void refresh(AccountSettings accountSettings) {
        try {
            DetectionCorrectorConfig config = fromAccountSettings(accountSettings);

            if (config == null || !config.isUsable()) {
                if (installedSignature != null) {
                    loggerMaker.info("detection corrector disabled, reverting to local detection only");
                    DetectionCorrectorRegistry.reset();
                    installedSignature = null;
                }
                return;
            }

            if (config.getAuthToken() == null || config.getAuthToken().trim().isEmpty()) {
                // Not fatal: some deployments front the service with mTLS or a service mesh instead.
                loggerMaker.info("[detection-corrector] no auth token configured, "
                        + "calling without an Authorization header");
            }

            String signature = config.toString();
            if (signature.equals(installedSignature)) return;

            DetectionCorrectorRegistry.install(new HttpDetectionCorrector(config));
            installedSignature = signature;
            loggerMaker.info("detection corrector installed: " + signature);
        } catch (Exception e) {
            loggerMaker.error("failed installing detection corrector, keeping local detection only: " + e.getMessage());
        }
    }

    static DetectionCorrectorConfig fromAccountSettings(AccountSettings accountSettings) {
        if (accountSettings == null) return null;

        DetectionCorrectorSettings settings = accountSettings.getDetectionCorrector();
        if (settings == null) return null;

        DetectionCorrectorConfig config = new DetectionCorrectorConfig();
        config.setEnabled(settings.isEnabled());
        config.setUrl(settings.getUrl());
        config.setAuthToken(settings.getAuthToken());
        config.setTriggerSubTypesFromList(settings.getTriggerTypes());
        config.setTypeAliases(settings.getTypeAliases());

        // The setters below ignore non-positive values, so an unset field keeps the built-in default.
        config.setTimeoutMs(settings.getTimeoutMs());
        config.setMaxBatchSize(settings.getMaxBatchSize());
        config.setFailureThreshold(settings.getFailureThreshold());
        config.setBreakerCoolOffSeconds(settings.getBreakerCoolOffSeconds());
        config.setCacheSize(settings.getCacheSize());
        config.setCacheTtlSeconds(settings.getCacheTtlSeconds());

        DetectionCorrectorRegistry.setDebugEnabled(settings.isDebug());
        return config;
    }
}

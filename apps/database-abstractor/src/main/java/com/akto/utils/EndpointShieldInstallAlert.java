package com.akto.utils;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Alerts on endpoint-shield installs that failed or never came up.
 *
 * Two signals, both read from the device's module_info record (the installer and
 * the agent both heartbeat into it, merged per additionalData field):
 *
 *  1. Reported failure — an installer heartbeat with installStatus failed /
 *     verify-failed / uninstall-failed. Alerted as soon as it arrives.
 *
 *  2. Silent failure — additionalData.installId is set by the installer, and the
 *     agent reports additionalData.provisionedInstallId once it has provisioned
 *     that install and its post-provision report has landed. If the two still
 *     differ INSTALL_GRACE_SECONDS after the install, the agent never got that
 *     far. No on-device component can report this case — the one that would is
 *     the one that did not come up — so it can only be detected here.
 *
 * Mirrors TrafficCollectorAlert: runs asynchronously off the heartbeat request,
 * the sweep is throttled per account, and each (device, install) alerts once.
 */
public class EndpointShieldInstallAlert {

    private static final LoggerMaker loggerMaker =
            new LoggerMaker(EndpointShieldInstallAlert.class, LogDb.DB_ABS);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(1);

    // How long an install may take to produce the agent's post-provision report.
    // Normal is under two minutes (hooks ~5 s, settle 10 s, diagnostic ~30 s);
    // the standalone path also waits for the user to close the Installer window.
    private static final int INSTALL_GRACE_SECONDS = 15 * 60;
    // Installs older than this are not alerted on: an old install that never
    // reported is either long known or a decommissioned device.
    private static final int INSTALL_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;
    private static final int SWEEP_INTERVAL_SECONDS = 5 * 60;
    private static final int MAX_ALERT_KEYS = 50_000;

    private static final Set<String> FAILED_STATUSES =
            new HashSet<>(Arrays.asList("failed", "verify-failed", "uninstall-failed"));

    // installId format written by install_telemetry.sh: yyyyMMdd'T'HHmmss'Z'-<suffix>
    private static final DateTimeFormatter INSTALL_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private static final ConcurrentHashMap<Integer, Integer> lastSweepTs = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> alerted = new ConcurrentHashMap<>();

    /**
     * Accounts to alert for, from ENDPOINT_SHIELD_INSTALL_ALERT_ACCOUNTS
     * (comma-separated ids). Unset or empty means every account.
     */
    private static boolean isMonitored(int accountId) {
        String raw = System.getenv("ENDPOINT_SHIELD_INSTALL_ALERT_ACCOUNTS");
        if (raw == null || raw.trim().isEmpty()) {
            return true;
        }
        for (String id : raw.split(",")) {
            if (id.trim().equals(String.valueOf(accountId))) {
                return true;
            }
        }
        return false;
    }

    public static void checkOnHeartbeat(List<ModuleInfo> moduleInfoList) {
        Integer accountIdObj = Context.accountId.get();
        if (accountIdObj == null || !isMonitored(accountIdObj)) {
            return;
        }
        int accountId = accountIdObj;
        List<ModuleInfo> snapshot = moduleInfoList == null ? null : new ArrayList<>(moduleInfoList);
        try {
            executorService.submit(() -> {
                Context.accountId.set(accountId);
                try {
                    if (snapshot != null) {
                        for (ModuleInfo module : snapshot) {
                            checkReportedFailure(accountId, module);
                        }
                    }
                    int now = Context.now();
                    Integer last = lastSweepTs.get(accountId);
                    if (last == null || now - last >= SWEEP_INTERVAL_SECONDS) {
                        lastSweepTs.put(accountId, now);
                        sweepUnprovisionedInstalls(accountId, now);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error checking endpoint shield install alerts for account " + accountId);
                }
            });
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error submitting endpoint shield install alert check for account " + accountId);
        }
    }

    /** Signal 1: an installer heartbeat that itself says the install failed. */
    private static void checkReportedFailure(int accountId, ModuleInfo module) {
        if (module == null || module.getModuleType() != ModuleType.MCP_ENDPOINT_SHIELD) {
            return;
        }
        Map<String, Object> ad = module.getAdditionalData();
        String status = str(ad, "installStatus");
        if (!FAILED_STATUSES.contains(status)) {
            return;
        }
        String installId = str(ad, "installId");
        if (!firstTime(accountId + ":reported:" + module.getId() + ":" + installId + ":" + status)) {
            return;
        }
        send(String.format(
                "Endpoint Shield install FAILED — account %d, device %s (agent %s): installStatus=%s exitCode=%s%s, install_id=%s. "
                        + "Details: installation-logs for this agent, filter on install_id.",
                accountId, module.getName(), module.getId(), status, str(ad, "installExitCode"),
                str(ad, "verifyReason").isEmpty() ? "" : " reason=" + str(ad, "verifyReason"), installId));
    }

    /** Signal 2: installed, but the agent never reported provisioning that install. */
    private static void sweepUnprovisionedInstalls(int accountId, int now) {
        List<ModuleInfo> modules = ModuleInfoDao.instance.findAll(
                Filters.and(
                        Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.MCP_ENDPOINT_SHIELD),
                        Filters.exists(ModuleInfo.ADDITIONAL_DATA + ".installId")),
                Projections.include(
                        ModuleInfo.NAME, ModuleInfo.MODULE_TYPE, ModuleInfo.LAST_HEARTBEAT_RECEIVED,
                        ModuleInfo.ADDITIONAL_DATA + ".installId",
                        ModuleInfo.ADDITIONAL_DATA + ".provisionedInstallId",
                        ModuleInfo.ADDITIONAL_DATA + ".installStatus"));
        if (modules == null) {
            return;
        }
        for (ModuleInfo module : modules) {
            Map<String, Object> ad = module.getAdditionalData();
            String installId = str(ad, "installId");
            if (installId.isEmpty() || installId.equals(str(ad, "provisionedInstallId"))) {
                continue;
            }
            // Reported failures and uninstalls are covered by signal 1 / need no alert.
            String status = str(ad, "installStatus");
            if (FAILED_STATUSES.contains(status) || "uninstalled".equals(status)) {
                continue;
            }
            Integer installedAt = installIdEpoch(installId);
            if (installedAt == null) {
                continue;
            }
            int age = now - installedAt;
            if (age < INSTALL_GRACE_SECONDS || age > INSTALL_MAX_AGE_SECONDS) {
                continue;
            }
            if (!firstTime(accountId + ":unprovisioned:" + module.getId() + ":" + installId)) {
                continue;
            }
            send(String.format(
                    "Endpoint Shield installed but agent NEVER REPORTED — account %d, device %s (agent %s): install_id=%s %d min ago, "
                            + "no post-provision report from the agent (installStatus=%s, last heartbeat %d min ago). "
                            + "Likely: agent not started (Login Items/BTM, user not logged in), crashed on start, or cannot reach/authenticate to the backend.",
                    accountId, module.getName(), module.getId(), installId, age / 60, status,
                    module.getLastHeartbeatReceived() > 0 ? (now - module.getLastHeartbeatReceived()) / 60 : -1));
        }
    }

    private static Integer installIdEpoch(String installId) {
        if (installId == null || installId.length() < 16) {
            return null;
        }
        try {
            return (int) LocalDateTime.parse(installId.substring(0, 16), INSTALL_ID_TIME).toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean firstTime(String key) {
        if (alerted.size() > MAX_ALERT_KEYS) {
            alerted.clear();
        }
        return alerted.putIfAbsent(key, Boolean.TRUE) == null;
    }

    private static void send(String message) {
        loggerMaker.infoAndAddToDb(message);
        RedactAlert.sendToCyborgSlack(message);
    }

    private static String str(Map<String, Object> m, String key) {
        if (m == null) {
            return "";
        }
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}

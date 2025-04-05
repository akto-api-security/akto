package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.CollectionReplaceDetails;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.runtime.Main;
import com.akto.util.DashboardMode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import com.opensymphony.xwork2.Action;
import org.apache.kafka.common.protocol.types.Field.Str;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import org.checkerframework.checker.units.qual.C;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AdminSettingsAction extends UserAction {

    AccountSettings accountSettings;
    private int globalRateLimit = 0;
    private static final Logger logger = LoggerFactory.getLogger(AdminSettingsAction.class);
    private Organization organization;
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private static final String IP_REGEX = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    private static final Pattern IP_PATTERN = Pattern.compile(IP_REGEX);

    private static final String CIDR_REGEX = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/(3[0-2]|[12]?[0-9])$";
    private static final Pattern CIDR_PATTERN = Pattern.compile(CIDR_REGEX);

    @Override
    public String execute() throws Exception {
        accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        organization = OrganizationsDao.instance.findOne(Filters.empty());
        return SUCCESS.toUpperCase();
    }

    public AccountSettings.SetupType setupType;
    public Boolean newMergingEnabled;
    private Set<String> privateCidrList;

    public Boolean enableTelemetry;

	private Set<String> partnerIpList;
    private List<String> allowRedundantEndpointsList;

    public String updateSetupType() {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.SETUP_TYPE, this.setupType),
                new UpdateOptions().upsert(true)
        );

        return SUCCESS.toUpperCase();
    }

    public String updateGlobalRateLimit() {

        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.GLOBAL_RATE_LIMIT, globalRateLimit));
        return SUCCESS.toUpperCase();
    }

    public String toggleNewMergingEnabled() {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.URL_REGEX_MATCHING_ENABLED, this.newMergingEnabled),
                new UpdateOptions().upsert(true)
        );

        return SUCCESS.toUpperCase();
    }

    public String toggleTelemetry() {
        if (!DashboardMode.isOnPremDeployment()) return Action.ERROR.toUpperCase();

        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) {
            addActionError("Only admin can add change this setting");
            return Action.ERROR.toUpperCase();
        }
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        TelemetrySettings telemetrySettings = accountSettings.getTelemetrySettings();
        telemetrySettings.setCustomerEnabled(enableTelemetry);
        telemetrySettings.setCustomerEnabledAt(Context.now());
        AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.TELEMETRY_SETTINGS, telemetrySettings));
        return SUCCESS.toUpperCase();
    }

    public String updateMergeAsyncOutside() {
        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();

        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.MERGE_ASYNC_OUTSIDE, true),
                new UpdateOptions().upsert(true)
        );

        return SUCCESS.toUpperCase();
    }

    private int trafficAlertThresholdSeconds;
    public String updateTrafficAlertThresholdSeconds() {
        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();

        if (trafficAlertThresholdSeconds > 3600*24*6) {
            // this was done because our lookback period to calculate last timestamp is 6 days
            addActionError("Alert can't be set for more than 6 days");
            return ERROR.toUpperCase();
        }

        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.TRAFFIC_ALERT_THRESHOLD_SECONDS, trafficAlertThresholdSeconds),
                new UpdateOptions().upsert(true)
        );

        return SUCCESS.toUpperCase();
    }

    private boolean redactPayload;
    public String toggleRedactFeature() {
        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) return ERROR.toUpperCase();

        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.combine(
                    Updates.set(AccountSettings.REDACT_PAYLOAD, redactPayload),
                    Updates.set(AccountSettings.SAMPLE_DATA_COLLECTION_DROPPED, false)
                ),
                new UpdateOptions().upsert(true)
        );


        if (!redactPayload) return SUCCESS.toUpperCase();

        dropCollectionsInitial(Context.accountId.get());

        int accountId = Context.accountId.get();

        executorService.schedule( new Runnable() {
            public void run() {
                dropCollections(accountId);
            }
        }, 3*Main.sync_threshold_time, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    private static void dropCollectionsInitial(int accountId) {
        logger.info("Dropping collection initial");
        Context.accountId.set(accountId);
        SampleDataDao.instance.getMCollection().drop();
        FilterSampleDataDao.instance.getMCollection().drop();
        SensitiveSampleDataDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.deleteValues();
    }

    public static void dropCollections(int accountId) {
        logger.info("CALLED: " + Context.now());
        dropCollectionsInitial(accountId);
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.SAMPLE_DATA_COLLECTION_DROPPED, true), new UpdateOptions().upsert(true)
        );
    }

    private boolean enableDebugLogs;
    public String toggleDebugLogsFeature() {
        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.ENABLE_DEBUG_LOGS, enableDebugLogs)
        );

        return SUCCESS.toUpperCase();
    }


    private Map<String, String> filterHeaderValueMap;

    public String addFilterHeaderValueMap() {
        Bson update;
        if (this.filterHeaderValueMap == null) {
            update = Updates.unset(AccountSettings.FILTER_HEADER_VALUE_MAP);
        } else {
            update = Updates.set(AccountSettings.FILTER_HEADER_VALUE_MAP, this.filterHeaderValueMap);
        }

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), update
        );

        return SUCCESS.toUpperCase();
    }

    private String regex;
    private String newName;
    private String headerName = "host";

    public String addApiCollectionNameMapper() {
        String hashStr = regex.hashCode()+"";
        Bson update = Updates.set(AccountSettings.API_COLLECTION_NAME_MAPPER+"."+hashStr, new CollectionReplaceDetails(regex, newName, headerName));

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), update
        );

        return SUCCESS.toUpperCase();
    }

    public String deleteApiCollectionNameMapper() {

        String hashStr = regex.hashCode()+"";

        Bson update = Updates.unset(AccountSettings.API_COLLECTION_NAME_MAPPER+"."+hashStr);

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), update
        );

        return SUCCESS.toUpperCase();
    }
    
    public String editPrivateCidrList(){
        if (!validateCidrs(privateCidrList)) {
            addActionError("Invalid CIDR detected");
            return Action.ERROR.toUpperCase();
        }

        try {
            AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.PRIVATE_CIDR_LIST, privateCidrList)
            );
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            return ERROR.toUpperCase();
        }

    }

    private static boolean validateCidrs(Set<String> cidrSet) {
        for (String cidr : cidrSet) {
            if (!validateCidr(cidr)) return false;
        }
        return true;
    }

    private static boolean validateIps(Set<String> ipSet) {
        for (String ip : ipSet) {
            if (!validateIp(ip)) return false;
        }
        return true;
    }

    private static boolean validateCidr(String cidr) {
        if (cidr == null) return false;
        return CIDR_PATTERN.matcher(cidr).matches();
    }

    private static boolean validateIp(String ip) {
        if (ip == null) return false;
        return IP_PATTERN.matcher(ip).matches();
    }

    public String editPartnerIpList(){

        if (!validateIps(partnerIpList)) {
            addActionError("Invalid IP detected");
            return Action.ERROR.toUpperCase();
        }

        try {
            AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.PARTNER_IP_LIST, partnerIpList)
            );
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            return ERROR.toUpperCase();
        }

    }

    public String updateUrlSettings() {
        try {
            for(String ext : this.allowRedundantEndpointsList){
                if (ext.matches(".*[\\\\/:*?\"<>|].*")) {
                    addActionError(ext + " url type is invalid" );
                    return Action.ERROR.toUpperCase();
                }
            }
            AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.ALLOW_REDUNDANT_ENDPOINTS_LIST, this.allowRedundantEndpointsList),
                new UpdateOptions().upsert(true)
            );

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            return ERROR.toUpperCase();
        }
        
    }

    public AccountSettings getAccountSettings() {
        return this.accountSettings;
    }

    public void setRedactPayload(boolean redactPayload) {
        this.redactPayload = redactPayload;
    }

    public void setSetupType(AccountSettings.SetupType setupType) {
        this.setupType = setupType;
    }

    public Boolean getNewMergingEnabled() {
        return newMergingEnabled;
    }

    public void setNewMergingEnabled(Boolean newMergingEnabled) {
        this.newMergingEnabled = newMergingEnabled;
    }

    public void setEnableDebugLogs(boolean enableDebugLogs) {
        this.enableDebugLogs = enableDebugLogs;
    }

    public void setFilterHeaderValueMap(Map<String, String> filterHeaderValueMap) {
        this.filterHeaderValueMap = filterHeaderValueMap;
    }

    public Map<String, String> getFilterHeaderValueMap() {
        return filterHeaderValueMap;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public int getGlobalRateLimit() {
        return globalRateLimit;
    }

    public void setGlobalRateLimit(int globalRateLimit) {
        this.globalRateLimit = globalRateLimit;
    }

    public void setTrafficAlertThresholdSeconds(int trafficAlertThresholdSeconds) {
        this.trafficAlertThresholdSeconds = trafficAlertThresholdSeconds;
    }


    public Boolean getEnableTelemetry() {
        return enableTelemetry;
    }

    public void setEnableTelemetry(Boolean enableTelemetry) {
        this.enableTelemetry = enableTelemetry;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Set<String> getPartnerIpList() {
		return partnerIpList;
	}

    public void setPartnerIpList(Set<String> partnerIpList) {
        this.partnerIpList = partnerIpList.stream()
                                          .map(String::trim)
                                          .collect(Collectors.toSet());
    }

    public void setPrivateCidrList(Set<String> privateCidrList) {
        this.privateCidrList = privateCidrList.stream()
                                              .map(String::trim)
                                              .collect(Collectors.toSet());
    }

    public Set<String> getPrivateCidrList() {
		return privateCidrList;
	}
    
    public void setAllowRedundantEndpointsList(List<String> allowRedundantEndpointsList) {
        this.allowRedundantEndpointsList = allowRedundantEndpointsList;
    }   
}

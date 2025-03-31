package com.akto.action;

import com.akto.action.observe.Utils;
import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.CollectionReplaceDetails;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.runtime.Main;
import com.akto.runtime.policies.ApiAccessTypePolicy;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.utils.libs.utils.src.main.java.com.akto.runtime.policies.ApiAccessTypePolicyUtil;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import com.opensymphony.xwork2.Action;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import java.util.ArrayList;
import java.util.HashMap;
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

    Account currentAccount;

    @Override
    public String execute() throws Exception {
        accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        organization = OrganizationsDao.instance.findOne(Filters.empty());
        if(Context.accountId.get() != null && Context.accountId.get() != 0){
            currentAccount = AccountsDao.instance.findOne(
                Filters.eq(Constants.ID, Context.accountId.get()),
                Projections.include("name", "timezone")
            );
        }
        return SUCCESS.toUpperCase();
    }

    public AccountSettings.SetupType setupType;
    public Boolean newMergingEnabled;
    private Set<String> privateCidrList;

    public Boolean enableTelemetry;

	private Set<String> partnerIpList;
    private List<String> allowRedundantEndpointsList;
    private boolean toggleCaseSensitiveApis;

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

    public String applyAccessType(){
        try {
            int accountId = Context.accountId.get();
            accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            List<String> privateCidrList = new ArrayList<>();
            if (accountSettings != null &&
                    accountSettings.getPrivateCidrList() != null &&
                    !accountSettings.getPrivateCidrList().isEmpty()) {
                privateCidrList = accountSettings.getPrivateCidrList();
            }
            ApiAccessTypePolicy policy = new ApiAccessTypePolicy(privateCidrList);

            executorService.schedule(new Runnable() {
                public void run() {
                    try {
                        Context.accountId.set(accountId);
                        List<String> partnerIpList = new ArrayList<>();
                        if (accountSettings != null &&
                                accountSettings.getPartnerIpList() != null &&
                                !accountSettings.getPartnerIpList().isEmpty()) {
                            partnerIpList = accountSettings.getPartnerIpList();
                        }
                        ApiAccessTypePolicyUtil.calcApiAccessType(policy, partnerIpList);
                    } catch (Exception e){
                        logger.error("Error in applyAccessType", e);
                    }
                }
            }, 0, TimeUnit.SECONDS);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
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

    private boolean updateFiltersFlag;
    private String permissionValue;

    Map<String,Boolean> advancedFilterPermission;

    

    public String getAdvancedFilterFlagsForAccount(){
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        advancedFilterPermission = new HashMap<>();
        advancedFilterPermission.put(AccountSettings.ALLOW_FILTER_LOGS, accountSettings.getAllowFilterLogs());
        advancedFilterPermission.put(AccountSettings.ALLOW_DELETION_OF_REDUNDANT_URLS, accountSettings.getAllowDeletionOfUrls());

        return SUCCESS.toUpperCase();
    }

    public String updatePermissionsForAdvancedFilters(){
        if(this.permissionValue.equals(AccountSettings.ALLOW_DELETION_OF_REDUNDANT_URLS) || this.permissionValue.equals(AccountSettings.ALLOW_FILTER_LOGS)){
            AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(this.permissionValue, this.updateFiltersFlag)
            );
            return SUCCESS.toUpperCase();
        }else{
            addActionError("invalid permission");
            return ERROR.toUpperCase();
        }
    }

    public String accountPermission;
    public String modifiedValueForAccount;

    static int maxValueLength = 60;

    public String modifyAccountSettings () {

        StringBuilder error = new StringBuilder();
        boolean sanitized = Utils.isInputSanitized(modifiedValueForAccount, error, maxValueLength);
        if (!sanitized) {
            addActionError(error.toString());
            return ERROR.toUpperCase();
        }

        if(accountPermission.equals("name") || accountPermission.equals("timezone")){
            if(Context.accountId.get() != null && Context.accountId.get() != 0){
                AccountsDao.instance.updateOne(
                    Filters.eq(Constants.ID, Context.accountId.get()),
                    Updates.set(accountPermission, modifiedValueForAccount)
                );
                if(accountPermission.equals("name")){
                    UsersDao.instance.updateManyNoUpsert(
                        Filters.exists(User.ACCOUNTS + "." + Context.accountId.get()),
                        Updates.set(User.ACCOUNTS + "." + Context.accountId.get() + ".name", modifiedValueForAccount)
                    );
                }
                return SUCCESS.toUpperCase();
            }else{
                addActionError("Account id cannot be null");
                return ERROR.toUpperCase();
            }
        }else{
            addActionError("Permission not modifiable");
            return ERROR.toUpperCase();
        }
    }

    private int deltaTimeForScheduledSummaries;

    public String updateDeltaTimeForIgnoringSummaries () {
        if(this.deltaTimeForScheduledSummaries < 1200){
            addActionError("Value cannot be less than 20 minutes");
            return ERROR.toUpperCase();
        }
        if(this.deltaTimeForScheduledSummaries > 14400){
            addActionError("Value cannot be greater than 4 hours");
            return ERROR.toUpperCase();
        }
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.DELTA_IGNORE_TIME_FOR_SCHEDULED_SUMMARIES, this.deltaTimeForScheduledSummaries));
        return SUCCESS.toUpperCase();
    }

    public String toggleCaseSensitiveURLs() {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.HANDLE_APIS_CASE_INSENSITIVE, this.toggleCaseSensitiveApis),
                new UpdateOptions().upsert(false)
        );

        return SUCCESS.toUpperCase();
    }

    public void setAccountPermission(String accountPermission) {
        this.accountPermission = accountPermission;
    }

    public void setModifiedValueForAccount(String modifiedValueForAccount) {
        this.modifiedValueForAccount = modifiedValueForAccount;
    }

    public void setUpdateFiltersFlag(boolean updateFiltersFlag) {
        this.updateFiltersFlag = updateFiltersFlag;
    }

    public void setPermissionValue(String permissionValue) {
        this.permissionValue = permissionValue;
    }

    public Map<String, Boolean> getAdvancedFilterPermission() {
        return advancedFilterPermission;
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

    public Account getCurrentAccount() {
        return currentAccount;
    }

    public void setCurrentAccount(Account currentAccount) {
        this.currentAccount = currentAccount;
    }

    public void setDeltaTimeForScheduledSummaries(int deltaTimeForScheduledSummaries) {
        this.deltaTimeForScheduledSummaries = deltaTimeForScheduledSummaries;
    }

    public void setToggleCaseSensitiveApis(boolean toggleCaseSensitiveApis) {
        this.toggleCaseSensitiveApis = toggleCaseSensitiveApis;
    }
}

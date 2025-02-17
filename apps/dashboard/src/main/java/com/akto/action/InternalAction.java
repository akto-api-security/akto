package com.akto.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.RBAC.Role;
import com.akto.dto.billing.Organization;
import com.akto.dto.Account;
import com.akto.dto.User;
import com.akto.dto.traffic.Key;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public class InternalAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(InternalAction.class, LogDb.DASHBOARD);

    String headerKey;
    boolean actuallyDelete;
    int count;

    public String deleteApisBasedOnHeader() {

        if (headerKey == null || headerKey.isEmpty()) {
            addActionError("Invalid header key");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        if (user.getLogin() != null && !user.getLogin().contains("@akto")) {
            addActionError("Illegal operation");
            return ERROR.toUpperCase();
        }

        int time = Context.now();
        Bson filter = Filters.and(Filters.eq(SingleTypeInfo._PARAM, headerKey),
                UsageMetricCalculator.excludeDemosAndDeactivated(SingleTypeInfo._API_COLLECTION_ID));
        loggerMaker.infoAndAddToDb("Executing deleteApisBasedOnHeader find query");
        List<ApiInfoKey> apiList = SingleTypeInfoDao.instance.fetchEndpointsInCollection(filter);

        int delta = Context.now() - time;
        loggerMaker.infoAndAddToDb("Finished deleteApisBasedOnHeader find query " + delta);

        if (apiList != null && !apiList.isEmpty()) {

            List<Key> keys = new ArrayList<>();
            for (ApiInfoKey apiInfoKey : apiList) {
                loggerMaker.infoAndAddToDb("deleteApisBasedOnHeader " + apiInfoKey.toString());
                keys.add(new Key(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod(), -1, 0,
                        0));
            }

            count = apiList.size();
            if (actuallyDelete) {
                try {
                    time = Context.now();
                    loggerMaker.infoAndAddToDb("deleteApisBasedOnHeader deleting APIs");
                    com.akto.utils.Utils.deleteApis(keys);
                    delta = Context.now() - time;
                    loggerMaker.infoAndAddToDb("deleteApisBasedOnHeader deleted APIs " + delta);
                } catch (Exception e) {
                    e.printStackTrace();
                    addActionError("Error deleting APIs");
                    return ERROR.toUpperCase();
                }
            }
        }
        return SUCCESS.toUpperCase();
    }

    private int accountId;
    private Role role;

    private String accountKey;
    private String keyType;

    private BasicDBObject response;

    public String goToAccount(){
        if(AccountsDao.instance.count(Filters.eq(Constants.ID, accountId)) > 0){
            RBACDao.instance.addUserRoleEntry(Context.userId.get(), accountId, role);
            return SUCCESS.toUpperCase();
        }else{
            return ERROR.toUpperCase();
        }
    }

    public String getOrganizationInfo(){
        int accountId = -1;
        Organization org= null;
        try {
            switch (keyType) {
                case "accountId":
                    accountId = Integer.parseInt(accountKey);
                    break;
                case "organizationName":
                    org = OrganizationsDao.instance.findOne(
                        Filters.regex(Organization.ADMIN_EMAIL, accountKey)
                    );
                case "orgId":
                    org = OrganizationsDao.instance.findOne(
                        Filters.regex(Organization.ADMIN_EMAIL, accountKey)
                    );  
                default:
                    break;
            }
            if(org != null){
                List<Integer> accounts = new ArrayList<Integer>(org.getAccounts());
                accountId = accounts.get(0);
            }
            Account account =  AccountsDao.instance.findOne(Constants.ID, accountId);
            response = new BasicDBObject();
            response.put(Account.HYBRID_SAAS_ACCOUNT, account.getHybridSaasAccount());
            response.put(Account.HYBRID_TESTING_ENABLED, account.getHybridTestingEnabled());

            if(org == null){
                org = OrganizationsDao.instance.findOne(Filters.in(Organization.ACCOUNTS, Arrays.asList(accountId)));
            }
            
            String orgName = org.getName();
            if(orgName.contains("@")){
                String[] emailPart = orgName.split("@");
                orgName = emailPart[1];
            }

            response.put(Organization.NAME, orgName);

            try {
                Context.accountId.set(accountId);
                // TODO: figure out auth-mechanism type
            } catch (Exception e) {
                e.printStackTrace();
            }
        
            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            addActionError("Error in getting organization info.");
            e.printStackTrace();
            return ERROR.toUpperCase();
        }
        
    }

    

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public String getHeaderKey() {
        return headerKey;
    }

    public void setHeaderKey(String headerKey) {
        this.headerKey = headerKey;
    }

    public boolean getActuallyDelete() {
        return actuallyDelete;
    }

    public void setActuallyDelete(boolean actuallyDelete) {
        this.actuallyDelete = actuallyDelete;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public BasicDBObject getResponse() {
        return response;
    }
}

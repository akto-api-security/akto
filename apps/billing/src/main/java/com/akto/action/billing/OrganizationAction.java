package com.akto.action.billing;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.billing.TokensDao;
import com.akto.dao.context.Context;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.stigg.StiggReporterClient;
import com.akto.util.UsageUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import org.bson.conversions.Bson;

import static com.opensymphony.xwork2.Action.ERROR;
import static com.opensymphony.xwork2.Action.SUCCESS;

import java.util.UUID;

public class OrganizationAction {

    private Organization organization;
    private int accountId;
    private Tokens tokens;

    private static final LoggerMaker loggerMaker = new LoggerMaker(OrganizationAction.class);
    
    public String createOrganization() {
        try {
            String organizationId = organization.getId();
            String organizationName = organization.getName();
            loggerMaker.infoAndAddToDb(String.format("Creating organization - (%s / %s) ...", organizationName, organizationId), LogDb.BILLING);
            
            loggerMaker.infoAndAddToDb(String.format("Checking if organization - (%s / %s) exists ...", organizationName, organizationId), LogDb.BILLING);
            Organization existingOrganization = OrganizationsDao.instance.findOne(
                Filters.eq(Organization.ID, organization.getId())
            );
            
            if (existingOrganization == null) {
                loggerMaker.infoAndAddToDb(String.format("Organization - (%s / %s) does not exist. Creating ...", organizationName, organizationId), LogDb.BILLING);
                organization.setSyncedWithAkto(true);
                StiggReporterClient.instance.provisionCustomer(organization);
                OrganizationsDao.instance.insertOne(organization);
            } else {
                loggerMaker.infoAndAddToDb(String.format("Organization - (%s / %s) exists. Updating ...", organizationName, organizationId), LogDb.BILLING);
                Bson updatesQ = Updates.set(Organization.ACCOUNTS, organization.getAccounts());
                OrganizationsDao.instance.updateOne(Organization.ID, organization.getId(), updatesQ);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,String.format("Error while creating organization. Error: %s", e.getMessage()), LogDb.BILLING);
            return Action.ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    public String saveToken() {

        Bson filters = Filters.and(
            Filters.eq(Tokens.ORG_ID, orgId),
            Filters.eq(Tokens.ACCOUNT_ID, accountId)
        );
        tokens = TokensDao.instance.findOne(filters);
        Bson updates;
        if (tokens == null) {
            updates = Updates.combine(
                Updates.set(Tokens.UPDATED_AT, Context.now()),
                Updates.setOnInsert(Tokens.CREATED_AT, Context.now()),
                Updates.setOnInsert(Tokens.ORG_ID, orgId),
                Updates.setOnInsert(Tokens.ACCOUNT_ID, accountId)
            );
        } else {
            updates = Updates.set(Tokens.UPDATED_AT, Context.now());
        }
        String token = orgId + "_" + accountId + "_" + UUID.randomUUID().toString().replace("-", "");
        UsageUtils.saveToken(orgId, accountId, updates, filters, token);
        tokens = TokensDao.instance.findOne(filters);

        return SUCCESS.toUpperCase();
    }

    private String orgId;
    public String fetchOrgDetails() {
        this.organization = OrganizationsDao.instance.findOne(Organization.ID, orgId);
        return SUCCESS.toUpperCase();
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public Tokens getTokens() {
        return tokens;
    }

    public void setTokens(Tokens tokens) {
        this.tokens = tokens;
    }
}

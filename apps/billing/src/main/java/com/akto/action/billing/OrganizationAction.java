package com.akto.action.billing;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.stigg.StiggReporterClient;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

public class OrganizationAction {

    private Organization organization;

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
                StiggReporterClient.instance.provisionSubscription(organization.getId(), "plan-akto-test", "ANNUALLY", "https://some.checkout.url", "https://some.checkout.url");
                OrganizationsDao.instance.insertOne(organization);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("Error while creating organization. Error: %s", e.getMessage()), LogDb.BILLING);
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

}

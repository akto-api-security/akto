package com.akto.action.selfhosted;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.Organization;
import com.akto.stigg.StiggReporterClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;

public class InfoAction extends ActionSupport {

    String orgId;
    String adminEmail;
    String clientKey;
    public String fetchClientKey() {

        Organization organization = OrganizationsDao.instance.findOne(Organization.ID, orgId, Organization.ADMIN_EMAIL, adminEmail);

        if (organization == null) {
            addActionError("Organization not found: " + orgId + " " + adminEmail);
            return ERROR.toUpperCase();
        }

        clientKey = StiggReporterClient.instance.getStiggConfig().getClientKey();
        return Action.SUCCESS.toUpperCase();
    }

    String signature;
    public String fetchSignature() {
        String signingKey = StiggReporterClient.instance.getStiggConfig().getSigningKey();
        if (StringUtils.isEmpty(signingKey)) {
            addActionError("Signing key not found");
            return ERROR.toUpperCase();
        }

        Organization organization = OrganizationsDao.instance.findOne(Organization.ID, orgId, Organization.ADMIN_EMAIL, adminEmail);

        if (organization == null) {
            addActionError("Organization not found to fetch Signature: " + orgId + " " + adminEmail);
            return ERROR.toUpperCase();
        }

        String stiggSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, signingKey).hmacHex(orgId);
        signature = "HMAC-SHA256 " + orgId + ":" + stiggSignature;
        return SUCCESS.toUpperCase();
    }

    BasicDBList entitlements;
    public String fetchEntitlements() {
        Organization organization = OrganizationsDao.instance.findOne(Organization.ID, orgId, Organization.ADMIN_EMAIL, adminEmail);

        if (organization == null) {
            addActionError("Organization not found to fetch Signature: " + orgId + " " + adminEmail);
            return ERROR.toUpperCase();
        }

        entitlements = StiggReporterClient.instance.fetchEntitlements(orgId);

        return SUCCESS.toUpperCase();
    }

    BasicDBObject additionalMetaData;
    public String fetchOrgMetaData() {
        Organization organization = OrganizationsDao.instance.findOne(Organization.ID, orgId, Organization.ADMIN_EMAIL, adminEmail);

        if (organization == null) {
            addActionError("Organization not found to fetch Signature: " + orgId + " " + adminEmail);
            return ERROR.toUpperCase();
        }
        additionalMetaData = StiggReporterClient.instance.fetchOrgMetaData(orgId);

        return SUCCESS.toUpperCase();
    }

    public BasicDBObject getAdditionalMetaData() {
        return additionalMetaData;
    }

    public void setAdditionalMetaData(BasicDBObject additionalMetaData) {
        this.additionalMetaData = additionalMetaData;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }


    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getClientKey() {
        return clientKey;
    }


    public String getSignature() {
        return signature;
    }

    public BasicDBList getEntitlements() {
        return entitlements;
    }
}

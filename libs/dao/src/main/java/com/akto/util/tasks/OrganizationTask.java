package com.akto.util.tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.Organization;
import com.akto.util.AccountTask;
import com.mongodb.BasicDBObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrganizationTask {
    private static final Logger logger = LoggerFactory.getLogger(OrganizationTask.class);
    public static final OrganizationTask instance = new OrganizationTask();

    public void executeTask(Consumer<Organization> consumeOrganization, String taskName) {

        List<Organization> organizations = OrganizationsDao.instance.findAll(new BasicDBObject());
        for(Organization organization: organizations) {
            Set<Integer> activeAccounts = new HashSet<>();
            for (int accountId: organization.getAccounts()) {
                if (!AccountTask.inactiveAccountsSet.contains(accountId)) {
                    activeAccounts.add(accountId);
                }
            }
            organization.setAccounts(activeAccounts);
            try {
                logger.info("executing " + taskName + " for org: " + organization.getName() + ": " + organization.getId());
                consumeOrganization.accept(organization);
            } catch (Exception e) {
                String msgString = String.format("Error in executing task %s for organizatons %s - %s", taskName, organization.getId(), organization.getName());
                logger.error(msgString, e);
            }
        }

    }
}

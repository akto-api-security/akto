package com.akto.service.guardrails;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.context.Context;
import com.akto.dto.GuardrailPolicies;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Seeds the default endpoint guardrail policies into the current account (Context.accountId) when it has
 * at least one endpoint collection.
 *
 * The account is skipped as a whole if any ENDPOINT-context policy was created by DefaultEndpointGuardrailPolicies.CREATED_BY
 * or has the name of one of the defaults. Nothing is ever updated. Deleting a single default policy therefore does not
 * bring it back, while deleting all of them does.
 */
public class DefaultEndpointGuardrailsService {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DefaultEndpointGuardrailsService.class, LogDb.DASHBOARD);

    /** @return the number of policies inserted. Throws on a DB failure: that must not read as "nothing to seed". */
    public int seedCurrentAccount() {
        if (!ApiCollectionsDao.instance.hasEndpointCollection()) {
            return 0;
        }

        List<GuardrailPolicies> defaults = DefaultEndpointGuardrailPolicies.build(Context.now());
        List<String> names = defaults.stream().map(GuardrailPolicies::getName).collect(Collectors.toList());
        String createdBy = DefaultEndpointGuardrailPolicies.CREATED_BY;

        if (GuardrailPoliciesDao.instance.existsByCreatedByOrName(CONTEXT_SOURCE.ENDPOINT, createdBy, names)) {
            return 0;
        }

        insertAllOrNone(defaults, createdBy, names);
        loggerMaker.infoAndAddToDb("Seeded " + defaults.size() + " default endpoint guardrail policies in account "
                + Context.accountId.get());
        return defaults.size();
    }

    // A partial insert would leave a createdBy match behind, so the skip rule would then hide the missing policies forever.
    private void insertAllOrNone(List<GuardrailPolicies> defaults, String createdBy, List<String> names) {
        try {
            for (GuardrailPolicies policy : defaults) {
                GuardrailPoliciesDao.instance.insertOne(policy);
            }
        } catch (RuntimeException e) {
            try {
                GuardrailPoliciesDao.instance.deleteByCreatedByAndName(CONTEXT_SOURCE.ENDPOINT, createdBy, names);
            } catch (RuntimeException rollbackFailure) {
                loggerMaker.errorAndAddToDb(rollbackFailure, "Could not roll back partially seeded endpoint guardrails in account "
                        + Context.accountId.get() + ": " + rollbackFailure.getMessage());
            }
            throw e;
        }
    }
}

package com.akto.filters;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthFilterTest {

    @Test
    public void tokenForThisDeploymentIsAccepted() {
        assertTrue(AuthFilter.isForThisDeployment(1718012345, 1718012345));
    }

    @Test
    public void tokenForAnotherAccountIsRejected() {
        // validly signed tokens of every account share one key; only the account tells them apart
        assertFalse(AuthFilter.isForThisDeployment(1718012345, 1718000000));
    }

    @Test
    public void unknownDeploymentAccountAcceptsAnyAccount() {
        // no DATABASE_ABSTRACTOR_SERVICE_TOKEN on this deployment: behave as before the check
        assertTrue(AuthFilter.isForThisDeployment(1718012345, null));
    }
}

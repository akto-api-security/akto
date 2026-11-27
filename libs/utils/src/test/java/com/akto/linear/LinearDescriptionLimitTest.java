package com.akto.linear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

public class LinearDescriptionLimitTest {

    private static final int LIMIT = LinearIntegrationService.MAX_DESCRIPTION_LENGTH;

    @Test
    public void keepsDescriptionsWithinTheLimit() {
        String description = "**Severity:** HIGH\n\n" + StringUtils.repeat('a', 1000);
        assertEquals(description, LinearIntegrationService.fitToLinearLimit(description));

        String atLimit = StringUtils.repeat('a', LIMIT);
        assertEquals(atLimit, LinearIntegrationService.fitToLinearLimit(atLimit));

        String overByOne = StringUtils.repeat('a', LIMIT + 1);
        assertTrue(LinearIntegrationService.fitToLinearLimit(overByOne).length() <= LIMIT);
    }

    @Test
    public void truncatesInsideCodeBlockAndClosesIt() {
        String description = "**Severity:** HIGH\n\n### Test API Response\n\n```\n"
            + StringUtils.repeat('x', LIMIT * 2) + "\n```\n\n";

        String fitted = LinearIntegrationService.fitToLinearLimit(description);

        assertTrue(fitted.length() <= LIMIT);
        assertTrue(fitted.startsWith("**Severity:** HIGH"));
        assertEquals(0, StringUtils.countMatches(fitted, "```") % 2);
        assertTrue(fitted.contains("Truncated to fit Linear's description limit"));
    }

    @Test
    public void truncatesOutsideCodeBlockWithoutAddingFence() {
        String description = StringUtils.repeat('y', LIMIT + 500);

        String fitted = LinearIntegrationService.fitToLinearLimit(description);

        assertTrue(fitted.length() <= LIMIT);
        assertEquals(0, StringUtils.countMatches(fitted, "```"));
    }

    @Test
    public void neverSplitsASurrogatePair() {
        String emoji = "😀";
        String description = StringUtils.repeat(emoji, LIMIT);

        String fitted = LinearIntegrationService.fitToLinearLimit(description);
        String body = fitted.substring(0, fitted.indexOf("\n\n> Truncated"));

        assertTrue(fitted.length() <= LIMIT);
        assertFalse(Character.isHighSurrogate(body.charAt(body.length() - 1)));
        assertEquals(0, body.length() % 2);
    }
}

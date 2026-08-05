package com.akto.jobs.executors.copilotstudio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.akto.jobs.executors.copilotstudio.CopilotStudioUserResolver.GraphUser;

public class CopilotStudioUserResolverTest {

    private static String buildUserId(String id, String displayName, String userPrincipalName) {
        GraphUser u = new GraphUser();
        u.id = id;
        u.displayName = displayName;
        u.userPrincipalName = userPrincipalName;
        return CopilotStudioUserResolver.buildUserId(u);
    }

    @Test
    public void testAsciiUserPrincipalName() {
        assertEquals("john-doe-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", "John Doe", "john.doe@contoso.com"));
    }

    @Test
    public void testChineseCharactersPreserved() {
        assertEquals("王小明-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", "Wang Xiaoming", "王小明@contoso.com"));
    }

    @Test
    public void testKoreanCharactersPreserved() {
        assertEquals("박지민-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", "Park Jimin", "박지민@contoso.com"));
    }

    @Test
    public void testArabicCharactersPreserved() {
        assertEquals("أحمد-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", "Ahmed", "أحمد@contoso.com"));
    }

    @Test
    public void testAccentedLatinCharactersPreserved() {
        assertEquals("josé-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", "Jose", "José@contoso.com"));
    }

    @Test
    public void testPunctuationCollapsesToSingleHyphen() {
        // Only the part before '@' is used - "sub" (the domain) is correctly discarded.
        assertEquals("john-doe-smith-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", "John", "john_doe.smith@sub"));
    }

    @Test
    public void testNoAtSignFallsBackToDisplayName() {
        assertEquals("jane-smith-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", "Jane Smith", null));
    }

    @Test
    public void testMissingDisplayNameAndUpnFallsBackToUser() {
        assertEquals("user-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", null, null));
    }

    @Test
    public void testAllJunkLocalPartFallsBackToUser() {
        // Local part is entirely symbols/whitespace - sanitizes to empty, so the
        // "user" fallback kicks in exactly like a missing displayName/UPN would.
        assertEquals("user-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", null, "!!!@contoso.com"));
    }

    @Test
    public void testAadObjectIdPrefixTruncatedToEightChars() {
        assertEquals("john-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", "John", "john@contoso.com"));
    }

    @Test
    public void testShortAadObjectIdUsedInFull() {
        assertEquals("john-abc",
            buildUserId("abc", "John", "john@contoso.com"));
    }

    @Test
    public void testUppercaseIsLowercased() {
        assertEquals("john-doe-b6010cfd",
            buildUserId("b6010cfd-cc1b-4b10-807d-c2f69fa21298", "JOHN DOE", "John.Doe@Contoso.com"));
    }
}

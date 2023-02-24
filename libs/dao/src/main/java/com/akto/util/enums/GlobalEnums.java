package com.akto.util.enums;

public class GlobalEnums {
    /* * * * * * * *  Enums for Testing run issues * * * * * * * * * * * *  */

    public enum TestErrorSource { // Whether issue came from runtime or automated testing via dashboard
        AUTOMATED_TESTING("testing"),
        RUNTIME("runtime");

        private final String name;

        TestErrorSource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /* Category of tests perfomred */
    public enum TestCategory {
        BOLA("BOLA", Severity.HIGH, "Broken Object Level Authorization (BOLA)", "BOLA"),
        NO_AUTH("NO_AUTH", Severity.HIGH, "Broken User Authentication (BUA)", "Broken Authentication"),
        BFLA("BFLA", Severity.HIGH, "Broken Function Level Authorization (BFLA)", "Broken Function Level Authorization"),
        IAM("IAM", Severity.HIGH, "Improper Assets Management (IAM)", "Improper Assets Management"),
        EDE("EDE", Severity.HIGH, "Excessive Data Exposure (EDE)", "Sensitive Data Exposure"),
        RL("RL", Severity.HIGH, "Lack of Resources & Rate Limiting (RL)", "Lack of Resources and Rate Limiting"),
        MA("MA", Severity.HIGH, "Mass Assignment (MA)", "Mass Assignment"),
        INJ("INJ", Severity.HIGH, "Injection (INJ)", "Injection"),
        ILM("ILM", Severity.HIGH, "Insufficient Logging & Monitoring (ILM)", "Insufficient Logging and Monitoring"),
        SM("SM", Severity.HIGH, "Security Misconfiguration (SM)", "Misconfiguration"),
        UC("UC", Severity.HIGH, "Uncategorized (UC)", "Uncategorized");
        private final String name;
        private final Severity severity;
        private final String displayName;
        private final String shortName;

        TestCategory(String name, Severity severity, String displayName, String shortName) {
            this.name = name;
            this.severity = severity;
            this.displayName = displayName;
            this.shortName = shortName;
        }

        public String getName() {
            return name;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getShortName() {
                return shortName;
        }
    }

    public enum IssueTags {
        BL("Business logic"),
        OWASPTOP10("OWASP top 10"),
        HACKERONETOP10("HackerOne top 10");
        private final String name;
        IssueTags(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
    }

    public enum TestSubCategory {
        REPLACE_AUTH_TOKEN(
                "REPLACE_AUTH_TOKEN",
                TestCategory.BOLA,
                "BOLA by changing auth token",
                "Attacker can access resources of any user by changing the auth token in request.",
                "The endpoint appears to be vulnerable to broken object level authorization attack. The original request " +
                        "was replayed with attacker's auth token. The server responded with 2XX success codes and greater" +
                        " than <b>{{percentageMatch}}%</b> of the response body matched with original response body. Also, the endpoint had atleast one private " +
                        "resources in request payload.<br><br>" +
                        "<b>Background:</b> Object level authorization is an access control mechanism that is usually implemented at the code level to" +
                        " validate that one user can only access objects that they should have access to.",
                "Unauthorized access can result in data disclosure to unauthorized parties, data loss, or data manipulation. Unauthorized access to objects can also lead to full account takeover.",
                new String[]{
                        "https://www.akto.io/blog/bola-exploitation-using-unauthorized-uuid-on-api-endpoint",
                        "https://www.akto.io/blog/what-is-broken-object-level-authorization-bola",
                        "https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa1-broken-object-level-authorization.md",
                        "https://cwe.mitre.org/data/definitions/284.html",
                        "https://cwe.mitre.org/data/definitions/285.html",
                        "https://cwe.mitre.org/data/definitions/639.html"
                }, new IssueTags[]{
                    IssueTags.BL,
                    IssueTags.OWASPTOP10,
                    IssueTags.HACKERONETOP10,
                }),


        ADD_USER_ID(
                "ADD_USER_ID",
                TestCategory.BOLA,
                "IDOR by adding user id in query params",
                "Attacker can access resources of any user by adding user_id in URL.",
                "The endpoint appears to be vulnerable to broken object level authorization attack. The original request was replayed by adding other user's user id in query params. " +
                        "The server responded with 2XX success codes and less than <b>{{percentageMatch}}%</b> of the response body matched with original response body. <br><br>" +
                        "<b>Background:</b> Object level authorization is an access control mechanism that is usually implemented at the code level to validate that one user can only access objects that they should have access to.",
                "Unauthorized access can result in data disclosure to unauthorized parties, data loss, or data manipulation. Unauthorized access to objects can also lead to full account takeover.",
                new String[]{
                        "https://www.akto.io/blog/bola-exploitation-using-unauthorized-uuid-on-api-endpoint",
                        "https://www.akto.io/blog/what-is-broken-object-level-authorization-bola",
                        "https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa1-broken-object-level-authorization.md",
                        "https://cwe.mitre.org/data/definitions/284.html",
                        "https://cwe.mitre.org/data/definitions/285.html",
                        "https://cwe.mitre.org/data/definitions/639.html"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),
        ADD_METHOD_IN_PARAMETER(
                "ADD_METHOD_IN_PARAMETER",
                TestCategory.BFLA,
                "BFLA by HTTP verb tunneling by adding method param",
                "Attacker can access resources of any user by replacing method of the endpoint (eg: changemethod from get to post). This way attacker can get access to unauthorized endpoints.",
                "The endpoint appears to be vulnerable to broken function level authorization attack. The original request was replayed with the addition of request parameter " +
                        "(for example: {method = GET} was added to change it to GET method) in query params." +
                        " The application responded with 2XX success codes and the response body match was less than <b>{{percentageMatch}}%</b>.<br><br>" +
                        "<b>Background:</b> Some web frameworks provide a way to override the actual HTTP method in the request by emulating the missing HTTP verbs passing some custom parameter in query params. " +
                        "The main purpose of this is to circumvent some middleware (e.g. proxy, firewall) limitation where methods allowed usually do not encompass verbs such as PUT or DELETE.",
                "An attacker can perform sensitive actions (e.g., creation, modification, or erasure) that they should not have access to by simply overriding the HTTP method by adding parameter method in query params.",
                new String[]{
                        "https://owasp.org/www-project-web-security-testing-guide/v42/4-Web_Application_Security_Testing/02-Configuration_and_Deployment_Management_Testing/06-Test_HTTP_Methods",
                        "https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa5-broken-function-level-authorization.md",
                        "https://cwe.mitre.org/data/definitions/285.html",
                        "https://fanoframework.github.io/security/http-verb-tunnelling/#:~:text=What%20is%20it%3F,allows%20GET%20and%20POST%20request.&text=Alternatively%2C%20you%20can%20also%20use,body%20parameter%20_method%20like%20so."
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        ADD_METHOD_OVERRIDE_HEADERS(
                "ADD_METHOD_OVERRIDE_HEADERS",
                TestCategory.BFLA,
                "BFLA by HTTP method overriding ",
                "Attacker can access resources of any user by replacing method of the endpoint (eg: changemethod from get to post). This way attacker can get access to unauthorized endpoints.",
                "The endpoint appears to be vulnerable to broken function level authorization attack.The original request was replayed with addition of alternative headers" +
                        " for HTTP method overriding namely X-HTTP-Method, X-HTTP-Method-Override, X-Method-Override. The application responded with 2XX success codes and less than " +
                        "<b>{{percentageMatch}}%</b> of the response body matched with original response body.<br><br>" +

                        "<b>Background:</b> Some web frameworks provide a way to override the actual HTTP method in the request by emulating the missing HTTP verbs passing some custom header in the requests. " +
                        "The main purpose of this is to circumvent some middleware (e.g. proxy, firewall) limitation where methods allowed usually do not encompass verbs such as PUT or DELETE. The following alternative" +
                        " headers could be used to do such verb tunneling: X-HTTP-Method, X-HTTP-Method-Override, X-Method-Override",
                "An attacker can perform sensitive actions (e.g., creation, modification, or erasure) that they should not have access to by simply overriding the HTTP method.",
                new String[]{
                        "https://owasp.org/www-project-web-security-testing-guide/v42/4-Web_Application_Security_Testing/02-Configuration_and_Deployment_Management_Testing/06-Test_HTTP_Methods",
                        "https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa5-broken-function-level-authorization.md",
                        "https://cwe.mitre.org/data/definitions/285.html"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        CHANGE_METHOD(
                "CHANGE_METHOD",
                TestCategory.BFLA,
                "BFLA by changing HTTP method",
                "Attacker can access resources of any user by replacing method of the endpoint (eg: changemethod from get to post). This way attacker can get access to unauthorized endpoints.",
                "The endpoint appears to be vulnerable to broken function level authorization attack. The original request was modified by changing the HTTP methods and sent to application server. The server responded with 2XX success codes.",
                "An attacker can perform sensitive actions (e.g., creation, modification, or erasure) that they should not have access to by simply changing the HTTP method.",
                new String[]{
                        "https://owasp.org/www-project-web-security-testing-guide/v42/4-Web_Application_Security_Testing/02-Configuration_and_Deployment_Management_Testing/06-Test_HTTP_Methods",
                        "https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa5-broken-function-level-authorization.md",
                        "https://cwe.mitre.org/data/definitions/285.html"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        REMOVE_TOKENS(
                "REMOVE_TOKENS",
                TestCategory.NO_AUTH,
                "Broken Authentication by removing auth token",
                "API doesn't validate the authenticity of token. Attacker can remove the auth token and access the endpoint.",
                "The endpoint appears to be vulnerable to broken authentication attack. The original request was replayed by removing victim's <b>auth</b> token. The server responded with 2XX success codes.<br><br>" +
                        "<b>Background:</b> Authentication is the process of attempting to verify the digital identity of the sender of a communication. Testing the authentication schema means understanding how the authentication process works and using that information to " +
                        "circumvent the authentication mechanism. While most applications require authentication to gain access to private information or to execute tasks, not every authentication method is able to provide adequate security. Negligence, ignorance, or simple " +
                        "understatement of security threats often result in authentication schemes that can be bypassed by simply skipping the log in page and directly calling an internal page that is supposed to be accessed only after authentication has been performed.",
                "Broken User authentication is a serious vulnerability. Attackers can gain control to other users’ accounts in the system, read their personal data, and perform sensitive actions on their behalf, like money transactions and sending personal messages.",
                new String[]{
                        "https://owasp.org/www-project-web-security-testing-guide/v42/4-Web_Application_Security_Testing/",
                        "https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa2-broken-user-authentication.md",
                        "https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html",
                        "https://cwe.mitre.org/data/definitions/798.html"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        PARAMETER_POLLUTION(
                "PARAMETER_POLLUTION",
                TestCategory.BOLA,
                "BOLA by HTTP Parameter Pollution",
                "Attacker can access resources of any user by introducing multiple parameters with same name.",
                "The endpoint appears to be vulnerable to broken object level authorization attack. The original request was replayed by adding private resources in query params (for eg: <b>user_id=1234&account_id=436783).</b>. " +
                        " The server responded with 2XX success codes and less than <b>{{percentageMatch}}%</b> of the response body matched with original response body. <br><br>" +
                        "<b>Background:</b> Object level authorization is an access control mechanism that is usually implemented at the code level to validate that one user can only access objects that they should have access to.",
                "Unauthorized access can result in data disclosure to unauthorized parties, data loss, or data manipulation. Unauthorized access to objects can also lead to full account takeover.",
                new String[]{
                        "https://owasp.org/www-project-web-security-testing-guide/latest/4-Web_Application_Security_Testing/07-Input_Validation_Testing/04-Testing_for_HTTP_Parameter_Pollution",
                        "https://www.madlab.it/slides/BHEU2011/whitepaper-bhEU2011.pdf",
                        "https://www.akto.io/blog/what-is-broken-object-level-authorization-bola",
                        "https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa1-broken-object-level-authorization.md",
                        "https://cwe.mitre.org/data/definitions/284.html"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        REPLACE_AUTH_TOKEN_OLD_VERSION(
                "REPLACE_AUTH_TOKEN_OLD_VERSION",
                TestCategory.BOLA,
                "BOLA in old api versions",
                "Attacker can access resources of any user by changing the auth token in request and using older version of an API",
                "Unpatched old api versions were found for this endpoint. The original request was replayed by changing the version of the endpoint " +
                        "(For example: the request url was changed from <b>www.example.com/dfsh/v5/fd</b> to <b>www.example.com/dfsh/v1/fd</b>). The server responded with 2XX success codes.<br><br>" +
                        "The old API versions found appears to be vulnerable to broken object level authorization attack. The original " +
                        "request was replayed with attacker's auth token. The server responded with 2XX success codes and greater than <b>{{percentageMatch}}%</b> of the response body matched with original" +
                        " response body. Also, the endpoint had atleast one private resources in request payload.<br><br>" +
                        "<b>Background:</b> Old API versions are usually unpatched and are vulnerable to attacks such as BOLA. Object level authorization is an access control mechanism that is usually" +
                        " implemented at the code level to validate that one user can only access objects that they should have access to.",
                "Unauthorized access can result in data disclosure to unauthorized parties, data loss, or data manipulation. Unauthorized access to objects can also lead to full account takeover.",
                new String[]{
                        "https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa9-improper-assets-management.md",
                        "https://www.akto.io/blog/bola-exploitation-using-unauthorized-uuid-on-api-endpoint",
                        "https://www.akto.io/blog/what-is-broken-object-level-authorization-bola",
                        "https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa1-broken-object-level-authorization.md",
                        "https://cwe.mitre.org/data/definitions/284.html",
                        "https://cwe.mitre.org/data/definitions/285.html",
                        "https://cwe.mitre.org/data/definitions/639.html"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        JWT_NONE_ALGO(
                "JWT_NONE_ALGO",
                TestCategory.NO_AUTH,
                "JWT None Algorithm",
                "Since NONE Algorithm JWT is accepted by the server the attacker can tamper with the payload of JWT and access protected resources.",
                "The endpoint appears to be vulnerable to broken authentication attack.The original request was replayed by changing algorithm of JWT token " +
                        " to NONE in request headers. The server responded with 2XX success codes. This indicates that this endpoint can be accessed without JWT signature which means a malicious user can get unauthorized access to this endpoint.<br><br>" +
                        "<b>Background:</b> All JSON Web Tokens should contain the \"alg\" header parameter, which specifies the algorithm that the server should use to verify the signature of the token. In addition to cryptographically strong algorithms, " +
                        "the JWT specification also defines the \"none\" algorithm, which can be used with \"unsecured\" (unsigned) JWTs. When this algorithm is supported on the server, it may accept tokens that have no signature at all.<br><br>" +
                        "As the JWT header can be tampered with client-side, a malicious user could change the \"alg\" header to \"none\", then remove the signature and check whether the server still accepts the token.",
                "If JWT none algorithm works, attacker can do a full account takeover." +
                        "They can also exploit this vulnerability by supplying an arbitrary claim in the JWT payload to escalate their privileges or impersonate other users. For example, if the token contains a \"username\": \"joe\" claim, they could change this to \"username\": \"admin\".",
                new String[]{
                        "https://redhuntlabs.com/a-practical-guide-to-attack-jwt-json-web-token",
                        "https://portswigger.net/kb/issues/00200901_jwt-none-algorithm-supported"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        BFLA(
                "BFLA",
                TestCategory.BFLA,
                "BFLA",
                "Less privileged attacker can access admin resources",
                "",
                "",
                new String[]{
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        JWT_INVALID_SIGNATURE(
                "JWT_INVALID_SIGNATURE",
                TestCategory.NO_AUTH,
                "JWT Failed to verify Signature",
                "Since server is not validating the JWT signature the attacker can tamper with the payload of JWT and access protected resources",
                "The endpoint appears to be vulnerable to broken authentication attack.The original request was replayed by changing the valid signature" +
                        " of JWT to invalid signature. The server responded with 2XX success codes. This indicates that this endpoint can be " +
                        "accessed with an invalid JWT signature as the developer has failed to properly verify the signature with every request.<br><br>" +
                        "<b>Background:</b> The JSON Web Token specification provides several ways for developers to digitally sign payload claims. This ensures data integrity and robust user authentication. However, some servers fail to properly verify the signature, which can result in them accepting tokens with invalid signatures.",
                "Using this vulnerability an attacker can do a full account takeover. <br><br>" +
                        "They can also exploit this vulnerability by supplying an arbitrary claim in the JWT" +
                        " payload to escalate their privileges or impersonate other users. For example, if the token" +
                        " contains a \"username\": \"joe\" claim, they could change this to \"username\": \"admin\".",
                new String[]{
                        "https://redhuntlabs.com/a-practical-guide-to-attack-jwt-json-web-token",
                        "https://portswigger.net/kb/issues/00200900_jwt-signature-not-verified#:~:text=Description%3A%20JWT%20signature%20not%20verified&text=However%2C%20some%20servers%20fail%20to,privileges%20or%20impersonate%20other%20users."
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        ADD_JKU_TO_JWT(
                "ADD_JKU_TO_JWT",
                TestCategory.NO_AUTH,
                "JWT authentication bypass via jku header injection",
                "Since Host server is using the JKU field of the JWT without validating, attacker can tamper with the payload of JWT and access protected resources.",
                "The endpoint appears to be vulnerable to broken authentication attack.The original request was replayed by adding the JKU parameter value to the header of" +
                        " JWT and signing with Akto's key. The server responded with 2XX success codes. This indicates that this endpoint can be accessed with a tampered JWT.<br><br>" +
                        "<b>Background:</b> The JSON Web Signature specification defines the optional \"jku\" header, which contains a URL pointing to a set of keys used by the server to digitally sign " +
                        "the JWT. This parameter is particularly useful for servers that are configured to use multiple different keys because it can help to determine which key to use when verifying the" +
                        " signature. If the target application implicitly trusts this header, it may verify the signature using an arbitrary public key obtained from the provided URL, essentially relying " +
                        "on data that can be tampered with client-side. A malicious user could insert or modify a \"jku\" header so that it points to an external server containing a JSON Web Key Set that " +
                        "they've generated themselves. They could then re-sign the token using the matching private key and check whether the server still accepts it.",
                "Using this vulnerability an attacker can do a full account takeover. <br><br>" +
                        "They can also exploit this vulnerability by supplying an arbitrary claim in the JWT payload to escalate their privileges or impersonate other users. For example, " +
                        "if the token contains a \"username\": \"joe\" claim, they could change this to \"username\": \"admin\".",
                new String[]{
                        "https://redhuntlabs.com/a-practical-guide-to-attack-jwt-json-web-token",
                        "https://portswigger.net/web-security/jwt/lab-jwt-authentication-bypass-via-jku-header-injection"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),
        OPEN_REDIRECT(
                "OPEN_REDIRECT",
                TestCategory.SM,
                "Open redirect",
                "An attacker can construct a URL within the application that causes a redirection to an arbitrary external domain.",
                "Open redirection vulnerabilities arise when an application incorporates user-controllable data into the target of a redirection in an unsafe way." +
                        "The ability to use an authentic application URL, targeting the correct domain and with a valid SSL certificate (if SSL is used), lends credibility to the phishing attack because many users, even if they verify these features, will not notice the subsequent redirection to a different domain",
                "This behavior can be leveraged to facilitate phishing attacks against users of the application.",
                new String[]{
                        "https://portswigger.net/kb/issues/00500100_open-redirection-reflected"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        CUSTOM_IAM(
                "CUSTOM_IAM",
                TestCategory.IAM,
                "Assets found on the page",
                "Fuzzing",
                        "Fuzzing",
                        "Fuzzing",
                new String[]{
                        "fuzzing"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        }),

        PAGINATION_MISCONFIGURATION(
                "PAGINATION_MISCONFIGURATION",
                TestCategory.RL,
                "Possible DOS due to pagination misconfiguration",
                "Possible DOS due to pagination misconfiguration",
                "Possible DOS due to pagination misconfiguration",
                "Possible DOS due to pagination misconfiguration",
                new String[]{
                        "some link"
                }, new IssueTags[]{
                IssueTags.BL,
                IssueTags.OWASPTOP10,
                IssueTags.HACKERONETOP10,
        });


        private final String name;
        private final TestCategory superCategory;
        private final String issueDescription;
        private final String issueDetails;
        private final String issueImpact;
        private final String testName;
        private final String[] references;
        private final IssueTags[] issueTags;
        private static final TestSubCategory[] valuesArray = values();

        TestSubCategory(String name, TestCategory superCategory,String testName, String issueDescription, String issueDetails, String issueImpact, String[] references, IssueTags[] issueTags) {
            this.name = name;
            this.superCategory = superCategory;
            this.testName = testName;
            this.issueDescription = issueDescription;
            this.issueDetails = issueDetails;
            this.issueImpact = issueImpact;
            this.references = references;
            this.issueTags = issueTags;
        }

        public static TestSubCategory[] getValuesArray() {
            return valuesArray;
        }

        public static TestSubCategory getTestCategory(String category) {
            if (category.startsWith("http")) return CUSTOM_IAM;
            for (TestSubCategory testSubCategory : valuesArray) {
                if (testSubCategory.name.equalsIgnoreCase(category)) {
                    return testSubCategory;
                }
            }
            return null;
        }

        public String getName() {
            return name;
        }

        public TestCategory getSuperCategory() {
            return superCategory;
        }

        public String getIssueDescription() {
            return issueDescription;
        }

        public String getIssueDetails() {
            return issueDetails;
        }

        public String getIssueImpact() {
            return issueImpact;
        }

        public String getTestName() {
            return testName;
        }

        public String[] getReferences() {
            return references;
        }

        public IssueTags[] getIssueTags() {
            return issueTags;
        }
    }

    public enum Severity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFO
    }

    public enum TestRunIssueStatus {
        OPEN,
        IGNORED,
        FIXED
    }


    /* ********************************************************************** */
}

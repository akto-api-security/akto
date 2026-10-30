package com.akto.gpt.handlers.gpt_prompts;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * The proof rules a finding is judged by, chosen in code from the test template's category, so the
 * model gets only the rules for this kind of test instead of deciding the kind itself. Adapted from
 * the smart testing validator; here the default is TRUE_POSITIVE, since a finding is only dismissed
 * when its rules clearly say it is not real.
 */
final class FalsePositiveProofRules {

    static final String INJECTION = "injection";
    static final String SSRF = "ssrf";
    static final String AUTH_BOLA = "auth_bola";
    static final String JWT = "jwt";
    static final String BFLA = "bfla";
    static final String MASS_ASSIGNMENT = "mass_assignment";
    static final String INPUT_VALIDATION = "input_validation";
    static final String XSS = "xss";
    static final String CORS = "cors";
    static final String HEADERS = "headers";
    static final String ACCEPTANCE = "acceptance";
    static final String EXPOSURE = "exposure";
    static final String GENERAL = "general";

    private static final Map<String, String> CATEGORY_CLASSES = new HashMap<>();
    static {
        CATEGORY_CLASSES.put("BOLA", AUTH_BOLA);
        CATEGORY_CLASSES.put("BFLA", BFLA);
        CATEGORY_CLASSES.put("MA", MASS_ASSIGNMENT);
        CATEGORY_CLASSES.put("INJ", INJECTION);
        CATEGORY_CLASSES.put("INJECT", INJECTION);
        CATEGORY_CLASSES.put("COMMAND_INJECTION", INJECTION);
        CATEGORY_CLASSES.put("SSTI", INJECTION);
        CATEGORY_CLASSES.put("LFI", INJECTION);
        CATEGORY_CLASSES.put("CRLF", INJECTION);
        CATEGORY_CLASSES.put("SSRF", SSRF);
        CATEGORY_CLASSES.put("XSS", XSS);
        CATEGORY_CLASSES.put("CORS", CORS);
        CATEGORY_CLASSES.put("INPUT", INPUT_VALIDATION);
        CATEGORY_CLASSES.put("MHH", HEADERS);
        CATEGORY_CLASSES.put("SVD", HEADERS);
        CATEGORY_CLASSES.put("IIM", ACCEPTANCE);
        CATEGORY_CLASSES.put("UHM", ACCEPTANCE);
        CATEGORY_CLASSES.put("VEM", EXPOSURE);
    }

    private static final Pattern JWT_TEST = Pattern.compile("jwt|jwk|jku|\\bkid\\b|none.?alg|alg.?none|signature",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern UNCHANGED_REQUEST = Pattern.compile("-\\s*req:\\s*\\[\\s*\\]");
    private static final Pattern METHOD_CHANGE = Pattern.compile("modify_method");

    private FalsePositiveProofRules() {}

    /** The proof class of the test whose template YAML is given; GENERAL if it cannot be told. */
    static String detectClass(String template) {
        Map<?, ?> data = parse(template);
        Map<?, ?> info = asMap(data.get("info"));
        String category = String.valueOf(asMap(info.get("category")).get("name")).toUpperCase();
        String headline = data.get("id") + " " + info.get("subCategory") + " " + info.get("name");

        if ("NO_AUTH".equals(category)) {
            return JWT_TEST.matcher(headline).find() ? JWT : AUTH_BOLA;
        }
        if ("SM".equals(category)) {
            if (UNCHANGED_REQUEST.matcher(template).find()) return HEADERS;
            return METHOD_CHANGE.matcher(template).find() ? ACCEPTANCE : EXPOSURE;
        }
        String cls = CATEGORY_CLASSES.get(category);
        return cls != null ? cls : GENERAL;
    }

    static String proofRules(String cls) {
        switch (cls) {
            case INJECTION:
                return "Proof rules - injection (SQL/NoSQL/command/template injection, file inclusion, CRLF):\n"
                    + "TRUE_POSITIVE when the attack response shows the payload executed:\n"
                    + "- SQL/NoSQL: a database or driver error, or query output (a real DB name or version as a value).\n"
                    + "- Command injection: shell output (uid=, gid=, whoami, command stdout).\n"
                    + "- File inclusion / path traversal: the file CONTENTS (root:x:0:0: passwd lines, win.ini sections).\n"
                    + "- Template injection: the evaluated result (e.g. 49 for {{7*7}}).\n"
                    + "- CRLF: the injected header appearing as a real response header.\n"
                    + "FALSE_POSITIVE when:\n"
                    + "- every validate marker the scanner matched is also in the original response (see the facts), or\n"
                    + "  the matched text is ordinary data, e.g. a URL parameter like ?lang=en or a word in a label,\n"
                    + "  rather than file contents, command output or an error the payload caused;\n"
                    + "- the only difference from the original is in business values (a balance, amount, name or id):\n"
                    + "  Akto's test credentials often belong to another user, so such changes are never execution;\n"
                    + "- the payload only appears echoed back (e.g. ../etc/passwd as a string in a field, an error\n"
                    + "  message, the URL); echo is not execution;\n"
                    + "- the response is the API's normal business response with no execution output, e.g. the same\n"
                    + "  fields as the baseline with different values caused by something else (another session, data\n"
                    + "  that changed), or the facts say the response differs only by echo;\n"
                    + "- the request was rejected: 401/403, or another 4xx without a database error in the body.\n";
            case SSRF:
                return "Proof rules - SSRF:\n"
                    + "TRUE_POSITIVE when the response contains content fetched from the injected target: cloud\n"
                    + "metadata (ami-id, instance-id, AccessKeyId), internal service output, or remote HTML/JSON that\n"
                    + "is not in the baseline.\n"
                    + "FALSE_POSITIVE when the injected URL only appears echoed (in a field, link, error or Location),\n"
                    + "the body is the normal business response or an empty result, the request was rejected with a\n"
                    + "4xx validation error, a fetch error is the same for any host, or every validate marker the\n"
                    + "scanner matched is also in the original response.\n";
            case AUTH_BOLA:
                return "Proof rules - broken authentication / BOLA / IDOR (credentials removed or replaced, or the\n"
                    + "object id changed):\n"
                    + "TRUE_POSITIVE when the server processed the request without the right authorization and\n"
                    + "returned protected data: with credentials removed or replaced by another user's, a 2xx with the\n"
                    + "same protected data as the baseline IS the proof (percentage_match in validate checks exactly\n"
                    + "that); with a changed object id, another user's records are returned.\n"
                    + "FALSE_POSITIVE when:\n"
                    + "- the credentials and object id are identical to the baseline (nothing was tested);\n"
                    + "- the server rejected it: 401/403, access denied or an auth error in the body, or a header\n"
                    + "  reporting an authentication failure (e.g. www-authenticate with error=invalid-token);\n"
                    + "- an empty list or null is returned for another user's object;\n"
                    + "- the data is public by design (catalog, public profile, static content) or the same for any\n"
                    + "  caller, or only the caller's own data;\n"
                    + "- with a changed object id, only server-generated fields (ids, timestamps) differ.\n";
            case JWT:
                return "Proof rules - JWT / token validation (signature bypass, alg none, jku/jwk/kid, edited claims):\n"
                    + "TRUE_POSITIVE when the token was really tampered (it differs from the baseline token, see the\n"
                    + "request facts) AND the server accepted it: a 2xx with the protected data the baseline returns.\n"
                    + "FALSE_POSITIVE when:\n"
                    + "- the token is identical to the baseline token: the tampering was not applied (e.g. an encrypted\n"
                    + "  JWE cannot be modified), so a valid token was replayed and success is expected;\n"
                    + "- 401/403, or the body or a header rejects the token (invalid token, token expired, jwt\n"
                    + "  malformed, invalid signature, www-authenticate with error=invalid-token);\n"
                    + "- the endpoint is public (login, signup, public catalog), or the body is error-only.\n";
            case BFLA:
                return "Proof rules - BFLA / privilege escalation:\n"
                    + "TRUE_POSITIVE when the request used lower-privilege credentials (they differ from the baseline)\n"
                    + "and the privileged function still ran: admin-only data returned or the admin action took effect.\n"
                    + "FALSE_POSITIVE when the credentials are identical to the baseline (only a role header was added),\n"
                    + "the function is public or self-service, or the server rejected it (401/403, permission error).\n";
            case MASS_ASSIGNMENT:
                return "Proof rules - mass assignment:\n"
                    + "TRUE_POSITIVE when the server accepted the extra or privileged fields the attack added: a 2xx\n"
                    + "where the response keeps the normal result (validate's percentage_match) or shows the submitted\n"
                    + "value replacing a server-assigned one (role, isAdmin, owner, price).\n"
                    + "FALSE_POSITIVE when no field was actually added (request facts), the server rejected it (4xx,\n"
                    + "validation error), or the response shows the field was ignored or reset to the server value.\n";
            case INPUT_VALIDATION:
                return "Proof rules - input validation / business logic:\n"
                    + "TRUE_POSITIVE when the invalid value was honored with a business effect the server computed:\n"
                    + "totals, price, discount, balance, quantity, status, role or limit differ from the baseline because\n"
                    + "of the input, or the server accepted a value its rules should refuse as the template intends.\n"
                    + "FALSE_POSITIVE when the only change is the sent value echoed back, the server returned a 4xx\n"
                    + "validation error, or the business result is the same as the baseline (only ids or timestamps\n"
                    + "differ).\n";
            case XSS:
                return "Proof rules - XSS:\n"
                    + "TRUE_POSITIVE when the payload appears unencoded in an HTML response where it would execute.\n"
                    + "FALSE_POSITIVE when it only appears in a JSON field, is HTML-encoded, or only in headers.\n";
            case CORS:
                return "Proof rules - CORS (proof is ONLY in the response headers; ignore the body):\n"
                    + "TRUE_POSITIVE when the response headers meet the template's validate condition for the untrusted\n"
                    + "Origin the attack sent, e.g. Access-Control-Allow-Origin reflecting it (or *) together with\n"
                    + "Access-Control-Allow-Credentials: true when validate requires it. A normal 200 body is expected.\n"
                    + "FALSE_POSITIVE when Access-Control-Allow-Origin is missing, is a fixed trusted domain, or does not\n"
                    + "match the sent Origin, or credentials are missing when validate requires them.\n";
            case HEADERS:
                return "Proof rules - HTTP header checks (the request is sent unchanged, only headers are checked):\n"
                    + "Nothing is injected, so a request and response identical to the baseline are expected and are\n"
                    + "never a reason for FALSE_POSITIVE.\n"
                    + "TRUE_POSITIVE when the request or response shown really meets the template's validate condition\n"
                    + "on headers, e.g. the security headers it lists really are missing, or the version header really\n"
                    + "is present (compare header names case-insensitively). In validate, `not_contains` / `not_exists`\n"
                    + "on headers means the test flags the header being ABSENT: a missing header is the finding.\n"
                    + "FALSE_POSITIVE only when the header is actually present (e.g. under different casing), or the\n"
                    + "response is an error, not-found or login page where validate needs a normal response.\n";
            case ACCEPTANCE:
                return "Proof rules - acceptance (old API version, other HTTP method, or similar request change):\n"
                    + "Being accepted is the finding: TRUE_POSITIVE when the changed request (e.g. /v2/ rewritten to /v1/,\n"
                    + "GET changed to HEAD/PUT/DELETE) got a 2xx with real content, whatever data comes back. A response\n"
                    + "like the baseline is expected and is not a reason for FALSE_POSITIVE.\n"
                    + "FALSE_POSITIVE only when the change was not applied (the request facts show the same method and\n"
                    + "path as the baseline), the server rejected it (4xx/405), or it served an error, not-found, login\n"
                    + "or generic default page (e.g. an SPA index.html returned for any path).\n";
            case EXPOSURE:
                return "Proof rules - information exposure (verbose errors, exposed files or configuration):\n"
                    + "TRUE_POSITIVE when the response contains what the template's validate looks for: a stack trace,\n"
                    + "internal error details, a version string, or the configuration/file content itself.\n"
                    + "FALSE_POSITIVE when the response is a generic error without internals, an HTML fallback, SPA\n"
                    + "index, not-found or login page, the matched text only echoes the request, or every validate\n"
                    + "marker the scanner matched is also in the original response or is ordinary data (a URL\n"
                    + "parameter, a word in a label) rather than the exposed content.\n";
            default:
                return "Proof rules - infer from the template:\n"
                    + "Use execute (what was changed) and validate (what the scanner checked). TRUE_POSITIVE when the\n"
                    + "response really shows what validate and the test's description look for. FALSE_POSITIVE only\n"
                    + "when the change was not applied, the server clearly rejected the request (401/403, auth or\n"
                    + "validation error), or the matched content is only an echo of what was sent.\n";
        }
    }

    private static Map<?, ?> parse(String template) {
        if (template == null || template.trim().isEmpty()) return Collections.emptyMap();
        try {
            Object data = new Yaml().load(template);
            return data instanceof Map ? (Map<?, ?>) data : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }
}

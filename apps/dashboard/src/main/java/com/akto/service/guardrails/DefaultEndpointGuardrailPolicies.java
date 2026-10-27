package com.akto.service.guardrails;

import com.akto.dto.GuardrailPolicies;
import com.akto.dto.GuardrailPolicies.AnomalyDetection;
import com.akto.dto.GuardrailPolicies.AnonymizeDetection;
import com.akto.dto.GuardrailPolicies.BanCodeDetection;
import com.akto.dto.GuardrailPolicies.BasePromptRule;
import com.akto.dto.GuardrailPolicies.DeniedTopic;
import com.akto.dto.GuardrailPolicies.GibberishDetection;
import com.akto.dto.GuardrailPolicies.LLMRule;
import com.akto.dto.GuardrailPolicies.PiiType;
import com.akto.dto.GuardrailPolicies.SecretsDetection;
import com.akto.dto.GuardrailPolicies.SentimentDetection;
import com.akto.dto.GuardrailPolicies.TokenLimitDetection;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The default guardrail policies seeded into every account that has an endpoint collection.
 * The policies carry no fixed _id; DefaultEndpointGuardrailsService avoids duplicates by their createdBy and names.
 */
public final class DefaultEndpointGuardrailPolicies {

    static final String CREATED_BY = "default@akto.io";

    private static final double DETECTION_CONFIDENCE = 0.7;
    private static final double RULE_CONFIDENCE = 0.5;
    private static final int TOKEN_LIMIT_THRESHOLD = 4096;

    private static final List<String> CUSTOMER_PII_TYPES = Collections.unmodifiableList(Arrays.asList(
            "vin", "ssn", "ip_address", "credit_card", "email", "phone_number", "jwt", "uuid", "address",
            "aws_rds_url", "heroku", "aws_dynamodb_url", "username", "aws_docdb_url", "database", "aws_sqs_url",
            "aws_s3_bucket_url", "password", "token", "aws_arn_connect_instance", "aws_access_key_id", "secret"));

    private DefaultEndpointGuardrailPolicies() {}

    public static List<GuardrailPolicies> build(int now) {
        return Arrays.asList(customerPii(now), sourceCode(now), credentials(now), financialAdvice(now));
    }

    private static GuardrailPolicies customerPii(int now) {
        GuardrailPolicies p = base("Default-Customer PII",
                "Blocked response with default customer pii policy", now);
        p.setApplyOnRequest(true);
        p.setApplyOnResponse(true);
        p.setPiiTypes(piiTypes(CUSTOMER_PII_TYPES));
        return p;
    }

    private static GuardrailPolicies sourceCode(int now) {
        GuardrailPolicies p = base("Default-Source code detection",
                "Blocked by Akto default policy", now);
        p.setApplyOnRequest(true);
        p.setApplyOnResponse(false);
        p.setBanCodeDetection(new BanCodeDetection(true, DETECTION_CONFIDENCE));
        Map<String, Object> contentFiltering = emptyContentFiltering();
        contentFiltering.put("code", Collections.singletonMap("level", "HIGH"));
        p.setContentFiltering(contentFiltering);
        return p;
    }

    private static GuardrailPolicies credentials(int now) {
        GuardrailPolicies p = base("Default-Credentials Alert",
                "Blocked by default policy by Akto", now);
        p.setApplyOnRequest(true);
        p.setApplyOnResponse(false);
        p.setPiiTypes(piiTypes(Arrays.asList("token", "jwt")));
        p.setSecretsDetection(new SecretsDetection(true, DETECTION_CONFIDENCE));
        return p;
    }

    private static GuardrailPolicies financialAdvice(int now) {
        GuardrailPolicies p = base( "Default-Financial Advice",
                "Blocked as financial information was detected.", now);
        p.setApplyOnRequest(true);
        p.setApplyOnResponse(true);

        DeniedTopic topic = new DeniedTopic();
        topic.setTopic("Financial and Investment Advice");
        topic.setDescription("Any request for personalized financial or investment advice, including when phrased "
                + "indirectly or without naming the topic - building or allocating a portfolio, where or how much to "
                + "invest, which assets, stocks, funds, crypto, gold, or real estate to buy, sell, or hold, retirement "
                + "or savings planning, moving money between asset classes, or maximizing returns. This includes "
                + "naming specific companies or tickers (e.g. Apple, Nvidia, Tesla, Bitcoin) and asking whether to "
                + "buy, sell, or hold them.");
        topic.setSamplePhrases(Arrays.asList(
                "which specific stocks should I buy right now",
                "tell me exactly how to invest my savings",
                "what crypto will make me the most money",
                "should I put my savings into gold right now",
                "build me a portfolio that will maximize my returns over the next 5 years",
                "give me the top 10 stocks that will outperform the market this year",
                "I want to retire early, tell me exactly where to invest my monthly income",
                "how should I allocate my monthly savings across investments",
                "I'm thinking of putting my emergency fund into equity because interest rates are low, is that a good idea",
                "I already own Apple, Nvidia, Tesla, and Reliance shares, should I sell them and buy something else",
                "should I move my money out of savings and into the stock market",
                "is now a good time to buy a house as an investment"));
        p.setDeniedTopics(new ArrayList<>(Collections.singletonList(topic)));

        LLMRule llmRule = new LLMRule(true, FINANCIAL_LLM_PROMPT, RULE_CONFIDENCE);
        Map<String, List<String>> compliance = new HashMap<>();
        compliance.put("ISO 27001", new ArrayList<>());
        llmRule.setCompliance(compliance);
        p.setLlmRule(llmRule);
        return p;
    }

    /** Fields every default policy shares; each policy overrides only what makes it different. */
    private static GuardrailPolicies base(String name, String blockedMessage, int now) {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setName(name);
        p.setBlockedMessage(blockedMessage);
        p.setSeverity("MEDIUM");
        p.setBehaviour("alert");
        p.setActive(true);
        p.setContextSource(CONTEXT_SOURCE.ENDPOINT);
        p.setApplyToAllServers(true);
        p.setCreatedBy(CREATED_BY);
        p.setCreatedTimestamp(now);
        p.setUpdatedTimestamp(now);

        p.setAnomalyDetection(new AnomalyDetection(false, 0, 0));
        p.setAnonymizeDetection(new AnonymizeDetection(false, DETECTION_CONFIDENCE));
        p.setBanCodeDetection(new BanCodeDetection(false, DETECTION_CONFIDENCE));
        p.setBasePromptRule(new BasePromptRule(false, RULE_CONFIDENCE));
        p.setGibberishDetection(new GibberishDetection(false, DETECTION_CONFIDENCE));
        p.setSecretsDetection(new SecretsDetection(false, DETECTION_CONFIDENCE));
        p.setSentimentDetection(new SentimentDetection(false, DETECTION_CONFIDENCE));
        p.setTokenLimitDetection(new TokenLimitDetection(false, TOKEN_LIMIT_THRESHOLD));
        p.setLlmRule(new LLMRule(false, "", RULE_CONFIDENCE));
        p.setContentFiltering(emptyContentFiltering());

        p.setBlockPersonalAccounts(false);
        p.setNegatedAgentServers(false);
        p.setNegatedLlmServers(false);
        p.setNegatedMcpServers(false);
        p.setNegatedTargetDeviceIds(false);
        p.setNegatedTargetUserNames(false);
        p.setNegatedTargetTags(new HashMap<>());
        p.setTargetTags(new HashMap<>());

        p.setBlockedHosts(new ArrayList<>());
        p.setDeniedTopics(new ArrayList<>());
        p.setEnterpriseLicenseComplianceCategories(new ArrayList<>());
        p.setIgnorePhrases(new ArrayList<>());
        p.setPiiTypes(new ArrayList<>());
        p.setRedactionRules(new ArrayList<>());
        p.setRegexPatterns(new ArrayList<>());
        p.setRegexPatternsV2(new ArrayList<>());
        p.setSelectedAgentServers(new ArrayList<>());
        p.setSelectedAgentServersV2(new ArrayList<>());
        p.setSelectedLlmServersV2(new ArrayList<>());
        p.setSelectedMcpServers(new ArrayList<>());
        p.setSelectedMcpServersV2(new ArrayList<>());
        p.setTargetDeviceIds(new ArrayList<>());
        p.setUserMetadata(new ArrayList<>());
        return p;
    }

    private static Map<String, Object> emptyContentFiltering() {
        Map<String, Object> contentFiltering = new LinkedHashMap<>();
        contentFiltering.put("harmfulCategories", null);
        contentFiltering.put("code", null);
        contentFiltering.put("promptAttacks", null);
        return contentFiltering;
    }

    private static List<PiiType> piiTypes(List<String> types) {
        List<PiiType> piiTypes = new ArrayList<>();
        for (String type : types) {
            piiTypes.add(new PiiType(type, "block", 1));
        }
        return piiTypes;
    }

    private static final String FINANCIAL_LLM_PROMPT =
            "You are evaluating content for a Financial Information DLP policy in MONITOR MODE.\n"
            + "\n"
            + "Return MONITOR only when the content contains or requests sensitive company, vendor, supplier, accounting, invoice, banking, tax, or financial planning information.\n"
            + "\n"
            + "MONITOR when the content includes or requests:\n"
            + "- Vendor or supplier bank account numbers.\n"
            + "- Routing numbers, SWIFT codes, BIC codes, IBANs, wire instructions, ACH details, remittance instructions, or payment confirmation details.\n"
            + "- Vendor tax IDs, supplier tax IDs, invoice records, unpaid invoices, accounts payable, accounts receivable, payment reconciliation, or payment status.\n"
            + "- Non-public company financial forecasts, earnings guidance, revenue projections, EPS, margin forecasts, financial models, write-down exposure, budgets, settlement amounts, insurance claim payments, or internal financial planning.\n"
            + "- Requests to summarize, rewrite, reconcile, extract, export, model, or analyze the above financial information.\n"
            + "\n"
            + "Do NOT classify under this Financial Information policy when the primary sensitive content is:\n"
            + "- Customer names, addresses, phone numbers, email addresses, loyalty data, order counts, or customer exports.\n"
            + "- Payment card numbers, card masking, CVV, expiration dates, payment tokens, authorization codes, transaction IDs, or POS card activity.\n"
            + "- Employee medical notes, employee HR records, SSNs, DOBs, home addresses, compensation, payroll banking, or leave/accommodation information.\n"
            + "- Passwords, API keys, tokens, cloud keys, secrets, connection strings, or configuration secrets.\n"
            + "- Acquisitions, confidential strategy, proprietary pricing logic, business rules, source code, roadmaps, product strategy, or intellectual property unless the main issue is financial records or vendor/accounting data.\n"
            + "- Internal security incidents, vulnerabilities, logs, control gaps, or investigation details.\n"
            + "\n"
            + "Do NOT classify as Financial Information when the primary context is:\n"
            + "- Acquisition activity, merger activity, M&A, purchase of another company, target company evaluation, due diligence, regulatory approval, integration planning, synergy assumptions, valuation assumptions, deal strategy, target close date, or market-share impact.\n"
            + "- Material non-public company strategy or investor-sensitive information where the main risk is disclosure of confidential business activity, even if dollar amounts, valuation, revenue, margin, EPS, forecasts, or market-share percentages are included.\n"
            + "- Proprietary pricing logic, business rules, demand forecasting logic, internal algorithms, source code, or intellectual property, even if the content includes pricing, margin, discount, revenue, or sell-through terms.\n"
            + "\n"
            + "These should be classified under Confidential Business Information and Intellectual Property, not Financial Information.\n"
            + "\n"
            + "Contextual note:\n"
            + "Distinguish between information that has been officially published and non-public information such as internal forecasts, draft earnings, pending transactions, negotiation terms, unreleased performance results, or future business plans.\n"
            + "Publicly available information should not be treated as confidential solely because it concerns the company. Information should be treated as sensitive when the conversation indicates that it is internal, unreleased, provisional, pending approval, embargoed, or intended for a limited audience.\n"
            + "Evaluate the full conversation, including prior messages, rather than evaluating each prompt in isolation.\n"
            + "Combine related information shared across multiple messages when determining whether sensitive data is present.\n"
            + "Infer sensitivity from the meaning and relationship of the information, even when the user does not explicitly label it as confidential, private, financial, customer, employee, or security-related.\n"
            + "Consider whether several individually low-risk data elements, when combined, identify a person, account, transaction, system, or confidential business activity.\n"
            + "Treat requests to summarize, translate, reformat, mask, analyze, or improve sensitive information as processing of sensitive data, even when the user’s stated intent appears legitimate.\n"
            + "Consider the user’s intended action and destination, including whether the information is being prepared for external sharing, uploaded to a personal AI account, or transferred to an unapproved service.\n"
            + "Detect sensitive information that is split, abbreviated, reformatted, spaced out, or otherwise obfuscated across the conversation.\n"
            + "Do not reduce the classification solely because the user describes the activity as testing, educational, hypothetical, or authorized.\n"
            + "\n"
            + "ALLOW general financial education, public information, generic business writing, or fictional examples that do not include realistic sensitive financial records.\n"
            + "\n"
            + "Decision:\n"
            + "- MONITOR only when the main risk is Financial Information.\n"
            + "- ALLOW when the content belongs primarily to another DLP category or is general, public, sanitized, fictional without realistic sensitive records, or fully de-identified.";
}

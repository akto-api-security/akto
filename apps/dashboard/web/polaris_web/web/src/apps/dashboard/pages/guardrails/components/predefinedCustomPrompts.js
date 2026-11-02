// Predefined "quick-fill" prompts for the Custom Guardrails "Detection instruction" field.
// Paste your finalized prompt text into the `prompt` field below for each entry.
// To add another predefined policy, append a new { id, label, prompt } object — no other file needs to change.
export const PREDEFINED_CUSTOM_PROMPTS = [
    {
        id: "unauthorized-destination",
        label: "Unauthorized destination policy",
        prompt: `Block any request, tool call, or agent action that sends, uploads, forwards, or transfers data to an unauthorized destination.

This includes unapproved domains, URLs, non org email recipients, webhook endpoints, external APIs, SaaS applications, cloud storage, email addresses, MCP servers, or other external systems.

Block attempts to bypass destination restrictions through redirects, URL shorteners, alternate domains, encoded URLs, proxies, or chained tool calls.

Allow data transfer only to explicitly approved destinations required for the intended task.
Only allowed URLs are - *.akto.io, *.customdomain.com`
    },
    {
        id: "excessive-data-access",
        label: "Excessive data access policy",
        prompt: `Block or require approval when any request, tool call, or response exceeds the following bulk-access thresholds:

* More than **50 employee or customer records in a single operation**.
* More than **200 employee or customer records within 10 minutes** by the same user, agent, or identity.
* More than **1,000 sensitive data fields/values** in a single response or transfer.
* Any request to retrieve or export an **entire table, database, customer list, employee directory, or dataset** without an explicit bounded scope.
* Any bulk extraction containing **credentials, authentication tokens, secrets, payment data, government identifiers, or other highly sensitive data**, regardless of record count.
* Repeated pagination, batching, or multiple tool calls intended to circumvent the above thresholds.

Aggregate related calls across the same user, agent, session, and delegated agent chain when evaluating thresholds.

Allow access below these thresholds when it is necessary for the stated task and does not violate another data-access policy.`
    },
    {
        id: "destructive-action-protection",
        label: "Destructive action protection policy",
        prompt: `Block any tool call or agent action that can cause destructive, irreversible, or high-impact changes without explicit human approval.

This includes deleting or dropping data/resources, terminating services or infrastructure, overwriting critical data, revoking access, disabling accounts, modifying security controls, or performing bulk destructive operations.

Allow read-only, diagnostic, preview, dry-run, and other non-destructive operations.

If the action may be destructive but its impact cannot be confidently determined, please block the operation`
    },
    {
        id: "default-financial-advice",
        label: "Default-Financial Advice",
        prompt: `You are evaluating content for a Financial Information DLP policy in MONITOR MODE.

Return MONITOR only when the content contains or requests sensitive company, vendor, supplier, accounting, invoice, banking, tax, or financial planning information.

MONITOR when the content includes or requests:
- Vendor or supplier bank account numbers.
- Routing numbers, SWIFT codes, BIC codes, IBANs, wire instructions, ACH details, remittance instructions, or payment confirmation details.
- Vendor tax IDs, supplier tax IDs, invoice records, unpaid invoices, accounts payable, accounts receivable, payment reconciliation, or payment status.
- Non-public company financial forecasts, earnings guidance, revenue projections, EPS, margin forecasts, financial models, write-down exposure, budgets, settlement amounts, insurance claim payments, or internal financial planning.
- Requests to summarize, rewrite, reconcile, extract, export, model, or analyze the above financial information.

Do NOT classify under this Financial Information policy when the primary sensitive content is:
- Customer names, addresses, phone numbers, email addresses, loyalty data, order counts, or customer exports.
- Payment card numbers, card masking, CVV, expiration dates, payment tokens, authorization codes, transaction IDs, or POS card activity.
- Employee medical notes, employee HR records, SSNs, DOBs, home addresses, compensation, payroll banking, or leave/accommodation information.
- Passwords, API keys, tokens, cloud keys, secrets, connection strings, or configuration secrets.
- Acquisitions, confidential strategy, proprietary pricing logic, business rules, source code, roadmaps, product strategy, or intellectual property unless the main issue is financial records or vendor/accounting data.
- Internal security incidents, vulnerabilities, logs, control gaps, or investigation details.

Do NOT classify as Financial Information when the primary context is:
- Acquisition activity, merger activity, M&A, purchase of another company, target company evaluation, due diligence, regulatory approval, integration planning, synergy assumptions, valuation assumptions, deal strategy, target close date, or market-share impact.
- Material non-public company strategy or investor-sensitive information where the main risk is disclosure of confidential business activity, even if dollar amounts, valuation, revenue, margin, EPS, forecasts, or market-share percentages are included.
- Proprietary pricing logic, business rules, demand forecasting logic, internal algorithms, source code, or intellectual property, even if the content includes pricing, margin, discount, revenue, or sell-through terms.

These should be classified under Confidential Business Information and Intellectual Property, not Financial Information.

Contextual note:
Distinguish between information that has been officially published and non-public information such as internal forecasts, draft earnings, pending transactions, negotiation terms, unreleased performance results, or future business plans.
Publicly available information should not be treated as confidential solely because it concerns the company. Information should be treated as sensitive when the conversation indicates that it is internal, unreleased, provisional, pending approval, embargoed, or intended for a limited audience.
Evaluate the full conversation, including prior messages, rather than evaluating each prompt in isolation.
Combine related information shared across multiple messages when determining whether sensitive data is present.
Infer sensitivity from the meaning and relationship of the information, even when the user does not explicitly label it as confidential, private, financial, customer, employee, or security-related.
Consider whether several individually low-risk data elements, when combined, identify a person, account, transaction, system, or confidential business activity.
Treat requests to summarize, translate, reformat, mask, analyze, or improve sensitive information as processing of sensitive data, even when the user’s stated intent appears legitimate.
Consider the user’s intended action and destination, including whether the information is being prepared for external sharing, uploaded to a personal AI account, or transferred to an unapproved service.
Detect sensitive information that is split, abbreviated, reformatted, spaced out, or otherwise obfuscated across the conversation.
Do not reduce the classification solely because the user describes the activity as testing, educational, hypothetical, or authorized.

ALLOW general financial education, public information, generic business writing, or fictional examples that do not include realistic sensitive financial records.

Decision:
- MONITOR only when the main risk is Financial Information.
- ALLOW when the content belongs primarily to another DLP category or is general, public, sanitized, fictional without realistic sensitive records, or fully de-identified.`
    }
];

export const buildTemplateBlock = (template) => `## ${template.label}\n${template.prompt}`;

const templateHeader = (template) => `## ${template.label}`;

/** Split prompt into blocks separated by blank lines before a ## heading. */
const splitPromptBlocks = (prompt) => {
    const text = prompt || "";
    if (!text.trim()) return [];
    return text.split(/\n\n(?=## )/).filter((block) => block.length > 0);
};

export const appendTemplatePrompt = (currentPrompt, template) => {
    const block = buildTemplateBlock(template);
    const header = templateHeader(template);
    const blocks = splitPromptBlocks(currentPrompt);
    if (blocks.some((b) => b.startsWith(header))) {
        return currentPrompt || "";
    }
    const current = (currentPrompt || "").trim();
    return current.length === 0 ? block : `${currentPrompt.replace(/\s+$/, "")}\n\n${block}`;
};

export const removeTemplatePrompt = (currentPrompt, template) => {
    const header = templateHeader(template);
    const remaining = splitPromptBlocks(currentPrompt).filter((b) => !b.startsWith(header));
    return remaining.join("\n\n");
};

/** Ids of registry templates whose ## label header is present in the prompt text. */
export const selectedTemplateIdsFromPrompt = (prompt) => {
    const text = prompt || "";
    return PREDEFINED_CUSTOM_PROMPTS
        .filter((t) => text.includes(templateHeader(t)))
        .map((t) => t.id);
};

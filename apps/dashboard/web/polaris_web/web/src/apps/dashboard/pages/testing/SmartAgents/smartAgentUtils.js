// Limits match the backend (SmartAgentConfig / SmartAgentProfileAction).
export const LIMITS = {
    name: 80,
    description: 500,
    instructions: 2000,
    skillFiles: 100, // custom skill files per agent, references included (MAX_SKILL_FILES)
    files: 10,
    fileChars: 50000,
    links: 20,
}

// built-in skills (id, name, description). Test categories come from Run test.
export const BUILT_IN_SKILLS = [
    // OWASP API Security Top 10 (2023)
    { id: "API1_2023_BOLA", label: "API1:2023 Broken Object Level Authorization", description: "Learns which objects belong to which identity, then tries each identity's ids as the others." },
    { id: "API1_2023_TENANT_ISOLATION", label: "API1:2023 BOLA - Cross-Tenant Isolation", description: "Uses other organizations', projects' or workspaces' ids in path, body, headers and query; scoped routes must stay in scope." },
    { id: "API2_2023_BROKEN_AUTH", label: "API2:2023 Broken Authentication", description: "Tests authentication strength: weak or missing auth, token and JWT handling, session and password-reset flows." },
    { id: "API3_2023_MASS_ASSIGNMENT", label: "API3:2023 BOPLA - Mass Assignment", description: "Sends fields seen in responses or docs but not in the request (role, owner, status, price) and checks if they take effect." },
    { id: "API3_2023_DATA_EXPOSURE", label: "API3:2023 BOPLA - Excessive Data Exposure", description: "Responses that give more than the caller needs: other users' personal data, secrets, tokens, internal fields." },
    { id: "API4_2023_RESOURCE_CONSUMPTION", label: "API4:2023 Unrestricted Resource Consumption", description: "Tests limits on size, count, rate and cost: large payloads, big page sizes, expensive queries and missing throttling." },
    { id: "API5_2023_BFLA", label: "API5:2023 Broken Function Level Authorization", description: "Targets role and permission fields, admin-only functions, invites, and API key or token creation." },
    { id: "API6_2023_BUSINESS_FLOWS", label: "API6:2023 Unrestricted Access to Sensitive Business Flows", description: "Skips, repeats or reorders steps of a flow (checkout, approval, verification) and reuses one-time values." },
    { id: "API6_2023_BUSINESS_LIMITS", label: "API6:2023 Sensitive Business Flows - Business Limits", description: "Negative or huge quantities, prices, discounts, coupons, quotas and rounding." },
    { id: "API7_2023_SSRF", label: "API7:2023 Server Side Request Forgery", description: "Tests URL and host fields the server fetches: internal-address filter bypasses via alternate encodings and redirects." },
    { id: "API8_2023_MISCONFIGURATION", label: "API8:2023 Security Misconfiguration", description: "Tests CORS, missing security headers, verbose errors leaking internals, unsafe methods and caching." },
    { id: "API9_2023_INVENTORY", label: "API9:2023 Improper Inventory Management", description: "Uses the API docs from Knowledge base links: undocumented behaviour, unlinked routes, role requirements." },
    { id: "API10_2023_UNSAFE_CONSUMPTION", label: "API10:2023 Unsafe Consumption of APIs", description: "Tests blind trust of upstream and third-party data: injection through integrations and unvalidated responses." },
    // OWASP Web Top 10 (2021), applied to APIs
    { id: "WEB_A02_2021_CRYPTO_FAILURES", label: "A02:2021 Cryptographic Failures", description: "Tests how secrets and tokens are protected: weak or none JWT signing, predictable tokens, secrets in responses or URLs." },
    { id: "WEB_A03_2021_INJECTION", label: "A03:2021 Injection", description: "Tests input that reaches an interpreter: SQL/NoSQL, command, LDAP, template (SSTI), header/CRLF and reflected XSS." },
    { id: "WEB_A08_2021_INTEGRITY", label: "A08:2021 Software and Data Integrity Failures", description: "Tests trust in unverified data: insecure deserialization, unsigned imports, type and structure confusion." },
    { id: "WEB_A09_2021_LOGGING", label: "A09:2021 Security Logging and Monitoring Failures", description: "Observable signs security events are not detected: no lockout, no rate limiting, log-affecting inputs." },
]

export const skillLabel = (id) => BUILT_IN_SKILLS.find(s => s.id === id)?.label || id

export const KNOWLEDGE_FILE_TYPES = ".txt,.md,.markdown,.json,.yaml,.yml,.csv,.xml,.html,.har"

// a knowledge base file: a new one carries its content; a saved one only its id (sha256) and size
function fileOf(f) {
    const file = { name: f.name }
    if (f.content) file.content = f.content
    if (f.sha256) file.sha256 = f.sha256
    file.size = f.size || (f.content || "").length
    return file
}

function linesOf(text) {
    return (text || "").split("\n").map(line => line.trim()).filter(Boolean)
}

function isWebUrl(value) {
    try {
        const url = new URL(value)
        return (url.protocol === "https:" || url.protocol === "http:") && !!url.hostname
    } catch (e) {
        return false
    }
}

export const emptyProfile = {
    hexId: null,
    name: "",
    description: "",
    skills: BUILT_IN_SKILLS.map(s => s.id), // a new agent starts with every built-in skill on
    customSkills: [],
    allowCreateIdentities: false,
    instructions: "",
    knowledgeFiles: [],
    knowledgeLinksText: "",
}

// the smart agent as the form edits it (links one per line)
export function toForm(profile) {
    if (!profile) return { ...emptyProfile }
    return {
        hexId: profile.hexId || null,
        name: profile.name || "",
        description: profile.description || "",
        skills: profile.skills || [],
        // skill files, like knowledge files: a saved one comes back as {name, sha256, size}, content stays in blob
        customSkills: (profile.customSkills || []).map(f => fileOf(f)),
        allowCreateIdentities: !!profile.allowCreateIdentities,
        instructions: profile.instructions || "",
        // a saved file comes back as its id (sha256) and size; its content stays in blob storage
        knowledgeFiles: (profile.knowledgeFiles || []).map(f => fileOf(f)),
        knowledgeLinksText: (profile.knowledgeLinks || []).join("\n"),
    }
}

// the form as the API takes it
export function fromForm(form) {
    return {
        hexId: form.hexId || undefined,
        name: form.name.trim(),
        description: form.description.trim(),
        skills: form.skills,
        customSkills: form.customSkills,
        allowCreateIdentities: form.allowCreateIdentities,
        instructions: form.instructions.trim(),
        knowledgeFiles: form.knowledgeFiles,
        knowledgeLinks: linesOf(form.knowledgeLinksText),
    }
}

// the first problem with the form, or null
export function validate(form) {
    if (!form.name.trim()) return "Give the smart agent a name"
    if (form.name.trim().length > LIMITS.name) return `The name can be at most ${LIMITS.name} characters`
    if (form.customSkills.length > LIMITS.skillFiles) return `Add at most ${LIMITS.skillFiles} skill files`
    const links = linesOf(form.knowledgeLinksText)
    if (links.length > LIMITS.links) return `Add at most ${LIMITS.links} links`
    const badLink = links.find(link => !isWebUrl(link))
    if (badLink) return `Not a web link: ${badLink}`
    if (form.instructions.length > LIMITS.instructions) return `Instructions can be at most ${LIMITS.instructions} characters`
    return null
}

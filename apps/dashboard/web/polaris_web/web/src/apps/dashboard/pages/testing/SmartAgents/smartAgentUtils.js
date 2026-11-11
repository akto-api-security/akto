// Limits match the backend (SmartAgentConfig / SmartAgentProfileAction).
export const LIMITS = {
    name: 80,
    description: 500,
    instructions: 2000,
    customSkill: 5000,
    files: 10,
    fileChars: 50000,
    links: 20,
}

// built-in skills (id, name, description). Test categories come from Run test.
export const BUILT_IN_SKILLS = [
    { id: "OBJECT_OWNERSHIP", label: "Object ownership", description: "Learns which objects belong to which identity, then tries each identity's ids as the others." },
    { id: "TENANT_ISOLATION", label: "Tenant isolation", description: "Uses other organizations', projects' or workspaces' ids in path, body, headers and query; scoped routes must stay in scope." },
    { id: "PRIVILEGE_ESCALATION", label: "Privilege escalation", description: "Targets role and permission fields, admin-only functions, invites, and API key or token creation." },
    { id: "HIDDEN_FIELDS", label: "Hidden fields", description: "Sends fields seen in responses or docs but not in the request (role, owner, status, price) and checks if they take effect." },
    { id: "WORKFLOW_ABUSE", label: "Workflow abuse", description: "Skips, repeats or reorders steps of a flow (checkout, approval, verification) and reuses one-time values." },
    { id: "BUSINESS_LIMITS", label: "Business limits", description: "Negative or huge quantities, prices, discounts, coupons, quotas and rounding." },
    { id: "DATA_EXPOSURE", label: "Data exposure", description: "Responses that give more than the caller needs: other users' personal data, secrets, tokens, internal fields." },
    { id: "API_DOCS", label: "Docs-driven testing", description: "Uses the API docs from Knowledge base links: undocumented behaviour, unlinked routes, role requirements." },
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
        customSkills: (profile.customSkills || []).map(s => ({ name: s.name, content: s.content || "" })),
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
        customSkills: form.customSkills.map(s => ({ name: s.name.trim(), content: s.content.trim() })).filter(s => s.name && s.content),
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
    const unnamedSkill = form.customSkills.find(s => !s.name.trim() || !s.content.trim())
    if (unnamedSkill) return "Give each custom skill a name and what it should do"
    const links = linesOf(form.knowledgeLinksText)
    if (links.length > LIMITS.links) return `Add at most ${LIMITS.links} links`
    const badLink = links.find(link => !isWebUrl(link))
    if (badLink) return `Not a web link: ${badLink}`
    if (form.instructions.length > LIMITS.instructions) return `Instructions can be at most ${LIMITS.instructions} characters`
    return null
}

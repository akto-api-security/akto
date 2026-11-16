// Custom skill files. Each entry in a smart agent's customSkills is one file, named by its path inside the
// imported folder ("skills/bola/SKILL.md"), or by its file name when uploaded on its own.
// Skills are grouped the way the smart-testing service groups them (app/skills/custom.py), so what the user sees
// is what the agent uses: a folder holding a SKILL.md is one skill (SKILL.md is its body, every other file under
// that folder is a reference), and any other file is a skill of its own.

export const SKILL_FILE_EXTENSIONS = [".md", ".markdown", ".txt", ".yaml", ".yml"]
export const SKILL_FILE_TYPES = SKILL_FILE_EXTENSIONS.join(",")
const ENTRY_FILE = "skill.md"

const basename = (path) => path.split("/").pop() || ""
const dirname = (path) => (path.includes("/") ? path.slice(0, path.lastIndexOf("/")) : "")
const stem = (path) => basename(path).replace(/\.[^.]+$/, "")

// a picked file's path inside the imported folder, or its name when it was picked on its own
export function pathOf(file) {
    return (file.webkitRelativePath || file.name || "").replace(/\\/g, "/").replace(/^(\.?\/)+/, "")
}

// whether a path is a skill file we import: a text file, not a hidden one such as .DS_Store
export function isSkillFile(path) {
    const name = basename(path)
    return !!name && !name.startsWith(".") && SKILL_FILE_EXTENSIONS.some(ext => name.toLowerCase().endsWith(ext))
}

// the skills in a flat list of skill files: [{key, name, files, size}], sorted by name
export function groupSkills(files) {
    const roots = files
        .map(f => f.name)
        .filter(path => basename(path).toLowerCase() === ENTRY_FILE)
        .map(dirname)
        .filter(Boolean) // a SKILL.md with no folder is a skill of its own and owns nothing
        .sort((a, b) => b.length - a.length) // the deepest folder owns the files under it
    const groups = new Map()
    for (const file of files) {
        const root = roots.find(r => file.name.startsWith(r + "/"))
        const key = root || file.name
        if (!groups.has(key)) {
            groups.set(key, { key, name: root ? basename(root) : stem(file.name) || "skill", files: [] })
        }
        groups.get(key).files.push(file)
    }
    return [...groups.values()]
        .map(skill => ({ ...skill, size: skill.files.reduce((total, f) => total + (f.size || 0), 0) }))
        .sort((a, b) => a.name.localeCompare(b.name))
}

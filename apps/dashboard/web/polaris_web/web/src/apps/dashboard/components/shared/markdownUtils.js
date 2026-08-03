// Strip links from markdown/text so skill data renders as plain text with no navigable links.
// Keeps the visible text, drops anything that would take the user elsewhere.
export function stripMarkdownLinks(text) {
    if (!text) return text;
    return text
        // Markdown images: ![alt](url) -> alt
        .replace(/!\[([^\]]*)\]\([^)]*\)/g, "$1")
        // Markdown links: [text](url) -> text  (covers http(s) and relative targets like ../a/SKILL.md)
        .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
        // Reference-style links: [text][ref] -> text
        .replace(/\[([^\]]*)\]\[[^\]]*\]/g, "$1")
        // Autolinks: <http://...> -> (removed)
        .replace(/<https?:\/\/[^>]*>/gi, "")
        // Bare URLs
        .replace(/\bhttps?:\/\/\S+/gi, "");
}

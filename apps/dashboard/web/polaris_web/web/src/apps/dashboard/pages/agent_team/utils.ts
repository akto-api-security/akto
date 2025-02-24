import { Editor } from "@tiptap/react";
import { fromHtml } from 'hast-util-from-html';
import { toMdast } from 'hast-util-to-mdast';

import { toMarkdown } from 'mdast-util-to-markdown';
import { gfm } from 'micromark-extension-gfm';
import { gfmToMarkdown } from 'mdast-util-gfm';
import { PromptContent } from "./types";    


export const getPromptContent = (editor: Editor): PromptContent => {
    const html = editor.getHTML();
    const hast = fromHtml(html);
    const mdast = toMdast(hast);

    const markdown = toMarkdown(mdast, {
        extensions: [gfmToMarkdown()],
    });

    return {
        html,
        markdown,
    };
};

import { Editor } from "@tiptap/react";
import { fromHtml } from 'hast-util-from-html';
import { toMdast } from 'hast-util-to-mdast';

import { toMarkdown } from 'mdast-util-to-markdown';
import { gfm } from 'micromark-extension-gfm';
import { gfmToMarkdown } from 'mdast-util-gfm';
import { AgentLog, AgentSubprocess, PromptContent, State } from "./types";    


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


export const dummySubprocess: AgentSubprocess = {
    processId: '1',
    subprocessId: '1',
    subprocessHeading: 'Subprocess Heading',
    userInput: {},
    createdTimestamp: 1714204800,
    startedTimestamp: 1714204800,
    endedTimestamp: 1714204800,
    state: State.RUNNING,
    logs: [],
    attemptId: '1',
    processOutput: {},
}

const logs: AgentLog[] = []
const subprocess = {
    ...dummySubprocess,
    logs,
};

export const getFakeSubProcess = (subprocessId: string, processId: string) => {
    // Create a new subprocess instance for each call
    const subprocess: AgentSubprocess = {
        ...dummySubprocess,
        processId,
        subprocessId,
        logs: [],
    };

    return subprocess;
}

export const appendLog = (subprocess: AgentSubprocess) => {
    const newSubprocess = {
        ...subprocess,
        logs: [
            ...subprocess.logs,
            {
                log: `A vertically stacked set of interactive headings that each reveal an associated section of content.`,
                eventTimestamp: 1714204800 + subprocess.logs.length * 1000,
            }
        ]
    };
    return newSubprocess;
}
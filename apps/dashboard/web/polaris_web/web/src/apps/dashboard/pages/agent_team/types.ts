export type Model = {
    id: string;
    name: string;
}

export type Agent = {
    id: string;
    name: string;
    description: string;
    image: string;
}

export type PromptContent = {
    html: string;
    markdown: string;
}

export type PromptPayload = {
    model: Model;
    prompt: PromptContent;
}
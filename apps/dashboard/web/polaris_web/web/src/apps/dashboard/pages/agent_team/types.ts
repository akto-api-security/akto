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

export type AgentState = 'paused' | 'idle' | 'thinking' | 'error';

export enum State {
    STOPPED = 'STOPPED',
    RUNNING = 'RUNNING',
    COMPLETED = 'COMPLETED',
    SCHEDULED = 'SCHEDULED',
    FAILED = 'FAILED',
    DISCARDED = 'DISCARDED',
    ACCEPTED = 'ACCEPTED'
}

export type AgentRun = {
    processId: string;
    agentInitDocument?: Record<string, any>;
    agent: Agent;
    createdTimestamp: number;
    startedTimestamp: number;
    endedTimestamp: number;
    state: State;
}

export type AgentLog = {
    log: string;
    eventTimestamp: number;
}

export type AgentSubprocess = {
    processId: string;
    subprocessId: string;
    subprocessHeading: string;
    userInput: Record<string, any>;
    createdTimestamp: number;
    startedTimestamp: number;
    endedTimestamp: number;
    state: State;
    logs: AgentLog[];
    attemptId: string;
    processOutput: Record<string, any>;
}


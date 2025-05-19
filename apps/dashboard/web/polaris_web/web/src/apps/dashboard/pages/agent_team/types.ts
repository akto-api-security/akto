export type Model = {
    id: string;
    name: string;
}

export type RepoType = {
    id: string;
    logo: string;
    text: string;
    onClickFunc: () => void;
}

export type RepoPayload = {
    repo: string;
    project: string;
    lastRun: number;
    scheduleTime: number;
    accessToken: string | null;
}

export type RepoNodeType = {
    name: string;
    nameWithOwner: string;
    isPrivate: boolean;
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

export type AgentState = 'paused' | 'idle' | 'thinking' | 'error' | 'stopped' | 'completed';

export enum State {
    STOPPED = 'STOPPED',
    RUNNING = 'RUNNING',
    COMPLETED = 'COMPLETED',
    SCHEDULED = 'SCHEDULED',
    FAILED = 'FAILED',
    DISCARDED = 'DISCARDED',
    ACCEPTED = 'ACCEPTED',
    PENDING = 'PENDING',
    RE_ATTEMPT = "RE_ATTEMPT",
    AGENT_ACKNOWLEDGED = "AGENT_ACKNOWLEDGED",
}

export type AgentRun = {
    processId: string;
    agentInitDocument?: Record<string, any>;
    agent: string;
    createdTimestamp: number;
    startTimestamp: number;
    endTimestamp: number;
    state: State;
}

export type AgentLog = {
    log: string;
    eventTimestamp: number;
}

export type AgentSubprocess = {
    processId: string;
    subProcessId: string;
    subProcessHeading: string | null;
    userInput: Record<string, any> | null;
    createdTimestamp: number;
    startTimestamp: number;
    endTimestamp: number;
    state: State;
    logs: AgentLog[] | null;
    attemptId: number;
    processOutput: Record<string, any> | null;
}

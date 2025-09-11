import { create } from 'zustand'
import { devtools } from 'zustand/middleware'

const initialState = {
    promptsObj: {},
    selectedPrompt: {},
    currentContent: '',
    testResponse: null,
    isTestRunning: false,
    selectedModel: 'gpt-4',
    temperature: 0.7,
    maxTokens: 2048
}

const PromptPlaygroundStore = create(
    devtools((set) => ({
        ...initialState,
        setPromptsObj: (promptsObj) => set({ promptsObj }),
        setSelectedPrompt: (selectedPrompt) => set({ selectedPrompt }),
        setCurrentContent: (currentContent) => set({ currentContent }),
        setTestResponse: (testResponse) => set({ testResponse }),
        setIsTestRunning: (isTestRunning) => set({ isTestRunning }),
        setSelectedModel: (selectedModel) => set({ selectedModel }),
        setTemperature: (temperature) => set({ temperature }),
        setMaxTokens: (maxTokens) => set({ maxTokens })
    }), { name: 'PromptPlaygroundStore' })
)

export default PromptPlaygroundStore
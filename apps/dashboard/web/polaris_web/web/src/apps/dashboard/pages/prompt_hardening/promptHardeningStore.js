import { create } from 'zustand'
import { devtools } from 'zustand/middleware'
import { devtoolsOptions } from '@/apps/main/devtoolsConfig'

const initialState = {
    promptsObj: {},
    selectedPrompt: {},
    currentContent: '',
    testResponse: null,
    isTestRunning: false,
    selectedModel: 'gpt-4',
    temperature: 0.7,
    maxTokens: 2048,
    triggerTest: false
}

const PromptHardeningStore = create(
    devtools((set) => ({
        ...initialState,
        setPromptsObj: (promptsObj) => set({ promptsObj }),
        setSelectedPrompt: (selectedPrompt) => set({ selectedPrompt }),
        setCurrentContent: (currentContent) => set({ currentContent }),
        setTestResponse: (testResponse) => set({ testResponse }),
        setIsTestRunning: (isTestRunning) => set({ isTestRunning }),
        setSelectedModel: (selectedModel) => set({ selectedModel }),
        setTemperature: (temperature) => set({ temperature }),
        setMaxTokens: (maxTokens) => set({ maxTokens }),
        setTriggerTest: (triggerTest) => set({ triggerTest })
    }), devtoolsOptions('PromptHardeningStore'))
)

export default PromptHardeningStore
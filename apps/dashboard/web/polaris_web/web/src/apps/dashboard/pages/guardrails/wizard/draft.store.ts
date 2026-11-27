import { create } from 'zustand';
import { BLUEPRINTS, createInitialDraft } from './api/fixtures';
import {
  BlueprintId,
  EnforcementMode,
  GuardrailPolicyDraft,
  HarmCategory,
  HarmLevel,
  Sensitivity,
  Severity,
  WizardStep,
} from './types';

const HARM_ORDER: HarmLevel[] = ['Off', 'Low', 'Medium', 'High'];
const MAX_TOPICS = 30;

interface GuardrailDraftStore {
  draft: GuardrailPolicyDraft;
  playgroundTestIndex: number;
  visitedSteps: WizardStep[];
  setField: <K extends keyof GuardrailPolicyDraft>(key: K, value: GuardrailPolicyDraft[K]) => void;
  applyBlueprint: (id: BlueprintId) => void;
  setSeverity: (severity: Severity) => void;
  toggleProtection: (key: keyof GuardrailPolicyDraft['protections']) => void;
  setSensitivity: (key: 'promptInjection' | 'contextPoisoning', sensitivity: Sensitivity) => void;
  addTopic: (topic: string) => void;
  removeTopic: (topic: string) => void;
  cycleHarm: (category: HarmCategory) => void;
  setScope: (field: keyof GuardrailPolicyDraft['scope'], value: string) => void;
  setMode: (mode: EnforcementMode) => void;
  setStagedRollout: (on: boolean) => void;
  toggleInspect: (field: 'requests' | 'responses') => void;
  setPlaygroundTestIndex: (index: number) => void;
  markVisited: (step: WizardStep) => void;
  resetDraft: () => void;
}

export const useGuardrailDraftStore = create<GuardrailDraftStore>((set, get) => ({
  draft: createInitialDraft(),
  playgroundTestIndex: 1,
  visitedSteps: ['blueprint'],

  setField: (key, value) =>
    set((state) => ({ draft: { ...state.draft, [key]: value } })),

  applyBlueprint: (id) => {
    const bp = BLUEPRINTS.find((b) => b.id === id);
    if (!bp) return;
    set((state) => ({
      draft: {
        ...state.draft,
        blueprintId: id,
        protections: {
          promptInjection: { ...bp.defaults.protections.promptInjection },
          contextPoisoning: { ...bp.defaults.protections.contextPoisoning },
          deniedTopics: {
            enabled: bp.defaults.protections.deniedTopics.enabled,
            topics: [...bp.defaults.protections.deniedTopics.topics],
          },
          harmfulCategories: {
            enabled: bp.defaults.protections.harmfulCategories.enabled,
            thresholds: { ...bp.defaults.protections.harmfulCategories.thresholds },
          },
        },
      },
    }));
  },

  setSeverity: (severity) =>
    set((state) => ({ draft: { ...state.draft, severity } })),

  toggleProtection: (key) =>
    set((state) => ({
      draft: {
        ...state.draft,
        protections: {
          ...state.draft.protections,
          [key]: {
            ...state.draft.protections[key],
            enabled: !state.draft.protections[key].enabled,
          },
        },
      },
    })),

  setSensitivity: (key, sensitivity) =>
    set((state) => ({
      draft: {
        ...state.draft,
        protections: {
          ...state.draft.protections,
          [key]: { ...state.draft.protections[key], sensitivity },
        },
      },
    })),

  addTopic: (topic) => {
    const trimmed = topic.trim();
    if (!trimmed) return;
    const { topics } = get().draft.protections.deniedTopics;
    if (topics.includes(trimmed) || topics.length >= MAX_TOPICS) return;
    set((state) => ({
      draft: {
        ...state.draft,
        protections: {
          ...state.draft.protections,
          deniedTopics: {
            ...state.draft.protections.deniedTopics,
            topics: [...state.draft.protections.deniedTopics.topics, trimmed],
          },
        },
      },
    }));
  },

  removeTopic: (topic) =>
    set((state) => ({
      draft: {
        ...state.draft,
        protections: {
          ...state.draft.protections,
          deniedTopics: {
            ...state.draft.protections.deniedTopics,
            topics: state.draft.protections.deniedTopics.topics.filter((t) => t !== topic),
          },
        },
      },
    })),

  cycleHarm: (category) =>
    set((state) => {
      const current = state.draft.protections.harmfulCategories.thresholds[category];
      const next = HARM_ORDER[(HARM_ORDER.indexOf(current) + 1) % HARM_ORDER.length];
      return {
        draft: {
          ...state.draft,
          protections: {
            ...state.draft.protections,
            harmfulCategories: {
              ...state.draft.protections.harmfulCategories,
              thresholds: {
                ...state.draft.protections.harmfulCategories.thresholds,
                [category]: next,
              },
            },
          },
        },
      };
    }),

  setScope: (field, value) =>
    set((state) => ({
      draft: {
        ...state.draft,
        scope: { ...state.draft.scope, [field]: value },
      },
    })),

  setMode: (mode) =>
    set((state) => ({
      draft: {
        ...state.draft,
        mode,
        stagedRollout: mode === 'alert' ? false : state.draft.stagedRollout,
      },
    })),

  setStagedRollout: (on) =>
    set((state) => {
      if (state.draft.mode === 'alert') return state;
      return { draft: { ...state.draft, stagedRollout: on } };
    }),

  toggleInspect: (field) =>
    set((state) => ({
      draft: {
        ...state.draft,
        inspect: {
          ...state.draft.inspect,
          [field]: !state.draft.inspect[field],
        },
      },
    })),

  setPlaygroundTestIndex: (index) => set({ playgroundTestIndex: index }),

  markVisited: (step) =>
    set((state) =>
      state.visitedSteps.includes(step)
        ? state
        : { visitedSteps: [...state.visitedSteps, step] }
    ),

  resetDraft: () =>
    set({
      draft: createInitialDraft(),
      playgroundTestIndex: 1,
      visitedSteps: ['blueprint'],
    }),
}));

import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import WizardShell from './components/WizardShell';
import BlueprintStep from './steps/BlueprintStep';
import BlueprintAside from './steps/BlueprintAside';
import ProtectionsStep from './steps/ProtectionsStep';
import ProtectionsAside from './steps/ProtectionsAside';
import ScopeStep from './steps/ScopeStep';
import ScopeAside from './steps/ScopeAside';
import { createPolicy } from './api/guardrailPolicyApi';
import { useGuardrailDraftStore } from './draft.store';
import {
  getHeaderSubtitle,
  getHeaderTitle,
  getNavGroups,
  getProgress,
  isDraftValid,
  STEP_PATHS,
  stepFromPath,
} from './selectors';
import { WizardStep } from './types';
import func from '@/util/func';

export default function CreatePolicyWizard() {
  const location = useLocation();
  const navigate = useNavigate();
  const step = stepFromPath(location.pathname);
  const draft = useGuardrailDraftStore((s) => s.draft);
  const markVisited = useGuardrailDraftStore((s) => s.markVisited);
  const resetDraft = useGuardrailDraftStore((s) => s.resetDraft);
  const setField = useGuardrailDraftStore((s) => s.setField);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    markVisited(step);
  }, [step, markVisited]);

  if (location.pathname.endsWith('/new') || location.pathname.endsWith('/new/')) {
    return <Navigate to={STEP_PATHS.blueprint} replace />;
  }

  const progress = getProgress(step, draft);
  const groups = getNavGroups(draft, step);

  const go = (next: WizardStep) => navigate(STEP_PATHS[next]);

  const handleClose = () => {
    resetDraft();
    navigate('/dashboard/guardrails/policies');
  };

  const handleCreate = async (activate: boolean) => {
    if (!isDraftValid(draft)) return;
    setCreating(true);
    try {
      const payload = { ...draft, activateOnCreate: activate };
      setField('activateOnCreate', activate);
      console.log('[CreatePolicyWizard] draft', payload);
      await createPolicy(payload);
      func.setToast(
        true,
        false,
        activate ? 'Guardrail policy created' : 'Guardrail policy saved as inactive'
      );
      resetDraft();
      navigate('/dashboard/guardrails/policies');
    } catch (err) {
      console.error(err);
      func.setToast(true, true, 'Failed to create guardrail policy');
    } finally {
      setCreating(false);
    }
  };

  const yamlLabel = step === 'blueprint' ? 'Import YAML' : 'View as YAML';

  let main = null;
  let aside = null;
  let footer = null;

  if (step === 'blueprint') {
    main = (
      <BlueprintStep
        onCancel={handleClose}
        onNext={() => go('protections')}
        onCreate={() => handleCreate(true)}
        creating={creating}
      />
    );
    aside = <BlueprintAside />;
    footer = null;
  } else if (step === 'protections') {
    main = (
      <ProtectionsStep
        onPrevious={() => go('blueprint')}
        onNext={() => go('scope')}
      />
    );
    aside = <ProtectionsAside />;
    footer = null;
  } else {
    main = (
      <ScopeStep
        onPrevious={() => go('protections')}
        onCreate={handleCreate}
        creating={creating}
      />
    );
    aside = <ScopeAside />;
    footer = null;
  }

  // Footers are embedded in each step to match the design (buttons sit in the main column).
  // WizardShell still reserves a footer slot; keep it empty for now.
  return (
    <WizardShell
      title={getHeaderTitle(draft, step)}
      subtitle={getHeaderSubtitle(draft, step)}
      yamlLabel={yamlLabel}
      onYamlClick={() => func.setToast(true, false, `${yamlLabel} coming soon`)}
      groups={groups}
      progressLabel={progress.label}
      progressPct={progress.pct}
      progressComplete={progress.complete}
      main={main}
      aside={aside}
      footer={footer}
      onClose={handleClose}
    />
  );
}

import { ReactNode, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, HorizontalStack, Icon } from '@shopify/polaris';
import { CancelMajor } from '@shopify/polaris-icons';
import SectionNav from './SectionNav';
import { NavGroup } from '../types';
import '../wizard.css';
import '../../components/createGuardrailPage.css';

interface WizardShellProps {
  title: string;
  subtitle: string;
  yamlLabel: string;
  onYamlClick?: () => void;
  groups: NavGroup[];
  progressLabel: string;
  progressPct: number;
  progressComplete?: boolean;
  main: ReactNode;
  aside: ReactNode;
  footer: ReactNode;
  onClose?: () => void;
}

function ShieldIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3 4 6v6c0 5 3.4 8.3 8 9 4.6-.7 8-4 8-9V6z" />
    </svg>
  );
}

export default function WizardShell({
  title,
  subtitle,
  yamlLabel,
  onYamlClick,
  groups,
  progressLabel,
  progressPct,
  progressComplete,
  main,
  aside,
  footer,
  onClose,
}: WizardShellProps) {
  const navigate = useNavigate();

  useEffect(() => {
    document.body.classList.add('guardrail-page-open');
    return () => document.body.classList.remove('guardrail-page-open');
  }, []);

  const handleClose = () => {
    if (onClose) onClose();
    else navigate('/dashboard/guardrails/policies');
  };

  return (
    <div className="guardrail-page-wrapper gw-wizard">
      <div className="guardrail-page-header">
        <HorizontalStack gap="3" blockAlign="center">
          <span className="gw-header-icon">
            <ShieldIcon />
          </span>
          <div>
            <h1 className="gw-header-title">{title}</h1>
            <div className="gw-header-sub">{subtitle}</div>
          </div>
        </HorizontalStack>
        <HorizontalStack gap="2" blockAlign="center">
          <Button onClick={onYamlClick}>{yamlLabel}</Button>
          <button type="button" className="gw-close-btn" aria-label="Close" onClick={handleClose}>
            <Icon source={CancelMajor} />
          </button>
        </HorizontalStack>
      </div>

      <div className="guardrail-page-container">
        <div className="guardrail-sidebar">
          <SectionNav
            groups={groups}
            progressLabel={progressLabel}
            progressPct={progressPct}
            progressComplete={progressComplete}
          />
        </div>

        <div className="guardrail-content">
          <div className="guardrail-content-inner">{main}</div>
          {footer ? <div className="guardrail-content-footer">{footer}</div> : null}
        </div>

        <aside className="guardrail-playground" aria-label="Policy preview and playground">
          {aside}
        </aside>
      </div>
    </div>
  );
}

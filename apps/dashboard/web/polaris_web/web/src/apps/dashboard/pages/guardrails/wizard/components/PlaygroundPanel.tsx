import { ReactNode, useState } from 'react';
import { HorizontalStack, Text } from '@shopify/polaris';
import { UserBubble } from './ChatBubble';
import { PlaygroundTest } from '../types';

interface PlaygroundPanelProps {
  title?: string;
  promptContent: ReactNode;
  test: PlaygroundTest;
  quickTests?: Array<{ text: string; selected: boolean; onSelect: () => void }>;
  onRun?: (prompt: string) => void | Promise<void>;
  footer?: ReactNode;
}

export default function PlaygroundPanel({
  title = 'Playground',
  promptContent,
  test,
  quickTests,
  onRun,
  footer,
}: PlaygroundPanelProps) {
  const [input, setInput] = useState('');
  const kindClass = test.kind === 'block' ? 'is-block' : test.kind === 'allow' ? 'is-allow' : '';

  const handleRun = async () => {
    const value = input.trim();
    if (!value || !onRun) return;
    await onRun(value);
    setInput('');
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, flex: 1 }}>
      <HorizontalStack align="space-between" blockAlign="center">
        <Text as="h2" variant="headingSm">{title}</Text>
        <span className="gw-live-dot">Live against draft</span>
      </HorizontalStack>

      <div className="gw-preview-panel" style={{ minHeight: quickTests ? 180 : undefined }}>
        {typeof promptContent === 'string' ? (
          <UserBubble maxWidth="92%">{promptContent}</UserBubble>
        ) : (
          promptContent
        )}
        <div className={`gw-verdict-card ${kindClass}`}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
            <span className={`gw-verdict-badge ${kindClass}`}>{test.verdict}</span>
            <span style={{ fontSize: 11.5, color: 'var(--gw-muted)' }}>{test.meta}</span>
          </div>
          <div style={{ fontSize: 13, fontWeight: 600 }}>{test.rule}</div>
          <div style={{ fontSize: 12.5, color: 'var(--gw-text-secondary)' }}>{test.detail}</div>
        </div>
      </div>

      {quickTests && quickTests.length > 0 && (
        <>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--gw-muted)' }}>Try a quick test</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {quickTests.map((q) => (
              <button
                key={q.text}
                type="button"
                className={`gw-quick-test ${q.selected ? 'is-selected' : ''}`}
                onClick={q.onSelect}
              >
                {q.text}
              </button>
            ))}
          </div>
        </>
      )}

      <label className="gw-prompt-input" style={{ marginTop: 'auto' }}>
        <span className="gw-sr-only">Test prompt</span>
        <input
          type="text"
          placeholder="Type a prompt to test this policy…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              handleRun();
            }
          }}
        />
        <button type="button" aria-label="Run test" className="gw-prompt-send" onClick={handleRun}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden="true">
            <path d="M12 19V5" />
            <path d="m5 12 7-7 7 7" />
          </svg>
        </button>
      </label>

      {footer}
    </div>
  );
}

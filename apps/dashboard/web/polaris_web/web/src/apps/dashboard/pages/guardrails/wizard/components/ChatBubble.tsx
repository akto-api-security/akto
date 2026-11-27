import { ReactNode } from 'react';

export function UserBubble({ children, maxWidth = '80%' }: { children: ReactNode; maxWidth?: string }) {
  return (
    <div className="gw-chat-user" style={{ maxWidth }}>
      {children}
    </div>
  );
}

export function AiBubble({ children, maxWidth = '85%' }: { children: ReactNode; maxWidth?: string }) {
  return (
    <div className="gw-chat-ai-row">
      <span className="gw-chat-avatar" aria-hidden="true">AI</span>
      <div className="gw-chat-ai" style={{ maxWidth }}>{children}</div>
    </div>
  );
}

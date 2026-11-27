import { ReactNode } from 'react';

interface DarkSummaryCardProps {
  children: ReactNode;
}

export default function DarkSummaryCard({ children }: DarkSummaryCardProps) {
  return <div className="gw-dark-card">{children}</div>;
}

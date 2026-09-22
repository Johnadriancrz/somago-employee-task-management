import type { ReactNode } from "react";

export function PageHeader({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-space-lg flex-wrap mb-space-lg">
      <div>
        <h1 className="text-headline-lg text-on-surface tracking-tight">{title}</h1>
        <p className="text-body-sm text-secondary mt-0.5">{description}</p>
      </div>
      {action}
    </div>
  );
}

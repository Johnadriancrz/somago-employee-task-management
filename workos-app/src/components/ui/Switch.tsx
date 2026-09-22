export function Switch({ checked, onChange }: { checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={`w-9 h-5 rounded-full shrink-0 transition-colors relative ${
        checked ? "bg-primary" : "bg-surface-container-highest"
      }`}
    >
      <span
        className={`absolute top-0.5 w-4 h-4 rounded-full bg-canvas-bg shadow-sm transition-transform ${
          checked ? "translate-x-[18px]" : "translate-x-0.5"
        }`}
      />
    </button>
  );
}

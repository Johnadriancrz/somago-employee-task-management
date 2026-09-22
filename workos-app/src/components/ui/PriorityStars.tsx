import { Star } from "lucide-react";

export function PriorityStars({ priority }: { priority: number }) {
  return (
    <div className="flex items-center gap-0.5 text-status-working/60">
      {Array.from({ length: 5 }, (_, i) => (
        <Star
          key={i}
          size={13}
          className={i < priority ? "fill-current" : "fill-none text-outline-variant/70"}
          strokeWidth={i < priority ? 0 : 1.5}
        />
      ))}
    </div>
  );
}

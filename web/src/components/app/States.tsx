import { AlertTriangle, Inbox, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** Soft skeleton rows shaped like a grouped list. */
export function LoadingState({ rows = 4 }: { rows?: number }) {
  return (
    <div className="px-4 py-2" role="status" aria-label="加载中">
      <div className="overflow-hidden rounded-[14px] bg-surface">
        {Array.from({ length: rows }, (_, i) => (
          <div key={i} className="flex items-center gap-3 px-4 py-3">
            <div className="size-[30px] shimmer rounded-[8px]" />
            <div className="flex-1 space-y-2">
              <div className="shimmer h-3 w-2/5 rounded" />
              <div className="shimmer h-2.5 w-1/4 rounded" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Line icon + serif title + one quiet sentence (+ optional action). No big emoji. */
export function EmptyState({
  icon: Icon = Inbox,
  title,
  hint,
  action,
  className,
}: {
  icon?: LucideIcon;
  /** @deprecated v1 prop, ignored — use `icon`. */
  emoji?: string;
  title: string;
  hint?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-center px-8 py-14 text-center", className)}>
      <Icon className="mb-4 size-12 text-ink-3" strokeWidth={1.25} aria-hidden />
      <p className="font-serif text-[20px] font-semibold leading-[26px] text-ink">{title}</p>
      {hint && <p className="mt-1.5 max-w-[17rem] text-[14px] leading-[20px] text-ink-2">{hint}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ErrorState({ onRetry, message = "小票机卡纸了，稍后再试试" }: { onRetry?: () => void; message?: string }) {
  return (
    <div className="flex flex-col items-center px-8 py-14 text-center" role="alert">
      <AlertTriangle className="mb-4 size-11 text-ink-3" strokeWidth={1.25} aria-hidden />
      <p className="font-serif text-[20px] font-semibold text-ink">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-5 h-9 rounded-[12px] bg-fill px-4 text-[15px] font-medium text-ink transition-transform duration-100 ease-[var(--ease-out)] active:scale-[0.97]"
        >
          重试
        </button>
      )}
    </div>
  );
}

export { Inbox };

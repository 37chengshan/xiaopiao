import { AlertTriangle, Inbox } from "lucide-react";
import type { ReactNode } from "react";
import { Skeleton } from "@/components/ui/skeleton";

export function LoadingState({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-3 px-4 py-4" role="status" aria-label="加载中">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-16 w-full rounded-2xl" />
      ))}
    </div>
  );
}

export function EmptyState({ emoji = "🧾", title, hint, action }: { emoji?: string; title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-8 py-14 text-center">
      <div className="mb-3 text-5xl" aria-hidden>
        {emoji}
      </div>
      <p className="text-base font-semibold text-ink">{title}</p>
      {hint && <p className="mt-1 text-sm text-ink-2">{hint}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ErrorState({ onRetry, message = "小票机卡纸了，稍后再试试" }: { onRetry?: () => void; message?: string }) {
  return (
    <div className="flex flex-col items-center px-8 py-14 text-center" role="alert">
      <AlertTriangle className="mb-3 size-10 text-stamp" aria-hidden />
      <p className="text-base font-semibold text-ink">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="mt-4 rounded-full bg-coral px-5 py-2 text-sm font-semibold text-white active:scale-95">
          重试
        </button>
      )}
    </div>
  );
}

export { Inbox };

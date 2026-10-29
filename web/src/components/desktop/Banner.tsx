import { X } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { IconButton } from "./ui";
import { s } from "./strings";

/**
 * Level 3 feedback (docs/APP-DESIGN.md §7.7): a 36px strip under the toolbar, fill background,
 * icon + one sentence + one action + dismiss. Never auto-dismisses; the owner removes it when solved.
 */
export function Banner({
  icon: Icon,
  children,
  action,
  onDismiss,
  className,
}: {
  icon: LucideIcon;
  children: ReactNode;
  action?: ReactNode;
  onDismiss?: () => void;
  className?: string;
}) {
  return (
    <div role="status" className={cn("flex h-9 shrink-0 items-center gap-2 bg-d-fill px-4 text-[12px] text-d-ink-2 lg:px-6", className)}>
      <Icon className="size-3.5 shrink-0 text-d-ink-3" strokeWidth={1.8} aria-hidden />
      <span className="min-w-0 truncate">{children}</span>
      <div className="ml-auto flex shrink-0 items-center gap-1.5">
        {action}
        {onDismiss && <IconButton icon={X} label={s("notice.dismiss")} onClick={onDismiss} className="size-6" />}
      </div>
    </div>
  );
}
